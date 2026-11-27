package com.nexustools.genericui.ext.webview.desktop.njs

import com.nexustools.genericui.ext.webview.desktop.dom.WebDomDocument
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomElement
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomNode
import com.nexustools.genericui.ext.webview.desktop.dom.WebDomShadowRoot
import com.nexustools.njs.runtime.AbstractFunction
import com.nexustools.njs.runtime.BaseFunction
import com.nexustools.njs.runtime.BaseObject
import com.nexustools.njs.runtime.GenericObject
import com.nexustools.njs.runtime.Global
import com.nexustools.njs.runtime.JSBoolean
import com.nexustools.njs.runtime.JSNumber
import com.nexustools.njs.runtime.JSString
import com.nexustools.njs.runtime.Null
import com.nexustools.njs.runtime.Scopable
import com.nexustools.njs.runtime.Undefined

/**
 * Full W3C DOM EventTarget propagation (`capture`, `at_target`, `bubble`, `stopPropagation`,
 * `stopImmediatePropagation`, `preventDefault`, `once`, `passive`, `AbortSignal`), Shadow DOM
 * composed boundary retargeting, HTML form control default actions, and standard Web `Event` /
 * `CustomEvent` / `UIEvent` / `MouseEvent` / `PointerEvent` / `WheelEvent` / `KeyboardEvent` /
 * `FocusEvent` / `InputEvent` / `MessageEvent` constructors.
 */
