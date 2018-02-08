import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

import java.nio.file.Paths

buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath("org.javafxports:jfxmobile-plugin:1.3.8")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

plugins {
    java
    application
    id("com.github.johnrengelman.shadow") version "2.0.1"
}
apply {
    plugin("kotlin")
    plugin("org.javafxports.jfxmobile")
}

println("Java version: ${JavaVersion.current()}")
application {
    mainClassName = "xerus.music.Launcher"
}

// region dependencies

kotlin.experimental.coroutines "enable"

repositories {
    jcenter()
    //mavenCentral()
    maven { url "https://dl.bintray.com/ijabz/maven" }
}


dependencies {
    compile("xerus.utils", "javafx")
    compile("xerus.utils", "kotlin")

    compile("org.jetbrains.kotlin", "kotlin-stdlib", kotlinVersion)
    compile( "org.jetbrains.kotlinx",  "kotlinx-coroutines-core", "0.+")

    compile("net.jthink", "jaudiotagger", "2.2.4")
    compile("com.gluonhq", "charm-down-common", "2.0.1")
}

jfxmobile {
    android {
        manifest = "src/android/AndroidManifest.xml"

        compileSdkVersion = 26
        minSdkVersion = 26
    }
    downConfig {
        plugins("storage", "settings")
    }
}

//endregion

// ANDROID TASKS


val ANDROID = "Android"
androidInstall.setGroup(ANDROID)

val androidApk = tasks("android")
task apk {
    group = ANDROID
    dependsOn("clean", androidApk)
    androidApk.mustRunAfter("clean")
}

task pushApk(type: Exec) {
    group ANDROID
            commandLine "adb", "push", "D:/Data/GoogleDrive/Programmieren/MusicPlayer/MusicPlayer.apk", "storage/AE58-1072/Programmieren"
    //commandLine "adb", "push", "build/javafxports/android/MusicPlayer.apk", "storage/AE58-1072/Programmieren"
}

task androidRun {
    group ANDROID
            doLast {
                exec {
                    commandLine "adb", "logcat", "-c"
                }
                exec {
                    commandLine "adb",
                    "shell", "monkey",
                    "-p", "xerus.music", "1"
                }
                exec {
                    commandLine "adb", "logcat", "-s", "System.out"
                }
            }
}
androidRun.mustRunAfter(androidInstall)

task android(dependsOn: [androidInstall, androidRun], overwrite: true, group: ANDROID)

task androidUninstall(type: Exec) {
    group = ANDROID
    //commandLine "adb", "shell", "pm", "uninstall", "xerus.music"
}

// TASKS

val MAIN = "_main"


tasks {

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }

    "run"(JavaExec::class) {
        group = MAIN
        // gradle run -Dexec.args="FINE save"
        args = System.getProperty("exec.args", "").split(" ")
    }

    replace("jar", ShadowJar::class) {
        group = MAIN
        classifier = null
        description = "Assemble a Fat Jar"
        for (set in arrayOf(sourceSets.main, sourceSets.desktop)) {
            from(set.output, compileDesktopJava.outputs)
            configurations = project.configurations.desktopRuntime
        }
    }
}

/*
run.setGroup(MAIN)
task jar(type: ShadowJar, overwrite: true) {
    group = MAIN
    classifier = null
    description = "Assemble a Fat Jar"
    for (set in [sourceSets.main, sourceSets.desktop])
        from set.output
                from compileDesktopJava.outputs
                configurations = [project.configurations.desktopRuntime]
}

task release {
    group MAIN
    dependsOn(apk, jar)
    doLast {
        val releases = Paths.get("${releaseFolder}/MusicPlayer")
        exec {
            ignoreExitValue true
            commandLine "robocopy", "/NJH", "/MIR", "src/layouts", releases.resolve("/layouts")
        }
        exec {
            ignoreExitValue true
            commandLine "robocopy", "/NJH", "build/libs", releases, "MusicPlayer.jar"
        }
        exec {
            ignoreExitValue true
            commandLine "robocopy", "/NJH", "build/javafxports/android", releases, "MusicPlayer.apk"
        }
    }
}

task bundleDependencies(type: Tar) {
    group "distribution"
    baseName = "dependencies"
    for (config in configurations)
        if (config.canBeResolved)
            from config
}
*/
