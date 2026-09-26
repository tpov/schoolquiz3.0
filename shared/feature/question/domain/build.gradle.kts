plugins {
    id("schoolquiz.kmp.library.agp9")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(project(":shared:core:question-schema"))
            // Цена открытия урока считается из его же вопросов — см. ObserveLessonUnlockPricesUseCase.
            implementation(project(":shared:core:scoring"))
            implementation(project(":shared:feature:lesson:domain"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
