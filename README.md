# btree-kotlin

Kotlin Multiplatform port of Rust's `std::collections::BTreeMap` and
`BTreeSet`, translated line-by-line from
[`library/alloc/src/collections/btree/`](https://github.com/rust-lang/rust/tree/master/library/alloc/src/collections/btree).

## Why

Kotlin's standard library has no sorted-by-key map for the `commonMain`
multiplatform source set — neither built-in nor in
`kotlinx.collections.immutable`. There's an open feature request
([KT-38356](https://youtrack.jetbrains.com/issue/KT-38356)) but no ETA.

The two reasonable workarounds are:

1. Snapshot-and-sort on iteration over a `LinkedHashMap` — observable
   ordering matches `BTreeMap`, asymptotic costs are wrong.
2. Vendor a third-party sorted-list-like library — pulls in foreign API
   shapes and forces consumers to refactor.

This crate takes a third path: faithfully port the Rust standard
library's `BTreeMap` to Kotlin, preserving its B-tree-of-order-6 layout,
its iterator state machines, and its observable behavior. The result is
a drop-in `MutableMap<K, V>` / `MutableSet<T>` that orders by
`Comparable<K>` (or a supplied `Comparator<K>`).

## Status

Run `tools/ast_distance/ast_distance --deep tmp/rust-stdlib-collections-btree rust src/commonMain/kotlin/io/github/kotlinmania/btree kotlin` for the current parity numbers and per-file gaps. `ast_distance` is the sole oracle for port progress.

## Layout

```
src/commonMain/kotlin/io/github/kotlinmania/btree/
├── Node.kt              ← library/alloc/src/collections/btree/node.rs
├── Search.kt            ← search.rs
├── Navigate.kt          ← navigate.rs
├── Map.kt               ← map.rs               (public BTreeMap<K, V>)
├── Set.kt               ← set.rs               (public BTreeSet<T>)
├── Mem.kt               ← mem.rs
├── Borrow.kt            ← borrow.rs
├── Fix.kt               ← fix.rs
├── Remove.kt            ← remove.rs
├── Split.kt             ← split.rs
├── Append.kt            ← append.rs
├── MergeIter.kt         ← merge_iter.rs
└── DedupSortedIter.kt   ← dedup_sorted_iter.rs
```

Each `.kt` file's `// port-lint: source ...` header pinpoints which
upstream line it was translated from. The vendored Rust source itself
is **not** tracked in this repo — it's fetched into `tmp/` per clone
by `tools/fetch-rust-source.sh` (CI does this automatically; local
devs run it once after cloning).

## License

Dual-licensed under Apache-2.0 OR MIT, mirroring the upstream Rust
licensing. See `LICENSE-APACHE`, `LICENSE-MIT`, and `NOTICE`.

## Build

After cloning, rehydrate the vendored Rust source so port-lint and
ast_distance verification can run:

```
./tools/fetch-rust-source.sh
```

Then:

```
./gradlew compileKotlinMacosArm64
./gradlew macosArm64Test
```

Targets: macosArm64, macosX64, linuxX64, mingwX64, iosArm64, iosX64,
iosSimulatorArm64, js, wasmJs, androidLibrary.
