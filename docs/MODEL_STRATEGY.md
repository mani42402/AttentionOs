# On-device model strategy

## Current encoder

`sentence-transformers/static-similarity-mrl-multilingual-v1`, Matryoshka-truncated to 128
dimensions and quantised to INT8.

| | |
|---|---|
| Asset | 13.3 MB table + 0.8 MB vocabulary |
| Embedding | 128 dimensions |
| Vocabulary | 105,879 WordPiece tokens, multilingual |
| Runtime | none — a memory-mapped lookup table |
| Licence | Apache-2.0 |

Script coverage against the English table it replaced:

| | before | after |
|---|---|---|
| Latin | 27,769 | 57,928 |
| Han | 492 | 16,694 |
| Cyrillic | 86 | 12,163 |
| Arabic | 88 | 4,526 |
| Devanagari | 70 | 1,596 |
| Hangul | 70 | 1,397 |
| Kana | 188 | 1,187 |

Rebuild with `tools/build_encoder_asset.py`, which refuses to emit an asset whose INT8
reconstruction cosine drops below 0.999 or whose tokenizer is not WordPiece.

**128 dimensions, not 256.** Matryoshka training makes the leading dimensions usable alone, and
128 scored the same as 256 on the labelled set while halving both the asset and the
per-notification arithmetic. No reason to pay twice for it.

Download is 16.3 MB and the install footprint 18.2 MB, against a 30 MB budget. Median inference
fell from 2.05 ms to 1.38 ms *despite* the larger vocabulary, because the embedding is half as
wide.

## Three faults, each hiding the next

**The encoder could not read most of the world.** `potion-base-8M` is distilled from an English
model: 94% Latin vocabulary, 70 Devanagari tokens, 88 Arabic. Chinese came back 68% unknown.

**The tokenizer was shredding Indic and Arabic script.** The word pattern was `[\p{L}\p{N}]+`,
and Devanagari vowel signs are Unicode `Mc`, not letters — so "पापा" split into four "words" at
every matra and emitted 18 pieces where the reference emits 11. Every such notification was
reduced to syllable fragments whose embeddings mean nothing. It looked like a model problem until
somebody compared token counts against the reference tokenizer.

**Attention Mode made the top of the scale unreachable.** It subtracted 0.17 from every
non-urgent score so MEDIUM items would fall into the queueable band. A conversation from an
unknown sender has a ceiling of 0.850; the penalty took it to 0.680; `HIGH` begins at 0.680. No
encoder output could clear it — a perfect oracle returning urgency 1.0 landed exactly on the
threshold. Attention Mode now widens the queue band instead, which achieves the same restraint
without touching classification.

That third fault is why the first bake-off appeared to show the encoder did not matter: every
candidate scored an identical 4/11 on English because all three were hitting the same cap.

**A calibrated constant broke on contact with a new model.** Category assignment discarded any
match below a cosine of 0.22, a number tuned against one embedding space. In the new space it
silently stopped recognising security alerts as SECURITY, costing them their safety floor. The
prototype set already contains an OTHER entry, so nearest-prototype normalises itself; the
threshold was removed rather than retuned.

## Personalization

Frozen encoder, small trainable head — the right shape at 50–500 examples, where fine-tuning the
encoder would overfit.

**Replay-buffer refit.** The classifier is re-fit from scratch over every stored correction
instead of taking one gradient step per correction. One-pass online learning sees the third
example once, at a large learning rate, and never again: it underfits and the result depends on
the order corrections happened to arrive. Since embeddings are already persisted, the whole set
can simply be re-fit — a few thousand examples over 30 epochs costs milliseconds. Tests pin both
properties: refit is at least as accurate as the online pass on the same data, and reversing the
input order moves the weights by <0.05.

Classes are weighted by inverse frequency. Resampling was rejected: peer-reviewed evidence shows
it leaves accuracy unchanged while degrading calibration, and calibration is what matters here
because the probability is blended into a score and re-bucketed into five levels.

**Centroid cold-start.** Class means over the embeddings become usable at 3 corrections per
class, where a 390-weight classifier is still noise. This is the fix for the real problem: the
classifier could not act until 50 corrections *plus* four evaluation gates, a bar most users
never reach, so "it learns from you" was theoretical for them. The blend hands over gradually —
centroids dominate while examples are scarce, the classifier takes over as it earns its keep.

