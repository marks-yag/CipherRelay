package at.proxy.desktop

import at.proxy.local.LocalConfig

data class DesktopConfig(
    val profiles: Map<String, LocalConfig>,
    val defaultProfile: String
)
