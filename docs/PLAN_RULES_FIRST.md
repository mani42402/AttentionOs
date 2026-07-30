# Final plan: rules first, learning as the suggestion layer

Legend used throughout:

| mark | meaning |
|---|---|
| **[HAVE]** | shipped and verified on device today |
| **[BUILD]** | planned, with the stage that delivers it |
| **[EDGE]** | something no competitor in this category does |
| **[GAP]** | where we are currently behind, with the plan to close it |
| **[SPIKE]** | not promised until verified on a device |

## Why the architecture changes

Measurement, not preference. The classifier scores **48% on notifications unlike anything it was
written for** (`CorpusEvaluationTest.scoreHeldOutCorpus`, 135 cases, 17 languages). Every attempt to
raise it — more descriptions, a better encoder, confidence abstention — moved along one trade-off
curve. That is the ceiling of asking a 128-dimensional lookup table "is this important?".

Two signals in the same codebase are near-perfectly reliable and were being wasted: **a rule the user
wrote**, which fires correctly every time in any language forever, and **who the person is**, since
reply-in-under-a-minute beats any sentence match and can never be out-of-vocabulary.

BuzzKill is market proof — one developer, no AI, ~$3.99, and people love it — because it deletes the
question rather than answering it. What it lacks is the app *noticing* the pattern and offering the
rule. That gap is the product.

## Target decision pipeline

Strict order, first match wins, each stage overridable only from above.

```
1  User rules            everything below, fully user-controlled
2  Safety floors         calls, alarms, verified security + finance  (nothing may lower these)
3  Person signal         reply speed, open rate, burst open-order    (may raise)
4  App signal            engagement with that package                (may raise)
5  Message classifier    nearest of ~50 descriptions                 (ranks the remainder)
6  Default               deliver untouched
```

The 48% component does the smallest job instead of the whole job, so its errors land only on
notifications nothing else had an opinion about.

## The invariant, restated

Today the listener promises never to modify a source notification. Mute, snooze and batch break that,
so it becomes narrower but keeps the substance:

> **The app never hides a notification on its own judgement. It hides only what a rule says to hide,
> and every such action is logged, visible and reversible in one tap.**

Enforced structurally, not by intention:

- `MUTE`, `SNOOZE` and `BATCH` are reachable **only** through a `Rule`. The cancel function takes a
  `RuleId`, so no inference path can reach it.
- `source` is `USER`, `SUGGESTION_ACCEPTED` or `LEARNED`. Only `LEARNED` is created without a tap —
  in "just handle it" mode only, to quieten only, past the confidence gates only, always in the
  digest with an undo, and otherwise an ordinary editable rule.
- Every suppression writes `firedRuleId` to the event row, so "why did I not see this?" always has an
  answer.
- Safety floors can never be muted by any rule; attempting it is refused at creation time.

---

# The rule system

This is the section that closes the maturity gap. A rule is **conditions → actions**, with everything
below available to the user.

## Conditions

Grouped with AND / OR, each negatable, nested one level.

| condition | detail | mark |
|---|---|---|
| App | one or many, incl. "any app" | **[BUILD]** S1 |
| Person / sender | from the notification's `Person` or shortcut identity | **[BUILD]** S1, identity already **[HAVE]** |
| Text contains / equals / starts / ends | title, body, or either | **[BUILD]** S1 |
| Regular expression | with a length cap and a match-step budget, so a pasted pattern cannot hang the listener | **[BUILD]** S1 |
| Time of day | range, multiple ranges per rule | **[BUILD]** S1 |
| Day of week | any subset | **[BUILD]** S1 |
| Android's own category | message, call, alarm, event, promo, transport… straight from the notification | **[BUILD]** S1 |
| Ongoing / progress | so a download bar is never treated as an event | **[HAVE]** as a signal, **[BUILD]** as a condition |
| Group summary | "+18 new messages" versus a real message | **[BUILD]** S1 |
| **Our priority band** | condition on what the classifier decided — "if it says NOISE, mute it" | **[BUILD]** S1 **[EDGE]** |
| Phone state | currently silent / vibrate / DND | **[BUILD]** S2 |
| **Repeat / flood** | "3rd or later from this app within 10 minutes" — needs stored history, which competitors do not keep | **[BUILD]** S2 **[EDGE]** |
| **Sender engagement** | "from someone I reply to in under a minute" — a learned condition usable in a hand-written rule | **[BUILD]** S5 **[EDGE]** |

