import dev.scaffoldit.hytale.wire.HytaleManifest

rootProject.name = "dev.drav.glyphworks"

plugins {
    id("dev.scaffoldit") version "0.2.+"
}

hytale {
    usePatchline("pre-release")
    useVersion("latest")

    manifest {
        Group = "drav.dev"
        Name = "Glyphworks"
        Version = "0.2.0"
        Main = "dev.drav.glyphworks.GlyphworksPlugin"
        IncludesAssetPack = true
        DisabledByDefault = false
        // TODO: pin to "0.6.0" once Update 6 ships on the release patchline (Aug 27)
        ServerVersion = "*"
        
        Authors = listOf(
            HytaleManifest.Author("DrAv0011", "", "https://drav.dev")
        )
    }
}