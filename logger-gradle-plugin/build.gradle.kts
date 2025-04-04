plugins {
    id("java-gradle-plugin")
    id("maven-publish")
    kotlin("jvm")
}

group = "com.tpov.logger"
version = "1.0.0"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("gradle-plugin-api"))
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.20")
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.0")
}

gradlePlugin {
    plugins {
        create("loggerGradlePlugin") {
            id = "com.tpov.logger.gradle-plugin"
            implementationClass = "com.tpov.logger_gradle_plugin.LoggerGradlePlugin"
        }
    }
}

publishing {
    repositories {
        maven {
            url = uri("${System.getProperty("user.home")}/.m2/repository")
        }
    }
}