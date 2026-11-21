package com.example.flagmanager

/**
 * Roblox's official local FastFlag allowlist (announced 2025-09-29).
 * Only keys in this set are read from ClientAppSettings.json on the client.
 * Everything else is silently ignored by Roblox — no app-side trick changes that.
 * Source: https://devforum.roblox.com/t/allowlist-for-local-client-configuration-via-fast-flags/3966569
 */
object FlagCatalog {
    const val ALLOWLIST_URL =
        "https://devforum.roblox.com/t/allowlist-for-local-client-configuration-via-fast-flags/3966569"

    enum class Kind { BOOL, INT }

    data class Entry(
        val key: String,
        val title: String,
        val detail: String,
        val group: String,
        val kind: Kind,
        val recommended: String,
        val range: String = ""
    )

    val ENTRIES = listOf(
        // Geometry / level of detail
        Entry("DFIntCSGLevelOfDetailSwitchingDistance", "CSG LOD distance",
            "Distance where CSG parts switch to low detail.", "Geometry", Kind.INT, "400", "0-10000"),
        Entry("DFIntCSGLevelOfDetailSwitchingDistanceL12", "CSG LOD L12",
            "First LOD transition distance.", "Geometry", Kind.INT, "200", "0-10000"),
        Entry("DFIntCSGLevelOfDetailSwitchingDistanceL23", "CSG LOD L23",
            "Second LOD transition distance.", "Geometry", Kind.INT, "350", "0-10000"),
        Entry("DFIntCSGLevelOfDetailSwitchingDistanceL34", "CSG LOD L34",
            "Third LOD transition distance.", "Geometry", Kind.INT, "500", "0-10000"),

        // Rendering
        Entry("FFlagHandleAltEnterFullscreenManually", "Manual fullscreen",
            "Lets the app handle Alt+Enter instead of the OS.", "Rendering", Kind.BOOL, "True"),
        Entry("DFFlagTextureQualityOverrideEnabled", "Override texture quality",
            "Required before DFIntTextureQualityOverride takes effect.", "Rendering", Kind.BOOL, "True"),
        Entry("DFIntTextureQualityOverride", "Texture quality",
            "0 lowest · 1 low · 2 medium · 3 high.", "Rendering", Kind.INT, "2", "0-3"),
        Entry("FIntDebugForceMSAASamples", "MSAA samples",
            "Anti-aliasing sample count. 1 = off, 2/4/8 = on.", "Rendering", Kind.INT, "2", "1-8"),
        Entry("DFFlagDisableDPIScale", "Ignore DPI scale",
            "Keeps render resolution when display scaling changes.", "Rendering", Kind.BOOL, "True"),
        Entry("FFlagDebugGraphicsPreferD3D11", "Prefer Direct3D 11",
            "Windows only — no effect on Android.", "Rendering", Kind.BOOL, "True"),
        Entry("FFlagDebugSkyGray", "Gray sky",
            "Replaces the default skybox with flat gray.", "Rendering", Kind.BOOL, "True"),
        Entry("DFFlagDebugPauseVoxelizer", "Pause voxelizer",
            "Stops baked-shadow voxelization.", "Rendering", Kind.BOOL, "True"),
        Entry("DFIntDebugFRMQualityLevelOverride", "Graphics quality override",
            "Overrides the client's graphics quality level.", "Rendering", Kind.INT, "3", "1-10"),
        Entry("FIntFRMMaxGrassDistance", "Max grass distance",
            "Farthest grass render distance.", "Rendering", Kind.INT, "200", "0-10000"),
        Entry("FIntFRMMinGrassDistance", "Min grass distance",
            "Nearest grass render distance. 0 removes grass.", "Rendering", Kind.INT, "0", "0-10000"),
        Entry("FFlagDebugGraphicsPreferVulkan", "Prefer Vulkan",
            "Android devices with Vulkan support only.", "Rendering", Kind.BOOL, "True"),
        Entry("FFlagDebugGraphicsPreferOpenGL", "Prefer OpenGL",
            "Falls back to OpenGL ES on Android.", "Rendering", Kind.BOOL, "True"),

        // Interface
        Entry("FIntGrassMovementReducedMotionFactor", "Grass motion factor",
            "Reduces grass sway for accessibility.", "Interface", Kind.INT, "0", "0-100")
    )

    val ALLOWED: Set<String> = ENTRIES.map { it.key }.toSet()

    fun isAllowed(key: String): Boolean = key in ALLOWED

    fun meta(key: String): Entry? = ENTRIES.firstOrNull { it.key == key }

    fun byGroup(): Map<String, List<Entry>> = ENTRIES.groupBy { it.group }
}
