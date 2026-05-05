plugins {
    id("schoolquiz.jvm.library")
}

dependencies {
    implementation(project(":shared:feature:quest-authoring:data"))
    implementation(libs.firebase.admin)

    testImplementation(project(":shared:core:persistence"))
    testImplementation(project(":shared:core:catalog:domain"))
    testImplementation(project(":shared:core:question-schema"))
    testImplementation(project(":shared:core:sync"))
    testImplementation(project(":shared:feature:quest:domain"))
    testImplementation(project(":shared:feature:quest-authoring:domain"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}