## Actions

Several may fire per rule.

| action | detail | mark |
|---|---|---|
| Alert always | our own sound on the alarm stream so silent mode does not swallow it | **[BUILD]** S2, **[SPIKE]** for DND |
| Custom sound | pick any ringtone | **[BUILD]** S2 |
| Custom vibration | pattern per rule | **[HAVE]** patterns, **[BUILD]** per-rule |
| Repeat until seen | re-alert every N minutes until opened, capped | **[BUILD]** S2 |
| Mute | cancel it, logged against the rule | **[BUILD]** S2 |
| Snooze | `snoozeNotification`, API 26, our whole floor | **[BUILD]** S2 |
| Batch | hold and repost as one digest at a chosen time | **[BUILD]** S2 |
| Auto-reply | fill their reply field, fire their intent | **[BUILD]** S3, **[SPIKE]** |
| Raise / lower band | nudge without silencing | **[BUILD]** S1 |
| Add sender to VIP | one tap from a rule | **[BUILD]** S4 |
| Stop processing | explicit allow: this rule wins, later rules do not run | **[BUILD]** S1 |

## Rule management

| capability | mark |
|---|---|
| Enable / disable without deleting | **[BUILD]** S1 |
| Explicit ordering, drag to reorder | **[BUILD]** S1 |
| Duplicate a rule | **[BUILD]** S1 |
| Match count and last-matched, shown on the rule | **[BUILD]** S1 |
| Conflict detection — flags two rules that fight | **[BUILD]** S1 |
| **Preview against your real history** — "this rule would have matched 34 of your last 200 notifications, here they are" **before** saving | **[BUILD]** S1 **[EDGE]** |
| Export / import rules as JSON | **[BUILD]** S6 |

**Preview is the one to notice.** We keep an encrypted history; competitors do not. So we can show
exactly what a rule will do before it does it, against the user's own notifications. Nobody else can
offer that, because they have nothing to test against.

**Export caveat:** sender identities are HMACs keyed to the device, so they cannot be meaningfully
exported. Person conditions export as display names and are re-resolved on import, with anything
unmatched clearly listed rather than silently dropped.

## Precedence, stated once

Explicit `order` first. Ties broken by specificity: person > app + text > app > text > time. Ties
after that by `createdAt`. Documented, unit tested, and surfaced in the UI — "why did the wrong rule
win" is the first support question any rule engine generates.

---

# Learning without the user's help

Most people will never review anything, so passive learning is primary and review is an accelerator.
The honest arithmetic: a phone generates 50–200 notification interactions a day against maybe 5
corrections from someone feeling helpful.

| signal | tells us | mark |
|---|---|---|
| Opened from shade | direct interest | **[HAVE]** |
| Dealt with inside the app (`REASON_APP_CANCEL`) | read or replied | **[HAVE]** — fixed this week, was discarded entirely |
| One deliberate swipe | a real judgement | **[HAVE]** |
| Bulk "clear all" | **no** judgement | **[HAVE]** — fixed this week, was poisoning every sender score |
| Timed out untouched | ignored without even the effort | **[BUILD]** S5 |
| Dwell time | graded interest, not binary | stored **[HAVE]**, curve **[BUILD]** S5 |
| **Open order in a burst** | five waiting, which did they open first — a direct ranking statement | **[BUILD]** S5 **[EDGE]** |
| **Channel importance the user lowered themselves** | the user already told Android their preference | **[BUILD]** S5 **[EDGE]** |
| Android's own rank | free ordering hint | **[BUILD]** S5 |
| Hour-of-day engagement | quiet-hours suggestion | stored **[HAVE]**, aggregate **[BUILD]** S5 |

We receive a `RankingMap` on **every callback and currently read nothing from it**.
`Ranking.getUserSentiment()` may require the assistant role rather than a plain listener — **[SPIKE]**.

**Confidence gates before anything acts:** ≥ 5 interactions, ≥ 80% one-sided, spread over ≥ 3 distinct
days. **Inert user fallback:** someone who never opens or swipes gives only timeouts; the app then
concludes nothing, keeps the safety floors, and stays out of the way. It never invents a preference
from absent evidence.

