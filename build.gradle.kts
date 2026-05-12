import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec

plugins {
    kotlin("multiplatform") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    id("com.android.kotlin.multiplatform.library") version "9.2.1"
    id("com.vanniktech.maven.publish") version "0.36.0"
}

group = "io.github.kotlinmania"
version = "0.2.1"

kotlin {
    applyDefaultHierarchyTemplate()

    sourceSets.all {
        languageSettings.optIn("kotlin.time.ExperimentalTime")
        languageSettings.optIn("kotlin.concurrent.atomics.ExperimentalAtomicApi")
    }

    compilerOptions {
        allWarningsAsErrors.set(true)
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    val xcf = XCFramework("BTreeKotlin")

    macosArm64 {
        binaries.framework {
            baseName = "BTreeKotlin"
            xcf.add(this)
        }
    }
    linuxX64()
    mingwX64()
    iosArm64 {
        binaries.framework {
            baseName = "BTreeKotlin"
            xcf.add(this)
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "BTreeKotlin"
            xcf.add(this)
        }
    }
    js {
        browser()
        nodejs()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }

    swiftExport {
        moduleName = "BTreeKotlin"
        flattenPackage = "io.github.kotlinmania.btree"
    }

    android {
        namespace = "io.github.kotlinmania.btree"
        compileSdk = 34
        minSdk = 24
        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }
    }

    sourceSets {
        val commonMain by getting

        val commonTest by getting { dependencies { implementation(kotlin("test")) } }
    }
    jvmToolchain(21)
}

rootProject.extensions.configure<NodeJsEnvSpec>("kotlinNodeJsSpec") {
    version.set("22.22.2")
}

rootProject.extensions.configure<WasmNodeJsEnvSpec>("kotlinWasmNodeJsSpec") {
    version.set("22.22.2")
}

rootProject.extensions.configure<YarnRootEnvSpec>("kotlinYarnSpec") {
    version.set("1.22.22")
}

rootProject.extensions.configure<WasmYarnRootEnvSpec>("kotlinWasmYarnSpec") {
    version.set("1.22.22")
}

rootProject.extensions.configure<YarnRootExtension>("kotlinYarn") {
    resolution("diff", "8.0.3")
    resolution("**/diff", "8.0.3")
    resolution("serialize-javascript", "7.0.5")
    resolution("**/serialize-javascript", "7.0.5")
    resolution("webpack", "5.106.2")
    resolution("**/webpack", "5.106.2")
    resolution("follow-redirects", "1.16.0")
    resolution("**/follow-redirects", "1.16.0")
    resolution("lodash", "4.18.1")
    resolution("**/lodash", "4.18.1")
    resolution("ajv", "8.20.0")
    resolution("**/ajv", "8.20.0")
    resolution("brace-expansion", "5.0.5")
    resolution("**/brace-expansion", "5.0.5")
    resolution("flatted", "3.4.2")
    resolution("**/flatted", "3.4.2")
    resolution("minimatch", "10.2.5")
    resolution("**/minimatch", "10.2.5")
    resolution("picomatch", "4.0.4")
    resolution("**/picomatch", "4.0.4")
    resolution("qs", "6.15.1")
    resolution("**/qs", "6.15.1")
    resolution("socket.io-parser", "4.2.6")
    resolution("**/socket.io-parser", "4.2.6")
}


val patchedKarmaWebpackPackage = rootProject.layout.projectDirectory.dir("gradle/npm/karma-webpack").asFile.absolutePath.replace("\\", "/")

rootProject.extensions.configure<NodeJsRootExtension>("kotlinNodeJs") {
    versions.webpack.version = "5.106.2"
    versions.webpackCli.version = "7.0.2"
    versions.karma.version = "npm:karma-maintained@6.4.7"
    versions.karmaWebpack.version = "file:$patchedKarmaWebpackPackage"
    versions.mocha.version = "12.0.0-beta-10"
    versions.kotlinWebHelpers.version = "3.1.0"
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(group.toString(), "btree-kotlin", version.toString())

    pom {
        name.set("btree-kotlin")
        description.set("Kotlin Multiplatform port of Rust stdlib BTreeMap/BTreeSet from rust-lang/rust")
        inceptionYear.set("2026")
        url.set("https://github.com/KotlinMania/btree-kotlin")

        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("sydneyrenee")
                name.set("Sydney Renee")
                email.set("sydney@solace.ofharmony.ai")
                url.set("https://github.com/sydneyrenee")
            }
        }

        scm {
            url.set("https://github.com/KotlinMania/btree-kotlin")
            connection.set("scm:git:git://github.com/KotlinMania/btree-kotlin.git")
            developerConnection.set("scm:git:ssh://github.com/KotlinMania/btree-kotlin.git")
        }
    }
}

