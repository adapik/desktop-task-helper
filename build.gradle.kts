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

// Compile the native macOS notification helper (Swift → arm64 binary with embedded Info.plist).
// The binary is placed into the processed resources so it is included in the JAR and available
// at runtime via ClassLoader.getResourceAsStream("/notifier").
val compileNotifier = tasks.register<Exec>("compileNotifier") {
    onlyIf { System.getProperty("os.name").lowercase().contains("mac") }

    val swiftSrc  = file("src/main/swift/notifier.swift")
    val plistSrc  = file("src/main/swift/notifier-Info.plist")
    val outputBin = layout.buildDirectory.file("notifier-binary/notifier")

    inputs.files(swiftSrc, plistSrc)
    outputs.file(outputBin)

    doFirst { outputBin.get().asFile.parentFile.mkdirs() }

    commandLine(
        "swiftc", swiftSrc.absolutePath,
        "-o", outputBin.get().asFile.absolutePath,
        "-Xlinker", "-sectcreate",
        "-Xlinker", "__TEXT",
        "-Xlinker", "__info_plist",
        "-Xlinker", plistSrc.absolutePath,
        "-O"
    )
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(compileNotifier)
    from(compileNotifier.map { it.outputs.files.singleFile }) {
        into("notifier-bin")
    }
    // Info.plist is needed alongside the binary to reconstruct a proper .app bundle at runtime.
    from("src/main/swift/notifier-Info.plist") {
        into("notifier-bin")
    }
}

tasks.register<Exec>("jpackage") {
    dependsOn("jar")
    val jarFile = tasks.jar.get().archiveFile.get().asFile

    commandLine(
        "jpackage",
        "--input", jarFile.parentFile.absolutePath,
        "--main-jar", jarFile.name,
        "--main-class", "com.taskhelper.MainKt",
        "--name", "desktop-task-helper",
        "--app-version", project.version.toString(),
        "--type", "dmg",
        "--dest", layout.buildDirectory.dir("package").get().asFile.absolutePath,
        "--java-options", "-Djava.awt.headless=false",
        "--add-modules", "java.base,java.desktop,java.instrument,java.logging,jdk.unsupported"
    )
}
