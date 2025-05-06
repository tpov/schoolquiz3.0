plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("maven-publish")
}

// These will be overridden by the publish script
group = "com.tpov.log_api"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
    }
}

// Apply common publishing configuration
apply(from = "../publish.gradle.kts")

repositories {
    mavenLocal()
    mavenCentral()
}