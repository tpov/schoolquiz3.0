import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
}

val candidateRoot = projectDir.resolve("../..").canonicalFile
val sourceModules =
    mapOf(
        ":shared:core:network" to "shared/core/network",
        ":shared:core:catalog:domain" to "shared/core/catalog/domain",
        ":shared:core:leaderboard" to "shared/core/leaderboard",
        ":shared:core:question-schema" to "shared/core/question-schema",
        ":shared:core:scoring" to "shared/core/scoring",
        ":shared:core:outbox" to "shared/core/outbox",
        ":shared:core:sync" to "shared/core/sync",
        ":shared:core:foundation" to "shared/core/foundation",
        ":shared:feature:quest:domain" to "shared/feature/quest/domain",
        ":shared:feature:section:domain" to "shared/feature/section/domain",
        ":shared:feature:theme:domain" to "shared/feature/theme/domain",
        ":shared:feature:lesson:domain" to "shared/feature/lesson/domain",
        ":shared:feature:question:domain" to "shared/feature/question/domain",
        ":shared:feature:app-shell:domain" to "shared/feature/app-shell/domain",
        ":shared:feature:lesson-runner:domain" to "shared/feature/lesson-runner/domain",
    )

val moduleDependencies =
    mapOf(
        ":shared:core:network" to listOf("commonMainImplementation|org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2"),
        ":shared:core:catalog:domain" to listOf(
            "commonMainImplementation|org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2",
            "commonMainImplementation|io.insert-koin:koin-core:3.5.6",
        ),
        ":shared:core:leaderboard" to listOf("commonMainImplementation|org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3"),
        ":shared:core:question-schema" to listOf("commonMainImplementation|org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3"),
        ":shared:core:scoring" to listOf(
            "commonMainImplementation|org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3",
            "commonMainApi|:shared:core:question-schema",
        ),
        ":shared:core:outbox" to listOf(
            "commonMainImplementation|org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2",
            "commonMainApi|:shared:core:network",
        ),
        ":shared:core:sync" to listOf(
            "commonMainImplementation|org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2",
            "commonMainImplementation|org.jetbrains.kotlinx:kotlinx-datetime:0.5.0",
            "commonMainApi|:shared:core:outbox",
            "commonMainImplementation|:shared:core:catalog:domain",
            "commonMainImplementation|:shared:feature:quest:domain",
            "commonMainImplementation|:shared:feature:section:domain",
            "commonMainImplementation|:shared:feature:theme:domain",
            "commonMainImplementation|:shared:feature:lesson:domain",
            "commonMainImplementation|:shared:feature:question:domain",
        ),
        ":shared:feature:quest:domain" to listOf(
            "commonMainImplementation|org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2",
            "commonMainImplementation|io.insert-koin:koin-core:3.5.6",
            "commonMainImplementation|:shared:core:catalog:domain",
        ),
        ":shared:feature:section:domain" to listOf(
            "commonMainImplementation|org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2",
            "commonMainImplementation|io.insert-koin:koin-core:3.5.6",
            "commonMainImplementation|:shared:feature:quest:domain",
        ),
        ":shared:feature:theme:domain" to listOf(
            "commonMainImplementation|org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2",
            "commonMainImplementation|io.insert-koin:koin-core:3.5.6",
            "commonMainImplementation|:shared:feature:section:domain",
        ),
        ":shared:feature:lesson:domain" to listOf(
            "commonMainImplementation|org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2",
            "commonMainImplementation|io.insert-koin:koin-core:3.5.6",
            "commonMainImplementation|:shared:core:leaderboard",
            "commonMainImplementation|:shared:feature:theme:domain",
        ),
        ":shared:feature:question:domain" to listOf(
            "commonMainImplementation|org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2",
            "commonMainImplementation|io.insert-koin:koin-core:3.5.6",
            "commonMainImplementation|:shared:core:question-schema",
            "commonMainImplementation|:shared:core:scoring",
            "commonMainImplementation|:shared:feature:lesson:domain",
        ),
        ":shared:core:foundation" to emptyList(),
        ":shared:feature:app-shell:domain" to listOf(
            "commonMainImplementation|org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2",
            "commonMainImplementation|:shared:core:foundation",
            "commonMainApi|:shared:core:sync",
        ),
        ":shared:feature:lesson-runner:domain" to listOf(
            "commonMainImplementation|org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2",
            "commonMainImplementation|org.jetbrains.kotlinx:kotlinx-datetime:0.5.0",
            "commonMainImplementation|org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3",
            "commonMainImplementation|io.insert-koin:koin-core:3.5.6",
            "commonMainImplementation|:shared:core:leaderboard",
            "commonMainImplementation|:shared:core:question-schema",
            "commonMainApi|:shared:core:scoring",
            "commonMainImplementation|:shared:feature:lesson:domain",
            "commonMainImplementation|:shared:feature:question:domain",
            "commonMainImplementation|:shared:feature:app-shell:domain",
            "commonTestImplementation|org.jetbrains.kotlin:kotlin-test:2.4.20",
            "commonTestImplementation|org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2",
            "commonTestImplementation|:shared:feature:theme:domain",
        ),
    )

subprojects {
    if (path !in sourceModules) return@subprojects

    val sourceRoot = candidateRoot.resolve(sourceModules.getValue(path))
    layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("modules/${path.removePrefix(":").replace(':', '/') }"))

    pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

    extensions.configure<KotlinMultiplatformExtension> {
        jvm()
        jvmToolchain(27)
        sourceSets {
            getByName("commonMain").kotlin.srcDir(sourceRoot.resolve("src/commonMain/kotlin"))
            getByName("jvmMain").kotlin.srcDir(sourceRoot.resolve("src/jvmMain/kotlin"))
            getByName("commonTest").kotlin.srcDir(sourceRoot.resolve("src/commonTest/kotlin"))
            getByName("jvmTest").kotlin.srcDir(sourceRoot.resolve("src/jvmTest/kotlin"))
        }
    }

    moduleDependencies.getValue(path).forEach { entry ->
        val (configuration, notation) = entry.split('|', limit = 2)
        val dependency = if (notation.startsWith(":")) project(notation) else notation
        dependencies.add(configuration, dependency)
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
}
