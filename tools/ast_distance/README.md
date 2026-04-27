# ast_distance

This project borrows the `ast_distance` binary built in the sibling
`lalrpop-kotlin` project rather than rebuilding it here. To run
function-level parity comparisons against the vendored Rust source:

```bash
../lalrpop-kotlin/tools/ast_distance/ast_distance \
    --compare-functions \
    tmp/rust-stdlib-collections-btree/<file>.rs rust \
    src/commonMain/kotlin/io/github/kotlinmania/btree_kotlin/<File>.kt kotlin
```

If lalrpop-kotlin isn't on disk, clone it as a sibling directory first:

```bash
cd ..
git clone https://github.com/KotlinMania/lalrpop-kotlin.git
(cd lalrpop-kotlin/tools/ast_distance && cmake -B build && cmake --build build)
```

The C++ source for `ast_distance` lives in the lalrpop-kotlin repo so
both projects stay on the same version.
