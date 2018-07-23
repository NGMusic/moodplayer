import com.android.builder.model.AndroidArtifact
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.javafxports.jfxmobile.plugin.android.task.AndroidTask
import org.javafxports.jfxmobile.plugin.DownConfiguration
import org.javafxports.jfxmobile.plugin.JFXMobileExtension
import org.javafxports.jfxmobile.plugin.android.AndroidExtension
import org.javafxports.jfxmobile.plugin.ios.IosExtension
import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

import java.nio.file.Paths
import kotlin.reflect.KClass

buildscript {
	repositories {
		google()
	}
}

plugins {
	kotlin("jvm") version "1.2.51"
	java
	application
	id("com.github.johnrengelman.shadow") version "2.0.3"
	id("org.javafxports.jfxmobile") version "1.3.10"
	id("com.github.ben-manes.versions") version "0.17.0"
}

group = "xerus.music"

val kotlinVersion: String by extra {
	buildscript.configurations["classpath"].resolvedConfiguration.firstLevelModuleDependencies
			.find { it.moduleName == "org.jetbrains.kotlin.jvm.gradle.plugin" }!!.moduleVersion
}

application {
	mainClassName = "xerus.music.Launcher"
}

// region dependencies

kotlin.experimental.coroutines = Coroutines.ENABLE

repositories {
	jcenter()
	maven("https://jitpack.io")
	maven("https://dl.bintray.com/ijabz/maven")
	maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
	compile(kotlin("stdlib"))
	
	compile("com.github.Xerus2000.util", "javafx", "master-SNAPSHOT")
	
	// Music
	compile("com.github.Xerus2000", "smartplay", "master-SNAPSHOT")
	compile("net.jthink", "jaudiotagger", "2.2.4")
	
	// Logging
	compile("org.slf4j", "slf4j-api", "1.7.25")
	compile("io.github.microutils", "kotlin-logging", "1.5.4")
	compile("ch.qos.logback", "logback-classic", "1.2.3")
	
	// Platform-specifics
	val CHARM_DOWN_VERSION = "2.0.1"
	compile("com.gluonhq", "charm-down-common", CHARM_DOWN_VERSION)
	
	desktopRuntime("com.gluonhq", "charm-down-desktop", CHARM_DOWN_VERSION)
	desktopRuntime("org.xerial", "sqlite-jdbc", "3.21.0.1")
	
	androidRuntime("com.gluonhq", "charm-down-android", CHARM_DOWN_VERSION)
	androidRuntime("org.sqldroid", "sqldroid", "1.0.3")
	
	desktopCompile("com.github.Xerus2000.mpris-java", "mpris-extensions", "master-SNAPSHOT")
	
	iosRuntime("com.gluonhq", "charm-down-ios", CHARM_DOWN_VERSION)
	
}

jfxmobile {
	// Work around https://github.com/gradle/kotlin-dsl/issues/457
	fun JFXMobileExtension.android(action: AndroidExtension.() -> Unit) = (this as ExtensionAware).extensions.configure(AndroidExtension::class.java, action)
	
	fun JFXMobileExtension.ios(action: IosExtension.() -> Unit) = (this as ExtensionAware).extensions.configure(IosExtension::class.java, action)
	
	downConfig = DownConfiguration(project).apply {
		plugins("storage", "settings")
	}
	android {
		manifest = "src/android/AndroidManifest.xml"
		
		compileSdkVersion = "24"
		minSdkVersion = "21"
	}
}

//endregion

// ANDROID TASKS


val ANDROID = "Android"

val androidApk = tasks.getByName("android").apply { mustRunAfter("clean") }

tasks {
	
	"androidInstall" {
		group = ANDROID
	}
	
	"apk" {
		group = ANDROID
		dependsOn("clean", androidApk)
	}
	
	"pushApk"(Exec::class) {
		group = ANDROID
		commandLine("adb", "push", "build/javafxports/android/MusicPlayer.apk", "storage/AE58-1072/Programmieren")
	}
	
	"push" {
		doLast {
			exec { commandLine("adb", "push", "src/layouts/CSS", "storage/AE58-1072/Programmieren/") }
			exec { commandLine("adb", "push", "src/layouts/FXML", "storage/AE58-1072/Programmieren/") }
			exec { commandLine("adb", "push", "build/javafxports/android/MusicPlayer.apk", "storage/AE58-1072/Programmieren/") }
		}
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
	
	tasks.replace("android").dependsOn("androidInstall", "androidRun").group = ANDROID
	
	"androidUninstall"(Exec::class) {
		group = ANDROID
		commandLine("adb", "shell", "pm", "uninstall", "xerus.music")
	}
	
	"androidReinstall" {
		doLast {
			exec { commandLine("adb", "push", "shell", "pm", "uninstall") }
			exec { commandLine("gradle", "android") }
		}
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
		classifier = ""
		description = "Assemble a fat Jar"
		for (set in arrayOf(java.sourceSets["main"], java.sourceSets["desktop"])) {
			from(set.output)
		}
		from(tasks["compileDesktopJava"].outputs)
		configurations.add(project.configurations["desktopRuntime"])
	}
	
	"release"(Sync::class) {
		group = MAIN
		dependsOn("apk", "jar")
		val releases = Paths.get(properties["releaseFolder"].toString() + "/MusicPlayer")
		doLast {
			sync {
				from("src/main/resources/layouts")
				into(releases.resolve("/layouts"))
			}
			copy {
				from("build/libs", "build/javafxports/android")
				into("releases")
				include("MusicPlayer.jar", "MusicPlayer.apk")
			}
		}
	}
	
}

println("Java version: ${JavaVersion.current()}")