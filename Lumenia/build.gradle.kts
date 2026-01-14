plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.3.1"
    id("run-hytale")
}

group = findProperty("pluginGroup") as String? ?: "com.example"
version = findProperty("pluginVersion") as String? ?: "1.0.0"
description = findProperty("pluginDescription") as String? ?: "A Hytale plugin template"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    // Hytale Server API (provided by server at runtime)
    // Try to find HytaleServer.jar in common installation locations
    val hytaleHome = System.getProperty("user.home")
    val gameBuild = findProperty("gameBuild") as String? ?: "latest"
    val patchline = findProperty("patchline") as String? ?: "release"
    
    // Try macOS path first, then Windows path
    val macPath = "$hytaleHome/Library/Application Support/Hytale/install/$patchline/package/game/$gameBuild/Server/HytaleServer.jar"
    val windowsPath = "$hytaleHome/AppData/Roaming/Hytale/install/$patchline/package/game/$gameBuild/Server/HytaleServer.jar"
    val localPath = "libs/hytale-server.jar"
    
    val hytaleServerJar = when {
        file(macPath).exists() -> file(macPath)
        file(windowsPath).exists() -> file(windowsPath)
        file(localPath).exists() -> file(localPath)
        else -> {
            logger.warn("HytaleServer.jar not found! Please either:")
            logger.warn("  1. Place it in libs/hytale-server.jar")
            logger.warn("  2. Set gameBuild and patchline in gradle.properties")
            logger.warn("  3. Or ensure Hytale is installed in the default location")
            throw GradleException("HytaleServer.jar not found. Please check the configuration.")
        }
    }
    
    compileOnly(files(hytaleServerJar))
    
    // Common dependencies (will be bundled in JAR)
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains:annotations:24.1.0")
    
    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Configure server testing
runHytale {
    // TODO: Update this URL when Hytale server is available
    // Using Paper server as placeholder for testing the runServer functionality
    jarUrl = "https://fill-data.papermc.io/v1/objects/d5f47f6393aa647759f101f02231fa8200e5bccd36081a3ee8b6a5fd96739057/paper-1.21.10-115.jar"
}

tasks {
    // Configure Java compilation
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release = 25
    }
    
    // Configure resource processing
    processResources {
        filteringCharset = Charsets.UTF_8.name()
        
        // Replace placeholders in manifest.json
        val props = mapOf(
            "group" to project.group,
            "version" to project.version,
            "description" to project.description
        )
        inputs.properties(props)
        
        filesMatching("manifest.json") {
            expand(props)
        }
    }
    
    // Configure ShadowJar (bundle dependencies)
    shadowJar {
        archiveBaseName.set(rootProject.name)
        archiveClassifier.set("")
        
        // Relocate dependencies to avoid conflicts
        relocate("com.google.gson", "com.yourplugin.libs.gson")
        
        // Minimize JAR size (removes unused classes)
        minimize()
    }
    
    // Configure tests
    test {
        useJUnitPlatform()
    }
    
    // Make build depend on shadowJar
    build {
        dependsOn(shadowJar)
    }
}

// Configure Java toolchain
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
