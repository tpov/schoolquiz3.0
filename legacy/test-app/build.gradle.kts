plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("com.tpov.logger.gradle-plugin")
    id("application")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

application {
    mainClass.set("com.tpov.testapp.MainKt")
}

kotlin {
    jvmToolchain(11)
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(project(":log-api"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.0")
} 