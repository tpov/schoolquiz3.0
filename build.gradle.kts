// Root build file.
// Плагины для подпроектов регистрируются через convention plugins в `buildSrc/`
// и резолвятся из their own classpath. Здесь не должно быть `plugins { alias(...) apply false }`.

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

fun activeSubprojectTasks(taskName: String) =
    subprojects
        .filterNot { it.path.startsWith(":legacy") }
        .mapNotNull { it.tasks.findByName(taskName) }

// Aggregate tasks — allow `./gradlew detekt` / `./gradlew ktlintCheck` от root
tasks.register("detekt") {
    description = "Run detekt across all active (non-legacy) modules"
    group = "verification"
    dependsOn(activeSubprojectTasks("detekt"))
}

tasks.register("ktlintCheck") {
    description = "Run ktlint check across all active (non-legacy) modules"
    group = "verification"
    dependsOn(activeSubprojectTasks("ktlintCheck"))
}

tasks.register("ciCheck") {
    description = "Run the canonical local quality gate for active modules"
    group = "verification"
    dependsOn(":apps:android-next:assembleDebug")
    dependsOn(activeSubprojectTasks("test"))
    dependsOn(activeSubprojectTasks("allTests"))
    dependsOn(tasks.named("detekt"))
    dependsOn(tasks.named("ktlintCheck"))
}
