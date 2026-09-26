import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/** Experimental AGP 9 KMP convention, kept separate until the Room/KSP dependency slice is proven. */
class KmpLibraryAgp9ConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.kotlin.multiplatform.library")
            pluginManager.apply("io.gitlab.arturbosch.detekt")
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")

            val multiplatform = extensions.getByType(KotlinMultiplatformExtension::class.java)
            multiplatform.jvm()
            multiplatform.jvmToolchain(27)
            multiplatform.sourceSets.apply {
                getByName("commonTest").dependencies {
                    implementation(kotlin("test"))
                }
            }

            val androidTarget = multiplatform.targets.getByName("android") as KotlinMultiplatformAndroidLibraryTarget
            androidTarget.namespace =
                "com.tpov.schoolquiz." + path.removePrefix(":").replace(':', '.').replace('-', '_')
            androidTarget.compileSdk = 34
            androidTarget.minSdk = 26
            androidTarget.compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
            androidTarget.withHostTestBuilder {}.configure {}
            androidTarget.withDeviceTestBuilder {
                sourceSetTreeName = "test"
            }.configure {
                instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
            }

            extensions.configure<DetektExtension> {
                buildUponDefaultConfig = true
                allRules = false
                config.setFrom(rootProject.files("config/detekt/detekt.yml"))
                baseline = file("detekt-baseline.xml")
            }

            extensions.configure<KtlintExtension> {
                version.set("1.1.1")
                android.set(true)
                ignoreFailures.set(false)
                filter {
                    exclude { it.file.path.contains("/build/") }
                    exclude { it.file.path.contains("/generated/") }
                    exclude { it.file.path.contains("Test/") }
                    exclude { it.file.path.contains("/test/") }
                }
            }

            tasks.named("check").configure {
                dependsOn(tasks.matching { it.name.startsWith("detektMetadata") })
            }
        }
    }
}
