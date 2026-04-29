# btree-kotlin — Agent Guidelines

Line-by-line Kotlin Multiplatform port of `std::collections::BTreeMap`
and `BTreeSet` from the Rust standard library
(`library/alloc/src/collections/btree/`).

The upstream Rust source is **not tracked** in this repo — run
`./tools/fetch-rust-source.sh` once after cloning to populate
`tmp/rust-stdlib-collections-btree/`. That fetched tree is the only
authority on what each function should do; never edit it to make a
port easier.

## General Porting Principles

### 1. Line-by-line transliteration

- Maintain file structure and organization from the Rust source.
- Translate functions in the same order they appear upstream.
- Preserve every comment, inline note, and `# Safety`/`# Panics` block —
  translate the language conventions to KDoc but keep the intent verbatim. This means translating Rust concepts in comments (e.g. `traits`, `lifetimes`, `ZSTs`) to their exact Kotlin API equivalents.
- **NO PORTING NOTES**: Do not add comments explaining Kotlin workarounds, "Rust vs Kotlin" rationale, or any other porting narratives to the source code.
- **NO RUST IN COMMENTS**: Never leave untranslated Rust code snippets or snake_case identifiers in the Kotlin KDocs. Ensure the documentation accurately describes the Kotlin API.
- A missing function is preferable to a stub. If you can't translate
  something, leave the slot empty and track it explicitly (e.g. in
  `NEXT_ACTIONS.md`) rather than committing a fake implementation.

### 2. Provenance markers (REQUIRED)

Every ported `.kt` file must start with:

```kotlin
// port-lint: source node.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree
```

The `// port-lint:` line is the contract. Path MUST be relative to the `source.path` defined in the project's `.ast_distance_config.json` (e.g., `node.rs`, `map/tests.rs`). Do NOT use the full upstream `library/alloc/...` path. The second comment line satisfies Apache-2.0 §4(b)
"preserve copyright notices" and MIT's notice requirement.

### 3. Copyright header

Already covered by the two-line preamble above. Don't add a separate
multi-paragraph header — the upstream source doesn't have one and the
NOTICE file at the project root carries the long-form attribution.

### 4. License compatibility

The Kotlin port is dual-licensed Apache-2.0 OR MIT, mirroring upstream.
Don't add code under any other license without surfacing it for review.

### 5. Strict Structural Parity (`ast_distance`)

The `ast_distance` tool is useful for:
- coverage accounting (which symbols/functions are still missing),
- cheat detection (stubs, Rust-in-comments, porter-invented typealiases),
- and pinpointing specific parity gaps in a file.

It is not the gate.

The gate for btree-kotlin is **behavioral parity**, proven by the ported tests:
- Transliterate upstream test modules into `src/commonTest` and get them to pass.
- Prefer adding/porting the minimal test utilities needed to support those tests
  (under `src/commonTest/kotlin/io/github/kotlinmania/btree/testing/`), rather
  than bending `commonMain` to appease a structural score.

If `ast_distance` crashes (OOM / `Killed: 9`) on huge files (e.g. `map.rs` /
`Map.kt`) during `--deep` or `--compare-functions`, treat that as a tool
limitation. Keep porting from the Rust source and validate via the test gate.

## Rust → Kotlin translation rules (binding)

These rules are enforced by code review and by the `port-lint` check.
Deviations need a one-line comment explaining the reason.

### Type-level idioms

