plugins {
    id("schoolquiz.jvm.library")
}

dependencies {
    implementation(project(":shared:core:catalog:domain"))
    implementation(project(":shared:feature:quest:domain"))
    implementation(project(":shared:feature:app-shell:domain"))
    implementation(libs.kotlinx.coroutines.core)
}
