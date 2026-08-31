plugins {
    id("schoolquiz.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.tpov.schoolquiz.shared.core.scoring"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // UserAnswer is serialized so the chosen answer can be stored with an attempt.
            implementation(libs.kotlinx.serialization.json)
            // QuestionContent and Difficulty appear in this module's public signatures.
            api(project(":shared:core:question-schema"))
        }
    }
}
