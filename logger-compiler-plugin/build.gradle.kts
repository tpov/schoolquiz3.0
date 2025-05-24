plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("maven-publish")
}

group = "com.tpov.logger"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.24")
}

// Apply common publishing configuration
apply(from = "../publish.gradle.kts")

repositories {
    mavenLocal()
    mavenCentral()
}

// Добавляем конфигурацию maven-publish
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            groupId = "com.tpov.logger"
            artifactId = "logger-compiler-plugin"
            version = "1.0.0"
        }
    }
    repositories {
        mavenLocal()
    }
}