# Claude Code Project Instructions — btree-kotlin

## Project Overview

This is **btree-kotlin**, a line-by-line port of `std::collections::BTreeMap`
and `BTreeSet` from the Rust standard library to Kotlin Multiplatform.
The vendored Rust sources live in `tmp/rust-stdlib-collections-btree/`
(unchanged copies of upstream `library/alloc/src/collections/btree/`)
and the Kotlin port lives in
`src/commonMain/kotlin/io/github/kotlinmania/btree_kotlin/`.

The port exists because Kotlin's stdlib has no `commonMain` sorted-by-key
map and the kotlinx ecosystem's options force foreign API shapes on
consumers. Consumers of this library — starting with `lalrpop-kotlin` —
need a faithful `BTreeMap<K, V>` that orders by `Comparable<K>` (or a
supplied `Comparator<in K>`) and that behaves observably the same as
Rust's.

## Critical Workflows

### 1. Read AGENTS.md first

Every translation rule, idiom mapping, and naming convention is in
`AGENTS.md`. Don't translate a file without reading it. If you see a
construct AGENTS.md doesn't cover, add a row to the table in your PR
along with the translation you chose.

### 2. Port-Lint headers (REQUIRED)

Every Kotlin file MUST start with:

```kotlin
// port-lint: source library/alloc/src/collections/btree/<file>.rs
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree_kotlin
```

The `port-lint` line points at the upstream Rust path (relative to the
rust-lang/rust repo root), not the vendored copy. The second line
satisfies Apache-2.0 §4(b) and MIT's notice clause. Without these two
lines, `port-lint` analysis can't track provenance.

### 3. Quality verification

After porting a file, verify with:

```bash
./gradlew compileKotlinMacosArm64 --offline
./gradlew macosArm64Test --offline
```

The phase plan in `PORTING.md` says which files are expected to compile
at which phase boundary; don't expect everything to compile until all
phases land.

## Build Commands

```bash
# Full build
./gradlew build

# Run tests
./gradlew macosArm64Test

# Specific platform
./gradlew jvmTest        # via androidLibrary target
./gradlew jsTest
```

## STRICT RULES — Translation, Not Engineering

### This is a translation project.

Every Kotlin file is a line-by-line port of a Rust source file in
`tmp/rust-stdlib-collections-btree/`. The `// port-lint: source` header
at the top tells you which upstream file it came from. Never remove or
change the header.

**When you encounter a compile error, the fix is ALWAYS in the Rust
source.** Don't invent solutions to make the Kotlin compiler happy.
Don't change visibility, drop methods, or restructure the type
hierarchy. Read the corresponding Rust file and translate faithfully.

### No code stubs. Period.

No empty bodies, no `TODO()`, no `error("not implemented")`, no
`unimplemented!()`-equivalent placeholders. If you can't fully translate
a file, don't create it — leave the slot empty and mark it not-started
in `PORTING.md`.

### No third-party dependencies.

`std::collections::BTreeMap` depends only on `core::*` and `alloc::*`,
both of which Kotlin's stdlib subsumes. Don't add a dependency on
kotlinx-collections-immutable, kotlinx-coroutines, or anything else.
The `commonMain` source set has no library dependencies and that's
intentional — this crate is a building block that downstream libraries
depend on, not the other way around.

### No typealias re-export shims.

If Rust has `pub use foo::Bar`, find the callers in the rest of the
file (or in upstream consumers like `BTreeSet`) and point them at the
canonical Kotlin location of `Bar` rather than re-exporting through a
typealias. The exception is when Rust itself uses `pub type` (a real
type alias, not a re-export); in that case translate to `typealias` and
point the comment at the upstream `pub type` line.

### Use ast_distance for verification (BINDING)

The C++ source for `ast_distance` lives in `tools/ast_distance/`. Build
once per clone:

```bash
(cd tools/ast_distance && cmake -B build -S . && cmake --build build -j 8 && cp build/ast_distance .)
```

After every file lands, run:

```bash
./tools/ast_distance/ast_distance --compare-functions \
    tmp/rust-stdlib-collections-btree/<file>.rs rust \
    src/commonMain/kotlin/io/github/kotlinmania/btree_kotlin/<File>.kt kotlin
```

This is **mandatory** for porting discipline — record the AST cosine
score in `PORTING.md` for every file you port. CI also runs a
whole-tree `--deep` scan and fails the build if any port-lint header
is missing.

## Translation Mappings (short reference)

See `AGENTS.md` for the full table. The five biggest gotchas:

1. **`MaybeUninit<T>` arrays** become `arrayOfNulls<T>(n)`. Indexed
   access uses `!!` and the comment line above the access should
   restate the original `// SAFETY:` invariant.
2. **`*mut T` / `NonNull<T>`** become regular Kotlin references. Drop
   the manual lifetime tracking; GC handles it.
3. **`unsafe { ... }`** blocks dissolve. Each one is replaced by a
   `// SAFETY: ...` comment naming the invariant the original Rust
   maintained, then plain Kotlin code that respects the invariant.
4. **Iterators** translate to Kotlin classes implementing
   `Iterator<T>` / `MutableIterator<T>`. State machine fields
   translate verbatim.
5. **`Drop`** impls disappear. GC supersedes.

## When to Ask

Ask the user for clarification if:
- The Rust source uses an unstable/`#[unstable(...)]` API that has no
  stable equivalent in upstream — flag it before committing a guess.
- A function depends on `core::intrinsics::*` — those are compiler
  built-ins and need to be modeled differently.
- A `Drop` impl would have side effects beyond resource cleanup
  (rare in BTreeMap but possible) — those need a Kotlin equivalent
  that survives GC.

## TODO Policy

**DO NOT add TODO comments.** No exceptions in this project. The
upstream Rust source is fully implemented; the Kotlin port should be
too. If you can't translate something, don't commit a stub — leave the
file unwritten and record the gap in `PORTING.md`.

## Commit Messages

Follow Sydney's style:
- No AI branding or attribution
- Clear, descriptive messages about the technical changes
- No emoji unless requested
- No "Co-Authored-By" lines

Example:
```
Port library/alloc/src/collections/btree/search.rs to Search.kt

Translate SearchResult and search_tree_for_bifurcation from
upstream search.rs. Function signatures match line-for-line;
unsafe blocks are dissolved with SAFETY comments. AST cosine
similarity 0.91.
```
