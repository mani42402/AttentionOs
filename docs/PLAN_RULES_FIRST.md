# Plan: rules first, learning as the suggestion layer

## Why the architecture changes

Measurement, not preference. The classifier scores **48% on notifications unlike anything it was
written for** (`CorpusEvaluationTest.scoreHeldOutCorpus`, 135 cases, 17 languages). Every attempt to
raise that — more descriptions, a different encoder, confidence abstention — moved along one
trade-off curve. That is the ceiling of asking a 128-dimensional lookup table "is this important?".

Meanwhile two signals sitting in the same codebase are near-perfectly reliable and were being wasted:

- **A rule the user wrote.** "Ring for Ammi even on silent" fires 100% of the time, in any language,
  forever. No model, no vocabulary, no drift.
- **Who the person is.** Reply-in-under-a-minute is a stronger signal than any sentence match, and it
  cannot be out-of-vocabulary.

BuzzKill (~$3.99, one developer, Android-only, no AI at all) is market proof: it never answers "is
this important?" — it deletes the question by handing it to the user, and people love it. What it
lacks is the app *noticing* the pattern and offering the rule. That gap is the product.

So: rules become the backbone, learning becomes the thing that proposes rules, and the classifier
ranks only what no rule and no person signal covered.

## The invariant that changes, and how it stays honest

Today `AttentionNotificationListener` carries a hard promise:

> Never cancel, replace, snooze, or otherwise modify the source notification.

Mute, snooze and batch break that promise, and it was a good promise. It is replaced by a narrower
one that keeps the substance:

> **The app never hides a notification on its own judgement. It hides only what the user's own rule
> tells it to hide, and every such action is logged, visible, and reversible in one tap.**

Concretely, enforced in code rather than by intention:

- `MUTE`, `SNOOZE` and `BATCH` are reachable **only** from a `Rule` whose `source` is `USER` or
  `SUGGESTION_ACCEPTED`. The classifier has no code path to them — the function that cancels a
  notification takes a `RuleId`, so there is nothing to call without one.
- Every suppression writes `firedRuleId` onto the event row, so "why did I not see this?" always has
  an answer.
- A **Hidden by your rules** screen lists everything suppressed, with the rule that did it and an
  undo.
- Safety floors (calls, alarms, verified security and finance) can never be muted by any rule.
  Attempting it is refused at rule-creation time with a plain explanation.

## Target decision pipeline

Strict order, first match wins, and each stage may only be overridden by one above it.

```
1  User rules            person / app / phrase / time  ->  alert-on-silent, vibrate,
                                                           mute, batch, snooze, auto-reply
2  Safety floors         calls, alarms, verified security + finance   (nothing may lower these)
3  Person signal         reply speed, open rate, interaction count    (may raise)
4  App signal            engagement rate with that package            (may raise)
5  Message classifier    ~50 descriptions, nearest band               (ranks the remainder)
6  Default               deliver untouched
```

The 48% component now does the smallest job instead of the whole job. That is the point: its errors
land on notifications nothing else had an opinion about.

## Stages

Each stage ships on its own and leaves the app working. No stage depends on a later one.

### Stage 1 — rule model and engine (backbone)

- `rules` table, encrypted with the rest of the database. Schema v10.
- `RuleEngine.evaluate(signal, senderHash, now): RuleMatch?` — pure, no Android dependencies, unit
  testable without a device.
- Deterministic precedence when several rules match: explicit `order` column, ties broken by
  specificity (person > app+phrase > app > phrase > time), then by `createdAt`. Documented and tested,
  because "why did the wrong rule win" is the first support question a rule engine generates.
- Indexed lookup by `packageName` and `senderHash`, so evaluation cost is proportional to *matching*
  rules rather than to how many the user has. Budget: < 0.5 ms at 100 rules, asserted in a test.
- No UI yet. Rules are created in tests.

**Exit:** engine unit tests including precedence and conflict cases; measured evaluation cost; schema
v10 migration test.

### Stage 2 — actions

