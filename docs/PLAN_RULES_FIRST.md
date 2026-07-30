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

- `MUTE`, `SNOOZE` and `BATCH` are reachable **only** through a `Rule`. The classifier has no code
  path to them: the function that cancels a notification takes a `RuleId`, so there is nothing to
  call without one.
- A rule's `source` is `USER`, `SUGGESTION_ACCEPTED` or `LEARNED`. Only `LEARNED` can be created
  without a tap, only in "just handle it" mode, only to quieten, and only past the confidence gates —
  and it appears in the weekly digest with an undo. A `LEARNED` rule is otherwise an ordinary rule:
  visible, editable, deletable.
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

## Learning without the user's help

The single biggest risk in the previous draft: if the user never opens our app and never corrects
anything, does the app learn? It has to, because most people will never review anything. And the
honest finding is that **the passive signal is richer than the review signal** — a normal phone
generates 50-200 notification interactions a day, against maybe 5 corrections from someone feeling
helpful.

So passive learning is primary. Review is an accelerator, not a requirement.

### What Android gives us for free, per notification

| signal | what it tells us | status |
|---|---|---|
| `REASON_CLICK` | opened it from the shade | used |
| `REASON_APP_CANCEL` | dealt with it inside the app — read or replied | **fixed this week; was discarded** |
| `REASON_CANCEL` | one deliberate swipe: a real judgement | used |
| `REASON_CANCEL_ALL` | bulk tidy: **no** judgement | **fixed this week; was poisoning the data** |
| `REASON_TIMEOUT` | ignored without even the effort of swiping | not used — weak negative |
| dwell time (post to removal) | graded interest, not binary | stored, not used as a curve |
| **open order within a burst** | five pending, which did they open first — a direct ranking preference | **not used** |
| `Ranking.getImportance()` | the effective importance Android is applying | **not used** |
| `Ranking.getChannel()` | the channel, including **importance the user themselves lowered** | **not used** |
| `Ranking.getRank()` | Android's own ordering of the shade | **not used** |
| hour-of-day of engagement | when they actually respond versus ignore | stored, not aggregated |

We receive the `RankingMap` on every callback and read nothing from it. The channel one matters most:
if a user has manually turned an app's channel down in Android settings, **that is the user stating a
preference in their own words**, and it costs us nothing to honour it.

`Ranking.getUserSentiment()` would tell us Android's own read on whether the user has been dismissing
a stream, but it may be restricted to the `NotificationAssistantService` role rather than a plain
listener. **Spike before relying on it** — listed below.

### What each signal is allowed to conclude

Aggregated per person and per app, never per single event:

- **open rate** and **reply speed** -> person importance
- **timeout rate** and **single-swipe rate** -> a candidate for quietening
- **open order in a burst** -> relative ranking between two senders, which is what the app is
  ultimately for
- **user-lowered channel importance** -> immediate, high-confidence quietening signal
- **hour-of-day engagement** -> quiet-hours suggestion

Confidence gates before any of it acts: at least 5 interactions, at least 80% one-sided, spread over
at least 3 distinct days. Nothing concluded from a single afternoon.

### Fallback if a user is genuinely inert

Someone who never opens a notification and never swipes gives us nothing but timeouts. In that case
the app converges on "everything times out, nothing is distinguishable" and does the only honest
thing: it stays out of the way and keeps the safety floors. It never invents a preference from an
absence of evidence.

## Two kinds of user, one engine

One person wants to configure everything. Another installs the app and expects it to work. They must
not need different products.

**The engine is identical for both.** The only difference is a single onboarding question:

> When I learn something about your notifications:
> **(a) Just handle it, tell me after** — default
> **(b) Ask me first**

| | Just handle it (default) | Ask me first |
|---|---|---|
| passive learning | always on | always on |
| raising something's priority | applied silently — nothing is hidden, so this cannot lose a notification | applied silently, same reasoning |
| quietening something | applied, then a single line in a weekly digest: "I quietened Daraz, you ignored 19 of 20. Undo?" | proposed as a suggestion, waits for a tap |
| creating a rule | created with `source = LEARNED`, visible and deletable | created only on approval |
| the rule editor | present, never required | present |
| effort required | zero, forever | as much as they want |

Two properties make "just handle it" defensible rather than presumptuous:

1. **Raising is free.** Nothing is ever hidden by inference, so a wrong promotion costs one extra
   buzz — not a lost message.
2. **Quietening is always attributable and reversible.** Every quietened stream appears in *Hidden by
   your rules* with the evidence that caused it and one-tap undo. A user who never looks is not worse
   off than they were with no app; a user who looks can reverse anything in a tap.

The mode is switchable at any time, and switching to "ask me" does not undo what was already learned —
it only changes what happens next.

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

## Honestly, versus BuzzKill and the rest

No promises. What is verifiable, what is a real weakness, and what is only a plan until it ships.

### Where we genuinely win

**They learn nothing.** BuzzKill is a rule engine — that is its design, not a gap in it. Write no
rule and it does nothing at all. FilterBox is the same. Google's Modes are per-app switches. For the
majority of users who will never author a rule, every competitor does nothing and we do something.
This is the whole argument, and it is verifiable from their own feature lists.