# Two kinds of user, one engine

One onboarding question, and that is the whole difference:

> When I learn something: **(a) Just handle it, tell me after** — default, or **(b) Ask me first**

| | Just handle it | Ask me first |
|---|---|---|
| passive learning | always on | always on |
| raising priority | applied silently — nothing is hidden, so this cannot lose a message | same |
| quietening | applied, then one line in the weekly digest with an undo | proposed, waits for a tap |
| rule editor | present, never required | present |
| effort | zero, forever | as much as wanted |

---

# Closing the gaps

Each **[GAP]** with what actually closes it.

### Rule-engine maturity — they have years of shipped edge cases

**Closes at Stage 1–2.** The condition and action matrix above meets or exceeds their published
feature set, and three conditions (our band, flood detection, sender engagement) plus rule preview go
beyond it. What we will still lack is field-hardening, which only shipping earns — so Stage 1 carries
an unusually heavy test suite: every condition type, every combinator, precedence, and conflict cases.

### Size — 18 MB against their ~7 MB

**Partly closes, honestly.** 13 MB of ours is the multilingual embedding table, and it is the price of
working outside English — which they do not do at all. Actions taken:

- App Bundle so Play strips unused densities and languages per device **[BUILD]** S6
- R8 full mode and resource shrinking **[HAVE]**
- No inference runtime, no charting library, no Lottie, no icon pack **[HAVE]**

We will not reach 7 MB without dropping multilingual support, and that would be trading our main
advantage for a number nobody uninstalls over. Stated plainly rather than engineered around.

### Price — $3.99 once against a subscription

**Closes at Stage 6.** Rules are table stakes and ship **free**, including every condition and action
above. Paid covers what cannot be copied by adding a text field: passive learning, suggestions,
insights, and later cross-device sync. A **lifetime option** ships alongside the subscription, because
Android buyers demonstrably prefer one-time purchases and pretending otherwise costs sales.

### Power users are better served by them today

**Closes at Stage 1 + S6.** Full condition/action matrix, ordering, duplication, enable-disable,
conflict detection, JSON export/import, and rule preview against real history. After Stage 6 the
power user has strictly more, not less.

### iOS

**Does not close, for anyone.** iOS exposes no notification-read API to third parties; that is why
every app in this category is Android-only. Not a gap — a platform fact.

---

# Permissions, and asking rather than assuming

| permission | needed for | approach |
|---|---|---|
| Notification access | everything | required, explained at onboarding **[HAVE]** |
| `POST_NOTIFICATIONS` | our own alerts and digests | asked when first needed **[HAVE]** |
| **Do Not Disturb access** | letting an "alert always" rule through DND | **asked in context** — only when a user creates their first such rule, with a plain sentence on exactly what breaks without it, and a working degraded mode either way **[BUILD]** S2 |
| Contacts | nicer VIP picker | **optional**; fallback picks from senders already seen, needing no permission **[BUILD]** S4 |
| INTERNET | — | **never requested**, so the app is structurally incapable of sending anything anywhere **[HAVE]** |

On alarms specifically: playing on the alarm stream survives silent mode. Whether it survives DND
without the policy grant is **[SPIKE] 1**. If it does not, the rule shows "limited during Do Not
Disturb" with a one-tap grant, and still works everywhere else.

# Reply

Messaging apps attach their own reply action carrying a `RemoteInput`. We fill that field and fire
**their** `PendingIntent`, so **WhatsApp sends the message through WhatsApp**. We hold no INTERNET
permission and cannot send anything ourselves — that belongs in the store listing.

Inline reply from our screen, plus `AUTO_REPLY` as a rule action: **off by default**, opt-in per rule,
confirmation on first use, never to a sender with no history, every send logged, global kill switch.
Limits stated in the UI rather than discovered: text only, only apps exposing a reply action, and a
notification already dismissed cannot be replied to. **[SPIKE] 2** covers WhatsApp, Telegram, SMS and
Slack on Android 15/16.

---

# Stages

Each ships alone and leaves the app working.

**S1 — rule model and engine.** Schema v10. Pure-Kotlin `RuleEngine`, no Android dependencies, unit
testable. Every condition, combinator, precedence and conflict case tested. Indexed by package and
sender so cost is proportional to *matching* rules; budget < 0.5 ms at 100 rules, asserted.

