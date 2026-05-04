# Claude Code Project Instructions — btree-kotlin

## Project Overview

This is **btree-kotlin**, a line-by-line port of `std::collections::BTreeMap`
and `BTreeSet` from the Rust standard library to Kotlin Multiplatform.

The Kotlin port lives in
`src/commonMain/kotlin/io/github/kotlinmania/btree/`. The
upstream Rust source it was translated from is **not tracked** in this
repo — fetch it into `tmp/rust-stdlib-collections-btree/` by running
`./tools/fetch-rust-source.sh` after cloning. CI does this
automatically as a setup step before running ast_distance scans.

The port exists because Kotlin's stdlib has no `commonMain` sorted-by-key
map and the kotlinx ecosystem's options force foreign API shapes on
consumers. Consumers of this library — starting with `lalrpop-kotlin` —
need a faithful `BTreeMap<K, V>` that orders by `Comparable<K>` (or a
supplied `Comparator<in K>`) and that behaves observably the same as
Rust's.

## Translator's mindset

This is a translation project, not a software-engineering project. While porting a file, you are
the Kotlin author of the same document a Rust author wrote. Architecture, optimization, design
critique, drift measurement — all later. While translating, the only job is the translation.

The discipline:

1. **Read the whole upstream file before you type.** A line-by-line port composes only when you
   know how the file ends. If the file is too long to read in one sitting, split your turn into
   "read the file" and "write the file" — never start typing on a file you've only half-read.

2. **One Rust file → one Kotlin file. Always.** No splitting one `.rs` across several `.kt`. No
   merging several `.rs` into one `.kt`. The 1:1 mapping is the contract; everything downstream
   (ast_distance, port-lint headers, code review) assumes it. If a `.rs` is genuinely too big for
   one Kotlin file, that's a sign you're in `mod.rs`-equivalent territory and the upstream itself
   is a re-export — verify, don't split.

3. **Translate top to bottom in upstream order.** Preserve the declaration order. Don't reorder
   for "logical flow" — the upstream's order *is* the logical flow. The reader who already knows
   the Rust file should be able to scroll the Kotlin file and find every item in the same place.

4. **Comments are content.** License header, module-level doc, every `///` block, every inline
   `//` note, every upstream `// TODO`/`// FIXME` — all translate. Rust syntax inside doc comments
   gets rewritten to Kotlin equivalents (`Vec<T>` → `List<T>`, `Self::foo()` → `foo()`, lifetimes
   dropped, `cfg(test)` and `#[derive(...)]` lifted into prose). You are translating a *document*,
   not just the code.

5. **When a Rust idiom has no Kotlin analog, apply the mapping rule and move on.** `Box<T>`,
   `Arc<T>`, `Cell<T>`, `RefCell<T>`, `Rc<T>`, lifetimes, `PhantomData`, `mem::forget`,
   `drop_in_place`, `Pin`, `MaybeUninit`, `dyn Trait` — all collapse per the mapping table.
   Don't relitigate. A proc-macro becomes a builder/runtime API, not nothing. An upstream Rust
   crate with no KMP equivalent becomes a *separate Kotlin port*, not a `// TODO` placeholder.
   Pay the snowball cost upfront — the next consumer will thank you.

6. **Don't measure mid-port.** ast_distance, FnSim, similarity reports — useful *after* a file is
   done, useless *during*. Mid-translation measurement is procrastination dressed as rigor. Run
   the tools when a file lands or when a port phase wraps, not while you're choosing between
   `Result<T>` and `T?`.

7. **Don't optimize the translation.** "This Kotlin shape would be simpler" is the wrong
   thought. The upstream shape is the spec. If a faithful translation produces a function that
   takes a parameter you'd never write in Kotlin from scratch, take it. Optimization is a
   separate, named pass after parity is reached — never blended into the translation.

8. **Don't re-architect mid-port.** "This whole module would be cleaner if..." — write the
   thought on a sticky note, throw the sticky note away, finish the file. The current architecture
   is the upstream's architecture. Earn the right to redesign by first reaching parity.

9. **Compile errors during translation are normal and expected.** A bottom-of-tree file compiles
   when its deps are ported, not before. Don't pause to "make it compile" mid-port — that pulls
   you into stub-shaped fixes that you'll have to undo. Climb the dep tree bottom-up; the leaves
   compile first, then their parents, then everything compiles together at the end.

10. **Bottom-up always.** Port dependencies before consumers. If `state.rs` uses `EvalException`,
    port `eval_exception.rs` first. If `eval_exception.rs` uses `Error`/`WithDiagnostic`/`CallStack`,
    port those first. The order isn't optional; trying to port top-down produces a tree of stubs
    that all need replacing.

