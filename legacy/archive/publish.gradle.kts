plugins.withId("maven-publish") {
    extensions.configure<org.gradle.api.publish.PublishingExtension>("publishing") {
        repositories {
            mavenLocal()
            // Для публикации во внешний репозиторий раскомментируйте и настройте:
            // maven {
            //     name = "MyRepo"
            //     url = uri("https://your.repo.url")
            //     credentials {
            //         username = findProperty("repoUser") as String? ?: System.getenv("REPO_USER")
            //         password = findProperty("repoPassword") as String? ?: System.getenv("REPO_PASSWORD")
            //     }
            // }
        }
    }
} 