plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.6" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0" apply false
}

import org.gradle.api.tasks.Delete

buildscript {
    repositories {
        gradlePluginPortal()
        google()
        mavenLocal()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://plugins.gradle.org/m2/")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.4.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.23")
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:1.9.22-1.0.16")
        classpath("com.google.gms:google-services:4.4.2")
        classpath("com.tpov.logger:logger-gradle-plugin:1.0.0")
    }
}

allprojects {
    repositories {
        gradlePluginPortal()
        google()
        mavenLocal()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://plugins.gradle.org/m2/")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}

// Task для публикации logger-gradle-plugin и сборки основного проекта
// Запуск: ./gradlew publishPluginAndBuild
// Требует includeBuild("logger-gradle-plugin") в settings.gradle

tasks.register("publishPluginAndBuild") {
    group = "automation"
    description = "Публикует logger-gradle-plugin в локальный Maven и собирает основной проект"
    dependsOn(gradle.includedBuild("logger-gradle-plugin").task(":publishToMavenLocal"))
    dependsOn(":app:assembleDebug")
} 