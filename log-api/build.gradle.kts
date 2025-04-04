plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("maven-publish")
}

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

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.tpov.log_api"
            artifactId = "logger"
            version = "1.0.1"
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = uri("${System.getProperty("user.home")}/.m2/repository")
        }
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}