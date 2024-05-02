import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformJvmPlugin

plugins {
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.23" apply false
}

repositories {
    mavenCentral()
}

subprojects {
    repositories {
        mavenCentral()
    }
    
    plugins.withType<KotlinPlatformJvmPlugin> {
        kotlin {
            jvmToolchain(17)
        }
    }
    
    tasks.withType<Test> {
        useJUnitPlatform()
    }
}


tasks.create<Copy>("createStartingPoint") {
    destinationDir = buildDir.resolve("starting-point")
    from(projectDir) {
        exclude("**/build/**")
        exclude("**/.git/**")
        exclude("**/.gradle/**")
        exclude("**/*.class")
        exclude("**/PRESENTER-NOTES.*")
        exclude("**/.idea/**")
    }
}

