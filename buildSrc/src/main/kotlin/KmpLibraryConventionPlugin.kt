import com.android.build.gradle.LibraryExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension

class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("io.gitlab.arturbosch.detekt")
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")

            extensions.configure<KotlinMultiplatformExtension> {
                androidTarget()
                jvm()
                jvmToolchain(26)

                sourceSets.apply {
                    getByName("commonTest") {
                        dependencies {
                            implementation(kotlin("test"))
                        }
                    }
                }
            }

            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }

            extensions.configure<LibraryExtension> {
                compileSdk = 34

                defaultConfig {
                    minSdk = 26
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
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

            // Задача `detekt` у KMP-модуля ничего не видит: исходники лежат в commonMain и jvmMain,
            // а она смотрит в src/main/java. Именно её собирали корневой агрегат и ciCheck, поэтому
            // весь общий код проекта — очередь, синхронизация, база, домены и data-слои фич —
            // не проверялся ни разу, а гейт был зелёным. Настоящий анализ дают задачи
            // detektMetadata*/detekt<Target>; вешаем на check их, а не пустышку.
            tasks.named("check").configure {
                dependsOn(tasks.matching { it.name.startsWith("detektMetadata") })
            }
        }
    }
}
