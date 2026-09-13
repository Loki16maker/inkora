import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.sqldelight)
}

val supabaseUrl = providers.environmentVariable("INKORA_SUPABASE_URL").orNull.orEmpty()
val supabasePublishableKey = providers.environmentVariable("INKORA_SUPABASE_PUBLISHABLE_KEY").orNull.orEmpty()
fun String.asJavaStringLiteral(): String = replace("\\", "\\\\").replace("\"", "\\\"")

val generateDesktopCloudConfig = tasks.register("generateDesktopCloudConfig") {
    val output = layout.buildDirectory.file("generated/inkora-cloud/inkora-supabase.properties")
    outputs.file(output)
    doLast {
        val file = output.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            "url=${supabaseUrl.replace("\\", "\\\\").replace("\n", "")}\n" +
                "publishableKey=${supabasePublishableKey.replace("\\", "\\\\").replace("\n", "")}\n",
        )
    }
}

kotlin {
    androidTarget()
    jvm("desktop")

    // The default hierarchy is intentionally disabled because the Apple
    // targets are placeholders. Associate the desktop test compilation with
    // desktop main explicitly so tests can see the shared production classes
    // (and SQLDelight's generated JVM database) on Windows as well.
    targets.getByName("desktop").compilations.getByName("test").associateWith(
        targets.getByName("desktop").compilations.getByName("main"),
    )

    // Keep Apple source sets ready for a future macOS/iOS build. They are
    // intentionally not linked by the Windows build pipeline.
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain = getByName("commonMain")
        val commonTest = getByName("commonTest")
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
            implementation(libs.okio)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        val desktopMain = getByName("desktopMain")
        val desktopTest = getByName("desktopTest")
        desktopMain.dependsOn(commonMain)
        desktopMain.resources.srcDir(layout.buildDirectory.dir("generated/inkora-cloud"))
        desktopTest.dependsOn(commonTest)
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("org.apache.pdfbox:pdfbox:3.0.5")
            implementation("app.cash.sqldelight:sqlite-driver:2.0.2")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
        }
        val androidMain by getting
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.10.1")
            implementation("androidx.activity:activity-ktx:1.10.1")
            implementation("app.cash.sqldelight:android-driver:2.0.2")
        }
        desktopTest.dependencies {
            implementation(libs.kotlin.test)
            // Keep the desktop test classpath explicit when the shared metadata
            // variant is unavailable on Windows.
            implementation(files(layout.buildDirectory.dir("classes/kotlin/desktop/main")))
        }
    }
}

// CI uses JDK 21 while Android's Java compiler intentionally targets 17.
// Pin every JVM Kotlin compilation to the same bytecode level so release
// packaging passes Gradle's target compatibility validation.
tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

tasks.matching { it.name == "desktopProcessResources" || it.name == "jvmProcessResources" }.configureEach {
    dependsOn(generateDesktopCloudConfig)
}

compose.desktop {
    application {
        mainClass = "com.inkora.MainKt"
        nativeDistributions {
            // Windows is a primary V1 target: ship a self-contained MSI and
            // keep the unpacked app/EXE available for portable testing.
            targetFormats(TargetFormat.Msi)
            modules("java.sql", "jdk.httpserver")
            packageName = "Inkora"
            packageVersion = rootProject.version.toString()
            description = "Inkora handwritten notebook and PDF study workspace"
            vendor = "Inkora"
            windows {
                menuGroup = "Inkora"
                shortcut = true
                upgradeUuid = "c3cc7ed4-1d15-4c8c-a4a5-1b6f7f3d29e1"
            }
        }
    }
}

// PDFBox loads glyph lists, CMaps, and fallback fonts through
// Class.getResourceAsStream(). A jar URL cannot be resolved reliably by the
// Windows launcher when the installation path contains an exclamation mark
// (for example D:\\PDFDRAWER!!). Keep these resources in a plain directory
// that is placed before the dependency jars on the packaged classpath.
val preparePdfBoxResources = tasks.register("preparePdfBoxResources") {
    val desktopRuntime = configurations.getByName("desktopRuntimeClasspath")
    val generatedDirectory = layout.buildDirectory.dir("generated/pdfbox-resources")
    notCompatibleWithConfigurationCache("Resolves dependency jars while preparing filesystem PDFBox resources")
    inputs.files(desktopRuntime)
    outputs.dir(generatedDirectory)

    doLast {
        val output = generatedDirectory.get().asFile
        project.delete(output)
        output.mkdirs()
        desktopRuntime.files
            .filter { file -> file.name.startsWith("pdfbox-") || file.name.startsWith("fontbox-") }
            .forEach { dependency ->
                project.copy {
                    from(project.zipTree(dependency))
                    include("org/apache/pdfbox/resources/**")
                    include("org/apache/fontbox/cmap/**")
                    into(output)
                }
            }
    }
}

tasks.matching { it.name == "createDistributable" }.configureEach {
    dependsOn(preparePdfBoxResources)
    doLast {
        val appDirectory = layout.buildDirectory.dir("compose/binaries/main/app/Inkora/app").get().asFile
        val resourceDirectory = layout.buildDirectory.dir("generated/pdfbox-resources").get().asFile
        project.copy {
            from(resourceDirectory)
            into(appDirectory.resolve("resources"))
        }

        val configFile = appDirectory.resolve("Inkora.cfg")
        if (configFile.isFile) {
            val config = configFile.readText()
            val classpathEntry = "app.classpath=" + "\$APPDIR" + "\\resources"
            if (classpathEntry !in config.lineSequence().map(String::trim).toList()) {
                val headerIndex = config.indexOf("[Application]")
                if (headerIndex >= 0) {
                    val lineEnd = config.indexOf('\n', headerIndex).let { if (it < 0) config.length else it + 1 }
                    configFile.writeText(config.substring(0, lineEnd) + "$classpathEntry\r\n" + config.substring(lineEnd))
                }
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 1
    maxHeapSize = "512m"
}

android {
    namespace = "com.inkora"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Release APKs must keep the same signing key so Android can install an
    // update over the existing app. CI supplies these values through secrets;
    // local debug builds continue to work without a keystore.
    val releaseKeystorePath = providers.environmentVariable("INKORA_KEYSTORE_PATH").orNull
    val releaseKeystorePassword = providers.environmentVariable("INKORA_KEYSTORE_PASSWORD").orNull
    val releaseKeyAlias = providers.environmentVariable("INKORA_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.environmentVariable("INKORA_KEY_PASSWORD").orNull
    if (!releaseKeystorePath.isNullOrBlank() && !releaseKeystorePassword.isNullOrBlank() && !releaseKeyAlias.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.inkora"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = compileSdk
        versionCode = 10
        versionName = rootProject.version.toString()
        buildConfigField("String", "SUPABASE_URL", "\"${supabaseUrl.asJavaStringLiteral()}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${supabasePublishableKey.asJavaStringLiteral()}\"")
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // The UI entry point is supplied by the application layer. The shared
    // foundation remains usable by Android, desktop, and future Apple hosts.
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
}

sqldelight {
    databases {
        create("InkoraDatabase") {
            packageName.set("com.inkora.database.generated")
            srcDirs("src/commonMain/sqldelight")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight"))
        }
    }
}
