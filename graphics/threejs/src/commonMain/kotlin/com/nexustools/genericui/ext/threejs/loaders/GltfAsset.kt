package com.nexustools.genericui.ext.threejs.loaders

import com.nexustools.genericui.ext.threejs.animation.AnimationClip
import com.nexustools.genericui.ext.threejs.objects.Group
import com.nexustools.genericui.ext.threejs.objects.Skeleton

/**
 * Loaded glTF 2.0 scene hierarchy paired with parsed [AnimationClip]s and [Skeleton] rigs.
 */
data class GltfAsset(
    val scene: Group,
    val animations: List<AnimationClip> = emptyList(),
    val skins: List<Skeleton> = emptyList()
)
