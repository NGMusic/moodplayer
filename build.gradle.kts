import com.android.builder.model.AndroidArtifact
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.javafxports.jfxmobile.plugin.android.task.AndroidTask
import org.javafxports.jfxmobile.plugin.DownConfiguration
import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

import java.nio.file.Paths

plugins {
    kotlin("jvm") version kotlinVersion
    java
    application
    id("com.github.johnrengelman.shadow") version "2.0.1"
    id("org.javafxports.jfxmobile") version "1.3.8"
}

println("Java version: ${JavaVersion.current()}")

val kotlinVersion: String? by extra {
    buildscript.configurations["classpath"].resolvedConfiguration.firstLevelModuleDependencies
            .find { it.moduleName == "org.jetbrains.kotlin.jvm.gradle.plugin" }?.moduleVersion
}

application {
    mainClassName = "xerus.music.Launcher"
}

// region dependencies

kotlin.experimental.coroutines = Coroutines.ENABLE

repositories {
    jcenter()
    maven { setUrl("https://dl.bintray.com/ijabz/maven") }
}


dependencies {
    compile(kotlin("stdlib-jdk8"))

    compile("xerus.util", "javafx")
    compile("xerus.util", "kotlin")

    desktopCompile("xerus.mpris", "mpris")
    desktopCompile("xerus.mpris", "mpris-extensions")

    compile("org.slf4j", "slf4j-api", "1.7.25")
    compile("io.github.microutils", "kotlin-logging", "1.5.+")
    compile("ch.qos.logback", "logback-classic", "1.2.3")

    compile("net.jthink", "jaudiotagger", "2.2.4")
    compile("com.gluonhq", "charm-down-common", "2.0.1")

}

jfxmobile {
    downConfig = DownConfiguration(project).apply {
        plugins("storage", "settings")
    }
    /*android {
        manifest = "src/android/AndroidManifest.xml"

        compileSdkVersion = 26
        minSdkVersion = 26
    }*/
}

//endregion

// ANDROID TASKS


val ANDROID = "Android"

val androidApk = tasks.getByName("android")

tasks {

    "androidInstall" {
        group = ANDROID
    }

    "apk" {
        group = ANDROID
        dependsOn("clean", androidApk)
        androidApk.mustRunAfter("clean")
    }

    "pushApk"(Exec::class) {
        group = ANDROID
        commandLine("adb", "push", "D:/Data/GoogleDrive/Programmieren/MusicPlayer/MusicPlayer.apk", "storage/AE58-1072/Programmieren")
        //commandLine( "adb", "push", "build/javafxports/android/MusicPlayer.apk", "storage/AE58-1072/Programmieren")
    }

    "androidRun" {
        group = ANDROID
        doLast {
            exec {
                commandLine("adb", "logcat", "-c")
            }
            exec {
                commandLine("adb",
                        "shell", "monkey",
                        "-p", "xerus.music", "1")
            }
            exec {
                commandLine("adb", "logcat", "-s", "System.out")
            }
        }
        mustRunAfter("androidInstall")
    }

    //tasks.replace("android").dependsOn("androidInstall", "androidRun").group = ANDROID

    "androidUninstall"(Exec::class) {
        group = ANDROID
        //commandLine "adb", "shell", "pm", "uninstall", "xerus.music"
    }

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

    tasks.replace("jar", ShadowJar::class.java).run {
        group = MAIN
        classifier = null
        description = "Assemble a Fat Jar"
        for (set in arrayOf(java.sourceSets["main"], java.sourceSets["desktop"])) {
            from(set.output)
        }
        from(tasks["compileDesktopJava"].outputs)
        configurations.add(project.configurations["desktopRuntime"])
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
