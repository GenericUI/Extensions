package com.nexustools.genericui.ext.webview.desktop.njs

import com.nexustools.genericui.ext.webview.desktop.dom.WebDomElement
import com.nexustools.genericui.ext.webview.desktop.engine.WebAlienDocumentEngine
import com.nexustools.genericui.ext.webview.desktop.engine.WebAudioEngine
import com.nexustools.genericui.ext.webview.desktop.engine.WebWavDecoder
import com.nexustools.njs.runtime.AbstractFunction
import com.nexustools.njs.runtime.BaseObject
import com.nexustools.njs.runtime.Float32Array
import com.nexustools.njs.runtime.GenericArray
import com.nexustools.njs.runtime.GenericObject
import com.nexustools.njs.runtime.Global
import com.nexustools.njs.runtime.JSPromise
import com.nexustools.njs.runtime.Null
import com.nexustools.njs.runtime.Scopable
import com.nexustools.njs.runtime.Uint8Array
import com.nexustools.njs.runtime.Undefined

/**
 * NJS bindings for `Image` (`HTMLImageElement`), `HTMLVideoElement`, `HTMLAudioElement`,
 * WebVTT `TextTrack`s, and Web Audio `AudioContext` (`OscillatorNode`, `GainNode`, `AnalyserNode`,
 * `AudioBuffer`, `AudioBufferSourceNode`, `MediaElementAudioSourceNode`) powered by GenericUI's
 * `VideoPlayerAlienImpl`, `ImageLoader`, and `AudioAnalyzer`.
 */