Centroids widen *when* personalization can help, never *whether* it is allowed to. The seven-day
shadow pilot and every evaluation gate still hold, and the no-downgrade rule for protected
categories is unchanged.

### Calibration and the package feature

Two of the three refinements the plan deferred are now in, batched with the encoder swap so
there is one weights reset rather than three:

**Platt scaling.** The raw sigmoid of a class-weighted, L2-regularised fit is not a probability
— class weighting alone shifts it — yet the score was blended and re-bucketed at fixed cutoffs
as though 0.5 were a calibrated decision point. `refit` now fits `sigmoid(a·logit + b)` on the
correction set, with Platt's own target correction rather than hard 0/1 so a separable set
cannot drive the fit to infinite confidence. The slope is clamped positive: a negative slope
would invert the ranking the weights just learned. Below 30 corrections the transform stays the
identity, because two free parameters on a handful of points fit noise.

**Hashed per-package bias.** 64 buckets appended to the feature vector, one set per
notification. Sender memory is keyed per conversation, so "this Slack workspace always matters"
had to be relearned for every new conversation inside it; the classifier can now pick that up in
two or three corrections. Collisions are harmless — two apps sharing a bucket share a prior.

Both land as schema v9 and `MODEL_VERSION` 4. The migration defaults calibration to slope 1 /
intercept 0, which reproduces the previous behaviour exactly for a model fitted before it
existed.

### Still deferred: the five-class ordinal head

The model still predicts binary, blends into a score, then re-buckets into five levels, losing
information twice. It is deliberately not implemented yet.

Unlike the other two, it changes the *output shape*, and every safety gate — the shadow pilot,
the accuracy and important-recall thresholds, the no-downgrade rule for protected categories —
is written against a binary decision. Rewriting all of them is only justified if the five-class
head actually predicts better, and that cannot be established here: personalization quality is
measurable only against real correction histories, which do not exist until the app has been
used. Shipping it now would mean rewriting the safety gates on the strength of a guess.


## How a notification is classified

The nearest description decides. Nothing is added up.

`AttentionDescriptions` holds ~50 sentences describing *reasons a notification matters* — "a family
member has been hurt and needs you", "money has left my account without my permission", "a shop is
advertising a discount". Each names a band. At load the encoder embeds them once; each notification
is then one embedding plus a few dozen dot products of 128 floats, and takes the band of whichever
sentence it lands nearest.

Two things may adjust the result, and both may only **raise** it:

- **Deterministic safety floors** — a call, an alarm, or a keyword-confirmed security or finance
  event. These are promises the product makes, so they rest on evidence rather than on a model
  being right. The model's own nearest-category guess is deliberately *not* allowed to trigger one:
  nearest-prototype always returns something, and letting a guess drive a floor promoted a third of
  the corpus noise to HIGH.
- **A sender the user actually engages with** — three or more interactions and a 70% open rate. Who
  sent something outranks what it says, in any language, however novel the phrasing. It is the one
  signal that can never be out-of-vocabulary, and it was previously worth 0.08 in a sum.

### What this replaced

```
score  = 0.28                              constant
       + urgency          x 0.34           <- the only model output
       + senderImportance x 0.20           0.5 when the sender is unknown
       + senderOpenRate   x 0.10           0.5 when the sender is unknown
       + 0.08 if a conversation
       + 0.28 SECURITY / 0.13 FINANCE / -0.32 PROMOTION
priority = score >= 0.86 CRITICAL / 0.68 HIGH / 0.46 MEDIUM / 0.24 LOW
```

Nine tuned constants and four thresholds, with the model supplying one number weighted 0.34. For an
unknown sender, 0.43 of the score was fixed before any content was read — which is why tuning a
single constant once moved accuracy further than replacing the entire encoder did. All of it is
gone.

### Why descriptions rather than a trained head

A trained classifier needs labelled examples, and the only ones available are written by hand, so
the head would learn that phrasing. Descriptions need no training data, so there is nothing to
overfit to. They are matched in a multilingual space, so each is written **once**, in one language,
and covers every language the encoder reads — a keyword list would need every phrasing in every
language.

