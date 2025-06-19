pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "schoolquiz"
include(":app")
include(":settings")
include(":common")
include(":shop")
include(":userguide")
include(":network")
include(":core")
include(":log-api")
include(":logger-compiler-plugin")
include(":logger-processor")
include(":test-app")
include(":logger-gradle-plugin")