class NjsMediaAndAudioBridge(
    private val jsGlobal: Global,
    private val engine: WebAlienDocumentEngine,
    private val registry: NjsDomNodeRegistry,
    private val events: NjsDomEventSystem,
    private val scheduler: NjsEventScheduler
) {
    private val decodedAudioByMedia = LinkedHashMap<WebDomElement, WebWavDecoder.DecodedAudio?>()
    private val currentSrcByMedia = LinkedHashMap<WebDomElement, String>()
    private val readyStateByMedia = LinkedHashMap<WebDomElement, Int>()
    private val endedByMedia = LinkedHashMap<WebDomElement, Boolean>()

    fun install() {
        engine.onImageEvent = { el, type ->
            events.dispatchSimpleEvent(el, type, bubbles = false)
        }
        engine.onMediaEvent = { el, type ->
            events.dispatchSimpleEvent(el, type, bubbles = false)
        }

        registry.imageBindHook = { el, js -> attachImageApis(el, js) }
        registry.mediaBindHook = { el, js -> attachMediaApis(el, js) }

        val imageCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                val img = engine.document.createElement("img")
                params.getOrNull(0)?.takeIf { it !== Undefined }?.toInt()?.let { img.setAttribute("width", it.toString()) }
                params.getOrNull(1)?.takeIf { it !== Undefined }?.toInt()?.let { img.setAttribute("height", it.toString()) }
                return registry.wrapNode(img)
            }
            override fun name(): String = "Image"
        }
        jsGlobal.setHidden("Image", imageCtor)

        installAudioContextConstructor()
    }

    private fun attachImageApis(el: WebDomElement, js: GenericObject) {
        js.defineProperty("src", fn("get_src") { _, _ ->
            val raw = el.getAttribute("src").orEmpty()
            if (raw.isEmpty()) jsGlobal.wrap("") else jsGlobal.wrap(engine.resourceLoader.resolveUrl(raw, engine.document.url))
        }, fn("set_src") { _, p ->
            val v = p.getOrNull(0)?.toString().orEmpty()
            el.setAttribute("src", v)
            engine.loadImageElementIfNeeded(el)
            Undefined
        })
        js.defineProperty("alt", fn("get_alt") { _, _ -> jsGlobal.wrap(el.getAttribute("alt").orEmpty()) },
            fn("set_alt") { _, p -> el.setAttribute("alt", p.getOrNull(0)?.toString().orEmpty()); Undefined })
        js.defineProperty("complete", fn("get_complete") { _, _ ->
            if (!el.imageLoadedComplete && !el.getAttribute("src").isNullOrEmpty()) {
                engine.loadImageElementIfNeeded(el)
            }
            jsGlobal.wrap(el.imageLoadedComplete)
        }, jsGlobal.NOOP)
        js.defineProperty("naturalWidth", fn("get_nw") { _, _ ->
            if (!el.imageLoadedComplete && !el.getAttribute("src").isNullOrEmpty()) engine.loadImageElementIfNeeded(el)
            jsGlobal.wrap(el.imageNaturalWidth.toDouble())
        }, jsGlobal.NOOP)
        js.defineProperty("naturalHeight", fn("get_nh") { _, _ ->
            if (!el.imageLoadedComplete && !el.getAttribute("src").isNullOrEmpty()) engine.loadImageElementIfNeeded(el)
            jsGlobal.wrap(el.imageNaturalHeight.toDouble())
        }, jsGlobal.NOOP)
        js.defineProperty("width", fn("get_w") { _, _ ->
            val attrW = el.getAttribute("width")?.toIntOrNull() ?: el.imageNaturalWidth
            jsGlobal.wrap(attrW.toDouble())
        }, fn("set_w") { _, p -> el.setAttribute("width", p.getOrNull(0)?.toInt().toString()); Undefined })
        js.defineProperty("height", fn("get_h") { _, _ ->
            val attrH = el.getAttribute("height")?.toIntOrNull() ?: el.imageNaturalHeight
            jsGlobal.wrap(attrH.toDouble())
        }, fn("set_h") { _, p -> el.setAttribute("height", p.getOrNull(0)?.toInt().toString()); Undefined })

        js.setHidden("decode", fn("decode") { _, _ ->
            if (!el.imageLoadedComplete && !el.getAttribute("src").isNullOrEmpty()) engine.loadImageElementIfNeeded(el)
            val p = JSPromise.Instance(jsGlobal, jsGlobal.Promise)
            if (el.backingImageBuffer != null) p.resolve(Undefined)
            else p.reject(jsGlobal.wrap("Image decode failed"))
            p
        })
    }

    private fun attachMediaApis(el: WebDomElement, js: GenericObject) {
        val playerImpl = if (engine.capabilities.media) engine.ensureVideoPlayerImpl(el) else null

        fun syncSourceAndNotify() {
            if (!engine.capabilities.media) {
                readyStateByMedia[el] = 0
                return
            }
            val (resolved, decodedWav) = engine.loadMediaElementSource(el)
            if (resolved != null) {
                currentSrcByMedia[el] = resolved
                decodedAudioByMedia[el] = decodedWav
                readyStateByMedia[el] = 4
                endedByMedia[el] = false
                scheduler.queueMicrotask {
                    events.dispatchSimpleEvent(el, "loadedmetadata")
                    events.dispatchSimpleEvent(el, "loadeddata")
                    events.dispatchSimpleEvent(el, "canplay")
                    events.dispatchSimpleEvent(el, "canplaythrough")
                }
            }
        }

        playerImpl?.setOnEnded {
            endedByMedia[el] = true
            events.dispatchSimpleEvent(el, "ended")
        }
        playerImpl?.setOnTimeUpdate {
            events.dispatchSimpleEvent(el, "timeupdate")
        }

        js.defineProperty("src", fn("get_src") { _, _ ->
            jsGlobal.wrap(currentSrcByMedia[el] ?: el.getAttribute("src").orEmpty())
        }, fn("set_src") { _, p ->
            el.setAttribute("src", p.getOrNull(0)?.toString().orEmpty())
            syncSourceAndNotify()
            Undefined
        })
        js.defineProperty("currentSrc", fn("get_currentSrc") { _, _ ->
            if (!currentSrcByMedia.containsKey(el)) syncSourceAndNotify()
            jsGlobal.wrap(currentSrcByMedia[el].orEmpty())
        }, jsGlobal.NOOP)

        js.defineProperty("readyState", fn("get_readyState") { _, _ ->
            if (!currentSrcByMedia.containsKey(el)) syncSourceAndNotify()
            jsGlobal.wrap((readyStateByMedia[el] ?: if (currentSrcByMedia[el] != null) 4 else 0).toDouble())
        }, jsGlobal.NOOP)
        js.defineProperty("networkState", fn("get_networkState") { _, _ ->
            jsGlobal.wrap(if (currentSrcByMedia[el] != null) 1.0 else 0.0)
        }, jsGlobal.NOOP)

        js.defineProperty("duration", fn("get_duration") { _, _ ->
            if (!currentSrcByMedia.containsKey(el)) syncSourceAndNotify()
            val d = playerImpl?.getDurationSeconds()?.takeIf { it > 0.0 }
                ?: decodedAudioByMedia[el]?.durationSeconds?.takeIf { it > 0.0 }
                ?: if (currentSrcByMedia[el] != null) 1.0 else Double.NaN
            jsGlobal.wrap(d)
        }, jsGlobal.NOOP)
        js.defineProperty("currentTime", fn("get_ct") { _, _ ->
            jsGlobal.wrap(playerImpl?.getCurrentTimeSeconds() ?: 0.0)
        }, fn("set_ct") { _, p ->
            val sec = (p.getOrNull(0)?.toDouble() ?: 0.0).coerceAtLeast(0.0)
            playerImpl?.seekTo(sec)
            events.dispatchSimpleEvent(el, "seeked")
            events.dispatchSimpleEvent(el, "timeupdate")
            Undefined
        })
        js.defineProperty("paused", fn("get_paused") { _, _ ->
            jsGlobal.wrap(!(playerImpl?.isPlaying() ?: false))
        }, jsGlobal.NOOP)
        js.defineProperty("ended", fn("get_ended") { _, _ ->
            jsGlobal.wrap(endedByMedia[el] ?: false)
        }, jsGlobal.NOOP)
        js.defineProperty("volume", fn("get_volume") { _, _ ->
            jsGlobal.wrap((playerImpl?.getVolume() ?: 1.0f).toDouble())
        }, fn("set_volume") { _, p ->
            playerImpl?.setVolume((p.getOrNull(0)?.toDouble() ?: 1.0).toFloat().coerceIn(0f, 1f))
            events.dispatchSimpleEvent(el, "volumechange")
            Undefined
        })
        js.defineProperty("muted", fn("get_muted") { _, _ ->
            jsGlobal.wrap(el.hasAttribute("muted") || (playerImpl?.isMuted() == true))
        }, fn("set_muted") { _, p ->
            val m = p.getOrNull(0)?.toBool() ?: false
            if (m) el.setAttribute("muted", "") else el.removeAttribute("muted")
            playerImpl?.setMuted(m)
            events.dispatchSimpleEvent(el, "volumechange")
            Undefined
        })
        js.defineProperty("playbackRate", fn("get_pr") { _, _ ->
            jsGlobal.wrap((playerImpl?.getPlaybackSpeed() ?: 1.0f).toDouble())
        }, fn("set_pr") { _, p ->
            playerImpl?.setPlaybackSpeed((p.getOrNull(0)?.toDouble() ?: 1.0).toFloat().coerceIn(0.1f, 16f))
            events.dispatchSimpleEvent(el, "ratechange")
            Undefined
        })
        js.defineProperty("loop", fn("get_loop") { _, _ ->
            jsGlobal.wrap(el.hasAttribute("loop") || (playerImpl?.isLoop() == true))
        }, fn("set_loop") { _, p ->
            val l = p.getOrNull(0)?.toBool() ?: false
            if (l) el.setAttribute("loop", "") else el.removeAttribute("loop")
            playerImpl?.setLoop(l)
            Undefined
        })
        js.defineProperty("autoplay", fn("get_ap") { _, _ -> jsGlobal.wrap(el.hasAttribute("autoplay")) },
            fn("set_ap") { _, p -> if (p.getOrNull(0)?.toBool() == true) el.setAttribute("autoplay", "") else el.removeAttribute("autoplay"); Undefined })
        js.defineProperty("controls", fn("get_ctrls") { _, _ -> jsGlobal.wrap(el.hasAttribute("controls")) },
            fn("set_ctrls") { _, p -> if (p.getOrNull(0)?.toBool() == true) el.setAttribute("controls", "") else el.removeAttribute("controls"); Undefined })
        js.defineProperty("preload", fn("get_preload") { _, _ -> jsGlobal.wrap(el.getAttribute("preload") ?: "auto") },
            fn("set_preload") { _, p -> el.setAttribute("preload", p.getOrNull(0)?.toString().orEmpty()); Undefined })
        js.defineProperty("poster", fn("get_poster") { _, _ -> jsGlobal.wrap(el.getAttribute("poster").orEmpty()) },
            fn("set_poster") { _, p -> el.setAttribute("poster", p.getOrNull(0)?.toString().orEmpty()); Undefined })
        js.defineProperty("videoWidth", fn("get_vw") { _, _ ->
            jsGlobal.wrap((playerImpl?.getNaturalVideoWidth()?.takeIf { it > 0 } ?: 320).toDouble())
        }, jsGlobal.NOOP)
        js.defineProperty("videoHeight", fn("get_vh") { _, _ ->
            jsGlobal.wrap((playerImpl?.getNaturalVideoHeight()?.takeIf { it > 0 } ?: 240).toDouble())
        }, jsGlobal.NOOP)

        js.defineProperty("textTracks", fn("get_textTracks") { _, _ ->
            if (!currentSrcByMedia.containsKey(el)) syncSourceAndNotify()
            val tracks = engine.parsedTextTracksByMediaElement[el].orEmpty()
            val jsTracks = tracks.map { (label, cues) ->
                GenericObject(jsGlobal).apply {
                    set("label", jsGlobal.wrap(label))
                    set("kind", jsGlobal.wrap("subtitles"))
                    set("mode", jsGlobal.wrap("showing"))
                    val jsCues = cues.map { cue ->
                        GenericObject(jsGlobal).apply {
                            set("id", jsGlobal.wrap(cue.id))
                            set("startTime", jsGlobal.wrap(cue.startTime))
                            set("endTime", jsGlobal.wrap(cue.endTime))
                            set("text", jsGlobal.wrap(cue.text))
                        } as BaseObject?
                    }.toTypedArray()
                    set("cues", GenericArray(jsGlobal, jsGlobal.Array, jsCues))
                } as BaseObject?
            }.toTypedArray()
            GenericArray(jsGlobal, jsGlobal.Array, jsTracks)
        }, jsGlobal.NOOP)

        js.setHidden("load", fn("load") { _, _ -> syncSourceAndNotify(); Undefined })
        js.setHidden("canPlayType", fn("canPlayType") { _, p ->
            jsGlobal.wrap(if (engine.capabilities.media) engine.canPlayMimeType(p.getOrNull(0)?.toString().orEmpty()) else "")
        })
        js.setHidden("play", fn("play") { _, _ ->
            if (!currentSrcByMedia.containsKey(el)) syncSourceAndNotify()
            endedByMedia[el] = false
            playerImpl?.play()
            events.dispatchSimpleEvent(el, "play")
            events.dispatchSimpleEvent(el, "playing")
            JSPromise.Instance(jsGlobal, jsGlobal.Promise).apply { resolve(Undefined) }
        })
        js.setHidden("pause", fn("pause") { _, _ ->
            playerImpl?.pause()
            events.dispatchSimpleEvent(el, "pause")
            Undefined
        })
    }

    private fun installAudioContextConstructor() {
        val ctor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                if (!engine.capabilities.webAudio) {
                    throw com.nexustools.njs.runtime.JSError.JavaException(
                        "NotSupportedError",
                        "Web Audio is disabled by runtime capabilities"
                    )
                }
                val sr = params.getOrNull(0)?.get("sampleRate", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toDouble() ?: 44100.0
                val audioEngine = WebAudioEngine(sr)
                engine.audioEngines.add(audioEngine)
                return wrapAudioContext(audioEngine)
            }
            override fun name(): String = "AudioContext"
        }
        jsGlobal.setHidden("AudioContext", ctor)
        jsGlobal.setHidden("webkitAudioContext", ctor)
    }

    private fun wrapAudioContext(audioEngine: WebAudioEngine): GenericObject {
        val nodeToKotlin = LinkedHashMap<BaseObject, WebAudioEngine.Node>()
        val js = GenericObject(jsGlobal)
        val destJs = GenericObject(jsGlobal).apply {
            nodeToKotlin[this] = audioEngine.destination
        }

        fun wireNodeMethods(nodeJs: GenericObject, kNode: WebAudioEngine.Node) {
            nodeToKotlin[nodeJs] = kNode
            nodeJs.setHidden("connect", fn("connect") { _, p ->
                val targetJs = p.getOrNull(0) ?: return@fn Undefined
                val targetNode = nodeToKotlin[targetJs] ?: return@fn targetJs
                kNode.connect(targetNode)
                audioEngine.pumpSamples(1024)
                targetJs
            })
            nodeJs.setHidden("disconnect", fn("disconnect") { _, p ->
                val targetNode = p.getOrNull(0)?.let { nodeToKotlin[it] }
                kNode.disconnect(targetNode)
                Undefined
            })
        }

        fun makeAudioParam(getter: () -> Double, setter: (Double) -> Unit): GenericObject =
            GenericObject(jsGlobal).apply {
                defineProperty("value", fn("get_val") { _, _ -> jsGlobal.wrap(getter()) },
                    fn("set_val") { _, p -> setter(p.getOrNull(0)?.toDouble() ?: 0.0); audioEngine.pumpSamples(1024); Undefined })
                setHidden("setValueAtTime", fn("setValueAtTime") { _this, p ->
                    setter(p.getOrNull(0)?.toDouble() ?: 0.0)
                    audioEngine.pumpSamples(1024)
                    _this
                })
                setHidden("linearRampToValueAtTime", fn("linearRamp") { _this, p ->
                    setter(p.getOrNull(0)?.toDouble() ?: 0.0)
                    _this
                })
            }

        js.defineProperty("state", fn("get_state") { _, _ -> jsGlobal.wrap(audioEngine.state) }, jsGlobal.NOOP)
        js.setHidden("sampleRate", jsGlobal.wrap(audioEngine.sampleRate))
        js.defineProperty("currentTime", fn("get_ct") { _, _ ->
            jsGlobal.wrap(audioEngine.sampleClock.toDouble() / audioEngine.sampleRate)
        }, jsGlobal.NOOP)
        js.setHidden("destination", destJs)

        js.setHidden("resume", fn("resume") { _, _ ->
            audioEngine.state = "running"
            audioEngine.pumpSamples(1024)
            JSPromise.Instance(jsGlobal, jsGlobal.Promise).apply { resolve(Undefined) }
        })
        js.setHidden("suspend", fn("suspend") { _, _ ->
            audioEngine.state = "suspended"
            JSPromise.Instance(jsGlobal, jsGlobal.Promise).apply { resolve(Undefined) }
        })
        js.setHidden("close", fn("close") { _, _ ->
            audioEngine.state = "closed"
            audioEngine.disposeAll()
            JSPromise.Instance(jsGlobal, jsGlobal.Promise).apply { resolve(Undefined) }
        })

        js.setHidden("createOscillator", fn("createOscillator") { _, _ ->
            val osc = audioEngine.createOscillator()
            val oscJs = GenericObject(jsGlobal)
            wireNodeMethods(oscJs, osc)
            oscJs.defineProperty("type", fn("get_type") { _, _ -> jsGlobal.wrap(osc.type) },
                fn("set_type") { _, p -> osc.type = p.getOrNull(0)?.toString() ?: "sine"; Undefined })
            oscJs.setHidden("frequency", makeAudioParam({ osc.frequencyHz }, { osc.frequencyHz = it }))
            oscJs.setHidden("detune", makeAudioParam({ osc.detuneCents }, { osc.detuneCents = it }))
            oscJs.setHidden("start", fn("start") { _, p ->
                osc.start(p.getOrNull(0)?.takeIf { it !== Undefined }?.toDouble() ?: 0.0)
                audioEngine.pumpSamples(2048)
                Undefined
            })
            oscJs.setHidden("stop", fn("stop") { _, p ->
                osc.stop(p.getOrNull(0)?.takeIf { it !== Undefined }?.toDouble() ?: 0.0)
                Undefined
            })
            oscJs
        })

        js.setHidden("createGain", fn("createGain") { _, _ ->
            val gain = audioEngine.createGain()
            val gainJs = GenericObject(jsGlobal)
            wireNodeMethods(gainJs, gain)
            gainJs.setHidden("gain", makeAudioParam({ gain.gainValue }, { gain.gainValue = it }))
            gainJs
        })

        js.setHidden("createAnalyser", fn("createAnalyser") { _, _ ->
            val analyser = audioEngine.createAnalyser()
            val anJs = GenericObject(jsGlobal)
            wireNodeMethods(anJs, analyser)
            anJs.defineProperty("fftSize", fn("get_fft") { _, _ -> jsGlobal.wrap(analyser.fftSize.toDouble()) },
                fn("set_fft") { _, p -> analyser.fftSize = p.getOrNull(0)?.toInt() ?: 2048; Undefined })
            anJs.defineProperty("frequencyBinCount", fn("get_fbc") { _, _ ->
                jsGlobal.wrap(analyser.frequencyBinCount.toDouble())
            }, jsGlobal.NOOP)
            anJs.defineProperty("smoothingTimeConstant", fn("get_stc") { _, _ ->
                jsGlobal.wrap(analyser.smoothingTimeConstant.toDouble())
            }, fn("set_stc") { _, p -> analyser.smoothingTimeConstant = p.getOrNull(0)?.toDouble()?.toFloat() ?: 0.8f; Undefined })
            anJs.setHidden("getByteTimeDomainData", fn("getByteTimeDomainData") { _, p ->
                analyser.ensureFreshSamples()
                val arr = p.getOrNull(0) ?: return@fn Undefined
                val len = arr.get("length").toInt().coerceAtLeast(0)
                val dest = ByteArray(len)
                analyser.analyzer.getByteTimeDomainData(dest)
                for (i in 0 until len) arr.set(i, jsGlobal.wrap((dest[i].toInt() and 0xFF).toDouble()))
                Undefined
            })
            anJs.setHidden("getFloatTimeDomainData", fn("getFloatTimeDomainData") { _, p ->
                analyser.ensureFreshSamples()
                val arr = p.getOrNull(0) ?: return@fn Undefined
                val len = arr.get("length").toInt().coerceAtLeast(0)
                val dest = FloatArray(len)
                analyser.analyzer.getFloatTimeDomainData(dest)
                for (i in 0 until len) arr.set(i, jsGlobal.wrap(dest[i].toDouble()))
                Undefined
            })
            anJs.setHidden("getByteFrequencyData", fn("getByteFrequencyData") { _, p ->
                analyser.ensureFreshSamples()
                val arr = p.getOrNull(0) ?: return@fn Undefined
                val len = arr.get("length").toInt().coerceAtLeast(0)
                val dest = ByteArray(len)
                analyser.analyzer.getByteFrequencyData(dest)
                for (i in 0 until len) arr.set(i, jsGlobal.wrap((dest[i].toInt() and 0xFF).toDouble()))
                Undefined
            })
            anJs.setHidden("getFloatFrequencyData", fn("getFloatFrequencyData") { _, p ->
                analyser.ensureFreshSamples()
                val arr = p.getOrNull(0) ?: return@fn Undefined
                val len = arr.get("length").toInt().coerceAtLeast(0)
                val dest = FloatArray(len)
                analyser.analyzer.getFloatFrequencyData(dest)
                for (i in 0 until len) arr.set(i, jsGlobal.wrap(dest[i].toDouble()))
                Undefined
            })
            anJs
        })

        js.setHidden("createBuffer", fn("createBuffer") { _, p ->
            val ch = p.getOrNull(0)?.toInt() ?: 1
            val len = p.getOrNull(1)?.toInt() ?: 0
            val rate = p.getOrNull(2)?.toInt() ?: audioEngine.sampleRate.toInt()
            wrapAudioBuffer(audioEngine.createBuffer(ch, len, rate))
        })

        js.setHidden("createBufferSource", fn("createBufferSource") { _, _ ->
            val bs = audioEngine.createBufferSource()
            val bsJs = GenericObject(jsGlobal)
            wireNodeMethods(bsJs, bs)
            bsJs.defineProperty("buffer", fn("get_buf") { _, _ -> Null },
                fn("set_buf") { _, p ->
                    val arg0 = p.getOrNull(0)
                    val ch0 = arg0?.get("getChannelData", Scopable.OR_NULL) as? com.nexustools.njs.runtime.BaseFunction
                    if (ch0 != null) {
                        val nCh = (arg0.get("numberOfChannels").toInt()).coerceAtLeast(1)
                        val len = arg0.get("length").toInt().coerceAtLeast(0)
                        val sr = arg0.get("sampleRate").toInt().coerceAtLeast(1)
                        val arrs = Array(nCh) { c ->
                            val jsData = ch0.call(arg0, jsGlobal.wrap(c.toDouble()))
                            FloatArray(len) { idx -> jsData.get(idx).toDouble().toFloat() }
                        }
                        bs.buffer = WebAudioEngine.AudioBufferModel(nCh, len, sr, arrs)
                    }
                    Undefined
                })
            bsJs.defineProperty("loop", fn("get_loop") { _, _ -> jsGlobal.wrap(bs.loop) },
                fn("set_loop") { _, p -> bs.loop = p.getOrNull(0)?.toBool() ?: false; Undefined })
            bsJs.setHidden("start", fn("start") { _, p ->
                bs.start(p.getOrNull(0)?.takeIf { it !== Undefined }?.toDouble() ?: 0.0)
                audioEngine.pumpSamples(2048)
                Undefined
            })
            bsJs.setHidden("stop", fn("stop") { _, _ -> bs.stop(); Undefined })
            bsJs
        })

        js.setHidden("createMediaElementSource", fn("createMediaElementSource") { _, p ->
            val mediaEl = registry.unwrapElement(p.getOrNull(0)) ?: throw IllegalArgumentException("Invalid media element")
            val srcNode = audioEngine.createMediaElementSource(mediaEl)
            srcNode.decodedAudio = decodedAudioByMedia[mediaEl] ?: run {
                engine.loadMediaElementSource(mediaEl).second
            }
            val srcJs = GenericObject(jsGlobal)
            wireNodeMethods(srcJs, srcNode)
            srcJs
        })

        js.setHidden("decodeAudioData", fn("decodeAudioData") { _, p ->
            val abObj = p.getOrNull(0)
            val u8 = abObj?.get("__bytes", Scopable.OR_NULL) as? Uint8Array.Instance
            val rawBytes = u8?.arrayStorage ?: ByteArray(0)
            val decoded = audioEngine.decodeAudioData(rawBytes)
            val prom = JSPromise.Instance(jsGlobal, jsGlobal.Promise)
            if (decoded != null) prom.resolve(wrapAudioBuffer(decoded))
            else prom.reject(jsGlobal.wrap("EncodingError: Unable to decode audio buffer"))
            prom
        })

        return js
    }

    private fun wrapAudioBuffer(model: WebAudioEngine.AudioBufferModel): GenericObject = GenericObject(jsGlobal).apply {
        setHidden("numberOfChannels", jsGlobal.wrap(model.numberOfChannels.toDouble()))
        setHidden("length", jsGlobal.wrap(model.length.toDouble()))
        setHidden("sampleRate", jsGlobal.wrap(model.bufferSampleRate.toDouble()))
        setHidden("duration", jsGlobal.wrap(model.duration))
        setHidden("getChannelData", fn("getChannelData") { _, p ->
            val ch = (p.getOrNull(0)?.toInt() ?: 0).coerceIn(0, model.channelData.lastIndex)
            val f32 = Float32Array.Instance(jsGlobal, jsGlobal.get("Float32Array") as? Float32Array, model.channelData[ch])
            f32
        })
    }

    private fun fn(name: String, body: (BaseObject, Array<out BaseObject>) -> BaseObject): AbstractFunction =
        object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = body(_this, params)
            override fun name(): String = name
        }
}
