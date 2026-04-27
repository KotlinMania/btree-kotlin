import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    kotlin("multiplatform") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    id("com.android.kotlin.multiplatform.library") version "8.6.0"
    id("com.vanniktech.maven.publish") version "0.30.0"
}

group = "io.github.kotlinmania"
version = "0.1.0"

val androidSdkDir: String? =
    providers.environmentVariable("ANDROID_SDK_ROOT").orNull
        ?: providers.environmentVariable("ANDROID_HOME").orNull

if (androidSdkDir != null && file(androidSdkDir).exists()) {
    val localProperties = rootProject.file("local.properties")
    if (!localProperties.exists()) {
        val sdkDirPropertyValue = file(androidSdkDir).absolutePath.replace("\\", "/")
        localProperties.writeText("sdk.dir=$sdkDirPropertyValue")
    }
}

kotlin {
    applyDefaultHierarchyTemplate()

    sourceSets.all {
        languageSettings.optIn("kotlin.time.ExperimentalTime")
        languageSettings.optIn("kotlin.concurrent.atomics.ExperimentalAtomicApi")
    }

    val xcf = XCFramework("BTreeKotlin")

    macosArm64 {
        binaries.framework {
            baseName = "BTreeKotlin"
            xcf.add(this)
        }
    }
    macosX64 {
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
    iosX64 {
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

    androidLibrary {
        namespace = "io.github.kotlinmania.btree"
        compileSdk = 34
        minSdk = 24
    }

    sourceSets {
        val commonMain by getting

        val commonTest by getting { dependencies { implementation(kotlin("test")) } }
    }
    jvmToolchain(21)
}


mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates(group.toString(), "btree-kotlin", version.toString())

    pom {
        name.set("btree-kotlin")
        description.set(
            "Kotlin Multiplatform port of Rust's std::collections::BTreeMap / BTreeSet, " +
                "translated line-by-line from library/alloc/src/collections/btree/."
        )
        inceptionYear.set("2026")
        url.set("https://github.com/KotlinMania/btree-kotlin")

        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://opensource.org/licenses/Apache-2.0")
                distribution.set("repo")
            }
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
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
