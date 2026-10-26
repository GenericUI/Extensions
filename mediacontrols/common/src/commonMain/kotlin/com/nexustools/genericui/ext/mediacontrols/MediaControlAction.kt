package com.nexustools.genericui.ext.mediacontrols

/**
 * Transport and volume control actions dispatched by system media transport controls,
 * taskbar thumbnail toolbars, or hardware keyboard media keys.
 */
enum class MediaControlAction {
    PLAY,
    PAUSE,
    PLAY_PAUSE,
    STOP,
    NEXT,
    PREVIOUS,
    FAST_FORWARD,
    REWIND,
    SEEK_TO,
    MUTE_TOGGLE,
    VOLUME_UP,
    VOLUME_DOWN
}
