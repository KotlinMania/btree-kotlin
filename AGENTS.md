# btree-kotlin — Agent Guidelines

Line-by-line Kotlin Multiplatform port of `std::collections::BTreeMap`
and `BTreeSet` from the Rust standard library
(`library/alloc/src/collections/btree/`). The vendored Rust source lives
at `tmp/rust-stdlib-collections-btree/` and is the **only** authority
on what each function should do; never edit the upstream source to make
a port easier.

## General Porting Principles

### 1. Line-by-line transliteration

- Maintain file structure and organization from the Rust source.
- Translate functions in the same order they appear upstream.
- Preserve every comment, inline note, and `# Safety`/`# Panics` block —
  translate the language conventions to KDoc but keep the intent verbatim.
- A missing function is preferable to a stub. If you can't translate
  something, leave the slot empty and flag it in a `PORTING.md` checklist
  entry rather than committing a fake implementation.

### 2. Provenance markers (REQUIRED)

Every ported `.kt` file must start with:

```kotlin
// port-lint: source library/alloc/src/collections/btree/<file>.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree_kotlin
```

The `// port-lint:` line is the contract. Path is relative to the
upstream Rust repo root (so `library/alloc/src/collections/btree/node.rs`,
not `tmp/...`). The second comment line satisfies Apache-2.0 §4(b)
"preserve copyright notices" and MIT's notice requirement.

### 3. Copyright header

Already covered by the two-line preamble above. Don't add a separate
multi-paragraph header — the upstream source doesn't have one and the
NOTICE file at the project root carries the long-form attribution.

### 4. License compatibility

The Kotlin port is dual-licensed Apache-2.0 OR MIT, mirroring upstream.
Don't add code under any other license without surfacing it for review.

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
| `mod foo;` | leave the file in `package io.github.kotlinmania.btree_kotlin`; Kotlin packages mirror Rust modules conceptually but we keep a flat namespace inside `btree_kotlin` |

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

## File-by-file checklist

The phase ordering and which agent owns which file is tracked in
`PORTING.md`. Agents should consult that file before claiming a slot
to avoid two agents racing on the same translation.

## Out of scope

- The `testing/` directory under upstream's btree (a test harness with
  randomised ordering and crash-on-drop instrumentation). LALRPOP
  doesn't need it; we'll port the `tests.rs` files in `commonTest`
  using `kotlin.test` directly.
- `unstable` API features behind feature flags (`#[unstable(...)]`) —
  port only what's reachable through the stable surface.

## TODO policy

No TODO / `unimplemented!()` / stub bodies in committed code. If a
translation is incomplete, don't commit it; record the gap in
`PORTING.md` and either finish it or mark the file as not-started.
