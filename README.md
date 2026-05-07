# BTreeMap and BTreeSet in Kotlin

[![GitHub link](https://img.shields.io/badge/GitHub-KotlinMania%2Fbtree--kotlin-blue.svg)](https://github.com/KotlinMania/btree-kotlin)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kotlinmania/btree-kotlin)](https://central.sonatype.com/artifact/io.github.kotlinmania/btree-kotlin)
[![Build status](https://img.shields.io/github/actions/workflow/status/KotlinMania/btree-kotlin/ci.yml?branch=master)](https://github.com/KotlinMania/btree-kotlin/actions)

This is a Kotlin Multiplatform port of Rust's `std::collections::BTreeMap`
and `BTreeSet`, translated from the Rust standard library implementation in
[`rust-lang/rust`](https://github.com/rust-lang/rust/tree/master/library/alloc/src/collections/btree).

The upstream Rust repository is the main source repository for the Rust
compiler, standard library, and documentation. This package focuses only on the
standard-library B-tree collections and keeps their public behavior, tests, and
documentation as the behavioral oracle for the Kotlin API.

## Original Project

The original implementation belongs to the Rust Project and its contributors:

- upstream repository README: [`rust-lang/rust`](https://github.com/rust-lang/rust/blob/master/README.md)
- upstream source: [`library/alloc/src/collections/btree`](https://github.com/rust-lang/rust/tree/master/library/alloc/src/collections/btree)
- upstream `BTreeMap` docs: [`std::collections::BTreeMap`](https://doc.rust-lang.org/std/collections/struct.BTreeMap.html)
- upstream `BTreeSet` docs: [`std::collections::BTreeSet`](https://doc.rust-lang.org/std/collections/struct.BTreeSet.html)
- contributor credits: [thanks.rust-lang.org](https://thanks.rust-lang.org/)

`btree-kotlin` is downstream translation work. The collection design, edge-case
behavior, examples, and much of the test corpus come from the Rust standard
library. Thank you to the Rust Project contributors and maintainers for the
careful B-tree implementation this port is built from.

## Collection Model

`BTreeMap<K, V>` is an ordered map. Keys are stored by their natural
`Comparable<K>` order, iteration visits entries in ascending key order, and
lookup/update/removal follow the B-tree behavior of the upstream Rust
collection.

`BTreeSet<T>` is an ordered set implemented on top of the same B-tree map
machinery. Its iteration order is ascending item order, and its set operations
track the Rust standard library semantics where those operations have Kotlin
equivalents.

As in Rust, keys or set items should not change their ordering while they are
stored in a collection. Kotlin cannot model Rust's exact ownership and allocator
APIs, so this port keeps the observable collection behavior while adapting the
memory-management pieces to Kotlin Multiplatform.

## Installation

```kotlin
dependencies {
    implementation("io.github.kotlinmania:btree-kotlin:0.2.0")
}
```

## Usage

```kotlin
import io.github.kotlinmania.btree.BTreeMap
import io.github.kotlinmania.btree.BTreeSet

val reviews = BTreeMap<String, String>()
reviews.insert("Office Space", "Deals with real issues in the workplace.")
reviews.insert("Pulp Fiction", "Masterpiece.")

for ((title, review) in reviews) {
    println("$title: $review")
}

val books = BTreeSet<String>()
books.insert("The Odyssey")
books.insert("The Great Gatsby")

println(books.contains("The Odyssey"))
```

## Supported Platforms

- macOS arm64
- Linux x64
- Windows mingw-x64
- iOS arm64 / simulator-arm64 (Swift export + XCFramework)
- JS (browser + Node.js)
- Wasm-JS (browser + Node.js)
- Android (API 24+)

## Porting Status

`btree-kotlin` is a parity-oriented port. The production source tree has direct
provenance markers back to the Rust standard library files via
`// port-lint: source <path>` headers, and `tools/ast_distance/` is used to
measure remaining drift.

Current parity reports show the Rust source files are represented in Kotlin;
the remaining report noise is mostly Rust trait-implementation shape, docs, and
test-helper differences that do not map one-for-one onto Kotlin.

## Building

```bash
./gradlew build
./gradlew test
```

The repo-local `test` task runs the portable gate used for this package:
macOS arm64, JS Node, and WasmJS Node tests.

## Porting Guidelines

See [AGENTS.md](AGENTS.md) and [CLAUDE.md](CLAUDE.md) for the translation
contract: upstream ordering, provenance headers, Kotlin naming conventions, and
the no-stubs policy.

## License

The upstream Rust standard library is dual-licensed under Apache-2.0 or MIT,
at the user's option. See the Rust Project's
[`COPYRIGHT`](https://github.com/rust-lang/rust/blob/master/COPYRIGHT),
[`LICENSE-APACHE`](LICENSE-APACHE), and [`LICENSE-MIT`](LICENSE-MIT) for the
license terms mirrored by this port.

Original work: Copyright (c) The Rust Project Contributors.

Kotlin port: Copyright (c) 2026 Sydney Renee and The Solace Project.

## Acknowledgments

This project exists because the Rust Project made a high-quality, well-tested
B-tree map and set available under permissive open-source licenses. The Kotlin
port aims to preserve that work faithfully for Kotlin Multiplatform users while
making the original authorship visible and easy to follow.
