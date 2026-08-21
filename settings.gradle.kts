pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "schoolquiz"

// layered-scaffold:start
// apps
include(":apps:android-next")

// shared-core
include(":shared:core:catalog:domain")
include(":shared:core:catalog:data")
include(":shared:core:foundation")
include(":shared:core:logger")
include(":shared:core:model")
include(":shared:core:leaderboard")
include(":shared:core:question-schema")
include(":shared:core:persistence")
include(":shared:core:network")
include(":shared:core:preferences")
include(":shared:core:stats")
include(":shared:core:sync")
include(":shared:core:test")

// shared-feature
include(":shared:feature:app-shell:domain")
include(":shared:feature:app-shell:data")
include(":shared:feature:quest:domain")
include(":shared:feature:quest:data")
include(":shared:feature:quest-authoring:domain")
include(":shared:feature:quest-authoring:data")
include(":shared:feature:section:domain")
include(":shared:feature:section:data")
include(":shared:feature:theme:domain")
include(":shared:feature:theme:data")
include(":shared:feature:lesson:domain")
include(":shared:feature:lesson:data")
include(":shared:feature:question:domain")
include(":shared:feature:question:data")
include(":shared:feature:lesson-runner:domain")
include(":shared:feature:lesson-runner:data")
include(":shared:feature:local:settings:domain")
include(":shared:feature:local:settings:data")
include(":shared:feature:internet:auth:domain")
include(":shared:feature:internet:auth:data")
include(":shared:feature:internet:profile:domain")
include(":shared:feature:internet:profile:data")
include(":shared:feature:internet:social:domain")
include(":shared:feature:internet:social:data")
include(":shared:feature:internet:leaderboard:domain")
include(":shared:feature:internet:leaderboard:data")
include(":shared:feature:qualification:domain")
include(":shared:feature:qualification:data")
include(":shared:feature:economy:domain")
include(":shared:feature:economy:data")
include(":shared:feature:minigame:domain")
include(":shared:feature:minigame:data")

// android
include(":android:core:navigation")
include(":android:core:designsystem")
include(":android:core:userguide")
include(":android:feature:app-shell:presentation")
include(":android:feature:quest:presentation")
include(":android:feature:quest-authoring:presentation")
include(":android:feature:quest:test-fixtures")
include(":android:feature:quizzes-screen:presentation")
include(":android:feature:lesson-runner:presentation")
include(":android:feature:local:settings:presentation")
include(":android:feature:internet:auth:presentation")
include(":android:feature:internet:profile:presentation")
include(":android:feature:internet:social:presentation")
include(":android:feature:internet:leaderboard:presentation")
include(":android:feature:qualification:presentation")
include(":android:feature:economy:presentation")
include(":android:feature:minigame:presentation")

// platform
include(":platform:android-services")
include(":platform:firebase")
include(":platform:billing")
include(":platform:crypto")
include(":platform:telegram")

// server
include(":server:workers:sync")
include(":server:workers:leaderboard")
include(":server:workers:rewards")
include(":server:workers:review-collisions")
include(":server:workers:notifications")
include(":server:bot-telegram")
include(":server:ai-gateway")
include(":server:admin-tools")
// layered-scaffold:end
