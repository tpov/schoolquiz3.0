plugins {
    kotlin("jvm")
    `java-library`
}

group = "com.tpov"
version = "1.0.0"

repositories {
    mavenCentral()
}

    dependencies {
        implementation(kotlin("stdlib"))
        implementation(project(":log-api"))
        implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
        compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.20")
        compileOnly(files("D:/Programming/Android/root/platforms/android-34/android.jar"))

    }


java {
    sourceSets["main"].java.srcDirs("src/main/java")
    toolchain.languageVersion.set(JavaLanguageVersion.of(11))
}

tasks.register<Jar>("buildJar") {
    archiveBaseName.set("logger-processor")
    archiveVersion.set(version.toString())
    from(sourceSets["main"].output)
    destinationDirectory.set(buildDir.resolve("libs"))
}
