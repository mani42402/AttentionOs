# Security model

AttentionOS reads every notification on the device. That makes it, by construction, one of the
most sensitive apps a user can install, and the reason the design below is deliberately
conservative.

## What is stored

| Data | Stored by default | Notes |
|---|---|---|
| App/package identifier and label | yes | reveals which apps notify the user and when |
| Decision (priority, category, score, explanation) | yes | |
| Sender pseudonym | yes | keyed MAC, see below |
| Notification title and body | **no** | only when the user enables *Store message content* |
| Semantic embedding | yes | 384-dimension INT8 vector derived from the text |
| Interaction (opened/dismissed/corrected) and response time | yes | |
| Personalized model weights | yes | ~1.6 KB |

The embedding deserves a note: it is derived from the notification text, and embedding-inversion
research shows such vectors can leak part of their source. It is treated as sensitive data, not
as an anonymised artifact.

## Threat model

Defended against:

- **Someone with the device unlocked** — notification content is excluded from screenshots, the
  recents thumbnail and screen recording via `FLAG_SECURE` (default on, user-overridable).
- **Someone with a copy of the app's data directory** — the database is encrypted with a key
  that never leaves the hardware Keystore, so a copied file is opaque.
- **Offline enumeration of who the user talks to** — sender identities are keyed MACs, so there
  is no small preimage space to grind against.
- **Data escaping through OS transfer** — cloud backup and device-to-device transfer exclude
  every domain, including `file` where settings and wrapped keys live.
- **Data outliving its welcome** — retention prunes every table that accumulates personal data,
  with hard caps; the delete path destroys key material rather than only deleting rows.

Explicitly *not* defended against:

- **A compromised or rooted device running as this app.** Anything the app can decrypt, malware
  running with its identity can decrypt.
- **Screen-reading accessibility services.** Android grants them access by user consent.
- **The source apps themselves.** AttentionOS sees what Android hands it, and cannot protect
  data that the originating app also holds.
- **A user who exports their training data.** Export is explicit and user-initiated; from the
  moment the share sheet opens the destination app governs the file.

## Key hierarchy

```
AndroidKeyStore KEK — AES-256-GCM, hardware-backed, non-exportable
  ├─ wraps  DEK           256-bit SQLCipher database key
  └─ wraps  HMAC secret   256-bit sender-pseudonymisation key
```

Wrapped keys live in `files/keys/` as `[version][12-byte IV][ciphertext || 16-byte GCM tag]`.
The KEK itself never leaves the Keystore, so possession of the app's files is not possession of
the data.

**The KEK is deliberately not bound to user authentication.** `setUnlockedDeviceRequired` and
`setUserAuthenticationRequired` look strictly safer, but this app's primary workload is a
`NotificationListenerService` that runs while the screen is locked — which is exactly when
notifications arrive. Either flag would make `Cipher.init` throw on nearly every write. The
service is not `directBootAware`, so it does not run before first unlock; confidentiality at
rest is therefore carried by file-based encryption plus a hardware-bound KEK.

StrongBox is requested when present and falls back cleanly when it is not (most devices, and
every emulator, lack it).

## Algorithms

| Purpose | Algorithm | Why |
|---|---|---|
| Key wrapping | AES-256-GCM (Keystore) | authenticated; key material never in app memory |
| Database | SQLCipher 4, AES-256, raw-key mode | page-level encryption; a raw key skips PBKDF2, which is right for a `SecureRandom` key rather than a password |
| Sender pseudonyms | HMAC-SHA256, truncated to 96 bits | keyed, so offline enumeration has nothing to grind against |
| Randomness | `SecureRandom` | — |

Rejected: Jetpack `security-crypto` (deprecated and unmaintained); ChaCha20-Poly1305 (only wins
on CPUs without AES acceleration, which arm64 devices have); PBKDF2 over the database key (not
memory-hard, and pointless for a full-entropy key).

## Encryption migration

Existing installs are converted once, before Room opens the file, as copy-and-swap: the
plaintext database is untouched until a fully-formed, integrity-checked encrypted copy exists.
Any failure leaves the original intact and retries on the next launch.

Rows are copied explicitly rather than with `sqlcipher_export`. `ATTACH` is per-connection and
`SQLiteDatabase` routes statements across a connection pool by whether they look read-only:
executed as a statement the export function never steps (producing an empty file), executed as a
query it steps on a reader that never saw the `ATTACH`.

`fallbackToDestructiveMigration` is **not** enabled. Dropping every table would erase history,
sender memory and the learned model without warning; a migration defect must surface as a
fixable crash. Migrations are covered by instrumented tests.

## Deleting data

`PanicWipe` clears all rows, clears settings, deletes exported files, **destroys the Keystore
entry and wrapped keys**, then removes the database files. Destroying the keys is what makes the
guarantee real: deleted rows can still be carved out of a SQLite file's free pages, but without
the key the pages are meaningless. New keys are provisioned on the next launch, so the app stays
usable rather than bricked.

## Network posture

The app declares no `INTERNET` permission. There is no analytics SDK, no crash reporter and no
network client. The only way data leaves the device is a user-initiated export through the
system share sheet.

If opt-in cloud sync ships, encryption happens before anything is uploaded, and the network
capability arrives with a user-visible record of every request.

## Before shipping a release build

R8 minification is enabled for release. SQLCipher and ONNX Runtime are both reached through
JNI, so their classes are kept explicitly in `proguard-rules.pro` — without that a release
build can lose the database layer in a way that never shows up in debug.

This has been verified to *build*, but a signed release has not been run on a device. Do that
once before the first ship: install a signed release APK, confirm the database still opens and
a notification is still classified.

## Known gaps

- Backups are not yet implemented, so a lost or reset device loses the learned model. The
  backup design must not weaken the guarantees above: it needs a key derivable from a user
  secret, since Keystore keys cannot move between devices.
- The exported JSONL contains embeddings, which are not fully anonymous. Export is explicit,
  but the wording around it should say this plainly.
