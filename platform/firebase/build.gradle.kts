plugins {
    id("schoolquiz.android.library")
}

android {
    namespace = "com.tpov.schoolquiz.platform.firebase"
    testOptions {
        // Коды ошибок Firestore — enum, чей статический инициализатор ходит в android.util.SparseArray.
        // Без заглушек он падает на загрузке класса, и проверить разбор настоящего исключения SDK
        // нечем: тест был бы вынужден подсовывать уже готовую ветвь — ровно то, чего он должен не
        // допускать.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(project(":shared:core:stats"))
    implementation(project(":shared:core:sync"))
    implementation(project(":shared:core:network"))
    implementation(project(":shared:core:outbox"))
    implementation(project(":shared:core:question-schema"))
    implementation(project(":shared:core:catalog:domain"))
    implementation(project(":shared:core:catalog:data"))
    implementation(project(":shared:feature:quest:data"))
    implementation(project(":shared:feature:quest-authoring:data"))
    implementation(project(":shared:feature:internet:profile:data"))
    implementation(project(":shared:feature:internet:profile:domain"))
    implementation(project(":shared:feature:internet:leaderboard:data"))
    implementation(project(":shared:feature:internet:leaderboard:domain"))
    implementation(project(":shared:feature:economy:data"))
    implementation(project(":shared:feature:economy:domain"))
    implementation(project(":shared:feature:section:data"))
    implementation(project(":shared:feature:theme:data"))
    implementation(project(":shared:feature:lesson:data"))
    implementation(project(":shared:feature:lesson:domain"))
    implementation(project(":shared:feature:lesson-runner:data"))
    implementation(project(":shared:feature:lesson-runner:domain"))
    implementation(project(":shared:core:leaderboard"))
    implementation(project(":shared:feature:question:data"))
    implementation(project(":shared:core:analytics"))
    // Регистрация токена строится на observeUid() (Story 10.2, инвариант 8).
    implementation(project(":shared:feature:app-shell:domain"))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.database.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.functions)
    // Push (Story 10.2): version rides the BOM above, none declared in the catalog.
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.appcheck.play.integrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.play.services.base)
    implementation(libs.play.services.basement)
    testImplementation(libs.junit4)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.datetime)
    // Сквозные проверки идут от брошенного исключения SDK до снимка состояния синхронизации, то
    // есть через настоящие шаги прогона. Шагам нужны типы, которых у самого модуля Firebase на
    // компиляции нет: доменные порты содержимого (их держит оркестратор каталога), строки базы
    // (их называют локальные источники) и шаг собственных чисел аккаунта.
    testImplementation(project(":shared:core:persistence"))
    testImplementation(project(":shared:feature:quest:domain"))
    testImplementation(project(":shared:feature:section:domain"))
    testImplementation(project(":shared:feature:theme:domain"))
    testImplementation(project(":shared:feature:question:domain"))
    testImplementation(project(":shared:feature:app-shell:data"))
}
