#!/usr/bin/env python3
"""Build the quantised static-embedding asset the app ships.

Downloads a model2vec / sentence-transformers static embedding table, truncates it to the
requested Matryoshka dimension, quantises row-wise to int8, and writes the flat binary the
Kotlin `StaticEmbeddingAnalyzer` memory-maps out of the APK.

    python3 tools/build_encoder_asset.py \
        --repo sentence-transformers/static-similarity-mrl-multilingual-v1 \
        --weights 0_StaticEmbedding/model.safetensors --dims 128 --out app/src/main/assets/models

Row-wise scales rather than one global scale: a single scale would be set by the largest
outlier in the whole table and would flatten every other row. Reported reconstruction cosine
should stay above 0.9999.

Format: magic "P2V1" | int32 vocabSize | int32 dims | float32[vocabSize] scales | int8[V*dims]
"""
import argparse, json, os, struct, sys, urllib.request

import numpy as np


def fetch(repo: str, path: str, dest: str) -> str:
    out = os.path.join(dest, os.path.basename(path))
    if os.path.exists(out):
        return out
    url = f"https://huggingface.co/{repo}/resolve/main/{path}"
    print(f"  fetching {url}")
    urllib.request.urlretrieve(url, out)
    return out


def read_safetensors(path: str) -> np.ndarray:
    with open(path, "rb") as handle:
        header_len = struct.unpack("<Q", handle.read(8))[0]
        header = json.loads(handle.read(header_len))
        base = 8 + header_len
        name = next(k for k in header if k != "__metadata__")
        meta = header[name]
        if meta["dtype"] != "F32":
            sys.exit(f"expected F32 weights, found {meta['dtype']}")
        start, end = meta["data_offsets"]
        handle.seek(base + start)
        raw = handle.read(end - start)
    return np.frombuffer(raw, dtype="<f4").reshape(meta["shape"])


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", required=True)
    parser.add_argument("--weights", default="model.safetensors")
    parser.add_argument("--tokenizer", default="tokenizer.json")
    parser.add_argument("--dims", type=int, required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--name", required=True, help="basename for the emitted files")
    parser.add_argument("--cache", default="build/encoder-cache")
    args = parser.parse_args()

    os.makedirs(args.cache, exist_ok=True)
    os.makedirs(args.out, exist_ok=True)

    table = read_safetensors(fetch(args.repo, args.weights, args.cache))
    vocab_size, full_dims = table.shape
    print(f"  table {vocab_size} x {full_dims}")
    if args.dims > full_dims:
        sys.exit(f"--dims {args.dims} exceeds the table's {full_dims}")

    # Matryoshka: leading dimensions are trained to stand alone, so truncation is a slice.
    emb = np.ascontiguousarray(table[:, : args.dims]).astype(np.float32)
    scales = np.abs(emb).max(axis=1) / 127.0
    scales[scales == 0] = 1e-8
    quantised = np.rint(emb / scales[:, None]).clip(-127, 127).astype(np.int8)

    reconstructed = quantised.astype(np.float32) * scales[:, None]

    def unit(x):
        norm = np.linalg.norm(x, axis=-1, keepdims=True)
        norm[norm == 0] = 1
        return x / norm

    cosine = (unit(emb) * unit(reconstructed)).sum(axis=1)
    print(f"  int8 reconstruction cosine: min {cosine.min():.5f} mean {cosine.mean():.5f}")
    if cosine.min() < 0.999:
        sys.exit("quantisation loss is too high to ship")

    table_path = os.path.join(args.out, f"{args.name}-q8.bin")
    with open(table_path, "wb") as handle:
        handle.write(b"P2V1")
        handle.write(struct.pack("<ii", vocab_size, args.dims))
        handle.write(scales.astype("<f4").tobytes())
        handle.write(quantised.tobytes())
    print(f"  wrote {table_path} ({os.path.getsize(table_path) / 1048576:.2f} MB)")

    tokenizer = json.load(open(fetch(args.repo, args.tokenizer, args.cache), encoding="utf-8"))
    model = tokenizer["model"]
    if model["type"] != "WordPiece":
        sys.exit(
            f"tokenizer is {model['type']}; the Kotlin tokenizer implements WordPiece only"
        )
    vocab = model["vocab"]
    ordered = [None] * len(vocab)
    for token, index in vocab.items():
        ordered[index] = token
    if any(t is None for t in ordered):
        sys.exit("vocabulary has gaps")
    if len(ordered) != vocab_size:
        sys.exit(f"vocabulary has {len(ordered)} entries but the table has {vocab_size} rows")

    vocab_path = os.path.join(args.out, f"{args.name}-vocab.txt")
    with open(vocab_path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(ordered))
    print(f"  wrote {vocab_path} ({os.path.getsize(vocab_path) / 1048576:.2f} MB)")


if __name__ == "__main__":
    main()
