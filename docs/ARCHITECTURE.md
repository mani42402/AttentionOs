# Architecture and engineering decisions

## Runtime flow

```text
Android notification
        │
        ▼
NotificationListenerService
        │ copies bounded fields
        ▼
PriorityEngine ── MiniLM transformer (INT8, ONNX Runtime)
        │             └── deterministic safety fallback
        ├── Personal logistic model (after activation gate)
        ▼
Attention decision
        ├── preserve the original Android notification unchanged
        ├── classify its recommended interruption level
        └── persist the decision asynchronously in Room
                        │
            open/dismiss feedback
                        ▼
              UserMemory + training row
                        │ explicit correction only
                        ▼
              one local logistic update
```

Explicit Important / Not important feedback overrides passive behavioral inference. It immediately
updates sender memory and writes a high-confidence HIGH or LOW training label; notification opens
and dismissals remain useful but lower-confidence implicit signals.

Each model-backed decision carries MiniLM's 384-dimensional normalized embedding. The app stores it
with the event and eventual training example using symmetric INT8 quantization (384 bytes rather
than 1,536 bytes as Float32). Exports include the Base64-encoded vector, encoding metadata, and exact
language-model version so training jobs cannot silently mix incompatible feature spaces.

The personalized classifier has 390 inputs: the frozen 384-dimensional MiniLM embedding, hour
sine/cosine, sender importance, sender open rate, focus-mode state, and the safe engine's base
score. A correction runs one bounded stochastic-gradient update and persists 390 Float32 weights
(about 1.6 KB) plus a bias and class counts. No background training job, wake lock, or model
fine-tuning session is required.

Personalized scoring stays disabled until 50 explicit corrections exist and both Important and Not
important have at least 10 examples. Each notification stores the personal probability produced
before feedback arrives. When the user later corrects it, that saved shadow prediction is evaluated
before the training update, preventing label leakage.

The onboarding safety test starts a seven-day real-device shadow pilot. Activation additionally
requires that pilot to finish, at least 40 evaluated shadow predictions, 65% personal accuracy, 80%
important-alert recall over at least eight important examples, and accuracy no worse than the base
engine on the same examples. When active, personalization contributes only 20% of the final score.
Security, finance, calls, alarms, and strongly urgent alerts cannot be downgraded by it. The
user-facing Summary reports the learning period and correction progress without exposing raw model
metrics or engineering gates.

Unreviewed model-backed decisions appear in a bounded review session. Important and Not important
reuse the same explicit-feedback path; Skip only advances the session and writes no label. Users can
optionally schedule quiet reminders at any minute, up to six times per day. WorkManager owns one
uniquely named periodic task per selected time, requires a non-low battery, and posts through a
shared silent low-importance channel. Updating the set cancels and rebuilds only tagged reminder
work.

## Why the MVP uses MiniLM instead of a generative LLM

A generative model creates unnecessary token-generation latency and memory pressure for a
classification task. The MVP runs a real three-layer pretrained transformer that produces
384-dimensional semantic embeddings. The quantized model is 17.5 MB, accepts at most 64 tokens,
uses one CPU thread, and is warmed on the notification service's background scope.

## Safety invariants

1. Notifications are never cancelled, replaced, hidden, or snoozed.
2. Security and finance categories are always ranked prominently.
3. Attention mode must be explicitly enabled.
4. Missing models or database failures must fail open; original Android notifications continue.
5. Raw content storage is opt-in.
6. No cloud permission or endpoint exists.
7. User data is excluded from Android backup and can be deleted locally.
8. Personalized weights are local and resettable.
9. Personalization cannot affect decisions during the seven-day pilot.
10. Review reminders are opt-in, silent, and cancellable.

The deterministic safety engine enforces score floors even when semantic confidence is weak:
security and calls are Critical, while finance and alarms are at least High. Strong combinations
such as a production incident or “action required immediately” bypass focus and quiet-hour
reductions. These floors execute before personalized blending.

## Interruption control

When Attention Mode is enabled, the notification listener requests Android's
`HINT_HOST_DISABLE_NOTIFICATION_EFFECTS`. This suppresses default notification sound and vibration
globally without removing notifications. After local AI inference, AttentionOS plays the user's
selected sound and/or vibration only for Critical, High, or optionally Medium decisions. Low and
Silent decisions remain in the original notification shade without an AttentionOS interruption.

When Attention Mode is disabled, the listener clears its hint and source applications control their
normal notification effects. Notification keys are deduplicated for 30 seconds so an app updating
the same notification does not create repeated AttentionOS alerts.

## Memory and CPU budget

- No polling, wake lock, foreground service, or always-running model.
- One supervisor coroutine scope per listener lifetime.
- A bounded 32-entry app-label cache.
- Input text capped at 2,000 characters.
- Indexed timestamp and sender fields.
- Recent UI queries capped at 60 rows.
- Daily, idle-only retention work.
- Up to six optional battery-aware daily review jobs; no custom timer or polling loop.
- Per-event inference timing persisted as a single integer and aggregated in SQL.
- UI motion can be disabled; continuous ambient and protection animations are not composed when off.

## Interface system

The Compose interface uses a restrained navy, cobalt, and teal light/dark color system, a compact
typography scale, consistent shapes, a full-width fixed bottom menu, and reusable control panels.
Canvas draws the small custom navigation icons and distribution bars directly, avoiding an icon or
charting dependency. Navigation selection and other short transitions animate only when the
persistent Motion effects preference is enabled.

Onboarding is replayable from Settings. It explains the helper in plain language and includes a
safe interactive notification demo that saves nothing. Replaying changes only the completion flag:
the original pilot start time, decisions, training examples, and personalized weights remain intact.

## Fine-tuning path

The exported JSONL format contains behavior-derived labels and minimized features. Before any
external training, add explicit consent, k-anonymity/redaction checks, dataset balance reporting,
label review, and a holdout evaluation that treats missed critical notifications as the highest-cost
error. Do not upload the local Room database.
