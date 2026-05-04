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
- Preserve every comment, inline note, and safety/panic doc section —
  translate the language conventions to KDoc but keep the intent verbatim. This means translating Rust concepts in comments (e.g. `traits`, `lifetimes`, `ZSTs`) to their exact Kotlin API equivalents.
- **NO PORTING NOTES**: Do not add comments explaining Kotlin workarounds, "Rust vs Kotlin" rationale, or any other porting narratives to the source code.
- **NO RUST IN COMMENTS**: KDoc must describe the Kotlin API in Kotlin
  terms. When upstream Rust uses snake_case identifiers (e.g.
  `first_key_value`, `len_underflow`) inside doc comments, the Kotlin
  port translates the *names* to Kotlin lowerCamelCase (`firstKeyValue`,
  `lenUnderflow`). **This is a translation direction, not a renaming
  scheme: never rename Kotlin code, files, or identifiers to snake_case
  to satisfy this rule.** Kotlin source stays Kotlin (PascalCase types,
  camelCase functions/locals, SCREAMING_SNAKE_CASE only for `const val`
  and enum entries). The rule prohibits *Rust syntax leaking into
  Kotlin KDoc*; it does not authorise *Rustifying Kotlin source*.
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
Deviations are documented in AGENTS.md, commit messages, or review notes,
not in source comments. Source comments must be upstream Rust comments
translated to Kotlin-facing API names and signatures.

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
| `unsafe { ... }` block | regular Kotlin code; keep only upstream comments, translated to Kotlin KDoc/source-comment style |
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

The translation direction is **always Rust → Kotlin**. Rust source uses
snake_case; the Kotlin port uses Kotlin idioms. Never rename Kotlin
files or identifiers to snake_case to "match" upstream — Kotlin source
follows Kotlin coding conventions.

- **Files / types:** `PascalCase` (e.g. `Node.kt`, `class BTreeMap`).
  Do not rename Kotlin files to lower_snake or `lowercase.kt` to mirror
  the Rust filename.
- **Functions, parameters, locals:** `lowerCamelCase`.
- **`const val` and enum entries:** `SCREAMING_SNAKE_CASE` permitted.
- **Packages:** all lowercase, no camelCase, no underscores.

| Rust (snake_case source) | Kotlin port (Kotlin idioms) |
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
GC obviates cleanup whose only purpose is freeing memory: omit those
drop translations. If a function name upstream is `dying_*`, port the
body but drop the leading `dying_` — e.g. `dying_remove_kv` becomes
`removeKv`. Document the rename in the function's KDoc.

There is one important exception: when upstream behavior observes
`Drop` side effects, especially through `catch_unwind` tests, model
that behavior deterministically. Do not rely on GC timing and do not
rewrite the test to weaker expectations.

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

### Trait default methods with `where` clauses → method-level Kotlin generic bounds

Rust traits routinely declare a default method whose body only typechecks
when the type parameter satisfies a stricter bound:

```rust
pub trait RangeBounds<T> {
    fn start_bound(&self) -> Bound<&T>;
    fn end_bound(&self) -> Bound<&T>;

    fn is_empty(&self) -> bool
    where T: PartialOrd,
    { /* default body uses < */ }
}
```

The trait itself stays unconstrained; the *method* picks up the bound via
its own `where` clause. Concrete impls can either inherit the default or
supply an inherent override.

Kotlin has no per-method `where T:` clause on an interface member —
class-level type parameters bind for the whole interface, and members
inherit that binding. Three obvious mappings fail:

1. **Tighten the interface to `<T : Comparable<T>>`.** Breaks the
   unbounded callers — any code that holds a `RangeBounds<Q>` for an
   opaque `Q` (e.g. the comparator-aware path through `BTreeMap`)
   suddenly demands a `Comparable` proof it does not have.
2. **Make the method abstract on the interface.** Forces every concrete
   impl, including ones over `Nothing` or unbounded type-parameter
   ranges, to invent a body. Adds duplicated logic and `override`
   boilerplate to types whose Rust counterpart inherits the default
   unchanged.
