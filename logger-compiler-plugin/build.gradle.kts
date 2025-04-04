plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("maven-publish")
}

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
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.0")
}

publishing {
    publications {
        create("maven", MavenPublication::class.java) {
            groupId = "com.tpov.logger"
            artifactId = "logger-compiler-plugin"
            version = "1.0.0"
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