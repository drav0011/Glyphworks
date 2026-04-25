repositories {
    
}

dependencies {
}

tasks.register<Exec>("generateAssetSchemas") {
    val appData = System.getenv("APPDATA") ?: throw GradleException("APPDATA environment variable is not set")
    val assetsZip = file("$appData/Hytale/install/pre-release/package/game/latest/Assets.zip")
    val schemaAssetsDir = layout.projectDirectory.dir("schemas/assets").asFile
    val devServerDir = layout.projectDirectory.dir("devserver").asFile
    val modsSourceDir = layout.projectDirectory.dir("src/main").asFile

    inputs.file(assetsZip)
    inputs.dir(modsSourceDir)
    outputs.dir(schemaAssetsDir)

    workingDir = devServerDir
    executable = "java"
    argumentProviders.add(CommandLineArgumentProvider {
        val serverJar = configurations.getByName("compileClasspath")
            .resolvedConfiguration
            .resolvedArtifacts
            .first { it.moduleVersion.id.module.group == "com.hypixel.hytale" }
            .file

        listOf(
            "-jar",
            serverJar.absolutePath,
            "--assets",
            assetsZip.absolutePath,
            "--mods",
            modsSourceDir.absolutePath,
            "--generate-asset-schema",
            schemaAssetsDir.absolutePath,
        )
    })

    doFirst {
        require(assetsZip.exists()) { "Hytale assets zip not found: ${assetsZip.absolutePath}" }
        require(devServerDir.exists()) { "Devserver directory not found: ${devServerDir.absolutePath}" }
        require(modsSourceDir.exists()) { "Mods source directory not found: ${modsSourceDir.absolutePath}" }

        schemaAssetsDir.mkdirs()
    }
}