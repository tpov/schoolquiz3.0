plugins {
    id("schoolquiz.jvm.library")
}

dependencies {
    implementation(project(":shared:core:catalog:domain"))
    implementation(project(":shared:feature:quest:domain"))
    implementation(project(":shared:feature:section:domain"))
    implementation(project(":shared:feature:theme:domain"))
    implementation(project(":shared:feature:lesson-runner:domain"))
    implementation(project(":shared:feature:lesson:domain"))
    implementation(project(":shared:feature:quest-authoring:domain"))
    implementation(project(":shared:feature:app-shell:domain"))
    implementation(libs.kotlinx.coroutines.core)
}
