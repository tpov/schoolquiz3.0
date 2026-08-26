import groovy.json.JsonSlurper

plugins {
    id("schoolquiz.android.compose.library")
}

// ─── NOIR token codegen ─────────────────────────────────────────────────────────────────────────
//
// noir-design-system/tokens/noir.tokens.json is the single source of truth for the palette. The
// generator turns it into Kotlin so the Compose theme cannot drift from the canonical values —
// editing a colour means editing the JSON, and every port picks it up.

val noirTokensJson = rootProject.file("noir-design-system/tokens/noir.tokens.json")
val noirTokensOutputDir = layout.buildDirectory.dir("generated/noirTokens/kotlin")

val generateNoirTokens by tasks.registering {
    inputs.file(noirTokensJson)
    outputs.dir(noirTokensOutputDir)
    doLast {
        val root = JsonSlurper().parse(noirTokensJson) as Map<*, *>
        val sections = listOf("color", "accent", "status")
        val tokens =
            sections.flatMap { section ->
                val group =
                    root[section] as? Map<*, *>
                        ?: error("noir.tokens.json: missing '$section' section")
                group.entries.mapNotNull { entry ->
                    val specMap =
                        entry.value as? Map<*, *>
                            ?: return@mapNotNull null // $comment, $schema and other annotations
                    val name = entry.key.toString()
                    val value = specMap["\$value"] as String
                    if (!value.matches(Regex("#[0-9A-Fa-f]{6}"))) {
                        error("noir.tokens.json: '$section/$name' = $value is not #RRGGBB")
                    }
                    Triple("$section-$name", value, specMap["role"] as? String)
                }
            }
        if (tokens.isEmpty()) error("noir.tokens.json: no color tokens found")

        fun kotlinName(path: String): String =
            path.split("-").mapIndexed { index, part ->
                if (index == 0) part else part.replaceFirstChar { it.uppercase() }
            }.joinToString("")

        val file =
            buildString {
                appendLine("// Generated from noir-design-system/tokens/noir.tokens.json — do not edit by hand.")
                appendLine("@file:Suppress(\"MagicNumber\", \"Filename\")")
                appendLine()
                appendLine("package com.tpov.schoolquiz.android.core.designsystem.noir")
                appendLine()
                appendLine("import androidx.compose.ui.graphics.Color")
                appendLine()
                appendLine("/** Canonical NOIR palette, mirrored 1:1 from the token JSON. */")
                appendLine("object NoirColorTokens {")
                tokens.forEach { (path, hex, role) ->
                    role?.lines()?.take(1)?.joinToString()?.takeIf { it.isNotBlank() }?.let {
                        appendLine("    /** $it */")
                    }
                    appendLine("    val ${kotlinName(path)} = Color(0xFF${hex.removePrefix("#")})")
                    appendLine()
                }
                appendLine("}")
            }

        val dir = noirTokensOutputDir.get().asFile.resolve("com/tpov/schoolquiz/android/core/designsystem/noir")
        dir.mkdirs()
        dir.resolve("NoirColorTokens.kt").writeText(file)
    }
}

android {
    namespace = "com.tpov.schoolquiz.android.core.designsystem"
    sourceSets["main"].java.srcDir(noirTokensOutputDir)
}

tasks.named("preBuild") {
    dependsOn(generateNoirTokens)
}

// Every consumer of the source set — compilers, ktlint, detekt — must see the generated file, and
// Gradle enforces that only when the dependency is declared on each of them.
tasks.configureEach {
    val name = this.name
    val consumesSources =
        name.contains("Kotlin") ||
            name.startsWith("ktlint") ||
            name.startsWith("runKtlint") ||
            name.startsWith("detekt")
    if (consumesSources) {
        dependsOn(generateNoirTokens)
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.bundles.compose.ui)
    api(libs.compose.ui.tooling.preview)
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)
    implementation(project(":shared:core:catalog:domain"))
    implementation(project(":shared:feature:quest:domain"))
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlin.test.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}