// ---------------------------------------------------------------------------
// CodeQL Java/Kotlin extraction task
//
// `.github/workflows/codeql.yml` invokes `./gradlew codeqlCompileJvm` to feed
// kotlinc-compiled commonMain through the CodeQL Java agent. The KMP build
// engages the K2 phased pipeline that bypasses the agent's
// `K2JVMCompiler.doExecute` hook, so this task runs a separate single-target
// JVM compile of commonMain sources via JavaExec with NO multiplatform flags,
// causing kotlinc to dispatch through the legacy path the agent hooks.
//
// When commonMain has no .kt files (pre-port repo with only .gitkeep), the
// task writes a tiny placeholder under `build/generated/codeql-empty-source/`
// so kotlinc always has at least one source. Skipping the task with onlyIf
// is intentionally avoided: a silent skip is indistinguishable in CI from a
// real run, which is abusable. The sentinel approach forces the compile to
// execute every time so any future drift is caught.

val codeqlKotlinc: Configuration by configurations.creating {
    description = "Kotlin compiler (CodeQL extraction target only — not published)"
    isCanBeResolved = true
    isCanBeConsumed = false
}

val codeqlSourceClasspath: Configuration by configurations.creating {
    description = "Runtime classpath for CodeQL extraction of commonMain sources"
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    codeqlKotlinc("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.3.21")
    codeqlSourceClasspath("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
    codeqlSourceClasspath("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")
    codeqlSourceClasspath("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.11.0")
    codeqlSourceClasspath("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.11.0")
    codeqlSourceClasspath("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.6.1")
    codeqlSourceClasspath("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm:0.4.0")
}

val codeqlCompileJvm = tasks.register<JavaExec>("codeqlCompileJvm") {
    description =
        "Compile commonMain Kotlin sources with kotlinc 2.3.21 for CodeQL Java/Kotlin extraction. " +
        "Not part of any published artifact; intended to be wrapped by `codeql database create` " +
        "or `github/codeql-action/init` so the LD_PRELOAD tracer can attach the extractor agent " +
        "to the in-process kotlinc."
    group = "verification"

    classpath(codeqlKotlinc)
    mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")

    val outDir = layout.buildDirectory.dir("classes/kotlin/codeql-jvm")
    val sources = fileTree("src/commonMain/kotlin") { include("**/*.kt") }
    val sentinelDir = layout.buildDirectory.dir("generated/codeql-empty-source")
    inputs.files(sources).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(codeqlSourceClasspath).withNormalizer(ClasspathNormalizer::class.java)
    outputs.dir(outDir)
    outputs.dir(sentinelDir)

    doFirst {
        outDir.get().asFile.mkdirs()
        val sourceFiles = sources.files.toMutableList()
        if (sourceFiles.isEmpty()) {
            val sentinelFile = sentinelDir.get().asFile.resolve(
                "io/github/kotlinmania/codeql/_CodeqlEmptySource.kt",
            )
            sentinelFile.parentFile.mkdirs()
            sentinelFile.writeText(
                """
                // Auto-generated. Present so codeqlCompileJvm has at least
                // one Kotlin source to feed kotlinc; replaced by real
                // commonMain content once porting begins.
                package io.github.kotlinmania.codeql

                private object _CodeqlEmptySource

                """.trimIndent(),
            )
            sourceFiles += sentinelFile
        }
        args = listOf(
            "-d", outDir.get().asFile.absolutePath,
            "-classpath", codeqlSourceClasspath.asPath,
            "-jvm-target", "21",
            "-no-stdlib",
            "-no-reflect",
            "-language-version", "2.3",
            "-api-version", "2.3",
            "-opt-in", "kotlin.time.ExperimentalTime",
            "-opt-in", "kotlin.concurrent.atomics.ExperimentalAtomicApi",
            "-Xexpect-actual-classes",
        ) + sourceFiles.map { it.absolutePath }
    }
}

tasks.register("test") {
    group = "verification"
    description =
        "Runs a portable test suite (macOS + JS + WasmJS). Android and non-host native targets are intentionally excluded."

    val defaultTestTasks = listOf(
        "macosArm64Test",
        "jsNodeTest",
        "wasmJsNodeTest",
    )

    dependsOn(defaultTestTasks.mapNotNull { taskName -> tasks.findByName(taskName) })
}
