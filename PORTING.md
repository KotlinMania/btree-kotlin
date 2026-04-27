# Porting Plan — std::collections::BTreeMap to Kotlin

## Phase plan

Each phase boundary is "the next phase's source compiles against what
just landed". Files inside a phase have no inter-dependencies and can
be ported in parallel by separate agents.

| Phase | Files | Approx Rust lines | Parallelism | Status |
|------:|-------|------------------:|-------------|--------|
| 1 | mem.rs, borrow.rs, search.rs, dedup_sorted_iter.rs, merge_iter.rs, append.rs, split.rs, fix.rs, remove.rs | 776 | 9 agents in parallel | not started |
| 2 | node.rs | 1883 | single agent (file is internally cross-referenced) | not started |
| 3 | navigate.rs | 787 | single agent | not started |
| 4 | map.rs + map/entry.rs | 4331 | single agent (one large file with `Entry` API in a sibling) | not started |
| 5 | set.rs | 2535 | single agent (thin wrapper over Map) | not started |
| 6 | wiring + parity test against `lalrpop-kotlin`'s 4-grammar corpus | — | foreground | not started |

Total source under port (excluding `tests.rs` files and `testing/`):
**~10 500 lines of Rust**.

## Per-file checklist

| Rust file | Lines | Kotlin file | Status | Notes |
|-----------|------:|-------------|--------|-------|
| `mod.rs` | 14 | — | n/a | Module declaration only; project rule "don't translate `mod.rs`" applies. Re-exports wired by file placement. |
| `mem.rs` | 33 | `Mem.kt` | not started | `replace`-style helpers; trivial |
| `borrow.rs` | 69 | `Borrow.kt` | not started | `DormantMutRef` lifetime gymnastics — translates to plain references |
| `dedup_sorted_iter.rs` | 49 | `DedupSortedIter.kt` | not started | Adjacent-equal-key dedup |
| `merge_iter.rs` | 98 | `MergeIter.kt` | not started | Two-iterator merge with `cmp` |
| `append.rs` | 66 | `Append.kt` | not started | Bulk insertion fast path |
| `split.rs` | 79 | `Split.kt` | not started | Bulk split fast path |
| `fix.rs` | 185 | `Fix.kt` | not started | Underflow recovery during deletion |
| `remove.rs` | 98 | `Remove.kt` | not started | Public removal entry points |
| `search.rs` | 289 | `Search.kt` | not started | Binary search within a node |
| `node.rs` | 1883 | `Node.kt` | not started | The B-tree node — `MaybeUninit` storage, raw pointer manipulation |
| `navigate.rs` | 787 | `Navigate.kt` | not started | Cursor / handle traversal |
| `map.rs` | 3712 | `Map.kt` | not started | Public `BTreeMap<K, V>` and its iterators |
| `map/entry.rs` | 619 | `MapEntry.kt` | not started | The `Entry` API |
| `set.rs` | 2535 | `Set.kt` | not started | `BTreeSet<T>` thin wrapper |

## Out of scope

- `node/tests.rs`, `map/tests.rs`, `set/tests.rs` — tests will be
  rewritten in Kotlin using `kotlin.test` once the public API lands.
  Don't translate them line-by-line.
- `testing/` directory under upstream's btree (crash-test instrumentation,
  randomised RNG harness). Out of scope for the initial port.

## Triage checkpoints

After each phase lands:

1. `./gradlew compileKotlinMacosArm64 --offline` must be green.
2. Port-lint headers must point at upstream Rust paths.
3. Run `ast_distance --compare-functions` for the file vs its upstream
   counterpart and record the resulting AST cosine in this checklist.
4. No new `TODO`, `error("...")`, or `arrayOfNulls<T>(...)` without a
   `// SAFETY:` comment immediately above naming the invariant.

## Phase 6 — wiring

Once Map.kt and Set.kt land:

1. Publish a snapshot Maven artifact (`io.github.kotlinmania:btree-kotlin:0.1.0-SNAPSHOT`)
   to a local repo for `lalrpop-kotlin` to consume.
2. In `lalrpop-kotlin`, replace the snapshot-and-sort `BTreeMap` /
   `BTreeSet` classes in `collections/map/Map.kt` and
   `collections/set/Set.kt` with `import io.github.kotlinmania.btree_kotlin.BTreeMap`
   etc. Keep the Kotlin-side `Map`/`Set` typealiases pointing at the
   imported types.
3. Re-run the lalrpop-kotlin parity test. The 4-grammar corpus must
   continue reporting `[Matching]` (byte-identical to upstream LALRPOP
   oracles). If any grammar regresses, the new BTreeMap has an
   observable-behavior difference from the snapshot version — that's
   a real bug and needs to be fixed before the wiring lands.
