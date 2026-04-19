// Root build file.
// Плагины для подпроектов регистрируются через convention plugins в `buildSrc/`
// и резолвятся из their own classpath. Здесь не должно быть `plugins { alias(...) apply false }`.

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

// Aggregate tasks — allow `./gradlew detekt` / `./gradlew ktlintCheck` от root
tasks.register("detekt") {
    description = "Run detekt across all active (non-legacy) modules"
    group = "verification"
    dependsOn(subprojects
        .filterNot { it.path.startsWith(":legacy") }
        .mapNotNull { it.tasks.findByName("detekt") })
}

tasks.register("ktlintCheck") {
    description = "Run ktlint check across all active (non-legacy) modules"
    group = "verification"
    dependsOn(subprojects
        .filterNot { it.path.startsWith(":legacy") }
        .mapNotNull { it.tasks.findByName("ktlintCheck") })
}