class NjsDomEventSystem(
    private val jsGlobal: Global,
    private val scheduler: NjsEventScheduler
) {
    private data class ListenerEntry(
        val type: String,
        val callback: BaseFunction,
        val capture: Boolean,
        val once: Boolean,
        val passive: Boolean,
        val signal: BaseObject?
    )

    private val listenersByTarget = LinkedHashMap<Any, MutableList<ListenerEntry>>()
    lateinit var windowObject: GenericObject
    var nodeResolver: ((WebDomNode) -> GenericObject)? = null
    var reverseNodeResolver: ((BaseObject) -> WebDomNode?)? = null
    var onFormSubmit: ((WebDomElement) -> Unit)? = null

    fun installConstructors() {
        val eventCtor = object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                val type = params.getOrNull(0)?.toString().orEmpty()
                val initDict = params.getOrNull(1)
                return buildBaseEvent(type, initDict, this)
            }
            override fun name(): String = "Event"
        }
        val customEventCtor = makeSubclassCtor("CustomEvent", eventCtor) { evt, initDict ->
            evt.set("detail", initDict?.get("detail", Scopable.OR_NULL) ?: Null)
        }
        val uiEventCtor = makeSubclassCtor("UIEvent", eventCtor) { evt, initDict ->
            evt.set("detail", initDict?.get("detail", Scopable.OR_NULL) ?: jsGlobal.wrap(0.0))
        }
        val mouseEventCtor = makeSubclassCtor("MouseEvent", uiEventCtor) { evt, initDict ->
            populateMouseProps(evt, initDict)
        }
        val pointerEventCtor = makeSubclassCtor("PointerEvent", mouseEventCtor) { evt, initDict ->
            populateMouseProps(evt, initDict)
            evt.set("pointerId", initDict?.get("pointerId", Scopable.OR_NULL) ?: jsGlobal.wrap(1.0))
            evt.set("pointerType", initDict?.get("pointerType", Scopable.OR_NULL) ?: jsGlobal.wrap("mouse"))
            evt.set("pressure", initDict?.get("pressure", Scopable.OR_NULL) ?: jsGlobal.wrap(0.5))
            evt.set("isPrimary", initDict?.get("isPrimary", Scopable.OR_NULL) ?: jsGlobal.wrap(true))
        }
        val wheelEventCtor = makeSubclassCtor("WheelEvent", mouseEventCtor) { evt, initDict ->
            populateMouseProps(evt, initDict)
            evt.set("deltaX", initDict?.get("deltaX", Scopable.OR_NULL) ?: jsGlobal.wrap(0.0))
            evt.set("deltaY", initDict?.get("deltaY", Scopable.OR_NULL) ?: jsGlobal.wrap(0.0))
            evt.set("deltaZ", initDict?.get("deltaZ", Scopable.OR_NULL) ?: jsGlobal.wrap(0.0))
            evt.set("deltaMode", initDict?.get("deltaMode", Scopable.OR_NULL) ?: jsGlobal.wrap(0.0))
        }
        val keyboardEventCtor = makeSubclassCtor("KeyboardEvent", uiEventCtor) { evt, initDict ->
            evt.set("key", initDict?.get("key", Scopable.OR_NULL) ?: jsGlobal.wrap(""))
            evt.set("code", initDict?.get("code", Scopable.OR_NULL) ?: jsGlobal.wrap(""))
            evt.set("keyCode", initDict?.get("keyCode", Scopable.OR_NULL) ?: jsGlobal.wrap(0.0))
            evt.set("which", initDict?.get("which", Scopable.OR_NULL) ?: evt.get("keyCode"))
            evt.set("shiftKey", initDict?.get("shiftKey", Scopable.OR_NULL) ?: jsGlobal.wrap(false))
            evt.set("ctrlKey", initDict?.get("ctrlKey", Scopable.OR_NULL) ?: jsGlobal.wrap(false))
            evt.set("altKey", initDict?.get("altKey", Scopable.OR_NULL) ?: jsGlobal.wrap(false))
            evt.set("metaKey", initDict?.get("metaKey", Scopable.OR_NULL) ?: jsGlobal.wrap(false))
            evt.set("repeat", initDict?.get("repeat", Scopable.OR_NULL) ?: jsGlobal.wrap(false))
        }
        val focusEventCtor = makeSubclassCtor("FocusEvent", uiEventCtor) { evt, initDict ->
            evt.set("relatedTarget", initDict?.get("relatedTarget", Scopable.OR_NULL) ?: Null)
        }
        val inputEventCtor = makeSubclassCtor("InputEvent", uiEventCtor) { evt, initDict ->
            evt.set("data", initDict?.get("data", Scopable.OR_NULL) ?: Null)
            evt.set("inputType", initDict?.get("inputType", Scopable.OR_NULL) ?: jsGlobal.wrap("insertText"))
        }
        val messageEventCtor = makeSubclassCtor("MessageEvent", eventCtor) { evt, initDict ->
            evt.set("data", initDict?.get("data", Scopable.OR_NULL) ?: Null)
            evt.set("origin", initDict?.get("origin", Scopable.OR_NULL) ?: jsGlobal.wrap(""))
            evt.set("lastEventId", initDict?.get("lastEventId", Scopable.OR_NULL) ?: jsGlobal.wrap(""))
        }
        val errorEventCtor = makeSubclassCtor("ErrorEvent", eventCtor) { evt, initDict ->
            evt.set("message", initDict?.get("message", Scopable.OR_NULL) ?: jsGlobal.wrap(""))
            evt.set("filename", initDict?.get("filename", Scopable.OR_NULL) ?: jsGlobal.wrap(""))
            evt.set("lineno", initDict?.get("lineno", Scopable.OR_NULL) ?: jsGlobal.wrap(0.0))
        }

        jsGlobal.setHidden("Event", eventCtor)
        jsGlobal.setHidden("CustomEvent", customEventCtor)
        jsGlobal.setHidden("UIEvent", uiEventCtor)
        jsGlobal.setHidden("MouseEvent", mouseEventCtor)
        jsGlobal.setHidden("PointerEvent", pointerEventCtor)
        jsGlobal.setHidden("WheelEvent", wheelEventCtor)
        jsGlobal.setHidden("KeyboardEvent", keyboardEventCtor)
        jsGlobal.setHidden("FocusEvent", focusEventCtor)
        jsGlobal.setHidden("InputEvent", inputEventCtor)
        jsGlobal.setHidden("MessageEvent", messageEventCtor)
        jsGlobal.setHidden("ErrorEvent", errorEventCtor)
    }

    fun attachEventTargetMethods(targetKey: Any, jsObj: GenericObject) {
        jsObj.setHidden("addEventListener", fn("addEventListener") { _, params ->
            val type = params.getOrNull(0)?.toString().orEmpty()
            val cb = params.getOrNull(1) as? BaseFunction ?: return@fn Undefined
            val opts = params.getOrNull(2)
            var capture = false
            var once = false
            var passive = false
            var signal: BaseObject? = null
            if (opts != null && opts !== Undefined && opts !== Null) {
                val isOptionsDict = opts is GenericObject &&
                    opts !is JSBoolean.Instance &&
                    opts !is JSNumber.Instance &&
                    opts !is JSString.Instance
                if (isOptionsDict) {
                    capture = opts.get("capture", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toBool() ?: false
                    once = opts.get("once", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toBool() ?: false
                    passive = opts.get("passive", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toBool() ?: false
                    signal = opts.get("signal", Scopable.OR_NULL)?.takeIf { it !== Undefined && it !== Null }
                } else {
                    capture = opts.toBool()
                }
            }
            if (signal != null && signal.get("aborted", Scopable.OR_NULL)?.toBool() == true) {
                return@fn Undefined
            }
            val list = listenersByTarget.getOrPut(targetKey) { ArrayList() }
            if (list.none { it.type == type && it.callback === cb && it.capture == capture }) {
                val entry = ListenerEntry(type, cb, capture, once, passive, signal)
                list.add(entry)
                if (signal != null) {
                    val sigAdd = signal.get("addEventListener", Scopable.OR_NULL) as? BaseFunction
                    sigAdd?.call(signal, jsGlobal.wrap("abort"), fn("removeOnAbort") { _, _ ->
                        listenersByTarget[targetKey]?.remove(entry)
                        Undefined
                    })
                }
            }
            Undefined
        })

        jsObj.setHidden("removeEventListener", fn("removeEventListener") { _, params ->
            val type = params.getOrNull(0)?.toString().orEmpty()
            val cb = params.getOrNull(1) as? BaseFunction ?: return@fn Undefined
            val opts = params.getOrNull(2)
            val isOptionsDict = opts is GenericObject &&
                opts !is JSBoolean.Instance &&
                opts !is JSNumber.Instance &&
                opts !is JSString.Instance
            val capture = when {
                isOptionsDict -> opts.get("capture", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toBool() ?: false
                opts != null && opts !== Undefined && opts !== Null -> opts.toBool()
                else -> false
            }
            listenersByTarget[targetKey]?.removeAll { it.type == type && it.callback === cb && it.capture == capture }
            Undefined
        })

        jsObj.setHidden("dispatchEvent", fn("dispatchEvent") { _, params ->
            val evt = params.getOrNull(0) as? GenericObject ?: return@fn jsGlobal.wrap(false)
            jsGlobal.wrap(dispatchEventObject(targetKey, evt))
        })
    }

    fun clearAll() {
        listenersByTarget.clear()
    }

    fun dispatchSimpleEvent(
        targetKey: Any,
        type: String,
        bubbles: Boolean = false,
        cancelable: Boolean = false,
        composed: Boolean = false,
        extraProps: ((GenericObject) -> Unit)? = null
    ): Boolean {
        val evt = createSimpleEvent(type, bubbles, cancelable, composed)
        extraProps?.invoke(evt)
        return dispatchEventObject(targetKey, evt)
    }

    fun dispatchPointerOrMouse(
        targetKey: Any,
        type: String,
        x: Double,
        y: Double,
        button: Int = 0,
        bubbles: Boolean = true,
        cancelable: Boolean = true
    ): Boolean {
        val ctorName = if (type.startsWith("pointer")) "PointerEvent" else "MouseEvent"
        val evt = createTypedEvent(ctorName, type, bubbles = bubbles, cancelable = cancelable, composed = true)
        evt.set("isTrusted", jsGlobal.wrap(true))
        evt.set("clientX", jsGlobal.wrap(x))
        evt.set("clientY", jsGlobal.wrap(y))
        evt.set("pageX", jsGlobal.wrap(x))
        evt.set("pageY", jsGlobal.wrap(y))
        evt.set("screenX", jsGlobal.wrap(x))
        evt.set("screenY", jsGlobal.wrap(y))
        evt.set("button", jsGlobal.wrap(button.toDouble()))
        evt.set("buttons", jsGlobal.wrap(if (type.endsWith("up")) 0.0 else (1 shl button).toDouble()))
        evt.set("shiftKey", jsGlobal.wrap(false))
        evt.set("ctrlKey", jsGlobal.wrap(false))
        evt.set("altKey", jsGlobal.wrap(false))
        evt.set("metaKey", jsGlobal.wrap(false))
        if (ctorName == "PointerEvent") {
            evt.set("pointerId", jsGlobal.wrap(1.0))
            evt.set("pointerType", jsGlobal.wrap("mouse"))
            evt.set("pressure", jsGlobal.wrap(if (type.endsWith("up")) 0.0 else 0.5))
            evt.set("isPrimary", jsGlobal.wrap(true))
        }
        return dispatchEventObject(targetKey, evt)
    }

    fun dispatchWheel(targetKey: Any, deltaX: Double, deltaY: Double): Boolean {
        val evt = createTypedEvent("WheelEvent", "wheel", bubbles = true, cancelable = true, composed = true)
        evt.set("isTrusted", jsGlobal.wrap(true))
        evt.set("deltaX", jsGlobal.wrap(deltaX))
        evt.set("deltaY", jsGlobal.wrap(deltaY))
        evt.set("deltaZ", jsGlobal.wrap(0.0))
        evt.set("deltaMode", jsGlobal.wrap(0.0))
        return dispatchEventObject(targetKey, evt)
    }

    fun dispatchKeyboard(
        targetKey: Any,
        type: String,
        key: String,
        code: String,
        keyCode: Int,
        shift: Boolean,
        ctrl: Boolean,
        alt: Boolean,
        meta: Boolean
    ): Boolean {
        val evt = createTypedEvent("KeyboardEvent", type, bubbles = true, cancelable = true, composed = true)
        evt.set("isTrusted", jsGlobal.wrap(true))
        evt.set("key", jsGlobal.wrap(key))
        evt.set("code", jsGlobal.wrap(code))
        evt.set("keyCode", jsGlobal.wrap(keyCode.toDouble()))
        evt.set("which", jsGlobal.wrap(keyCode.toDouble()))
        evt.set("shiftKey", jsGlobal.wrap(shift))
        evt.set("ctrlKey", jsGlobal.wrap(ctrl))
        evt.set("altKey", jsGlobal.wrap(alt))
        evt.set("metaKey", jsGlobal.wrap(meta))
        evt.set("repeat", jsGlobal.wrap(false))
        return dispatchEventObject(targetKey, evt)
    }

    fun dispatchFocus(targetKey: Any, type: String, relatedTarget: WebDomElement? = null): Boolean {
        val relatedJs: BaseObject = relatedTarget?.let { nodeResolver?.invoke(it) } ?: Null
        val evt = createTypedEvent("FocusEvent", type, bubbles = false, cancelable = false, composed = true)
        evt.set("isTrusted", jsGlobal.wrap(true))
        evt.set("relatedTarget", relatedJs)
        val ok = dispatchEventObject(targetKey, evt)
        if (type == "focus" || type == "blur") {
            val bubbleType = if (type == "focus") "focusin" else "focusout"
            val bEvt = createTypedEvent("FocusEvent", bubbleType, bubbles = true, cancelable = false, composed = true)
            bEvt.set("isTrusted", jsGlobal.wrap(true))
            bEvt.set("relatedTarget", relatedJs)
            dispatchEventObject(targetKey, bEvt)
        }
        return ok
    }

    fun dispatchInput(targetKey: Any, data: String): Boolean {
        val evt = createTypedEvent("InputEvent", "input", bubbles = true, cancelable = false, composed = true)
        evt.set("isTrusted", jsGlobal.wrap(true))
        evt.set("data", jsGlobal.wrap(data))
        evt.set("inputType", jsGlobal.wrap("insertText"))
        return dispatchEventObject(targetKey, evt)
    }

    private fun createTypedEvent(
        ctorName: String,
        type: String,
        bubbles: Boolean,
        cancelable: Boolean,
        composed: Boolean
    ): GenericObject {
        val initDict = GenericObject(jsGlobal).apply {
            set("bubbles", jsGlobal.wrap(bubbles))
            set("cancelable", jsGlobal.wrap(cancelable))
            set("composed", jsGlobal.wrap(composed))
        }
        return buildBaseEvent(type, initDict, jsGlobal.get(ctorName, Scopable.OR_NULL) as? BaseFunction)
    }

    fun createSimpleEvent(
        type: String,
        bubbles: Boolean = false,
        cancelable: Boolean = false,
        composed: Boolean = false
    ): GenericObject {
        val initDict = GenericObject(jsGlobal).apply {
            set("bubbles", jsGlobal.wrap(bubbles))
            set("cancelable", jsGlobal.wrap(cancelable))
            set("composed", jsGlobal.wrap(composed))
        }
        return buildBaseEvent(type, initDict, jsGlobal.get("Event") as? BaseFunction)
    }

    fun dispatchEventObject(targetKey: Any, eventObj: GenericObject): Boolean {
        val type = eventObj.get("type").toString()
        val bubbles = eventObj.get("bubbles").toBool()
        val cancelable = eventObj.get("cancelable").toBool()
        val composed = eventObj.get("composed").toBool()
        val originalTargetJs = targetToJsObject(targetKey)

        eventObj.setHidden("__stopProp", jsGlobal.wrap(false))
        eventObj.setHidden("__stopImm", jsGlobal.wrap(false))
        eventObj.setHidden("defaultPrevented", jsGlobal.wrap(false))
        eventObj.setHidden("target", originalTargetJs)

        val propagationKeys = buildPropagationPath(targetKey, composed)

        // Phase 1: CAPTURE (window -> target parent)
        eventObj.setHidden("eventPhase", jsGlobal.wrap(1.0))
        for (i in propagationKeys.lastIndex downTo 1) {
            if (isPropagationStopped(eventObj)) break
            val curKey = propagationKeys[i]
            val curJs = targetToJsObject(curKey)
            eventObj.setHidden("target", retargetEventTarget(targetKey, curKey, originalTargetJs))
            eventObj.setHidden("currentTarget", curJs)
            invokeListeners(curKey, curJs, type, eventObj, capturePhase = true, atTarget = false)
        }

        // Phase 2: AT_TARGET
        if (!isPropagationStopped(eventObj)) {
            eventObj.setHidden("eventPhase", jsGlobal.wrap(2.0))
            eventObj.setHidden("target", originalTargetJs)
            eventObj.setHidden("currentTarget", originalTargetJs)
            invokeListeners(targetKey, originalTargetJs, type, eventObj, capturePhase = true, atTarget = true)
        }

        // Phase 3: BUBBLE (target parent -> window)
        if (bubbles && !isPropagationStopped(eventObj)) {
            eventObj.setHidden("eventPhase", jsGlobal.wrap(3.0))
            for (i in 1 until propagationKeys.size) {
                if (isPropagationStopped(eventObj)) break
                val curKey = propagationKeys[i]
                val curJs = targetToJsObject(curKey)
                eventObj.setHidden("target", retargetEventTarget(targetKey, curKey, originalTargetJs))
                eventObj.setHidden("currentTarget", curJs)
                invokeListeners(curKey, curJs, type, eventObj, capturePhase = false, atTarget = false)
            }
        }

        eventObj.setHidden("eventPhase", jsGlobal.wrap(0.0))
        eventObj.setHidden("currentTarget", Null)

        val prevented = cancelable && eventObj.get("defaultPrevented").toBool()
        if (!prevented && targetKey is WebDomElement) {
            performDefaultControlAction(targetKey, type)
        }
        return !prevented
    }

    private fun performDefaultControlAction(el: WebDomElement, type: String) {
        if (type == "click") {
            when (el.lowerTagName) {
                "input" -> when (el.inputType) {
                    "checkbox" -> if (!el.isEffectivelyDisabled()) {
                        el.checked = !el.checked
                        el.isIndeterminateState = false
                        dispatchSimpleEvent(el, "input", bubbles = true, composed = true)
                        dispatchSimpleEvent(el, "change", bubbles = true)
                    }
                    "radio" -> if (!el.isEffectivelyDisabled()) {
                        el.checked = true
                        dispatchSimpleEvent(el, "input", bubbles = true, composed = true)
                        dispatchSimpleEvent(el, "change", bubbles = true)
                    }
                    "submit" -> if (!el.isEffectivelyDisabled()) {
                        el.findOwningForm()?.let { form ->
                            onFormSubmit?.invoke(form) ?: dispatchSimpleEvent(form, "submit", bubbles = true, cancelable = true)
                        }
                    }
                    "reset" -> if (!el.isEffectivelyDisabled()) {
                        el.findOwningForm()?.let { form ->
                            val allowed = dispatchSimpleEvent(form, "reset", bubbles = true, cancelable = true)
                            if (allowed) resetFormControls(form)
                        }
                    }
                }
                "button" -> if (!el.isEffectivelyDisabled() && el.inputType == "submit") {
                    el.findOwningForm()?.let { form ->
                        onFormSubmit?.invoke(form) ?: dispatchSimpleEvent(form, "submit", bubbles = true, cancelable = true)
                    }
                }
                "label" -> {
                    val forId = el.getAttribute("for")
                    val control = if (!forId.isNullOrEmpty()) {
                        el.ownerDocument?.getElementById(forId)
                    } else {
                        findFirstDescendantLabelable(el)
                    }
                    if (control != null && control !== el) {
                        control.isFocusedState = true
                        control.ownerDocument?.activeElement = control
                        dispatchSimpleEvent(control, "click", bubbles = true, cancelable = true, composed = true)
                    }
                }
            }
        }
    }

    private fun findFirstDescendantLabelable(root: WebDomNode): WebDomElement? {
        for (c in root.childNodes) {
            if (c is WebDomElement) {
                if (c.lowerTagName in setOf("input", "select", "textarea", "button")) return c
                findFirstDescendantLabelable(c)?.let { return it }
            }
        }
        return null
    }

    private fun resetFormControls(form: WebDomElement) {
        fun walk(n: WebDomNode) {
            for (c in n.childNodes) {
                if (c is WebDomElement) {
                    if (c.lowerTagName in setOf("input", "select", "textarea")) c.resetFormControl()
                    walk(c)
                }
            }
        }
        walk(form)
    }

    private fun invokeListeners(
        targetKey: Any,
        targetJs: BaseObject,
        type: String,
        eventObj: GenericObject,
        capturePhase: Boolean,
        atTarget: Boolean
    ) {
        val entries = listenersByTarget[targetKey]?.toList().orEmpty()
        for (entry in entries) {
            if (isImmediateStopped(eventObj)) break
            if (entry.type != type) continue
            if (!atTarget && entry.capture != capturePhase) continue
            if (entry.once) {
                listenersByTarget[targetKey]?.remove(entry)
            }
            eventObj.setHidden("__inPassive", jsGlobal.wrap(entry.passive))
            scheduler.invokeJsCallbackSafely(entry.callback, targetJs, eventObj)
        }
        eventObj.setHidden("__inPassive", jsGlobal.wrap(false))
        if (!capturePhase || atTarget) {
            val idlFn = targetJs.get("on$type", Scopable.OR_NULL) as? BaseFunction
            if (idlFn != null && !isImmediateStopped(eventObj)) {
                val res = try {
                    idlFn.call(targetJs, eventObj)
                } catch (t: Throwable) {
                    scheduler.onError?.invoke(t)
                    Undefined
                }
                if (res.toString() == "false") {
                    if (eventObj.get("cancelable").toBool()) {
                        eventObj.setHidden("defaultPrevented", jsGlobal.wrap(true))
                    }
                }
            }
        }
    }

    private fun buildPropagationPath(targetKey: Any, composed: Boolean): List<Any> {
        val path = ArrayList<Any>()
        path.add(targetKey)
        if (targetKey === windowObject) return path
        var cur: WebDomNode? = targetKey as? WebDomNode ?: return path
        var reachedDoc = cur is WebDomDocument
        while (cur != null) {
            val nextNode: WebDomNode? = when {
                cur is WebDomShadowRoot -> if (composed) cur.host else null
                cur.parentNode != null -> cur.parentNode
                else -> null
            }
            if (nextNode != null) {
                path.add(nextNode)
                if (nextNode is WebDomDocument) reachedDoc = true
            }
            cur = nextNode
        }
        if (reachedDoc) {
            path.add(windowObject)
        }
        return path
    }

    private fun retargetEventTarget(originalTarget: Any, listenerTarget: Any, originalJs: BaseObject): BaseObject {
        if (originalTarget !is WebDomNode || listenerTarget !is WebDomNode) return originalJs
        val origRoot = originalTarget.getRootNode(composed = false)
        val listenerRoot = listenerTarget.getRootNode(composed = false)
        if (origRoot === listenerRoot) return originalJs
        var cur: WebDomNode = originalTarget
        while (true) {
            val root = cur.getRootNode(composed = false)
            if (root === listenerRoot || root !is WebDomShadowRoot) {
                return targetToJsObject(cur)
            }
            if (root.contains(listenerTarget)) {
                return targetToJsObject(cur)
            }
            cur = root.host
        }
    }

    private fun targetToJsObject(key: Any): GenericObject = when (key) {
        is GenericObject -> key
        is WebDomNode -> nodeResolver?.invoke(key) ?: GenericObject(jsGlobal)
        else -> windowObject
    }

    private fun isPropagationStopped(evt: GenericObject): Boolean =
        evt.get("__stopProp").toBool() || evt.get("__stopImm").toBool()

    private fun isImmediateStopped(evt: GenericObject): Boolean =
        evt.get("__stopImm").toBool()

    private fun buildBaseEvent(type: String, initDict: BaseObject?, ctor: BaseFunction?): GenericObject {
        val bubbles = initDict?.get("bubbles", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toBool() ?: false
        val cancelable = initDict?.get("cancelable", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toBool() ?: false
        val composed = initDict?.get("composed", Scopable.OR_NULL)?.takeIf { it !== Undefined }?.toBool() ?: false
        val evt = GenericObject(ctor, jsGlobal)
        evt.set("type", jsGlobal.wrap(type))
        evt.set("bubbles", jsGlobal.wrap(bubbles))
        evt.set("cancelable", jsGlobal.wrap(cancelable))
        evt.set("composed", jsGlobal.wrap(composed))
        evt.set("defaultPrevented", jsGlobal.wrap(false))
        evt.set("eventPhase", jsGlobal.wrap(0.0))
        evt.set("target", Null)
        evt.set("currentTarget", Null)
        evt.set("timeStamp", jsGlobal.wrap(scheduler.nowMs()))
        evt.set("isTrusted", jsGlobal.wrap(false))
        evt.setHidden("__stopProp", jsGlobal.wrap(false))
        evt.setHidden("__stopImm", jsGlobal.wrap(false))
        evt.setHidden("__inPassive", jsGlobal.wrap(false))
        evt.setHidden("preventDefault", fn("preventDefault") { _this, _ ->
            if (_this.get("cancelable").toBool() && !_this.get("__inPassive").toBool()) {
                (_this as? GenericObject)?.setHidden("defaultPrevented", jsGlobal.wrap(true))
            }
            Undefined
        })
        evt.setHidden("stopPropagation", fn("stopPropagation") { _this, _ ->
            (_this as? GenericObject)?.setHidden("__stopProp", jsGlobal.wrap(true))
            Undefined
        })
        evt.setHidden("stopImmediatePropagation", fn("stopImmediatePropagation") { _this, _ ->
            (_this as? GenericObject)?.setHidden("__stopProp", jsGlobal.wrap(true))
            (_this as? GenericObject)?.setHidden("__stopImm", jsGlobal.wrap(true))
            Undefined
        })
        evt.setHidden("composedPath", fn("composedPath") { _this, _ ->
            val tgt = _this.get("target")
            val domNode = reverseNodeResolver?.invoke(tgt)
            if (domNode == null) {
                com.nexustools.njs.runtime.GenericArray(jsGlobal)
            } else {
                val path = buildPropagationPath(domNode, _this.get("composed").toBool())
                com.nexustools.njs.runtime.GenericArray(jsGlobal, jsGlobal.Array, path.map { targetToJsObject(it) as BaseObject? }.toTypedArray())
            }
        })
        return evt
    }

    private fun makeSubclassCtor(
        name: String,
        parentCtor: BaseFunction,
        extra: (GenericObject, BaseObject?) -> Unit
    ): AbstractFunction = object : AbstractFunction(jsGlobal) {
        init {
            (prototype() as? GenericObject)?.setPrototypeOf(parentCtor.prototype())
        }
        override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
        override fun construct(vararg params: BaseObject): BaseObject {
            val type = params.getOrNull(0)?.toString().orEmpty()
            val initDict = params.getOrNull(1)
            val evt = buildBaseEvent(type, initDict, this)
            extra(evt, initDict)
            return evt
        }
        override fun name(): String = name
    }

    private fun populateMouseProps(evt: GenericObject, initDict: BaseObject?) {
        evt.set("clientX", initDict?.get("clientX", Scopable.OR_NULL) ?: jsGlobal.wrap(0.0))
        evt.set("clientY", initDict?.get("clientY", Scopable.OR_NULL) ?: jsGlobal.wrap(0.0))
        evt.set("pageX", initDict?.get("pageX", Scopable.OR_NULL) ?: evt.get("clientX"))
        evt.set("pageY", initDict?.get("pageY", Scopable.OR_NULL) ?: evt.get("clientY"))
        evt.set("screenX", initDict?.get("screenX", Scopable.OR_NULL) ?: evt.get("clientX"))
        evt.set("screenY", initDict?.get("screenY", Scopable.OR_NULL) ?: evt.get("clientY"))
        evt.set("button", initDict?.get("button", Scopable.OR_NULL) ?: jsGlobal.wrap(0.0))
        evt.set("buttons", initDict?.get("buttons", Scopable.OR_NULL) ?: jsGlobal.wrap(0.0))
        evt.set("shiftKey", initDict?.get("shiftKey", Scopable.OR_NULL) ?: jsGlobal.wrap(false))
        evt.set("ctrlKey", initDict?.get("ctrlKey", Scopable.OR_NULL) ?: jsGlobal.wrap(false))
        evt.set("altKey", initDict?.get("altKey", Scopable.OR_NULL) ?: jsGlobal.wrap(false))
        evt.set("metaKey", initDict?.get("metaKey", Scopable.OR_NULL) ?: jsGlobal.wrap(false))
    }

    private fun fn(name: String, body: (BaseObject, Array<out BaseObject>) -> BaseObject): AbstractFunction =
        object : AbstractFunction(jsGlobal) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = body(_this, params)
            override fun name(): String = name
        }
}
