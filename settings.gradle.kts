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
include(":shared:core:foundation")
include(":shared:core:logger")
include(":shared:core:model")
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
include(":shared:feature:quiz:domain")
include(":shared:feature:quiz:data")
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
include(":android:feature:quiz:presentation")
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
include(":server:functions")
include(":server:workers:sync")
include(":server:workers:leaderboard")
include(":server:workers:rewards")
include(":server:workers:review-collisions")
include(":server:workers:notifications")
include(":server:bot-telegram")
include(":server:ai-gateway")
include(":server:admin-tools")
// layered-scaffold:end