| Rust | Kotlin |
|---|---|
| `BTreeMap<K, V>` | `class BTreeMap<K, V>(comparator: Comparator<in K>?)` |
| `BTreeSet<T>` | `class BTreeSet<T>(comparator: Comparator<in T>?)` |
| `&'a T` (shared reference) | regular Kotlin reference |
| `&'a mut T` (exclusive ref) | regular Kotlin reference; mutate through it |
| `*mut T`, `*const T`, `NonNull<T>` | regular Kotlin reference; GC handles drop |
| `MaybeUninit<T>` | `arrayOfNulls<T>(n)` slot, accessed via `!!` (callers know it's initialised) |
| `ManuallyDrop<T>` | omit — no equivalent, GC supersedes |
| `core::mem::replace(&mut x, y)` | inline swap helper or direct read-then-write |
| `core::ptr::drop_in_place` | omit — GC |
| `core::ptr::read(p)` / `core::ptr::write(p, v)` | direct field access |
| `unsafe { ... }` block | regular Kotlin code; add `// SAFETY: <reason>` line marking the original boundary |
| `'a` lifetime parameter | regular generic type parameter; lifetimes don't translate |
| `where K: Ord` | `where K : Comparable<K>` (or `Comparator<in K>` field) |
| `where K: ?Sized` | irrelevant in Kotlin; drop the bound |

### Function/value idioms

| Rust | Kotlin |
|---|---|
| `pub fn foo()` | `fun foo()` (public by default) |
| `pub(crate) fn foo()` | `internal fun foo()` |
| `fn foo()` (private) | `private fun foo()` |
| `let x = ...` | `val x = ...` |
| `let mut x = ...` | `var x = ...` |
| `match x { ... }` | `when (x) { ... }` |
| `if let Some(v) = x` | `x?.let { v -> ... }` |
| `?` operator (Try) | inline early-return: `val v = x ?: return null` (or throw, depending on context) |
| `Option<T>` | `T?` (nullable) |
| `Result<T, E>` | throw `E` as exception, return `T` |

### Naming

| Rust (snake_case) | Kotlin (lowerCamelCase) |
|---|---|
| `fn first_key_value` | `fun firstKeyValue` |
| `let len_underflow` | `val lenUnderflow` |
| Type names already PascalCase | unchanged (`BTreeMap`, `NodeRef`) |
| `const FOO_BAR: usize = 5` | `const val FOO_BAR: Int = 5` (UPPER_SNAKE for compile-time constants is idiomatic Kotlin) |
| `mod foo;` | leave the file in `package io.github.kotlinmania.btree`; Kotlin packages mirror Rust modules conceptually but we keep a flat namespace inside `btree` |

### Iterator translation

`BTreeMap` ships eight iterator types: `Iter`, `IterMut`, `Keys`,
`Values`, `ValuesMut`, `IntoIter`, `Range`, `RangeMut`, plus their
cursor variants. Each translates to a Kotlin class implementing
`Iterator<T>` (or `MutableIterator<T>` if it supports `remove`). The
`fused`-iterator semantics from Rust come for free in Kotlin —
`hasNext()` returning false once is sufficient.

### Drop semantics

Rust's `BTreeMap` does careful cleanup in its `Drop` impl. Kotlin's
GC obviates this: omit drop translations entirely. If a function name
upstream is `dying_*`, port the body but drop the leading `dying_` —
e.g. `dying_remove_kv` becomes `removeKv`. Document the rename in
the function's KDoc.

## Patterns from Phase 1 (binding for downstream phases)

These came out of the first wave of agent work. Roll them in when
you encounter the corresponding upstream construct.

### `pub(super)` → `internal`

`pub(super)` in upstream's flat `btree/` module is effectively
crate-internal (visible to siblings, hidden from external callers).
Kotlin's `internal` is the closest available match. The AGENTS.md
table above already covers `pub(crate) → internal`; treat
`pub(super)` the same.

### `mem::replace` / `take_mut` translation pattern

Rust's `mem::replace(&mut v, change)` and friends can't be transliterated
1:1 because Kotlin lambdas can't take a mutable reference to a caller's
local or field. Translate them as **return-the-new-value**: callers
read the old value, pass it through the closure, and assign the result
back. For `mem::replace` specifically, return `Pair<T, R>` so the side
result is preserved. Inline the read-modify-write at the call-site
when that's clearer than threading the helper through.

### `core::iter::Peekable<I>` translation

Kotlin stdlib has no `Peekable`. Inline a tiny private adapter when
you need it: a wrapper over the source iterator with one-element
look-ahead exposed via `peek(): T?` and `next(): T?` (both nullable —
null means exhausted, no separate `Option` shape needed). Cache the
peeked element in a `private var pending: T? = null` slot; refill on
demand. The Phase 1 `DedupSortedIter.kt` is the canonical example.

### Sum types with iterator-emitted variants

Rust `enum Foo<T> { A(T), B(T) }` used as a "next item came from A or
B" tag translates to **two `T?` slots plus a discriminator enum**:

```kotlin
private enum class Side { NONE, A, B }
private var side: Side = Side.NONE
private var slotA: T? = null
private var slotB: T? = null
```

The discriminator is the source of truth — nullness of the slots is
incidental, so the layout works correctly even when `T` itself is a
nullable type. `MergeIter.kt` is the canonical example.

### `Iterator::next() -> Option<Item>` split into Kotlin `hasNext`/`next`

Rust iterators expose a single `next()` returning `Option<T>`. Kotlin's
`Iterator<T>` interface requires `hasNext(): Boolean` + `next(): T`.
Translate by computing the next item once into a `private var pending: T? = null`
cache, returning `pending != null` from `hasNext()`, and consuming
the cache from `next()`. Refill `pending` lazily from the underlying
state machine. Recurring pattern across all the iterator ports.

### `Cmp: Fn(...) -> Ordering` translation

Rust `Ordering::{Less, Equal, Greater}` maps cleanly onto Kotlin's
`Comparator.compare` convention: negative / zero / positive Int.
Translate `cmp_fn: F where F: Fn(&A, &B) -> Ordering` as
`cmpFn: (A, B) -> Int` and document the contract in KDoc. Don't
introduce a Kotlin `Ordering` enum.

### `K: Borrow<Q> + Ord` plus a borrow-aware compare

Upstream uses `key.cmp(k.borrow())` to compare a query of type `&Q`
against a stored key of type `K` where `K: Borrow<Q>`. Kotlin doesn't
have `Borrow`, so:

1. Bound `Q : Comparable<Q>` (mirrors Rust's `Q: Ord`).
2. Bound `K : Comparable<Q>` — ask the stored key to compare against
   the query type directly. (Some types will need a small Comparable
   adapter in Kotlin.)
3. Replace `key.cmp(k.borrow())` with `-k.compareTo(key)` or
   `k.compareTo(key).inv()` — the sign flip preserves the Rust
   orientation (positive = key > stored, etc.).

### `ExactSizeIterator` has no Kotlin equivalent

Rust's `I: ExactSizeIterator` gives `.len()` on the iterator. Kotlin's
`Iterator<T>` doesn't. When porting a function that needs the length,
take the `Int` length as an explicit parameter from the caller. The
caller usually has it from the source collection's `size`.

### `FusedIterator` is implicit

Rust's `FusedIterator` marker promises that once `next()` returns
`None`, all subsequent calls also return `None`. Kotlin's
`Iterator<T>` contract has this implicitly — once `hasNext()` returns
`false`, the iterator stays exhausted. No marker, no extra code.

### `impl Clone` and `impl Debug`

Rust iterator types often `derive(Clone)` so you can fork them. Kotlin
`Iterator<T>` is generally not cloneable (no shared interface for it).
Omit the `Clone` translation; document in KDoc that consumers should
not assume forkability. For `impl Debug`, render as `override fun toString()`
matching upstream's tuple format.

### Trait specialization (`default fn`) has no equivalent

Rust's `impl<V> IsSetVal for V { default fn is_set_val() = false }`
plus an override `impl IsSetVal for SetValZST` doesn't translate
1:1. Use a runtime type check at the call site:
`fun <V> isSetVal(value: V): Boolean = value is SetValZst`. Document
the change in the function's KDoc, and explicitly note that
`isSetVal()` (no argument) became `isSetVal(v)` (takes the value).

### Compile-time-incomplete files are OK in early phases

A file that doesn't compile in isolation because it references types
from a later-phase file (e.g. Search.kt referencing NodeRef before
node.rs lands) is fine — but it must not be committed without:

- A header comment listing which phase will resolve the dangling refs.
- Zero stubs of the types it depends on. Forward references that fail
  to resolve are preferable to fake placeholder classes that conflict
  with the real implementation when it lands.

## File-by-file checklist

Use `ast_distance --deep` as a progress dashboard (missing files, symbol gaps,
and cheat detection), but consider a file "done" only when the corresponding
ported tests are present and passing.

## Out of scope

- The `testing/` directory under upstream's btree. **LEARNING**: While the upstream `testing/` crate is out of scope, many ported tests (e.g. in `map/tests.rs`) rely on its utilities (`Governor`, `CrashTestDummy`, `DeterministicRng`). We must port minimal, functional Kotlin equivalents of these utilities directly into `src/commonTest/kotlin/io/github/kotlinmania/btree/testing/` to support the test transliteration, ensuring no testing code leaks into `commonMain`. We then port the `tests.rs` files in `commonTest` using `kotlin.test` directly.
- `unstable` API features behind feature flags (`#[unstable(...)]`) —
  port only what's reachable through the stable surface.

## TODO policy

No TODO / `unimplemented!()` / stub bodies in committed code. If a
translation is incomplete, don't commit it; leave the slot missing and track
it in `NEXT_ACTIONS.md`.
