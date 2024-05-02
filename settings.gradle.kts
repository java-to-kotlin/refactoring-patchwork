import org.apache.tools.ant.DirectoryScanner

rootProject.name = "mastering-kotlin-refactoring-patchwork"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

val http4kVersion = "5.14.1.0"
val forkhandlesVersion = "2.13.1.0"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            library("forkhandles", "dev.forkhandles:forkhandles-bom:$forkhandlesVersion")
            library("result4k","dev.forkhandles:result4k:$forkhandlesVersion")
            library("values4k","dev.forkhandles:values4k:$forkhandlesVersion")
            library("tuples4k","dev.forkhandles:tuples4k:$forkhandlesVersion")
            
            library("http4k-bom", "org.http4k:http4k-bom:$http4kVersion")
            library("http4k-core", "org.http4k:http4k-core:$http4kVersion")
            library("http4k-server-undertow", "org.http4k:http4k-server-undertow:$http4kVersion")
            library("http4k-client-apache", "org.http4k:http4k-client-apache:$http4kVersion")
            
            bundle("http4k", listOf(
                "http4k-bom",
                "http4k-core",
                "http4k-server-undertow",
                "http4k-client-apache"
            ))
            
            library("kotlin-serialisation-json", "org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
            library("kondor", "com.ubertob.kondor:kondor-core:2.3.2")
            
            library("hsqldb", "org.hsqldb:hsqldb:2.7.2")
            library("flyway", "org.flywaydb:flyway-database-hsqldb:10.10.0")
            
            bundle("dbdev", listOf(
                "hsqldb",
                "flyway"
            ))
            
            library("faker", "io.github.serpro69:kotlin-faker:1.16.0")
            
            library("junit-bom", "org.junit:junit-bom:5.10.1")
            library("junit-jupiter", "org.junit.jupiter:junit-jupiter:5.10.1")
            
            bundle("junit", listOf(
                "junit-jupiter"
            ))
        }
    }
}

/* Unfortunately, Gradle requires this to be configured globally, not
 * in the task that needs it.
 */
DirectoryScanner.removeDefaultExclude("**/.gitignore")

include("exercises:signup")
include("exercises:leaderboard")
include("exercises:boatlog")