3. **Runtime cast helper.** The "engineering" pattern:
   `if (left is Comparable<*> && right is Comparable<*>) ... else throw IllegalStateException(...)`.
   Compile-time bounds become runtime crashes — the opposite of a
   faithful translation. The cheat detector flags this style and zeros
   the file's score.

#### The faithful pattern

Translate the trait default to a Kotlin **extension function with a
stricter generic bound on the extension's own type parameter**:

```kotlin
interface RangeBounds<T> {
    fun startBound(): Bound<T>
    fun endBound(): Bound<T>
    // The `is_empty(&self) where T: PartialOrd` default lives outside the
    // interface, on an extension function whose own type parameter
    // carries the bound.
}

fun <T : Comparable<T>> RangeBounds<T>.isEmpty(): Boolean {
    val s = startBound()
    val e = endBound()
    return !when {
        s is Bound.Unbounded || e is Bound.Unbounded -> true
        s is Bound.Included && e is Bound.Included -> s.value <= e.value
        else -> {
            val sv = if (s is Bound.Included) s.value else (s as Bound.Excluded).value
            val ev = if (e is Bound.Included) e.value else (e as Bound.Excluded).value
            sv < ev
        }
    }
}
```

Concrete impls that want to specialise the default supply a **member
function** with the same name. Kotlin resolves `range.isEmpty()` to the
member when the static receiver type is the concrete class, and to the
extension when it is the interface — exactly mirroring Rust's
"default method, per-impl override":

```kotlin
class OpsRange<Idx : Comparable<Idx>>(val start: Idx, val end: Idx) : RangeBounds<Idx> {
    override fun startBound() = Bound.Included(start)
    override fun endBound() = Bound.Excluded(end)

    // Specialised member shadows the extension when the static receiver
    // type is OpsRange<Idx>. No `override` keyword — there is nothing on
    // the interface to override; the member just wins resolution.
    fun isEmpty(): Boolean = !(start.compareTo(end) < 0)
}
```

#### Recipe

1. The interface keeps only the methods the trait declares without
   where-clauses.
2. Each default-method-with-where-clause becomes a Kotlin extension
   function whose own type-parameter bound mirrors the where-clause
   (`<T : Comparable<T>>`, `<Q : Comparable<Q>>` plus
   `where K : Comparable<Q>`, etc.).
3. Concrete subtypes specialise by declaring a same-named member — no
   `override` keyword, since there is nothing on the interface to
   override; the member wins resolution for the concrete static
   receiver type.
4. Callers that hold the unbounded interface type (e.g. `RangeBounds<Q>`
   for opaque `Q`) cannot invoke the comparison-using methods. That is
   correct: Rust would reject the same call without the where-clause's
   bound. Such callers must take a comparator argument or use the
   dual-overload pattern below.

#### Pair with the dual-overload pattern when both paths are needed

When a function has to work in both the comparator-aware and natural-order
paths, expose two overloads — the unbounded one takes the comparator
explicitly, the bounded one is sugar that synthesises the comparator
from the type's `Comparable` impl. The canonical implementation lives
right here in `Search.kt::searchTree`/`searchNode`/`findLowerBoundEdge`/
`findUpperBoundEdge` (lines 106-285) and `Navigate.kt::searchTreeForBifurcation`,
`lowerBound`, `upperBound`:

```kotlin
internal fun <BorrowType, K, V, Q> NodeRef<...>.searchTree(
    key: Q,
    compare: (K, Q) -> Int,
): SearchResult<...> { /* heavy lifting */ }

internal fun <BorrowType, K, V, Q : Comparable<Q>> NodeRef<...>.searchTree(
    key: Q,
): SearchResult<...> where K : Comparable<Q> =
    searchTree(key) { stored, query -> stored.compareTo(query) }
```

The natural-order overload is a one-line delegation; the heavy lifting
lives in the comparator overload.

#### Why this is faithful, not engineering

- The interface mirrors Rust's trait declaration shape exactly — no
  extra constraints introduced.
- The extension function's type-parameter bound mirrors Rust's `where`
  clause exactly.
- Concrete-class members shadow the extension exactly the way Rust
  inherent-impl methods override a trait default.
