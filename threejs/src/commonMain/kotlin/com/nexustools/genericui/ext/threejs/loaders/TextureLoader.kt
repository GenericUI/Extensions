package com.nexustools.genericui.ext.threejs.loaders

import com.nexustools.genericui.ext.threejs.textures.Texture
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageDecodeOptions
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.ImageSource
import com.nexustools.genericui.image.PixelFormat

/**
 * Cross-platform 2D texture loader (`THREE.TextureLoader`) backed directly by
 * GenericUI's `com.nexustools.genericui.image.ImageLoader` and `ImageBuffer`.
 */
class TextureLoader {

    fun load(
        urlOrPath: String,
        onLoad: ((Texture) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ): Texture {
        val texture = Texture()
        try {
            val source = if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) {
                ImageSource.fromUrl(urlOrPath)
            } else {
                ImageSource.fromFile(urlOrPath)
            }
            val buffer = ImageLoader.load(
                source = source,
                options = ImageDecodeOptions(preferredPixelFormat = PixelFormat.RGBA_8888)
            )
            applyImageBufferToTexture(buffer, texture)
            onLoad?.invoke(texture)
        } catch (t: Throwable) {
            onError?.invoke(t)
        }
        return texture
    }

    fun loadFromBytes(
        encodedBytes: ByteArray,
        onLoad: ((Texture) -> Unit)? = null
    ): Texture {
        val buffer = ImageLoader.loadFromBytes(
            encodedBytes = encodedBytes,
            options = ImageDecodeOptions(preferredPixelFormat = PixelFormat.RGBA_8888)
        )
        val texture = fromImageBuffer(buffer)
        onLoad?.invoke(texture)
        return texture
    }

    fun fromImageBuffer(buffer: ImageBuffer): Texture {
        val rgba = buffer.toRgba8888()
        return Texture(
            width = rgba.width,
            height = rgba.height,
            rgbaBytes = rgba.bytes
        ).apply {
            needsUpdate = true
        }
    }

    private fun applyImageBufferToTexture(buffer: ImageBuffer, texture: Texture) {
        val rgba = buffer.toRgba8888()
        texture.width = rgba.width
        texture.height = rgba.height
        texture.rgbaBytes = rgba.bytes
        texture.needsUpdate = true
    }
}
