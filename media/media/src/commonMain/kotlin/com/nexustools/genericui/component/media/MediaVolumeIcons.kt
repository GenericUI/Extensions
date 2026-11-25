package com.nexustools.genericui.component.media

import com.nexustools.genericui.component.media.res.R
import com.nexustools.genericui.ext.media.MediaExtension
import com.nexustools.genericui.resources.ResourceID

/**
 * Exposes `:extensions:media:media`'s layered 3-wave speaker vector drawables from [R.drawable].
 */
object MediaVolumeIcons {
    init {
        MediaExtension.rawDefaultStyleScss()
    }

    val speaker: ResourceID get() = R.drawable.ic_volume_speaker
    val muteCross: ResourceID get() = R.drawable.ic_volume_mute_cross
    val wave1: ResourceID get() = R.drawable.ic_volume_wave_1
    val wave2: ResourceID get() = R.drawable.ic_volume_wave_2
    val wave3: ResourceID get() = R.drawable.ic_volume_wave_3
}
