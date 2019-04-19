import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.javafxports.jfxmobile.plugin.DownConfiguration
import org.javafxports.jfxmobile.plugin.JFXMobileExtension
import org.javafxports.jfxmobile.plugin.android.AndroidExtension
import org.javafxports.jfxmobile.plugin.ios.IosExtension
import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.nio.file.Paths

buildscript {
	repositories {
		google()
	}
}

plugins {
	kotlin("jvm") version "1.2.71"
	application
	id("com.github.johnrengelman.shadow") version "4.0.1"
	id("org.javafxports.jfxmobile") version "1.3.16"
	id("com.github.ben-manes.versions") version "0.20.0"
}

group = "xerus.music"

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
	compile("com.github.Xerus2000.util", "javafx", "cd7376ec6e33543d81ee07884ec5a3b5827ee008")
	
	// Music
	compile("com.github.ngmusic", "moodplay", "3a1c3aa2b24136d2c1cd900afe840d21d0131939")
	compile("net.jthink", "jaudiotagger", "2.2.4")
	
	// Logging
	compile("org.slf4j", "slf4j-api", "1.7.25")
	compile("io.github.microutils", "kotlin-logging", "1.5.4")
	implementation("ch.qos.logback", "logback-classic", "1.2.3")
	
	// Platform-specifics
	val CHARM_DOWN_VERSION = "2.0.1"
	compile("com.gluonhq", "charm-down-common", CHARM_DOWN_VERSION)
	
	desktopRuntime("com.gluonhq", "charm-down-desktop", CHARM_DOWN_VERSION)
	desktopRuntime("org.xerial", "sqlite-jdbc", "3.25.2")
	
	androidRuntime("com.gluonhq", "charm-down-android", CHARM_DOWN_VERSION)
	androidRuntime("org.sqldroid", "sqldroid", "1.0.3")
	
	desktopCompile("com.github.ngmusic.mpris-java", "mpris-extensions", "74f51fb6ef2535a55f41b01a0cf160c1ef4e698d")
	
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
		
		compileSdkVersion = "26"
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
	
	create("apk") {
		group = ANDROID
		dependsOn("clean", androidApk)
	}
	
	create<Exec>("pushApk") {
		group = ANDROID
		commandLine("adb", "push", "build/javafxports/android/MusicPlayer.apk", "storage/AE58-1072/Programmieren")
	}
	
	create("push") {
		doLast {
			Runtime.getRuntime().exec("adb shell ls /storage")
			exec { commandLine("adb", "push", "src/layouts/CSS", "storage/AE58-1072/Programmieren/") }
			exec { commandLine("adb", "push", "src/layouts/FXML", "storage/AE58-1072/Programmieren/") }
			exec { commandLine("adb", "push", "build/javafxports/android/MusicPlayer.apk", "storage/AE58-1072/Programmieren/") }
		}
	}
	
	create("androidRun") {
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
	
	create<Exec>("androidUninstall") {
		group = ANDROID
		commandLine("adb", "shell", "pm", "uninstall", "xerus.music")
	}
	
	create("androidReinstall") {
		doLast {
			exec { commandLine("adb", "push", "shell", "pm", "uninstall") }
			exec { commandLine("gradle", "android") }
		}
	}
	
}

// TASKS

val MAIN = "_main"


tasks {
	
	"run"(JavaExec::class) {
		group = MAIN
		// gradle run -Dargs="FINE save"
		args = System.getProperty("exec.args", "").split(" ")
	}
	
	tasks.replace("jar", ShadowJar::class.java).run {
		group = MAIN
		classifier = ""
		description = "Assemble a fat Jar"
		/*for (set in arrayOf(sourceSets.getByName("main"), sourceSets.getByName("desktop"))) {
			from(set.output)
		}*/
		from(tasks["compileDesktopJava"].outputs)
		configurations.add(project.configurations["desktopRuntime"])
	}
	
	create<Sync>("release") {
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
	
	withType<KotlinCompile> {
		kotlinOptions.jvmTarget = "1.8"
	}
	
}

println("Java version: ${JavaVersion.current()}")