11. **Hard files are not skippable.** logos-codegen, lalrpop's table generator, an annotate-snippets
    equivalent — when you hit one, port it. Skipping leaves a `// TODO`-shaped hole that grows
    every time another consumer needs it. The snowball is the whole point: each hard port done
    makes the next port easier, because the dep is now in Kotlin.

12. **Warnings are real, but `@Suppress` is never the answer.** `UNUSED_PARAMETER` on a callback
    helper means the function shape doesn't fit Kotlin — restructure the signature, don't suppress.
    `UNCHECKED_CAST` means the type system is missing an invariant — encode it. Every warning is
    either a real bug or a translation choice that needs revisiting; treat them as compile errors.

13. **Stop at file boundaries, not function boundaries.** After every completed file, exhale,
    commit, move on. Don't pause mid-function to second-guess a choice. The whole-file context
    is what makes individual choices coherent.

14. **Doc-port discipline applies even when the upstream doc is awkward.** If the upstream
    author wrote a tortured English sentence in a doc comment, translate the tortured sentence.
    Don't smooth it. Don't paraphrase. Their doc is the contract for the Kotlin doc.

15. **The cheat detector is your friend.** If `ast_distance` forces your file's score to 0
    because you left snake_case identifiers or `pub` keywords in Kotlin comments, take it as a
    literal instruction: rewrite those comments to be Kotlin-native. Rust syntax in Kotlin source
    — code or comments — is the cheat we're catching.

The sticky-note version: **"Read the file. Translate it. Don't think about anything else."**

## Critical Workflows

### 1. Read AGENTS.md first

Every translation rule, idiom mapping, and naming convention is in
`AGENTS.md`. Don't translate a file without reading it. If you see a
construct AGENTS.md doesn't cover, add a row to the table in your PR
along with the translation you chose.

### 2. Port-Lint headers (REQUIRED)

Every Kotlin file MUST start with:

```kotlin
// port-lint: source <path-relative-to-tmp/rust-stdlib-collections-btree/>
// Derived from the Rust standard library (rust-lang/rust),
// copyright The Rust Project Developers, dual-licensed Apache-2.0 / MIT.
package io.github.kotlinmania.btree
```

The `port-lint` line is **relative to the fetched upstream tree under**
`tmp/rust-stdlib-collections-btree/` (e.g. `node.rs`, `map/tests.rs`).
The second line satisfies Apache-2.0 §4(b) and MIT's notice clause.
Without these two lines, `port-lint` analysis can't track provenance.

### 3. Quality verification

After porting a file, verify with:

```bash
./gradlew compileKotlinMacosArm64 --offline
./gradlew macosArm64Test --offline
```

Compilation is a **precondition**, not a correctness gate. The gate is
**behavioral parity**, proven by the ported tests passing against the
same fixtures as upstream.

Run `./tools/ast_distance/ast_distance --deep tmp/rust-stdlib-collections-btree rust src/commonMain/kotlin/io/github/kotlinmania/btree kotlin`
to check coverage accounting, provenance/header drift, and cheat detection.
Do not treat similarity scores as a verdict of correctness.

## Naming conventions (do not Rustify Kotlin)

- **Files / types:** `PascalCase` (do not rename Kotlin files to `snake_case`)
- **Functions / locals:** `camelCase`
- **Packages:** all lowercase (no camelCase)

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
a file, don't create it — leave the slot empty. ast_distance's
`--missing` report is how the gap is tracked.

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
    src/commonMain/kotlin/io/github/kotlinmania/btree/<File>.kt kotlin
```

This is **mandatory** for porting discipline. CI also runs a
whole-tree `--deep` scan and fails the build if any port-lint header
is missing. `ast_distance` is the accounting/cheat-detection tool for
port progress; it is not the behavioral gate. Do not maintain a
parallel status file.

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

## Trait default methods with `where` clauses

Rust trait default methods gated by `where T: SomeBound` translate to
Kotlin **extension functions whose own generic type parameter carries
the bound** — never tighten the interface, never add a runtime
`is Comparable<*>` cast, never make the method abstract just to dodge
the issue. Concrete subtypes specialise by declaring a same-named
member (no `override` keyword); Kotlin resolves to the member for the
concrete static receiver type and to the extension for the interface
type, exactly mirroring Rust's per-impl override of a trait default.
When the bound lives on a *class* parameter rather than a trait method,
fall back to the `Comparator<in K>` field pattern with a
comparator-or-natural dispatch helper.

`Range.kt`'s `RangeBounds<T>` interface plus the
`fun <T : Comparable<T>> RangeBounds<T>.isEmpty()` and
`.contains(item)` extensions are the canonical example in this repo;
`Search.kt::searchTree` and friends show the dual-overload pattern for
functions that need both a comparator-aware path and a natural-order
path. See [AGENTS.md](./AGENTS.md) §"Trait default methods with `where`
clauses" for the worked recipe and rationale.

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
file unwritten and let ast_distance's `--missing` report surface the gap.

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
