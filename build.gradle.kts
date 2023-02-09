import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Test

plugins {
    kotlin("jvm") version "1.7.21"
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    group = "io.runebox.revtools"
    version = "1.0.0"

    repositories {
        mavenLocal()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }

    dependencies {
        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "17"
    }
}
