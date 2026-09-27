pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "schoolquiz-lesson-runner-jvm-proof"

// This build intentionally has no includeBuild and no buildSrc. Point the project descriptors
// at the candidate's real source modules while suppressing their AGP-backed build scripts.
val candidateRoot = file("../..")
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

sourceModules.forEach { (path, sourcePath) ->
    include(path)
    project(path).apply {
        projectDir = candidateRoot.resolve(sourcePath)
        buildFileName = "lesson-runner-jvm-harness-disabled.gradle.kts"
    }
}

// Gradle creates parent projects for colon-separated paths; map those descriptors to real
// candidate directories too, while keeping them as empty aggregators in this harness.
mapOf(
    ":shared" to "shared",
    ":shared:core" to "shared/core",
    ":shared:core:catalog" to "shared/core/catalog",
    ":shared:feature" to "shared/feature",
    ":shared:feature:quest" to "shared/feature/quest",
    ":shared:feature:section" to "shared/feature/section",
    ":shared:feature:theme" to "shared/feature/theme",
    ":shared:feature:lesson" to "shared/feature/lesson",
    ":shared:feature:question" to "shared/feature/question",
    ":shared:feature:app-shell" to "shared/feature/app-shell",
    ":shared:feature:lesson-runner" to "shared/feature/lesson-runner",
).forEach { (path, sourcePath) ->
    project(path).apply {
        projectDir = candidateRoot.resolve(sourcePath)
        buildFileName = "lesson-runner-jvm-harness-disabled.gradle.kts"
    }
}
