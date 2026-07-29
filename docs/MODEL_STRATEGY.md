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

### Deliberately deferred

Three refinements from the original plan are not implemented. Each changes the feature space or
the output shape, so each costs a version bump that discards learned weights:

- **Probability calibration** (Platt scaling / tuned threshold). The blend currently feeds a raw
  sigmoid into a score and re-buckets at fixed cutoffs, assuming 0.5 is the right decision
  point. Calibration is the cheapest of the three and the one that most affects how the
  probability behaves once it is blended.
- **Hashed per-package bias feature** — would let the model learn "this workspace always
  matters" in 2–3 corrections rather than dozens.
- **Five-class ordinal head** — the model predicts binary, blends into a score, then re-buckets
  into five levels, losing information twice.

These are best done together, and together with the static-embedding bake-off below: that
evaluation may change the embedding to 256 dimensions, which rewrites `FEATURE_COUNT` and resets
weights anyway. Batching them means one reset and one comparable measurement instead of four.

They also cannot be judged properly yet. Personalization quality is only measurable against real
correction histories, which do not exist until the app has been used for a while.

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
