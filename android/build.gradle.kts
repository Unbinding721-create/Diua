buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://storage.googleapis.com/download.flutter.io' }
    }
    dependencies {
        // Use a gradle plugin compatible with your Gradle wrapper; adjust version as needed
        classpath 'com.android.tools.build:gradle:7.4.1'
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://storage.googleapis.com/download.flutter.io' }
        maven { url 'https://dl.google.com/dl/android/maven2' }
    }
}

val newBuildDir: Directory = rootProject.layout.buildDirectory.dir("../../build").get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
