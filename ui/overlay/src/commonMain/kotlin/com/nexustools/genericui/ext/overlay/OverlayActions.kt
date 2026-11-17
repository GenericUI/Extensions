package com.nexustools.genericui.ext.overlay

/**
 * Domain-agnostic action callbacks invoked by an [AbstractOverlay], its [OverlayElement]s,
 * and [OverlayInputRouter].
 */
data class OverlayActions(
    val onPlayPause: (() -> Unit)? = null,
    val onPlay: (() -> Unit)? = null,
    val onPause: (() -> Unit)? = null,
    val onSeekToSec: ((Double) -> Unit)? = null,
    val onSkipDeltaSec: ((Double) -> Unit)? = null,
    val onNext: (() -> Unit)? = null,
    val onPrevious: (() -> Unit)? = null,
    val onVolumeChange: ((Int) -> Unit)? = null,
    val onMuteToggle: (() -> Unit)? = null,
    val onSpeedChange: ((Double) -> Unit)? = null,
    val onSelectAudioTrack: ((String) -> Unit)? = null,
    val onSelectSubtitleTrack: ((String?) -> Unit)? = null,
    val onToggleFullscreen: (() -> Unit)? = null,
    val onBadgeClick: ((String) -> Unit)? = null,
    val onCustomAction: ((actionId: String, payload: Any?) -> Unit)? = null
) {
    /**
     * Triggers play/pause based on [isPlaying], preferring [onPlayPause] and falling back
     * to [onPause] or [onPlay].
     */
    fun triggerPlayPause(isPlaying: Boolean) {
        when {
            onPlayPause != null -> onPlayPause.invoke()
            isPlaying -> onPause?.invoke()
            else -> onPlay?.invoke()
        }
    }
}
