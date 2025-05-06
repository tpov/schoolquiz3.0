plugins {
    id("java-gradle-plugin")
    id("maven-publish")
    kotlin("jvm") version "1.9.22"
}

// These will be overridden by the publish script
group = "com.tpov.logger"
version = "1.0.0"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("gradle-plugin-api"))
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.22")
}

gradlePlugin {
    plugins {
        create("loggerGradlePlugin") {
            id = "com.tpov.logger.gradle-plugin"
            implementationClass = "com.tpov.logger_gradle_plugin.LoggerGradlePlugin"
        }
    }
}

// Apply common publishing configuration
apply(from = "../publish.gradle.kts")