**Nobody proposes rules.** "You reply to Bilal within a minute, every time — ring on silent for him?"
does not exist in this category. It is the feature that turns a power-user tool into something an
ordinary person benefits from.

**Per-person reply-speed priority.** A rule can say "always alert for Ammi". None of them can rank
Ammi above Bilal because you answer her faster, without being told.

**Encrypted history.** Ours is SQLCipher with an Android Keystore-wrapped key, verified on device.
I have not audited how competitors store theirs and will not claim they are worse — but none of them
advertise encryption at rest, and we can state ours precisely.

**Language.** Their phrase rules are literal strings: "urgent" needs a separate rule for
`فوری`, `तुरंत`, `jaldi`. Our phrase rules do the same, but the semantic layer additionally reads 17
languages at 0% unknown tokens. Presented as an assist, because at 48% on unseen material it is not
a headline.

### Where we lose, and should say so internally

**Their rule engine is more mature.** Shipped, reviewed, years of edge cases. Stages 1-3 reach parity
on the core; the long tail of trigger and action combinations will take longer.

**They are smaller and cheaper.** ~7 MB against our 18 MB, and $3.99 once against a subscription. Our
13 MB is the multilingual table, which is the price of working outside English.

**A power user who enjoys writing rules is better served by them today.** That is a real segment and
we should not pretend otherwise. Our answer is the user who does not want to write rules at all.

### Feature-by-feature answer

| what they do | our answer | honest status |
|---|---|---|
| ring on silent for chosen people | same, alarm stream | Stage 2, **pending DND spike** |
| custom vibration per rule | same, patterns already exist | Stage 2 |
| mute / dismiss automatically | same, but only from a user or learned rule, always logged | Stage 2 |
| snooze | same, `snoozeNotification`, API 26 | Stage 2 |
| batch into a digest | same, existing WorkManager | Stage 2 |
| auto-reply | same, their reply field and their intent | Stage 3, **pending RemoteInput spike** |
| keyword / phrase rules | same, plus semantic matching across languages | Stage 1 + existing |
| time-based rules | same | Stage 1 |
| per-app rules | same | Stage 1 |
| — | **learns with no input at all** | Stages 5, passive signals |
| — | **proposes rules from evidence** | Stage 5 |
| — | **ranks people by how you actually behave** | Stages 3-5 |
| — | **encrypted history and real deletion** | shipped |
| — | **works with no network permission** | shipped |
| iOS | not possible for anyone — iOS exposes no notification-read API | n/a |

### The one-sentence positioning

Every competitor is a tool you must operate. This is a tool that watches how you already behave and
offers to do the operating for you — and if you would rather operate it yourself, it does that too.

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
| suggestion engine produces nonsense | in "ask me" mode it can only propose; in "just handle it" mode it may only quieten with evidence, always logged and reversible |
| user never interacts with anything | timeouts are the only signal; the app converges on "nothing is distinguishable", keeps the safety floors, and stays out of the way. It never invents a preference from absent evidence |
| user interacts but never opens our app | passive learning is unaffected — it needs no visit. The weekly digest is a notification, not a screen |
| `Ranking.getUserSentiment()` restricted to the assistant role | dropped; the other eleven passive signals do not depend on it |
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

3. **`Ranking` access from a plain listener.** Which of `getImportance`, `getChannel`, `getRank` and
   `getUserSentiment` a non-assistant listener may actually read on Android 15/16. The channel one is
   the most valuable passive signal in the plan — a user who lowered an app's channel themselves has
   already told us what they want. Half a day.

If any fails the plan changes, and it is cheaper to learn that now than after building on it.

## Schema

Version 10. Additive only, so the migration cannot lose data.

```
rules
  id, enabled, name, order,
  triggerType     PERSON | APP | PHRASE | TIME | COMPOSITE
  senderHash?, packageName?, phrase?, startMinute?, endMinute?, daysMask?
  action          ALERT_ALWAYS | VIBRATE | MUTE | BATCH | SNOOZE | AUTO_REPLY
  actionPayload?  reply text, snooze minutes, pattern id
  source          USER | SUGGESTION_ACCEPTED | LEARNED
  evidenceJson?   what the app observed, shown to the user for LEARNED rules
  createdAt, matchCount, lastMatchedAt        <- so the user can see it working

suggestions
  id, kind, subjectHash?, packageName?, evidenceJson,
  proposedRuleJson, state PENDING | ACCEPTED | DISMISSED, createdAt

notification_events
  + firedRuleId?          <- the audit trail for every suppression
```

`phrase` is user-authored content and lives in the encrypted database like everything else.

## Pricing consequence

Rules are table stakes against a $3.99 competitor, so they belong in the free tier. What a paid tier
can honestly charge for is the part nobody can copy by adding a text field: the passive learning, the
suggestions, the insights, and later cross-device sync. That also aligns the incentive correctly — we
get paid for the app understanding the user, not for withholding a switch.

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
- a user who never opens the app still accumulates person and app signal — asserted by an
  instrumented test that replays a notification history with zero visits to any screen
- every `LEARNED` rule carries the evidence that created it, and undo restores the previous state
- release build verified on device, and the size budget still under 30 MB
