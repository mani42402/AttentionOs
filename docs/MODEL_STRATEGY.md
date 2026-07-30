# On-device model strategy

## Current encoder

`potion-base-8M` — a static token-embedding table distilled from `bge-base-en-v1.5`, quantised
to INT8.

| | |
|---|---|
| Asset | 7.3 MB (`models/potion-base-8m-q8.bin`) + 0.2 MB vocabulary |
| Embedding | 256 dimensions |
| Vocabulary | pruned bert-base-uncased WordPiece, 29,528 tokens |
| Runtime | none — a memory-mapped lookup table |
| Licence | MIT |

There is no transformer. Every token has one pretrained vector and a sentence is the mean of its
tokens, normalised. A notification therefore costs a few thousand additions instead of six
attention layers.

The honest description of the trade: a bag of token vectors **cannot represent word order**, so
"the payment failed" and "failed the payment" embed identically. That is a real limitation, and
the only reason it is acceptable is that it was measured on notification text rather than
assumed away.

### What it replaced

`all-MiniLM-L6-v2` INT8 plus ONNX Runtime. Removing both is the largest size change the project
will ever make:

| | before | after |
|---|---|---|
| Download (AAB, excluding metadata) | 29.9 MB | **9.8 MB** |
| Install footprint | 53.3 MB | **11.9 MB** |
| `libonnxruntime.so` | 26.7 MB | gone |
| encoder asset | 22.0 MB | 7.3 MB |

ONNX Runtime measured **26.7 MB**, not the 6–15 MB the original plan assumed. That single
correction is what made the bake-off decisive rather than marginal.

## Bake-off: `all-MiniLM-L6-v2` vs `potion-base-8M`

Run by `EncoderEvaluationTest` on an Android 16 emulator: identical samples, identical scoring,
identical prototype sentences, one encoder swapped.

A first run over 20 samples put MiniLM ahead on category accuracy by 5 points. That is one
sample. The labelled set was widened to 50 before anything was decided, and the ordering
reversed — which is the whole reason the first number could not be trusted.

```
                    all-MiniLM-L6-v2   potion-base-8M
samples                           50               50
category accuracy              68.0%            72.0%
guaranteed alerts    100.0% (12/12)   100.0% (12/12)   [asserted]
wanted promptly       70.6% (12/17)     70.6% (12/17)  [measured]
correctly quiet       84.8% (28/33)     81.8% (27/33)
latency median                4.6 ms           1.9 ms
latency p90                   7.3 ms           3.3 ms
```

**Decision: adopt `potion-base-8M`.** Not because it scores better — a 2-sample difference on a
50-sample set is noise in both directions — but because it is *not measurably worse* on any
metric that matters, ties exactly on both safety and ranking measures, is 2.4× faster, and costs
20 MB less to download and 41 MB less on disk.

What the numbers do **not** support is a claim that the static encoder understands notifications
better. The defensible statement is that on this task, at this text length, the transformer's
extra capacity was not buying anything measurable.

Both encoders miss the same five "wanted promptly" cases, and both hold every hard safety floor
at 100%. The shared misses are a personalization gap, not an encoder gap — see below.

### Consequences

- ONNX Runtime, the MiniLM assets, and `MiniLmLanguageAnalyzer` are deleted.
- The tokenizer's `[CLS]`/`[SEP]` bracketing and attention mask existed only to build ONNX input
  tensors. Static pooling averages per-token vectors directly, so both were removed rather than
  left as unreachable code.
- Embeddings move 384 → 256 dimensions, so `PersonalizedAttentionModel.MODEL_VERSION` is 3 and
  weights learned in the old feature space are discarded rather than reinterpreted.
- `StaticEmbeddingParityTest` checks the Kotlin tokenizer against HuggingFace `tokenizers` on the
  shipped vocabulary, including accented Latin and CJK. A bake-off between two encoders is
  meaningless if one of them is not the model it claims to be, and a one-rule difference in
  normalisation would not show up anywhere in a scorecard.

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


## Measured against real notifications

