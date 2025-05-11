package com.github.yag.cr.desktop

import com.github.yag.cr.local.LocalConfig

data class DesktopConfig(
    val profiles: Map<String, LocalConfig>,
    val defaultProfile: String
)
