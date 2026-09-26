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

rootProject.name = "schoolquiz-room-kmp-spike"

val candidateRoot = file("..")
listOf(
    ":shared:core:network",
    ":shared:core:catalog:domain",
    ":shared:core:leaderboard",
    ":shared:core:question-schema",
    ":shared:core:scoring",
    ":shared:core:outbox",
    ":shared:core:persistence",
    ":shared:feature:quest:domain",
    ":shared:feature:section:domain",
    ":shared:feature:theme:domain",
    ":shared:feature:lesson:domain",
    ":shared:feature:question:domain",
    ":shared:core:sync",
).forEach { path ->
    include(path)
    project(path).projectDir = candidateRoot.resolve(path.removePrefix(":").replace(':', '/'))
}

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
).forEach { (path, relativeDirectory) ->
    project(path).projectDir = candidateRoot.resolve(relativeDirectory)
}
