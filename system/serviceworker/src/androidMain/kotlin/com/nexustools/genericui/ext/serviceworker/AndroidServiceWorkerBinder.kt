package com.nexustools.genericui.ext.serviceworker

import android.os.Binder

/**
 * Local in-process Android [Binder] linking Activities/clients directly to the single-process
 * `@MainServiceWorker` host managed by [AndroidServiceWorkerController].
 */
class AndroidServiceWorkerBinder internal constructor(
    internal val controller: AndroidServiceWorkerController
) : Binder()
