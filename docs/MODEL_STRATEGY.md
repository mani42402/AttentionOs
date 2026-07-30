# On-device model strategy

## Current encoder

`sentence-transformers/all-MiniLM-L6-v2`, INT8-quantised for ARM64.

| | |
|---|---|
| Asset | 22.0 MB (`models/minilm-l6-qint8-arm64.onnx`) |
| Embedding | 384 dimensions |
| Vocabulary | bert-base-uncased, 30,522 tokens |
| Runtime | ONNX Runtime, CPU, one intra-op thread |
| Licence | Apache-2.0 |

Replaced `paraphrase-MiniLM-L3-v2` (17.5 MB, 3 layers). The swap cost +4.5 MB for twice the
encoder depth. The vocabulary file is **byte-identical** between the two models (verified by
SHA-256), so the tokenizer and its golden vectors were unaffected.

Changing the encoder changes the feature space the personal classifier was trained in, so
`PersonalizedAttentionModel.MODEL_VERSION` was bumped to 2. Weights learned against L3 are
discarded rather than reinterpreted — reusing them would produce confident nonsense instead of
an error.

## Baseline scorecard

Measured by `EncoderEvaluationTest` on an Android 16 emulator against a 20-notification
labelled set. Re-run it after any encoder change to get a comparable number.

```
model              all-MiniLM-L6-v2-qint8-arm64
category accuracy  75.0%
guaranteed alerts  100.0%  (6/6)   [asserted]
wanted promptly     75.0%  (6/8)   [measured]
correctly quiet     83.3%  (10/12)
latency median      4.8 ms
latency p90        20.5 ms
```

The harness asserts only what the engine actually guarantees — the never-suppress categories
plus calls and alarms — and measures everything else. Asserting on unguaranteed behaviour would
make the test encode a promise the design never made.

**The two measured misses are the interesting result.** "Production down: the checkout service
is returning 500s" and "Manager: can you jump on a call right now?" both rank MEDIUM. Neither
matches a hard floor, and the keyword rules miss them on a technicality: the production-incident
rule wants "production" near " is down", and the strong-action rule wants "action required"
alongside "right now". This is exactly the gap personalization is meant to close, so it is
tracked as a quality metric rather than patched with more keywords — widening the keyword list
until a test passes would be fitting the rules to the test set.

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

## Next: static-embedding bake-off

`potion-base-8M` (Model2Vec static token lookup, 7.6 MB, MIT) is the candidate to beat. It
scores above MiniLM-L6 on MTEB *classification* while losing ground on semantic similarity, and
it needs no transformer forward pass at all.

The prize is not the model size. `libonnxruntime.so` is **26.7 MB stored / 9.8 MB compressed —
larger than the model it exists to run**. A static encoder removes the runtime entirely, which
would take the release bundle from 25.4 MB to roughly a third of that and put inference in the
sub-millisecond range.

The risk is that the prototype-cosine urgency path is a similarity task, which is where static
embeddings are weakest. If the bake-off favours potion, that heuristic should be retired in
favour of the learned classifier — the task static embeddings are actually better at.

Run `EncoderEvaluationTest` against both and decide on the numbers.

## Multilingual (later)

`static-similarity-mrl-multilingual-v1` truncated to 128 dimensions is 13.6 MB plus a 2.5 MB
tokenizer — *smaller than today* while covering 50+ languages, with ONNX Runtime deleted. It
needs a real Rust tokenizer (a 105k mBERT vocabulary is beyond the hand-rolled WordPiece) and a
dimension change. Not before the English bake-off settles.

Note the current CJK limitation: tokenization is correct (characters are segmented per BERT's
rules), but an English vocabulary maps most ideographs to `[UNK]`, so Chinese notifications
carry little signal. Kana and Hangul fare better. Real multilingual quality needs the model
above, not another tokenizer fix.