They are also debuggable in a way a prompt is not. Add a sentence, re-run 225 cases, read exactly
what moved. Two descriptions here were caught over-reaching that way: "a driver is at my door" was
matching every "your order is 5 minutes away", and "asking me a direct question" was matching
casual weekend plans. Narrowing both recovered 6 noise cases for 2 recall.

### The bound

The list enumerates *reasons to care*, not phrasings. New apps appear constantly; new reasons for a
human to need something almost never. ~50 sentences cover 15 message kinds across 15 languages.

That claim is falsifiable, and this is where it fails: if real users keep needing reasons not on the
list, the space is not bounded and a model that can be instructed in prose is the better answer. The
seam exists — `LanguageAnalyzer` is an interface and `EncoderAsset` is injectable — so a cascade
sending only the ambiguous minority to a larger model is an implementation, not a rewrite.

## Measured on the full corpus

225 cases: 15 languages x 15 message kinds, including **Roman Urdu and Hinglish**, in
`androidTest/assets/notification-corpus.json`.

```
                          weighted sum      descriptions
must-reach                  71/120  59%      110/120  92%
noise correctly quiet       86/105  82%       85/105  81%

by message kind                  before            after
  family emergency            8/15  53%       15/15  100%
  bank OTP                   15/15 100%       15/15  100%
  bank fraud                 15/15 100%       15/15  100%
  missed call                15/15 100%       15/15  100%
  school: child absent        0/15   0%       15/15  100%
  landlord: rent overdue      4/15  27%       14/15   93%
  boss: urgent problem        5/15  33%       11/15   73%
  partner: waiting outside    9/15  60%       10/15   67%
```

Recall rose 33 points and noise rejection held. Per-language spread is 67-100% with Roman Urdu at
88%, unknown tokens 0.0% everywhere. Median inference 1.4 ms, p99 2.9 ms; safety floors held
102/102 under a 400-notification flood.

`school_child` going 0/15 to 15/15 is the clearest single result: nothing about the encoder changed
between those two numbers, only what was allowed to decide.

### What is still wrong

Category accuracy on the older 50-sample English set reads 52%, down from 68%. That set scores the
*category label*, which is now a nearest-prototype guess with no cutoff and which no longer feeds
any decision except through the deterministic floors. It should be retired or rewritten to score
bands.

`partner_now` at 67% and `boss_urgent` at 73% are the weakest real kinds. Both are short messages
whose urgency is contextual — "come down now" means something only if you agreed to meet.

## Ruled out

Researched July 2026; revisit only if the underlying facts change.

- **Generative on-device LLMs.** The smallest usable build (Gemma 3 270M) is 249–304 MB, roughly
  ten times the entire app budget, at ~460–660 ms per inference on mid-range hardware and about
  3%/day battery on a *flagship* at 100 notifications/day. Google Play began enforcing battery
  technical quality in March 2026, and per-notification generative inference inside a listener
  service is the textbook way to trip it. MediaPipe's LLM Inference API is maintenance-only on
  Android.
- **EmbeddingGemma** — smallest published ONNX build is 175 MB.
- **Multilingual transformer encoders** — 118 MB+ at INT8, because the XLM-R/mBERT vocabulary
  table alone is ~96M parameters.
- **On-device LoRA / ONNX Runtime training / LiteRT training** — the ORT mobile *training*
  artifact lags the inference one by many releases, and fine-tuning an encoder on ≤500 examples
  would overfit. A frozen encoder with a small trainable head is the right shape here.
- **Migrating to LiteRT or chasing GPU/NPU delegates** — NNAPI was deprecated in Android 15, the
  PyTorch→LiteRT path still routes through ONNX, and for a model this size delegate setup and
  host↔accelerator transfer dominate the work.


## Remaining language work

The encoder now reads every script the corpus covers. What is left is not the model:

1. **Language-independent security and finance floors.** Digit-pattern detection plus the sending
   package. A safety floor must not depend on an English keyword list, or on any model.
2. **Weight named-person conversations properly.** A message from a named contact in a messaging
   app adds 0.08 to the score. That single number is why "Dad is in the hospital, call me now"
   reads as social chatter.
3. **Separate the category-accuracy regression** into its two causes before deciding whether it
   matters.