- The "unbounded callers cannot use these methods" property mirrors
  Rust's compile-time rejection of calling
  `<RangeBounds<Q>>::is_empty()` without `Q: PartialOrd`.
- No runtime casts. No `IllegalStateException` for a missing
  `Comparable`. No `is Comparable<*>` checks. The cheat detector
  stays green.

#### When you cannot apply this

When the bound lives on a *class* type parameter rather than a trait
method (e.g. `impl<K: Ord> BTreeMap<K, V> { fn get<Q>(&self, k: &Q) where K: Borrow<Q>, Q: Ord }`,
where `K: Ord` is on the impl block, not the method), Kotlin has no
method-level analog at all — class type parameters bind for the whole
class. Use the alternative documented in the mapping table above:
`where K: Ord` → `where K : Comparable<K>` *or* `Comparator<in K>` field.
A class-level comparator field plus a `compareKeys(a, b)` dispatch helper
preserves the design. Any natural-order fallback dispatch inside that
helper is part of the documented design contract, not a translation hack.
This is exactly what `BTreeMap`'s constructor does: it accepts a nullable
`Comparator<in K>?`, and `compareKeys` dispatches to the comparator when
present and to a `Comparable<K>`-based fallback otherwise.

### Marker-specific impl overloads use typed routers

Rust often expresses typestate-specific behavior as several `impl`
blocks whose methods have the same Rust name but different marker
parameters, such as one `next_checked` for immutable ranges and
another for value-mutable ranges. Kotlin/JVM erases generic receiver
arguments, so same-name extension overloads that differ only by
`Marker.Immut` versus `Marker.ValMut` are not a portable KMP shape.

Use a router shape instead:

- Keep the generic storage type marker-parameterized (`LeafRange<BorrowType, K, V>`).
- Put marker-specific operations on typed receiver helpers, using
  distinct Kotlin names when erased signatures would collide
  (`nextChecked`, `nextCheckedValMut`, `nextUnchecked`,
  `nextUncheckedValMut`).
- Route shared movement logic through generic private helpers that take
  the marker-specific extraction as a lambda.
- Do not use `@JvmName`, `@Suppress`, JVM imports, fake typealiases, or
  unchecked casts to force Rust's same-name impl layout into Kotlin.
- Do not add source comments explaining the router. Source comments in
  the translated file still come only from upstream Rust comments,
  rewritten for the Kotlin API.

This is a faithful porting pattern. `ast_distance` may report missing
or extra functions because it expects all Rust impl methods with the
same name to collapse to one lowerCamelCase Kotlin name. Treat that as
tooling noise once manual review confirms every upstream behavior is
present and the typed routers compile warning-free across all targets.

### Observable `Drop` / `Clone` side effects use internal hooks

Most Rust `Drop` impls disappear in Kotlin because GC owns memory, but
some upstream tests deliberately make `Drop` or `Clone` observable by
incrementing counters or panicking. Those cases are semantic behavior,
not allocator plumbing.

Use internal opt-in hooks in `commonMain` for the owning collection
paths that need to trigger those effects:

- Define narrow internal interfaces for the side effects, such as
  `BTreeDroppable.dropForBtree()` and `BTreeCloneable.cloneForBtree()`.
- Keep the hooks opt-in. Ordinary user values that do not implement
  the interface are left alone.
- Call clone hooks from translated `clone()` paths before inserting
  cloned keys or values into the new tree.
- Call drop hooks from deterministic owner cleanup paths: `clear()`,
  owning iterators, and panic cleanup around operations like
  `append()` / `merge()`.
- When a drop hook throws, keep dropping the remaining owned elements
  and rethrow the first failure. This mirrors Rust's drop guards around
  unwinding.
- In tests that correspond to Rust `catch_unwind(move || drop(x))`,
  explicitly call the Kotlin owner cleanup method for `x`; do not wait
  for GC and do not lower the assertion to "eventually dropped".
- Do not expose these hooks as public API, do not add JVM-only
  annotations, and do not use them to inflate ast_distance scores.

This pattern is allowed even though `ast_distance` will report the hook
functions as extra Kotlin symbols. The manual check is whether the
upstream Rust behavior is observable and the ported tests prove the
same observable effect.

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