`RealWorldMultilingualTest` runs 59 notifications of the kind people actually receive — a mother
asking about dinner, a partner waiting outside, a family message about a hospital, a bank OTP, a
landlord about rent, school about a child — in English, Hindi, Urdu, Chinese and Spanish.

```
           n    unknown tokens   must-reach   quiet-right
ENGLISH    18        0.5%           4/11          6/7
HINDI      12        5.0%           3/8           4/4
URDU       10        3.1%           3/7           3/3
CHINESE    10       68.1%           3/7           3/3
SPANISH     9        0.0%           1/6           3/3
```

"Must-reach" means a person would be upset to have missed it. Nothing is ever hidden — a MEDIUM
or LOW notification still appears in the shade — so a miss means *delivered quietly instead of
promptly*, which is the difference between the product working and not.

### What this says

**The classifier contributes almost nothing on real interpersonal messages.** English recall is
4 of 11, and those four are exactly the ones a deterministic floor catches: the missed call, the
OTP, the fraud alert, the alarm. Everything requiring comprehension lands low:

```
Family: "Dad is in the hospital, call me now"          -> MEDIUM (SOCIAL)
Priya:  "I'm outside your office, come down"           -> MEDIUM (SOCIAL)
Rahul (Manager): "Can you look at the outage before…"  -> LOW (WORK)
Landlord: "Rent is overdue, please transfer today"     -> LOW (OTHER)
School: "Aarav was marked absent today"                -> LOW (OTHER)
```

Earlier scorecards read 68–75% because their samples contained the urgency vocabulary the
keyword rules were written against — "production down", "returning errors", "security alert".
Real messages from real people do not.

**The safety floors are English-only.** `securityWords` and `financeWords` are ASCII literals, so
`ओटीपी`, `او ٹی پی`, `验证码` and `código de verificación` match nothing. A Hindi-, Urdu-,
Chinese- or Spanish-speaking user's one-time password and fraud alert receive no protection at
all. The only language-independent floors are the ones Android supplies as a `categoryHint`:
calls and alarms. Those held in every language, and are the reason the non-English columns are
not zero.

**Hindi and Urdu look readable and are not.** At 3–5% unknown tokens they appear healthier than
Chinese at 68%, but that is an artefact: WordPiece decomposes their words into the few Devanagari
and Arabic characters the vocabulary happens to contain. Those character embeddings were
distilled from English text, so the encoder returns confident nonsense rather than admitting it
cannot read the input. Chinese failing loudly is the safer failure.

### What would fix it, cheapest first

1. **Language-independent security and finance floors.** A 4–8 digit code in a short body is a
   one-time password in every language, and the sending package is known. This is the urgent one:
   it is a safety gap, not a quality gap, and it does not need a new model.
2. **Weight named-person conversations properly.** A message from a named contact in a messaging
   app currently adds 0.08 to the score. That single number is why "Dad is in the hospital" reads
   as social chatter. Language-independent and cheap.
3. **The multilingual encoder** — `static-similarity-mrl-multilingual-v1` at 128d, 13.6 MB plus a
   2.5 MB Rust tokenizer. Fixes the scripts properly, and is the only one of the three that
   requires new weights.

Personalization does not substitute for any of these. It needs a seven-day pilot and 50
corrections before it may influence ranking, so a new user gets the uncorrected behaviour for at
least a week.

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


## Multilingual (later)

`static-similarity-mrl-multilingual-v1` truncated to 128 dimensions is 13.6 MB plus a 2.5 MB
tokenizer — *smaller than today* while covering 50+ languages, with ONNX Runtime deleted. It
needs a real Rust tokenizer (a 105k mBERT vocabulary is beyond the hand-rolled WordPiece) and a
dimension change. Not before the English bake-off settles.

Note the current CJK limitation: tokenization is correct (characters are segmented per BERT's
rules), but an English vocabulary maps most ideographs to `[UNK]`, so Chinese notifications
carry little signal. Kana and Hangul fare better. Real multilingual quality needs the model
above, not another tokenizer fix.
