plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    application
}

group = "com.taskhelper"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.slf4j:slf4j-simple:2.0.16")
    implementation("net.java.dev.jna:jna:5.14.0")
}

application {
    mainClass.set("com.taskhelper.MainKt")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.taskhelper.MainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

kotlin {
    jvmToolchain(25)
}
