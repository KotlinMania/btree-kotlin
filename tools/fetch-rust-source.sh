#!/usr/bin/env bash
#
# Fetch the upstream Rust standard library btree source into
# tmp/rust-stdlib-collections-btree/. The source is not tracked in
# this repo (per .gitignore); each developer / CI run rehydrates from
# upstream.
#
# Run from the repo root:
#
#     ./tools/fetch-rust-source.sh
#
# Pin a specific upstream commit (instead of master) by exporting
# RUST_SOURCE_REF before invoking. Default is `master`.

set -euo pipefail

REF="${RUST_SOURCE_REF:-master}"
DEST="tmp/rust-stdlib-collections-btree"
BASE="https://raw.githubusercontent.com/rust-lang/rust/${REF}/library/alloc/src/collections/btree"

mkdir -p "$DEST/map" "$DEST/node" "$DEST/set"

# Production source files.
files=(
  append.rs
  borrow.rs
  dedup_sorted_iter.rs
  fix.rs
  map.rs
  mem.rs
  merge_iter.rs
  mod.rs
  navigate.rs
  node.rs
  remove.rs
  search.rs
  set.rs
  set_val.rs
  split.rs
  map/entry.rs
  map/tests.rs
  node/tests.rs
  set/tests.rs
)

# Also pull core::ops::range.rs — Search.kt's Bound / RangeBounds
# trait references live there.
core_files=(
  "library/core/src/ops/range.rs:range.rs"
)

echo "fetching from ref $REF..."
for f in "${files[@]}"; do
  url="$BASE/$f"
  out="$DEST/$f"
  mkdir -p "$(dirname "$out")"
  if curl -sf "$url" -o "$out"; then
    echo "  ok: $f"
  else
    echo "  miss: $f (skipping; may have been removed upstream)"
  fi
done

# Pull core::ops::range.rs into the same tree.
for entry in "${core_files[@]}"; do
  src="${entry%%:*}"
  dest="${entry##*:}"
  url="https://raw.githubusercontent.com/rust-lang/rust/${REF}/${src}"
  out="$DEST/$dest"
  if curl -sf "$url" -o "$out"; then
    echo "  ok: $dest (from $src)"
  else
    echo "  miss: $dest (from $src)"
  fi
done

echo
echo "vendored source ready at $DEST"
echo "(remember: tmp/ is gitignored; do not commit anything from this tree)"