- `ALERT_ALWAYS` — our own sound on the alarm stream so it survives silent mode.
- `VIBRATE_PATTERN` — reuses the existing `InterruptionController` patterns.
- `MUTE` — `cancelNotification(key)`, event row marked with the rule.
- `SNOOZE` — `snoozeNotification(key, minutes)`, API 26, our whole floor.
- `BATCH` — cancel, store, repost as one summary at a chosen time via the existing WorkManager setup.
- **Hidden by your rules** screen with undo.

**Exit:** each action verified on device; instrumented test that a muted notification is recorded with
its rule id; instrumented test that a safety-floor notification cannot be muted.

### Stage 3 — reply

Messaging apps attach their own reply action with a `RemoteInput`. We fill that field and fire *their*
`PendingIntent`. **The message is sent by WhatsApp, through WhatsApp.** Our app has no `INTERNET`
permission and cannot send anything itself — that is worth saying in the store listing.

- Capability detection per notification: does it carry an action with `remoteInputs`?
- Inline reply from our review screen.
- Rule action `AUTO_REPLY` with a fixed text, **off by default**, opt-in per rule.
- Replying marks the notification handled, which feeds the person signal.

Deliberate limits, stated in the UI rather than discovered: text only; only apps that expose a reply
action; a notification already dismissed cannot be replied to.

**Exit:** verified against WhatsApp, Telegram and SMS on device; graceful message when unsupported.

### Stage 4 — onboarding seed

- Up to **5 people** you must never miss, up to **3 always-alert apps**. Everything skippable.
- Each selection creates a visible, editable rule — so the user's first experience of a rule is one
  they made without writing anything.
- Skipping is a first-class path: the app runs in pure observation.
- Contact picker needs `READ_CONTACTS`, which is a real privacy cost. **Fallback: seed from senders
  already seen in the shade, no permission required.** Contacts stays an optional convenience.

**Exit:** onboarding creates working rules; skipping leaves a functional app; no new mandatory
permission.

### Stage 5 — the suggestion layer (the differentiator)

The learning stops acting silently and starts proposing.

- Evidence thresholds before any suggestion: ≥ 5 interactions, ≥ 80% one-sided, ≥ 3 distinct days.
  Nothing suggested from a single afternoon.
- Suggestion kinds: promote a person, quieten an app, batch an app, quiet-hours pattern.
- "You reply to Bilal within a minute, every time. Ring on silent for him?" → one tap creates a rule
  he can see, edit and delete.
- **Never more than 2 pending suggestions**, never re-proposed once dismissed, permanently.
- Accepted suggestions become rules with `source = SUGGESTION_ACCEPTED`, indistinguishable afterwards
  from ones the user wrote — including deletable.

**Exit:** suggestions generated from a replayed engagement history in an instrumented test; dismissal
is permanent; cap respected.

### Stage 6 — insights instead of a feed

Replace the scrolling list as the primary surface.

- "Learned 3 people who matter to you."
- "Daraz: 9 of 10 swiped away. Keep it quiet?"
- "Your quietest hours are 2–5pm."
- Progress toward personalisation, and what it changed.

The full list stays, one tap away, for auditing.

**Exit:** dual-theme review; accessibility assertions extended to the new surfaces.

## Fallbacks

Every layer degrades to the one below rather than failing.

| failure | behaviour |
|---|---|
| rule engine throws | logged, skipped, fall through to safety floors — a bad rule never blocks a notification |
| two rules conflict | documented precedence resolves it; the UI flags the pair so the user can reorder |
| notification policy access not granted | alert-on-silent uses the alarm stream; if DND still gates it, the rule is marked "limited" in the UI with what is missing and a link to grant it |
| `snoozeNotification` unavailable or refused | fall back to `MUTE` + a batched repost |
| no reply action on the notification | inline reply hidden, "Open conversation" offered instead |
| auto-reply target already dismissed | skipped, recorded, never retried blindly |
| embedding table corrupt or absent | keyword analyser only, already implemented and tested |
| keystore invalidated | typed error, guided re-provision, never a silent wipe (already implemented) |
| suggestion engine produces nonsense | it can only *propose*; nothing changes without a tap |
| classifier wrong | it is stage 5 of 6, and nothing it decides can hide anything |

