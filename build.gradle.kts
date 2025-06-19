@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.detekt) apply true // Apply to root project, will configure for subprojects
    alias(libs.plugins.ktlint) apply true // Apply to root project, will configure for subprojects
}

import org.gradle.api.tasks.Delete

// The configureAndroidCommon function and the subprojects block have been removed
// as their logic is now handled by convention plugins in buildSrc.

allprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt") // Apply detekt to all subprojects
    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        source.setFrom(files(project.projectDir))
        config.setFrom(files(rootProject.file("detekt.yml"))) // Assuming you have a detekt.yml in root
        buildUponDefaultConfig = true
    }

    apply(plugin = "org.jlleitschuh.gradle.ktlint") // Apply ktlint to all subprojects
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set(libs.versions.ktlintGradle.get()) // Use version from toml
        // verbose.set(true)
        // android.set(true) // Set if you have specific Android linting needs with Ktlint
        // disabledRules.set(setOf("..."))
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}

// Task для публикации logger-gradle-plugin и сборки основного проекта
// Запуск: ./gradlew publishPluginAndBuild
// Требует includeBuild("logger-gradle-plugin") в settings.gradle.kts

tasks.register("publishPluginAndBuild") {
    group = "automation"
    description = "Публикует logger-gradle-plugin в локальный Maven и собирает основной проект."
    // Убедитесь, что logger-gradle-plugin включен в settings.gradle.kts через includeBuild
    // Пример: includeBuild("logger-gradle-plugin")
    // dependsOn(gradle.includedBuild("logger-gradle-plugin").task(":publishToMavenLocal"))
    // dependsOn(":app:assembleDebug")

    // Временное решение, если includeBuild еще не настроен или вызывает проблемы на этом этапе
    // Можно закомментировать dependsOn, если logger-gradle-plugin еще не готов к сборке/публикации
    // или если вы хотите сначала настроить остальную часть проекта.
    // Для полноценной работы этой задачи, убедитесь, что logger-gradle-plugin настроен для публикации.
    doLast {
        println("Задача publishPluginAndBuild: Убедитесь, что logger-gradle-plugin включен через includeBuild и настроен для публикации.")
        println("В данный момент dependsOn закомментированы для предотвращения ошибок на этапе миграции.")
    }
} 