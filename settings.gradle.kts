import dev.scaffoldit.hytale.wire.HytaleManifest

rootProject.name = "dev.drav.glyphworks"

plugins {
    id("dev.scaffoldit") version "0.2.+"
}

hytale {
    usePatchline("release")
    useVersion("0.6.0")

    manifest {
        Group = "drav.dev"
        Name = "Glyphworks"
        Version = "0.3.0"
        Main = "dev.drav.glyphworks.GlyphworksPlugin"
        IncludesAssetPack = true
        DisabledByDefault = false
        ServerVersion = "0.6.0"

        // Machines consult this plugin's deny rules before mutating the world.
        // Optional: without it the checks fail open and automation is unrestricted.
        OptionalDependencies = mapOf("Hytale:TriggerVolumes" to "*")
        
        Authors = listOf(
            HytaleManifest.Author("DrAv0011", "", "https://drav.dev")
        )
    }
}