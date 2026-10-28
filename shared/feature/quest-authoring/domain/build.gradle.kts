plugins {
    id("schoolquiz.kmp.library")
}

android {
    namespace = "com.tpov.schoolquiz.shared.feature.quest_authoring.domain"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            // Отметка правки (4.23) сериализует авторскую форму вопроса тем же движком, каким
            // публикация пишет документы, — нужен JSON-билдер, а не только аннотации схемы.
            implementation(libs.kotlinx.serialization.json)
            implementation(project(":shared:core:catalog:domain"))
            implementation(project(":shared:core:question-schema"))
            implementation(project(":shared:feature:quest:domain"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
