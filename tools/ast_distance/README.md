# ast_distance

Cross-language AST similarity tool used to verify the Kotlin port stays
faithful to the upstream Rust source. Every Kotlin file that lands in
`src/commonMain/kotlin/io/github/kotlinmania/btree/` should be
compared against its `library/alloc/src/collections/btree/` counterpart
under `tmp/rust-stdlib-collections-btree/`, and the resulting AST
cosine score recorded in `PORTING.md`.

## Build

The binary is built from the C++ source in this directory. Tree-sitter
grammars are fetched at configure time via CMake's `FetchContent`.

```bash
cd tools/ast_distance
cmake -B build -S .
cmake --build build -j 8
cp build/ast_distance .
```

The compiled binary lands at `tools/ast_distance/ast_distance`. Both
`build/` and the copied binary are gitignored — rebuild from source on
each clone.

## Usage

### Compare a single file pair (most common)

```bash
./tools/ast_distance/ast_distance --compare-functions \
    tmp/rust-stdlib-collections-btree/<file>.rs rust \
    src/commonMain/kotlin/io/github/kotlinmania/btree/<File>.kt kotlin
```

Reports per-function name parity, AST cosine, and identifier-cosine
between the upstream Rust and the Kotlin port. Each agent porting a
file MUST run this after their port lands and record the cosine in
`PORTING.md`.

### Whole-directory deep scan

```bash
./tools/ast_distance/ast_distance --deep \
    tmp/rust-stdlib-collections-btree rust \
    src/commonMain/kotlin/io/github/kotlinmania/btree kotlin
```

Walks both trees, pairs files by the `// port-lint:` header, and
emits an aggregate report. CI runs this at the project level.

## Other modes

```bash
./tools/ast_distance/ast_distance --help
```

For the full list — duplicate-symbol detection, missing-file ranking,
documentation-coverage comparison, etc.

## Don't pipe / redirect output

The tool detects piping, redirection, and `script -q` wrapping. Run
it directly in the terminal and read the output from the tool's
result.