**S2 — actions and rule UI.** Alert-always, custom sound, vibration, repeat-until-seen, mute, snooze,
batch, raise/lower. Rule editor with preview against real history. *Hidden by your rules* screen with
undo, present from the start rather than added later. Contextual DND grant.

**S3 — reply.** Capability detection, inline reply, `AUTO_REPLY` with the safeguards above.

**S4 — onboarding seed.** Up to 5 people, up to 3 always-alert apps, all skippable, each creating a
visible editable rule. Contacts optional.

**S5 — passive learning and suggestions.** The signals table above. Suggestion kinds: promote a
person, quieten an app, batch an app, quiet-hours. Max 2 pending, permanently dismissible.

**S6 — insights, export, packaging.** Progress and findings instead of a feed. JSON rule
export/import. App Bundle, lifetime pricing tier.

# Fallbacks

| failure | behaviour |
|---|---|
| rule engine throws | logged, skipped, falls through to safety floors — a bad rule never blocks a notification |
| regex pathological | step budget aborts the match, rule marked faulty in the UI, notification delivered |
| two rules conflict | documented precedence resolves it; UI flags the pair to reorder |
| DND access refused | alarm stream only; rule shows "limited during Do Not Disturb" with a one-tap grant |
| `snoozeNotification` refused | falls back to mute plus a batched repost |
| no reply action present | inline reply hidden, "Open conversation" offered |
| auto-reply target already gone | skipped, recorded, never blindly retried |
| `getUserSentiment` restricted | dropped; the other ten passive signals do not depend on it |
| embedding table corrupt | keyword analyser only **[HAVE]**, already tested |
| keystore invalidated | typed error and guided restore, never a silent wipe **[HAVE]** |
| user never interacts at all | concludes nothing, keeps safety floors, stays out of the way |
| user never opens our app | passive learning is unaffected; the digest is a notification, not a screen |
| classifier wrong | it is stage 5 of 6 and can hide nothing |

# Risks

**Auto-reply sending the wrong thing** — highest consequence here. Off by default, opt-in per rule,
first-use confirmation, never to unknown senders, logged, global kill switch.

**Rules becoming a maze** — exactly what makes BuzzKill hard for ordinary people. Nobody has to write
one: onboarding and suggestions create them. The editor is the advanced path, not the front door.

**First wrongly hidden notification destroys trust** — only rules can hide; *Hidden by your rules*
with undo exists from S2, not retrofitted.

**Scope** — six stages is a lot. Each is independently shippable and S1–S2 alone reach core parity.

# Spikes, before building on any of it

1. **Alarm stream versus Do Not Disturb** — does it survive without `ACCESS_NOTIFICATION_POLICY`, and
   what exactly is lost if refused. Half a day.
2. **`RemoteInput` across real apps** — WhatsApp, Telegram, SMS, Slack on Android 15/16. Half a day.
3. **`Ranking` fields from a plain listener** — which of importance, channel, rank, sentiment we may
   read. The channel one is the most valuable passive signal in this plan. Half a day.

Any failure changes the plan, and it is far cheaper to learn now.

# Schema v10, additive only

```
rules
  id, enabled, name, order,
  conditionsJson    condition tree: AND/OR/NOT over the condition table above
  actionsJson       one or more actions with payloads
  source            USER | SUGGESTION_ACCEPTED | LEARNED
  evidenceJson?     what was observed, shown for LEARNED rules
  createdAt, matchCount, lastMatchedAt

suggestions
  id, kind, subjectHash?, packageName?, evidenceJson,
  proposedRuleJson, state PENDING | ACCEPTED | DISMISSED, createdAt

notification_events
  + firedRuleId?    the audit trail for every suppression
```

Conditions are stored as a JSON tree rather than columns because the shape is a tree; it lives in the
encrypted database like everything else, and user-authored text never leaves it.

# Exit criteria

- `testDebugUnitTest`, `lintDebug`, `connectedDebugAndroidTest` green
- held-out classifier recall not regressed — rules must not become a way to quietly lower the bar
- every muted notification attributable to a rule, asserted by test
- a user who never opens the app still accumulates person and app signal, asserted by an instrumented
  test that replays a history with zero screen visits
- no new mandatory permission; DND and contacts both optional with stated degradation
- release verified on device, size still under the 30 MB budget
