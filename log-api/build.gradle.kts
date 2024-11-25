plugins {
    kotlin("jvm")
}

dependencies {
    implementation(kotlin("stdlib"))

}
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(11))
}