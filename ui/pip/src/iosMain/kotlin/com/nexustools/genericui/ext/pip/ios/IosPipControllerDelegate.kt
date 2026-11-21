@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ext.pip.ios

import platform.AVKit.AVPictureInPictureController
import platform.AVKit.AVPictureInPictureControllerDelegateProtocol
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 * Objective-C [AVPictureInPictureControllerDelegateProtocol] bridge forwarding AVKit PiP
 * lifecycle transitions and UI restoration callbacks to [IosPipBackend].
 */
class IosPipControllerDelegate(
    private val onWillStart: () -> Unit = {},
    private val onDidStart: () -> Unit = {},
    private val onFailedToStart: (NSError?) -> Unit = {},
    private val onWillStop: () -> Unit = {},
    private val onDidStop: () -> Unit = {},
    private val onRestoreUserInterface: ((Boolean) -> Unit) -> Unit = { completion -> completion(true) }
) : NSObject(), AVPictureInPictureControllerDelegateProtocol {

    override fun pictureInPictureControllerWillStartPictureInPicture(
        pictureInPictureController: AVPictureInPictureController
    ) {
        onWillStart()
    }

    override fun pictureInPictureControllerDidStartPictureInPicture(
        pictureInPictureController: AVPictureInPictureController
    ) {
        onDidStart()
    }

    override fun pictureInPictureController(
        pictureInPictureController: AVPictureInPictureController,
        failedToStartPictureInPictureWithError: NSError
    ) {
        onFailedToStart(failedToStartPictureInPictureWithError)
    }

    override fun pictureInPictureControllerWillStopPictureInPicture(
        pictureInPictureController: AVPictureInPictureController
    ) {
        onWillStop()
    }

    override fun pictureInPictureControllerDidStopPictureInPicture(
        pictureInPictureController: AVPictureInPictureController
    ) {
        onDidStop()
    }

    override fun pictureInPictureController(
        pictureInPictureController: AVPictureInPictureController,
        restoreUserInterfaceForPictureInPictureStopWithCompletionHandler: (Boolean) -> Unit
    ) {
        onRestoreUserInterface(restoreUserInterfaceForPictureInPictureStopWithCompletionHandler)
    }
}
