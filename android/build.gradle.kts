android/build.gradle.kts (ROOT PROJECT FILE)

plugins {
    // Defines the Android Gradle Plugin (AGP) version for all modules
    id("com.android.application") version "8.7.0" apply false 
    
    // Defines the Kotlin Gradle Plugin version for all modules
    id("org.jetbrains.kotlin.android") version "1.8.22" apply false // Use your Kotlin version
}

// 2. Repositories (usually defined in settings.gradle.kts now, but OK here too)
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://storage.googleapis.com/download.flutter.io") }
        maven { url = uri("https://dl.google.com/dl/android/maven2") } // Redundant but harmless
    }
}

// 3. Keep the custom build directory configuration (must use Kotlin DSL syntax)
val newBuildDir: File = rootProject.layout.buildDirectory.dir("../../build").get().asFile
rootProject.layout.buildDirectory.set(newBuildDir)

subprojects {
    val newSubprojectBuildDir: File = newBuildDir.resolve(project.name)
    project.layout.buildDirectory.set(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}