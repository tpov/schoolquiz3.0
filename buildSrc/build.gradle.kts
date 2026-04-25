plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.compose.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation("org.jlleitschuh.gradle.ktlint:org.jlleitschuh.gradle.ktlint.gradle.plugin:12.1.0")
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "schoolquiz.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "schoolquiz.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("kmpLibrary") {
            id = "schoolquiz.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("jvmLibrary") {
            id = "schoolquiz.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("androidComposeLibrary") {
            id = "schoolquiz.android.compose.library"
            implementationClass = "AndroidComposeLibraryConventionPlugin"
        }
        register("androidComposeApplication") {
            id = "schoolquiz.android.compose.application"
            implementationClass = "AndroidComposeApplicationConventionPlugin"
        }
    }
}
