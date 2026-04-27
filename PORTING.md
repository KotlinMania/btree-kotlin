# Porting Plan — std::collections::BTreeMap to Kotlin

## Phase plan

Each phase boundary is "the next phase's source compiles against what
just landed". Files inside a phase have no inter-dependencies and can
be ported in parallel by separate agents.

| Phase | Files | Approx Rust lines | Parallelism | Status |
|------:|-------|------------------:|-------------|--------|
| 1 | mem.rs, borrow.rs, search.rs, dedup_sorted_iter.rs, merge_iter.rs, append.rs, split.rs, fix.rs, remove.rs | 776 | 9 agents in parallel | not started |
| 2 | node.rs | 1883 | single agent (file is internally cross-referenced) | landed |
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
| `mem.rs` | 33 | `Mem.kt` | landed | `replace`-style helpers; trivial. AST cosine avg 0.858. `PanicGuard`/`Drop` dissolved (GC). Helpers return new value (and side result for `replace`); callers assign back. |
| `borrow.rs` | 69 | `Borrow.kt` | landed | `DormantMutRef` lifetime gymnastics — translates to plain references. `'a` parameter dropped, `NonNull<T>` becomes `val ptr: T`, `unsafe`/lifetime annotations dissolved. `pub(super)` → `internal`. Rust tuple `(&'a mut T, Self)` becomes `Pair<T, DormantMutRef<T>>`. AST cosine avg 0.494 (low because each `unsafe { &mut *self.ptr.as_ptr() }` dissolves to a single `return ptr`). Name parity 4/4. |
| `dedup_sorted_iter.rs` | 49 | `DedupSortedIter.kt` | landed | Adjacent-equal-key dedup. `Peekable<I>` inlined as a private one-element-buffer class (no `core::iter::Peekable` in Kotlin stdlib). `pub(super)` → `internal`. `K: Eq` → `where K : Any` (Kotlin `==` is structural). Rust's single-call `Iterator::next -> Option<Item>` is split into Kotlin's `hasNext`/`next` via a `pending: Pair<K, V>?` cache (a `Pair` is always a non-null reference, so null-as-absence is unambiguous even when V is nullable). AST cosine avg 0.791 on `next`, 0.834 on `computeNext`. |
| `set_val.rs` | 29 | `SetVal.kt` | landed | Zero-sized marker (`SetValZst`, PascalCase per project naming) + `isSetVal(value)` bridge. Trait specialization dissolved per AGENTS.md — `IsSetVal` and its blanket+specialized impls collapse into a single top-level `internal fun <V> isSetVal(value: V): Boolean = value is SetValZst`. `data object` for `equals`/`hashCode`/`toString`; explicit `Comparable<SetValZst>` since `data object` doesn't auto-derive ordering. Signature change vs Rust: `V::is_set_val()` (no value) becomes `isSetVal(v)` (takes a value), since Kotlin has no static dispatch on a type parameter without `reified`. Search.kt's call site is the only existing one and remains spelled `isSetVal<V>()` (no value) until Phase-2 NodeRef provides a way to materialize a sample V from `self`; documented in Search.kt's forward-ref preamble. |
| `merge_iter.rs` | 98 | `MergeIter.kt` | landed | Two-iterator merge with `cmp`. `pub(super)` → `internal`. `Option<Peeked<I>>` (sum-typed enum) encoded as two `T?` slots plus a `PeekSide { NONE, A, B }` tag, since Kotlin lacks a small free-standing tagged union; `peekedSide` is the discriminator (not nullness), so `T = T?` works. `cmp_fn: F` where `F: Fn(&I::Item, &I::Item) -> Ordering` becomes `(T, T) -> Int` matching `Comparator.compare`. `nexts` returns `Pair<T?, T?>`. `lens` takes `aLen`/`bLen: Int` since Kotlin `Iterator<T>` has no `ExactSizeIterator` analogue. `impl Clone` omitted (Kotlin `Iterator<T>` is generally not cloneable); `impl Debug` rendered as `toString()`. AST cosine avg 0.890 on matched (`nexts`, `lens`). |
| `append.rs` | 66 | `Append.kt` | not started | Bulk insertion fast path |
| `split.rs` | 79 | `Split.kt` | not started | Bulk split fast path |
| `fix.rs` | 185 | `Fix.kt` | not started | Underflow recovery during deletion |
| `remove.rs` | 98 | `Remove.kt` | not started | Public removal entry points |
| `search.rs` | 289 | `Search.kt` | landed | Binary search within a node. `SearchBound`, `SearchResult`, `IndexResult`, `Bifurcation` defined as `sealed class` with per-subclass `toString()`. `searchTree`, `searchTreeForBifurcation`, `findLowerBoundEdge`/`findUpperBoundEdge`, `searchNode`, `findKeyIndex`, `findLowerBoundIndex`/`findUpperBoundIndex` translated as extension functions on `NodeRef<...>`. `K: Borrow<Q>` rendered as `K : Comparable<Q>` (k.compareTo(q) replaces key.cmp(k.borrow()), with sign inverted to preserve Rust's `key vs k` orientation). `Result<Bifurcation, Handle<...,Leaf,Edge>>` of `searchTreeForBifurcation` translated as a sealed `BifurcationResult` with `Ok` / `LeafEdge` variants — Kotlin/Native disallows type-parameterized `Throwable` subclasses, so the AGENTS.md "throw E, return T" default doesn't apply; the sealed-result shape is the more transliteration-faithful of the two valid workarounds. `searchTreeForBifurcation` is `inline reified V` so the `IsSetVal` static-dispatch overload `isSetVal<V>()` resolves at the call site (matches Rust's `V::is_set_val()` 1:1; cascades to Phase-4 callers). AST cosine avg 0.820 on `from_range` (only matched function — ast_distance tool does not currently pair Rust `impl<...> NodeRef<...> { fn foo }` methods against Kotlin `fun NodeRef<...>.foo` extension functions, so 8 of the 9 source functions show as unmatched even though all are translated 1:1). |
| `range.rs` (subset) | ~1500 (~80 ported) | `Range.kt` | landed | Bound<T> sealed class + RangeBounds<T> interface only; the concrete Range / RangeFrom / RangeInclusive / RangeFull types out of scope (Kotlin stdlib has its own range types — wire later if needed). `fn contains<U>` default-method translation narrows U to T and runtime-casts `T as Comparable<T>`, since Kotlin interface methods cannot impose extra constraints on the interface's own type parameter beyond what the interface declares. Lifetime: `start_bound(&self) -> Bound<&T>` returns `Bound<T>` (no shared-borrow vocabulary in Kotlin). AST cosine avg 0.881 on the one matched function (`from_range` — the `Bound::map`, `Bound::as_ref`, `Bound::as_mut`, `Bound::copied`, `Bound::cloned` helpers are out of scope and intentionally unmatched). |
| `node.rs` | 1883 | `Node.kt` | landed | The B-tree node. `MaybeUninit` storage translated to `arrayOfNulls<Any?>(CAPACITY)`. Raw pointers/`NonNull` collapsed to plain Kotlin references. `BoxedNode = NonNull<LeafNode>` rendered as a Kotlin class hierarchy: `class InternalNode<K, V> : LeafNode<K, V>()` mirrors the upstream `#[repr(C)]` cast trick — a `LeafNode` reference can hold either a leaf or internal instance, and `force()` discriminates at runtime via the `height` field exactly where Rust would. Phantom-type markers (`Marker.Leaf`/`Internal`/`LeafOrInternal`/`Owned`/`Dying`/`DormantMut`/`Immut`/`Mut`/`ValMut`/`KV`/`Edge`) translated as uninstantiable empty classes inside `internal object Marker`. The `BorrowType` constraint trait kept as a Kotlin interface; `TRAVERSAL_PERMIT` rendered as instance property (never queried because markers are uninstantiable — the `const { assert!(BorrowType::TRAVERSAL_PERMIT) }` gate dissolves to a comment in `ascend`/`descend`). `Result<T, Self>` shapes (`ascend`, `chooseParentKv`, `Edge::leftKv`/`rightKv`) translated as small sealed `*Result` wrappers (`AscendResult`, `EdgeKvResult`, `ChooseParentKvResult`) carrying `Ok`/`Err` so callers can recover `self` on the failure branch — straightforward translation of Rust's `Result` since the `?` operator doesn't apply here. `forget_node_type` (5 upstream impls) split into 3 distinct Handle names (`forgetNodeTypeLeafEdge` / `forgetNodeTypeInternalEdge` / `forgetNodeTypeKv`) and 2 SplitResult names (`forgetNodeTypeLeaf` / `forgetNodeTypeInternal`); Kotlin extension-function overload resolution rejects same-name extensions whose receivers differ only in concrete generic argument. `mem::replace` follows AGENTS.md "return-the-new-value" pattern — see `replaceKv`. `Drop`, `Clone`, `LeafNode::init`, the boxed-allocator `new` forms, and `as_internal_ptr`/`as_leaf_ptr` all dissolved (GC supersedes Drop, Kotlin field initialisers supersede `init`, and direct `node` access supersedes the pointer accessors). `key_area_mut(...)` / `val_area_mut(...)` / `edge_area_mut(...)` (which take a `SliceIndex`) replaced by single-slot `writeKeyArea` / `readKeyArea` / `writeValArea` / `readValArea` / `writeEdgeArea` / `readEdgeArea` plus four free `slice_*` helpers (`sliceInsert` / `sliceRemove` / `sliceShl` / `sliceShr` / `moveToSlice`) which take an array + explicit `sliceLen` since Kotlin lacks slice references. `len_mut(): &mut u16` replaced by `setLen(Int)` / `incLen(Int)`. `keys()` exposed both for `NodeRef<Marker.Immut, ...>` and for the generic `NodeRef<BorrowType, ...>` shape so Search.kt's `node.reborrow().keys()` resolves. `Send`/`Sync` impls dropped (Kotlin/Native has no equivalent). Internal helper `LeftOrRight<T>` rendered as a sealed class with `Left(value)` / `Right(value)` variants. AST cosine avg 0.717 across 97/117 strict-matched function-name pairs (1883 Rust lines → 2152 Kotlin lines, 1.14× expansion). Downstream agents (navigate, fix, remove, append, split, map, set) note: the `BorrowType` / `Type` phantom-type pattern is the most divergent translation — Kotlin combines the same phantom-type generics for static method-availability gating with the runtime `height` field for `force()` discrimination at the same locations Rust does. Treat the `Marker.*` types as opaque tags; runtime casts via `castToLeafUnchecked`/`castToInternalUnchecked` remain necessary in the same places Rust uses `unsafe`. |
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
