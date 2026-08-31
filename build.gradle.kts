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

// Instrumented tests need a device to RUN, but they compile like anything else — and when nothing
// compiles them they rot silently. RunFakeComponent fell a whole interface behind before anyone
// noticed, because the gate never looked at androidTest.
tasks.register("compileAndroidTests") {
    description = "Compile instrumented test sources so they cannot rot unnoticed"
    group = "verification"
    dependsOn(activeSubprojectTasks("compileDebugAndroidTestKotlin"))
}

// The Cloud Functions carry the scoring mirror, the economy and the unlock prices, and their tests
// live in a separate toolchain the Gradle gate never reached. A green gate that does not run them
// is a gate that cannot see half the money.
tasks.register<Exec>("functionsTest") {
    description = "Run the Cloud Functions unit tests"
    group = "verification"
    workingDir = file("functions")
    commandLine("npm", "test")
    // Only when the toolchain is actually present; a machine without node still gets a usable gate.
    onlyIf { file("functions/node_modules").isDirectory }
}

tasks.register("ciCheck") {
    description = "Run the canonical local quality gate for active modules"
    group = "verification"
    dependsOn(":apps:android-next:assembleDebug")
    dependsOn(activeSubprojectTasks("test"))
    dependsOn(activeSubprojectTasks("allTests"))
    dependsOn(tasks.named("compileAndroidTests"))
    dependsOn(tasks.named("functionsTest"))
    dependsOn(tasks.named("detekt"))
    dependsOn(tasks.named("ktlintCheck"))
}