## Risks, and what actually mitigates them

**Auto-reply sends something wrong to the wrong person.** The highest-consequence feature in the
plan. Off by default; opt-in per rule; confirmation on first use; never to a sender with no history;
every auto-reply logged and visible; global kill switch in Settings.

**Rules become a power-user maze.** This is what makes BuzzKill hard for ordinary users. Mitigation:
nobody has to write one — onboarding and suggestions create them. The rule editor is the advanced
path, not the front door.

**Suggestion nagging.** Two pending maximum, permanent dismissal, evidence thresholds above.

**Trust damage from the first wrongly hidden notification.** Only user rules can hide; the Hidden
screen and one-tap undo exist from Stage 2, not added later.

**Scope.** Six stages is a lot. Each is independently shippable, and Stages 1–2 alone match
BuzzKill's core.

## Spikes to run before committing to Stage 2 and 3

Two things I will not promise until verified on a device:

1. **Alarm-stream audio versus Do Not Disturb.** Whether it survives DND without
   `ACCESS_NOTIFICATION_POLICY`, and what exactly is lost when that grant is refused. Half a day.
2. **`RemoteInput` reply across real apps.** WhatsApp, Telegram, SMS, Slack — which expose a reply
   action, and whether firing it works from a listener on Android 15/16. Half a day.

If either fails the plan changes, and it is cheaper to learn that now.

## Schema

Version 10. Additive only, so the migration cannot lose data.

```
rules
  id, enabled, name, order,
  triggerType     PERSON | APP | PHRASE | TIME | COMPOSITE
  senderHash?, packageName?, phrase?, startMinute?, endMinute?, daysMask?
  action          ALERT_ALWAYS | VIBRATE | MUTE | BATCH | SNOOZE | AUTO_REPLY
  actionPayload?  reply text, snooze minutes, pattern id
  source          USER | SUGGESTION_ACCEPTED
  createdAt, matchCount, lastMatchedAt        <- so the user can see it working

suggestions
  id, kind, subjectHash?, packageName?, evidenceJson,
  proposedRuleJson, state PENDING | ACCEPTED | DISMISSED, createdAt

notification_events
  + firedRuleId?          <- the audit trail for every suppression
```

`phrase` is user-authored content and lives in the encrypted database like everything else.

## Competitive position

| | BuzzKill | FilterBox | Google Modes | this plan |
|---|---|---|---|---|
| user rules | yes | yes | per-app only | yes |
| ring on silent for a person | yes | partial | no | yes |
| auto-reply | yes | no | no | yes, opt-in |
| batching | yes | yes | no | yes |
| **learns and proposes rules** | no | no | no | **yes** |
| **per-person reply-speed priority** | no | no | no | **yes** |
| **encrypted history** | no | no | n/a | **yes** |
| **works in any language** | rules only | rules only | n/a | rules + multilingual assist |
| network permission | none | none | n/a | **none** |

Rules are table stakes and should be free. The learning layer, insights and sync are what a Pro tier
is for — which also means the paid tier is the part competitors cannot copy by adding a text field.

## Docs to update as each stage lands

- `docs/ARCHITECTURE.md` — the six-stage pipeline replaces the current score-then-threshold section
- `docs/RULES.md` — new: trigger and action reference, precedence, worked examples
- `docs/SCHEMA.md` — v10 tables and migration
- `docs/MODEL_STRATEGY.md` — record that the classifier moved to stage 5 and why
- `README.md` — reply mechanics and the "we cannot send anything, we have no network permission" claim

## Overall exit criteria

- `testDebugUnitTest`, `lintDebug`, `connectedDebugAndroidTest` green
- held-out recall not regressed — rules must not be a way to quietly lower the bar on the classifier
- a muted notification is always attributable to a rule, asserted by test
- no new mandatory permission; contacts and notification-policy access both optional with stated
  degradation
- release build verified on device, and the size budget still under 30 MB
