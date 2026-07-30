# AttentionOS

AttentionOS is an Android-first, private personalized notification helper. It classifies
notifications, learns from local choices, and helps users protect focused time without hiding or
deleting anything. This repository contains a functional native MVP, not a web mockup.

## What is implemented

- Native Kotlin + Jetpack Compose application
- Android `NotificationListenerService`
- Real pretrained MiniLM transformer inference using ONNX Runtime
- Five-level attention classification: critical, high, medium, low, and silent
- Attention mode that ranks low-value notifications without removing them
- Per-priority sound and vibration controls for Critical, High, and Medium
- Hard safety rule that always ranks security and financial alerts prominently
- Room database for decisions, learned sender memory, and training examples
- Local feedback loop based on notification opens and dismissals
- Explicit Important / Not important corrections as high-confidence training labels
- Quantized 384-dimensional MiniLM embeddings stored with labeled examples
- A persisted local logistic model that incrementally trains from explicit corrections
- Shadow evaluation against the pretrained baseline before personalization can activate
- A mandatory seven-day shadow pilot before personalization can activate
- Conservative activation after time, count, class-balance, accuracy, and important-recall gates
- Safety gates that prevent personalization from downgrading protected urgent alerts
- Deterministic priority floors for security, finance, calls, alarms, and strong incident phrases
- Guided, replayable four-step onboarding with feature explanations, an interactive notification
  demo, interruption preferences, and a safety test
- A focused visual review session for Important / Not important corrections, with safe skipping
- Optional silent review reminders at any minute, up to six times per day
- A concise Summary focused on today's notifications, personalization progress, and safety
- Persistent light/dark appearance and reduced-motion controls
- Privacy-first storage (message content is disabled by default)
- JSON Lines export with hashed sender identities
- One-tap local data deletion and configurable retention
- Battery-aware daily cleanup using WorkManager
- Light/dark UI with Home, Review, Summary, and Settings destinations

## Build and run

Requirements:

- Android Studio with JDK 17
- Android SDK 35

Open this directory in Android Studio, allow Gradle to sync, and run the `app` configuration on an
Android 8.0+ device. On first launch, the onboarding flow explains the safety guarantees and opens
Android's notification-access settings when requested. Try the interactive notification demo,
choose interruption preferences, run the five-scenario local safety test, and tap
**Start using my helper**. The app then observes real notifications during a seven-day learning
period without changing or deleting them.

The redesigned control center exposes Attention Mode, notification access, sound/vibration by
priority, local learning, raw-content storage, theme, motion, multiple daily reminders, retention,
export, personalization reset, and local deletion. **Replay guided setup** revisits onboarding
without restarting the learning period or deleting learned data.

Command-line verification:

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug
```

## Privacy model

Everything is local. There is no internet permission and no analytics SDK.

By default the app stores:

- app/package identifier;
- decision category, priority, and confidence;
- a truncated SHA-256 hash representing the app/sender pair;
- interaction label and response time.

## Performance model

The notification language path uses the pretrained, INT8-quantized
`sentence-transformers/paraphrase-MiniLM-L3-v2` model. It produces semantic embeddings locally via
ONNX Runtime. The model is warmed away from the UI thread, capped at 64 tokens, serialized through
one inference lane, and configured for one CPU thread. A deterministic analyzer remains as a
fail-safe if model loading or inference ever fails.

MiniLM's weights stay frozen. Each explicit **Important** or **Not important** correction performs
one online update to a 390-feature logistic classifier using the embedding plus time, focus mode,
sender history, and the safe engine's base score. Its Float32 weights occupy about 1.6 KB. The
personal model remains inactive for a seven-day shadow pilot and until it has at least 50 explicit
corrections with at least 10 in both classes. It must also reach 65% shadow accuracy, 80%
important-alert recall, and perform at least as well as the pretrained baseline. Before every gate
passes, its predictions are recorded for evaluation but cannot change interruption behavior. The
user-facing Summary deliberately shows only understandable learning progress; detailed activation
metrics remain an implementation concern.

Package labels use a bounded 32-item LRU cache. Text copied from a notification is capped at 2,000
characters. Per-notification inference time is measured locally. Housekeeping runs once daily only
when the device is idle and the battery is not low. Each selected review time uses one
battery-aware periodic WorkManager task and a shared silent, low-importance notification channel.
The schedule is capped at six daily times and uses no polling loop.

## Android limitation

A regular notification listener receives a copy after the source app posts an alert. It cannot
change that individual notification's channel sound or vibration before delivery. AttentionOS
therefore never modifies or removes the original notification. Production-grade, per-alert
interruption control requires an OS/OEM notification-intelligence role; the MVP deliberately does
not overstate this capability.

## Project map

```text
app/src/main/java/com/attentionos/
├── core/
│   ├── common/    shared constants (time, scheduling limits)
│   └── di/        AppContainer, the manual dependency graph
├── data/
│   ├── db/        Room database, DAO, entities, migrations
│   ├── settings/  DataStore-backed preferences
│   └── repository/ decision recording, feedback, hashing
├── domain/        priority engine, shared AttentionPolicy, models
├── ai/            ONNX analyzer and WordPiece tokenizer
├── service/       notification listener, reminder and retention workers
├── training/      local personalized classifier and export
└── ui/
    ├── theme/  components/  navigation/
    └── onboarding/  home/  review/  insights/  settings/
```

Notification title and message content are stored only when the user explicitly enables
**Store message content**. Android backups exclude the database; note that DataStore settings
are not yet excluded from device-to-device transfer (tracked for the security phase).

The original product brief remains in [`MVP.md`](MVP.md).
