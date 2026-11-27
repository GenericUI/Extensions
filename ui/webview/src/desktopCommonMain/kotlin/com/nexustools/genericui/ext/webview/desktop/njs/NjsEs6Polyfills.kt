package com.nexustools.genericui.ext.webview.desktop.njs

import com.nexustools.njs.runtime.AbstractArray
import com.nexustools.njs.runtime.AbstractFunction
import com.nexustools.njs.runtime.BaseFunction
import com.nexustools.njs.runtime.BaseObject
import com.nexustools.njs.runtime.Date
import com.nexustools.njs.runtime.Float32Array
import com.nexustools.njs.runtime.Float64Array
import com.nexustools.njs.runtime.GenericArray
import com.nexustools.njs.runtime.GenericObject
import com.nexustools.njs.runtime.Global
import com.nexustools.njs.runtime.Int16Array
import com.nexustools.njs.runtime.Int32Array
import com.nexustools.njs.runtime.Int8Array
import com.nexustools.njs.runtime.JSBoolean
import com.nexustools.njs.runtime.JSError
import com.nexustools.njs.runtime.JSNumber
import com.nexustools.njs.runtime.JSPromise
import com.nexustools.njs.runtime.JSString
import com.nexustools.njs.runtime.Null
import com.nexustools.njs.runtime.RegEx
import com.nexustools.njs.runtime.Scopable
import com.nexustools.njs.runtime.Symbol
import com.nexustools.njs.runtime.Uint16Array
import com.nexustools.njs.runtime.Uint32Array
import com.nexustools.njs.runtime.Uint8Array
import com.nexustools.njs.runtime.Uint8ClampedArray
import com.nexustools.njs.runtime.Undefined

/**
 * Installs complete ES6+ standard library extensions onto an NJS [Global]:
 * `Map`, `Set`, `WeakMap`, `WeakSet`, `ArrayBuffer`, `DataView`, full `String.prototype` /
 * `Array.prototype` / `Object` / `Promise` (`race`, `allSettled`, `any`, `finally`) / `Number` /
 * `Math`, error constructors (`TypeError`, `RangeError`, `SyntaxError`, `ReferenceError`, `DOMException`),
 * URI encoders, `structuredClone`, `Symbol.toStringTag`/`for`, and `TypedArray` helpers.
 */
object NjsEs6Polyfills {

    fun install(global: Global, scheduler: NjsEventScheduler) {
        installErrorTypes(global)
        installSymbolExtensions(global)
        installStringExtensions(global)
        installArrayExtensions(global)
        installObjectExtensions(global)
        installNumberAndMathExtensions(global)
        installPromiseExtensions(global)
        installUriFunctions(global)
        installMapAndSet(global)
        installArrayBufferAndTypedArrayHelpers(global)
        installStructuredCloneAndMicrotask(global, scheduler)
    }

    private fun installErrorTypes(global: Global) {
        val baseError = global.Error
        for (errType in listOf("TypeError", "RangeError", "SyntaxError", "ReferenceError", "URIError")) {
            val ctor = object : AbstractFunction(global) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
                override fun construct(vararg params: BaseObject): BaseObject {
                    val msg = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString().orEmpty()
                    return JSError.Instance(
                        global.String!!, baseError, global.Symbol.iteratorSymbol, global.Number!!,
                        errType, msg, "$errType: $msg"
                    )
                }
                override fun name(): String = errType
            }
            global.setHidden(errType, ctor)
        }
        val domExceptionCtor = object : AbstractFunction(global) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                val msg = params.getOrNull(0)?.takeIf { it !== Undefined }?.toString().orEmpty()
                val exName = params.getOrNull(1)?.takeIf { it !== Undefined }?.toString() ?: "Error"
                return JSError.Instance(
                    global.String!!, baseError, global.Symbol.iteratorSymbol, global.Number!!,
                    exName, msg, "$exName: $msg"
                ).apply {
                    set("name", global.wrap(exName))
                    set("message", global.wrap(msg))
                }
            }
            override fun name(): String = "DOMException"
        }
        global.setHidden("DOMException", domExceptionCtor)
    }

    private fun installSymbolExtensions(global: Global) {
        val sym = global.Symbol
        val registry = LinkedHashMap<String, Symbol.Instance>()
        if (sym.get("toStringTag", Scopable.OR_NULL) == null) {
            sym.setHidden("toStringTag", sym.create("Symbol.toStringTag"))
        }
        if (sym.get("species", Scopable.OR_NULL) == null) {
            sym.setHidden("species", sym.create("Symbol.species"))
        }
        sym.setHidden("for", object : AbstractFunction(global) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val key = params.getOrNull(0)?.toString().orEmpty()
                return registry.getOrPut(key) { sym.create(key) }
            }
        })
        sym.setHidden("keyFor", object : AbstractFunction(global) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject {
                val target = params.getOrNull(0)
                val match = registry.entries.firstOrNull { it.value === target }?.key
                return if (match != null) global.wrap(match) else Undefined
            }
        })
    }

    private fun installStringExtensions(global: Global) {
        val proto = global.String!!.prototype() as? GenericObject ?: return
        proto.setHidden("startsWith", fn(global, "startsWith") { _this, params ->
            val s = _this.toString()
            val prefix = params.getOrNull(0)?.toString().orEmpty()
            val pos = params.getOrNull(1)?.takeIf { it !== Undefined }?.toInt()?.coerceAtLeast(0) ?: 0
            global.wrap(s.regionMatches(pos, prefix, 0, prefix.length))
        })
        proto.setHidden("endsWith", fn(global, "endsWith") { _this, params ->
            val s = _this.toString()
            val suffix = params.getOrNull(0)?.toString().orEmpty()
            val endLen = params.getOrNull(1)?.takeIf { it !== Undefined }?.toInt()?.coerceIn(0, s.length) ?: s.length
            global.wrap(endLen >= suffix.length && s.regionMatches(endLen - suffix.length, suffix, 0, suffix.length))
        })
        proto.setHidden("includes", fn(global, "includes") { _this, params ->
            val s = _this.toString()
            val needle = params.getOrNull(0)?.toString().orEmpty()
            val pos = params.getOrNull(1)?.takeIf { it !== Undefined }?.toInt()?.coerceAtLeast(0) ?: 0
            global.wrap(s.indexOf(needle, pos) >= 0)
        })
        proto.setHidden("repeat", fn(global, "repeat") { _this, params ->
            val count = (params.getOrNull(0)?.toInt() ?: 0).coerceAtLeast(0)
            global.wrap(_this.toString().repeat(count))
        })
        proto.setHidden("padStart", fn(global, "padStart") { _this, params ->
            val s = _this.toString()
            val targetLen = params.getOrNull(0)?.toInt() ?: 0
            val pad = params.getOrNull(1)?.takeIf { it !== Undefined }?.toString()?.ifEmpty { " " } ?: " "
            if (s.length >= targetLen) global.wrap(s)
            else {
                val needed = targetLen - s.length
                val prefix = buildString {
                    while (length < needed) append(pad)
                }.substring(0, needed)
                global.wrap(prefix + s)
            }
        })
        proto.setHidden("padEnd", fn(global, "padEnd") { _this, params ->
            val s = _this.toString()
            val targetLen = params.getOrNull(0)?.toInt() ?: 0
            val pad = params.getOrNull(1)?.takeIf { it !== Undefined }?.toString()?.ifEmpty { " " } ?: " "
            if (s.length >= targetLen) global.wrap(s)
            else {
                val needed = targetLen - s.length
                val suffix = buildString {
                    while (length < needed) append(pad)
                }.substring(0, needed)
                global.wrap(s + suffix)
            }
        })
        proto.setHidden("trimStart", fn(global, "trimStart") { _this, _ -> global.wrap(_this.toString().trimStart()) })
        proto.setHidden("trimLeft", fn(global, "trimLeft") { _this, _ -> global.wrap(_this.toString().trimStart()) })
        proto.setHidden("trimEnd", fn(global, "trimEnd") { _this, _ -> global.wrap(_this.toString().trimEnd()) })
        proto.setHidden("trimRight", fn(global, "trimRight") { _this, _ -> global.wrap(_this.toString().trimEnd()) })
        proto.setHidden("charAt", fn(global, "charAt") { _this, params ->
            val s = _this.toString()
            val idx = params.getOrNull(0)?.toInt() ?: 0
            global.wrap(if (idx in s.indices) s[idx].toString() else "")
        })
        proto.setHidden("charCodeAt", fn(global, "charCodeAt") { _this, params ->
            val s = _this.toString()
            val idx = params.getOrNull(0)?.toInt() ?: 0
            if (idx in s.indices) global.wrap(s[idx].code.toDouble()) else global.NaN
        })
        proto.setHidden("codePointAt", fn(global, "codePointAt") { _this, params ->
            val s = _this.toString()
            val idx = params.getOrNull(0)?.toInt() ?: 0
            if (idx in s.indices) global.wrap(s[idx].code.toDouble()) else Undefined
        })
        proto.setHidden("lastIndexOf", fn(global, "lastIndexOf") { _this, params ->
            val s = _this.toString()
            val needle = params.getOrNull(0)?.toString().orEmpty()
            val fromIdx = params.getOrNull(1)?.takeIf { it !== Undefined }?.toInt() ?: s.length
            global.wrap(s.lastIndexOf(needle, fromIdx.coerceIn(0, s.length)).toDouble())
        })
        proto.setHidden("replaceAll", fn(global, "replaceAll") { _this, params ->
            val s = _this.toString()
            val search = params.getOrNull(0)?.toString().orEmpty()
            val rep = params.getOrNull(1)?.toString().orEmpty()
            global.wrap(if (search.isEmpty()) s else s.replace(search, rep))
        })
        proto.setHidden("concat", fn(global, "concat") { _this, params ->
            val sb = StringBuilder(_this.toString())
            for (p in params) sb.append(p.toString())
            global.wrap(sb.toString())
        })
    }

    private fun installArrayExtensions(global: Global) {
        val proto = global.Array.prototype() as? GenericObject ?: return

        proto.setHidden("includes", fn(global, "includes") { _this, params ->
            val target = params.getOrNull(0) ?: Undefined
            val len = _this.get("length").toInt()
            val from = (params.getOrNull(1)?.takeIf { it !== Undefined }?.toInt() ?: 0).coerceAtLeast(0)
            for (i in from until len) {
                if (sameValueZero(_this.get(i), target)) return@fn global.wrap(true)
            }
            global.wrap(false)
        })
        proto.setHidden("indexOf", fn(global, "indexOf") { _this, params ->
            val target = params.getOrNull(0) ?: Undefined
            val len = _this.get("length").toInt()
            val from = (params.getOrNull(1)?.takeIf { it !== Undefined }?.toInt() ?: 0).coerceAtLeast(0)
            for (i in from until len) {
                if (strictEq(_this.get(i), target)) return@fn global.wrap(i.toDouble())
            }
            global.wrap(-1.0)
        })
        proto.setHidden("lastIndexOf", fn(global, "lastIndexOf") { _this, params ->
            val target = params.getOrNull(0) ?: Undefined
            val len = _this.get("length").toInt()
            val from = (params.getOrNull(1)?.takeIf { it !== Undefined }?.toInt() ?: (len - 1)).coerceAtMost(len - 1)
            for (i in from downTo 0) {
                if (strictEq(_this.get(i), target)) return@fn global.wrap(i.toDouble())
            }
            global.wrap(-1.0)
        })
        proto.setHidden("filter", fn(global, "filter") { _this, params ->
            val cb = params.getOrNull(0) as? BaseFunction ?: return@fn GenericArray(global)
            val thisArg = params.getOrNull(1) ?: Undefined
            val len = _this.get("length").toInt()
            val out = ArrayList<BaseObject?>()
            for (i in 0 until len) {
                val item = _this.get(i)
                if (cb.call(thisArg, item, global.wrap(i.toDouble()), _this).toBool()) {
                    out.add(item)
                }
            }
            GenericArray(global, global.Array, out.toTypedArray())
        })
        proto.setHidden("find", fn(global, "find") { _this, params ->
            val cb = params.getOrNull(0) as? BaseFunction ?: return@fn Undefined
            val thisArg = params.getOrNull(1) ?: Undefined
            val len = _this.get("length").toInt()
            for (i in 0 until len) {
                val item = _this.get(i)
                if (cb.call(thisArg, item, global.wrap(i.toDouble()), _this).toBool()) return@fn item
            }
            Undefined
        })
        proto.setHidden("findIndex", fn(global, "findIndex") { _this, params ->
            val cb = params.getOrNull(0) as? BaseFunction ?: return@fn global.wrap(-1.0)
            val thisArg = params.getOrNull(1) ?: Undefined
            val len = _this.get("length").toInt()
            for (i in 0 until len) {
                val item = _this.get(i)
                if (cb.call(thisArg, item, global.wrap(i.toDouble()), _this).toBool()) return@fn global.wrap(i.toDouble())
            }
            global.wrap(-1.0)
        })
        proto.setHidden("some", fn(global, "some") { _this, params ->
            val cb = params.getOrNull(0) as? BaseFunction ?: return@fn global.wrap(false)
            val thisArg = params.getOrNull(1) ?: Undefined
            val len = _this.get("length").toInt()
            for (i in 0 until len) {
                if (cb.call(thisArg, _this.get(i), global.wrap(i.toDouble()), _this).toBool()) return@fn global.wrap(true)
            }
            global.wrap(false)
        })
        proto.setHidden("every", fn(global, "every") { _this, params ->
            val cb = params.getOrNull(0) as? BaseFunction ?: return@fn global.wrap(true)
            val thisArg = params.getOrNull(1) ?: Undefined
            val len = _this.get("length").toInt()
            for (i in 0 until len) {
                if (!cb.call(thisArg, _this.get(i), global.wrap(i.toDouble()), _this).toBool()) return@fn global.wrap(false)
            }
            global.wrap(true)
        })
        proto.setHidden("reduce", fn(global, "reduce") { _this, params ->
            val cb = params.getOrNull(0) as? BaseFunction ?: return@fn Undefined
            val len = _this.get("length").toInt()
            var start = 0
            var acc = if (params.size >= 2) params[1] else {
                if (len == 0) return@fn Undefined
                start = 1
                _this.get(0)
            }
            for (i in start until len) {
                acc = cb.call(Undefined, acc, _this.get(i), global.wrap(i.toDouble()), _this)
            }
            acc
        })
        proto.setHidden("concat", fn(global, "concat") { _this, params ->
            val out = ArrayList<BaseObject?>()
            val len = _this.get("length").toInt()
            for (i in 0 until len) out.add(_this.get(i))
            for (arg in params) {
                if (arg is GenericArray || arg is AbstractArray<*>) {
                    val al = arg.get("length").toInt()
                    for (j in 0 until al) out.add(arg.get(j))
                } else {
                    out.add(arg)
                }
            }
            GenericArray(global, global.Array, out.toTypedArray())
        })
        proto.setHidden("reverse", fn(global, "reverse") { _this, _ ->
            val len = _this.get("length").toInt()
            for (i in 0 until len / 2) {
                val a = _this.get(i)
                val b = _this.get(len - 1 - i)
                _this.set(i, b)
                _this.set(len - 1 - i, a)
            }
            _this
        })
        proto.setHidden("shift", fn(global, "shift") { _this, _ ->
            val len = _this.get("length").toInt()
            if (len <= 0) return@fn Undefined
            val first = _this.get(0)
            for (i in 0 until len - 1) {
                _this.set(i, _this.get(i + 1))
            }
            _this.delete(len - 1)
            _this.set("length", global.wrap((len - 1).toDouble()), _this)
            first
        })
        proto.setHidden("unshift", fn(global, "unshift") { _this, params ->
            val len = _this.get("length").toInt()
            val shiftBy = params.size
            for (i in len - 1 downTo 0) {
                _this.set(i + shiftBy, _this.get(i))
            }
            for (i in params.indices) {
                _this.set(i, params[i])
            }
            global.wrap((len + shiftBy).toDouble())
        })
        proto.setHidden("splice", fn(global, "splice") { _this, params ->
            val len = _this.get("length").toInt()
            val rawStart = params.getOrNull(0)?.toInt() ?: 0
            val start = if (rawStart < 0) (len + rawStart).coerceAtLeast(0) else rawStart.coerceAtMost(len)
            val delCount = (params.getOrNull(1)?.takeIf { it !== Undefined }?.toInt() ?: (len - start)).coerceIn(0, len - start)
            val removed = ArrayList<BaseObject?>(delCount)
            for (i in 0 until delCount) removed.add(_this.get(start + i))
            val tail = ArrayList<BaseObject>()
            for (i in start + delCount until len) tail.add(_this.get(i))
            val items = if (params.size > 2) params.drop(2) else emptyList()
            var write = start
            for (item in items) _this.set(write++, item)
            for (t in tail) _this.set(write++, t)
            for (k in write until len) _this.delete(k)
            _this.set("length", global.wrap(write.toDouble()), _this)
            GenericArray(global, global.Array, removed.toTypedArray())
        })
        proto.setHidden("flat", fn(global, "flat") { _this, params ->
            val depth = params.getOrNull(0)?.takeIf { it !== Undefined }?.toInt() ?: 1
            val out = ArrayList<BaseObject?>()
            fun flatten(arr: BaseObject, d: Int) {
                val l = arr.get("length").toInt()
                for (i in 0 until l) {
                    val el = arr.get(i)
                    if (d > 0 && (el is GenericArray || el is AbstractArray<*>)) {
                        flatten(el, d - 1)
                    } else {
                        out.add(el)
                    }
                }
            }
            flatten(_this, depth)
            GenericArray(global, global.Array, out.toTypedArray())
        })
        proto.setHidden("entries", fn(global, "entries") { _this, _ ->
            val len = _this.get("length").toInt()
            val list = Array(len) { idx ->
                GenericArray(global, global.Array, arrayOf(global.wrap(idx.toDouble()), _this.get(idx))) as BaseObject?
            }
            GenericArray(global, global.Array, list)
        })
        proto.setHidden("keys", fn(global, "keys") { _this, _ ->
            val len = _this.get("length").toInt()
            val list = Array(len) { idx -> global.wrap(idx.toDouble()) as BaseObject? }
            GenericArray(global, global.Array, list)
        })
        proto.setHidden("values", fn(global, "values") { _this, _ ->
            val len = _this.get("length").toInt()
            val list = Array(len) { idx -> _this.get(idx) as BaseObject? }
            GenericArray(global, global.Array, list)
        })
        proto.setHidden(global.Symbol.iteratorSymbol.toString(), proto.get("values"))
    }

    private fun installObjectExtensions(global: Global) {
        val objCtor = global.Object
        objCtor.setHidden("values", fn(global, "values") { _, params ->
            val target = params.getOrNull(0) ?: return@fn GenericArray(global)
            val vals = target.keys().map { target.get(it) as BaseObject? }.toTypedArray()
            GenericArray(global, global.Array, vals)
        })
        objCtor.setHidden("entries", fn(global, "entries") { _, params ->
            val target = params.getOrNull(0) ?: return@fn GenericArray(global)
            val pairs = target.keys().map { k ->
                GenericArray(global, global.Array, arrayOf(global.wrap(k), target.get(k))) as BaseObject?
            }.toTypedArray()
            GenericArray(global, global.Array, pairs)
        })
        objCtor.setHidden("fromEntries", fn(global, "fromEntries") { _, params ->
            val iterable = params.getOrNull(0) ?: return@fn GenericObject(global)
            val res = GenericObject(global)
            val len = iterable.get("length").toInt()
            for (i in 0 until len) {
                val entry = iterable.get(i)
                val k = entry.get(0).toString()
                val v = entry.get(1)
                res.set(k, v)
            }
            res
        })
        objCtor.setHidden("hasOwn", fn(global, "hasOwn") { _, params ->
            val obj = params.getOrNull(0) ?: return@fn global.wrap(false)
            val key = params.getOrNull(1)?.toString().orEmpty()
            global.wrap(obj.hasOwnProperty(key))
        })
        objCtor.setHidden("is", fn(global, "is") { _, params ->
            val a = params.getOrNull(0) ?: Undefined
            val b = params.getOrNull(1) ?: Undefined
            if (a is JSNumber.Instance && b is JSNumber.Instance && a.value.isNaN() && b.value.isNaN()) {
                global.wrap(true)
            } else {
                global.wrap(strictEq(a, b))
            }
        })
        objCtor.setHidden("freeze", fn(global, "freeze") { _, params ->
            params.getOrNull(0) ?: Undefined
        })
        objCtor.setHidden("defineProperties", fn(global, "defineProperties") { _, params ->
            val obj = params.getOrNull(0) ?: return@fn Undefined
            val props = params.getOrNull(1) ?: return@fn obj
            val definePropFn = objCtor.get("defineProperty") as? BaseFunction
            for (k in props.keys()) {
                definePropFn?.call(objCtor, obj, global.wrap(k), props.get(k))
            }
            obj
        })
    }

    private fun installNumberAndMathExtensions(global: Global) {
        val numCtor = global.Number ?: return
        numCtor.setHidden("isFinite", fn(global, "isFinite") { _, params ->
            val v = params.getOrNull(0)
            global.wrap(v is JSNumber.Instance && v.value.isFinite())
        })
        numCtor.setHidden("isNaN", fn(global, "isNaN") { _, params ->
            val v = params.getOrNull(0)
            global.wrap(v is JSNumber.Instance && v.value.isNaN())
        })
        numCtor.setHidden("isInteger", fn(global, "isInteger") { _, params ->
            val v = params.getOrNull(0)
            global.wrap(v is JSNumber.Instance && v.value.isFinite() && v.value == kotlin.math.floor(v.value))
        })
        numCtor.setHidden("parseInt", global.get("parseInt"))
        numCtor.setHidden("parseFloat", global.get("parseFloat"))
        numCtor.setHidden("MAX_SAFE_INTEGER", global.wrap(9007199254740991.0))
        numCtor.setHidden("MIN_SAFE_INTEGER", global.wrap(-9007199254740991.0))
        numCtor.setHidden("EPSILON", global.wrap(2.220446049250313E-16))

        val mathObj = global.get("Math", Scopable.OR_NULL)
        if (mathObj != null) {
            mathObj.set("hypot", fn(global, "hypot") { _, params ->
                var sum = 0.0
                for (p in params) {
                    val d = p.toDouble()
                    sum += d * d
                }
                global.wrap(kotlin.math.sqrt(sum))
            })
            mathObj.set("trunc", fn(global, "trunc") { _, params ->
                val d = params.getOrNull(0)?.toDouble() ?: Double.NaN
                global.wrap(kotlin.math.truncate(d))
            })
            mathObj.set("sign", fn(global, "sign") { _, params ->
                val d = params.getOrNull(0)?.toDouble() ?: Double.NaN
                global.wrap(kotlin.math.sign(d))
            })
        }
    }

    private fun installPromiseExtensions(global: Global) {
        val promiseCtor = global.Promise
        val proto = promiseCtor.prototype() as? GenericObject
        proto?.setHidden("finally", fn(global, "finally") { _this, params ->
            val onFinally = params.getOrNull(0) as? BaseFunction
            if (_this is JSPromise.Instance && onFinally != null) {
                _this.then(fn(global, "onFinallyOk") { _, args ->
                    onFinally.call(Undefined)
                    args.getOrNull(0) ?: Undefined
                }, fn(global, "onFinallyErr") { _, args ->
                    onFinally.call(Undefined)
                    throw JSError.Thrown(args.getOrNull(0) ?: Undefined)
                })
            } else {
                _this
            }
        })
        promiseCtor.setHidden("race", fn(global, "Promise_race") { _, params ->
            val listObj = params.getOrNull(0) ?: GenericArray(global)
            val inst = JSPromise.Instance(global, promiseCtor)
            val len = listObj.get("length").toInt()
            for (i in 0 until len) {
                val p = listObj.get(i)
                if (p is JSPromise.Instance) {
                    p.then(fn(global, "ok") { _, a -> inst.resolve(a.getOrNull(0) ?: Undefined); Undefined },
                        fn(global, "err") { _, a -> inst.reject(a.getOrNull(0) ?: Undefined); Undefined })
                } else {
                    inst.resolve(p)
                    break
                }
            }
            inst
        })
        promiseCtor.setHidden("allSettled", fn(global, "Promise_allSettled") { _, params ->
            val listObj = params.getOrNull(0) ?: GenericArray(global)
            val len = listObj.get("length").toInt()
            val master = JSPromise.Instance(global, promiseCtor)
            if (len == 0) {
                master.resolve(GenericArray(global))
                return@fn master
            }
            val out = arrayOfNulls<BaseObject>(len)
            var done = 0
            for (i in 0 until len) {
                val item = listObj.get(i)
                val p = if (item is JSPromise.Instance) item else JSPromise.Instance(global, promiseCtor).apply { resolve(item) }
                val idx = i
                p.then(fn(global, "fulfilled") { _, args ->
                    out[idx] = GenericObject(global).apply {
                        set("status", global.wrap("fulfilled"))
                        set("value", args.getOrNull(0) ?: Undefined)
                    }
                    if (++done == len) master.resolve(GenericArray(global, global.Array, out))
                    Undefined
                }, fn(global, "rejected") { _, args ->
                    out[idx] = GenericObject(global).apply {
                        set("status", global.wrap("rejected"))
                        set("reason", args.getOrNull(0) ?: Undefined)
                    }
                    if (++done == len) master.resolve(GenericArray(global, global.Array, out))
                    Undefined
                })
            }
            master
        })
    }

    private fun installUriFunctions(global: Global) {
        global.setHidden("encodeURIComponent", fn(global, "encodeURIComponent") { _, params ->
            global.wrap(percentEncode(params.getOrNull(0)?.toString().orEmpty(), keepUriReserved = false))
        })
        global.setHidden("encodeURI", fn(global, "encodeURI") { _, params ->
            global.wrap(percentEncode(params.getOrNull(0)?.toString().orEmpty(), keepUriReserved = true))
        })
        global.setHidden("decodeURIComponent", fn(global, "decodeURIComponent") { _, params ->
            global.wrap(percentDecode(params.getOrNull(0)?.toString().orEmpty()))
        })
        global.setHidden("decodeURI", fn(global, "decodeURI") { _, params ->
            global.wrap(percentDecode(params.getOrNull(0)?.toString().orEmpty()))
        })
    }

    private fun installMapAndSet(global: Global) {
        val mapCtor = object : AbstractFunction(global) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                val entries = ArrayList<Pair<BaseObject, BaseObject>>()
                val inst = GenericObject(this, global)

                fun findIndex(key: BaseObject): Int {
                    for (i in entries.indices) {
                        if (sameValueZero(entries[i].first, key)) return i
                    }
                    return -1
                }

                inst.defineProperty("size", fn(global, "get_size") { _, _ -> global.wrap(entries.size.toDouble()) }, global.NOOP)
                inst.setHidden("set", fn(global, "set") { _, args ->
                    val k = args.getOrNull(0) ?: Undefined
                    val v = args.getOrNull(1) ?: Undefined
                    val idx = findIndex(k)
                    if (idx >= 0) entries[idx] = entries[idx].first to v else entries.add(k to v)
                    inst
                })
                inst.setHidden("get", fn(global, "get") { _, args ->
                    val idx = findIndex(args.getOrNull(0) ?: Undefined)
                    if (idx >= 0) entries[idx].second else Undefined
                })
                inst.setHidden("has", fn(global, "has") { _, args ->
                    global.wrap(findIndex(args.getOrNull(0) ?: Undefined) >= 0)
                })
                inst.setHidden("delete", fn(global, "delete") { _, args ->
                    val idx = findIndex(args.getOrNull(0) ?: Undefined)
                    if (idx >= 0) {
                        entries.removeAt(idx)
                        global.wrap(true)
                    } else global.wrap(false)
                })
                inst.setHidden("clear", fn(global, "clear") { _, _ -> entries.clear(); Undefined })
                inst.setHidden("forEach", fn(global, "forEach") { _, args ->
                    val cb = args.getOrNull(0) as? BaseFunction ?: return@fn Undefined
                    val thisArg = args.getOrNull(1) ?: Undefined
                    for ((k, v) in entries.toList()) cb.call(thisArg, v, k, inst)
                    Undefined
                })
                inst.setHidden("keys", fn(global, "keys") { _, _ ->
                    GenericArray(global, global.Array, entries.map { it.first as BaseObject? }.toTypedArray())
                })
                inst.setHidden("values", fn(global, "values") { _, _ ->
                    GenericArray(global, global.Array, entries.map { it.second as BaseObject? }.toTypedArray())
                })
                inst.setHidden("entries", fn(global, "entries") { _, _ ->
                    GenericArray(global, global.Array, entries.map {
                        GenericArray(global, global.Array, arrayOf(it.first, it.second)) as BaseObject?
                    }.toTypedArray())
                })

                val initArg = params.getOrNull(0)
                if (initArg != null && initArg !== Undefined && initArg !== Null) {
                    val len = initArg.get("length").toInt()
                    for (i in 0 until len) {
                        val pair = initArg.get(i)
                        val k = pair.get(0)
                        val v = pair.get(1)
                        val idx = findIndex(k)
                        if (idx >= 0) entries[idx] = entries[idx].first to v else entries.add(k to v)
                    }
                }
                return inst
            }
            override fun name(): String = "Map"
        }
        global.setHidden("Map", mapCtor)

        val setCtor = object : AbstractFunction(global) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                val items = ArrayList<BaseObject>()
                val inst = GenericObject(this, global)

                fun containsItem(v: BaseObject): Boolean = items.any { sameValueZero(it, v) }

                inst.defineProperty("size", fn(global, "get_size") { _, _ -> global.wrap(items.size.toDouble()) }, global.NOOP)
                inst.setHidden("add", fn(global, "add") { _, args ->
                    val v = args.getOrNull(0) ?: Undefined
                    if (!containsItem(v)) items.add(v)
                    inst
                })
                inst.setHidden("has", fn(global, "has") { _, args ->
                    global.wrap(containsItem(args.getOrNull(0) ?: Undefined))
                })
                inst.setHidden("delete", fn(global, "delete") { _, args ->
                    val v = args.getOrNull(0) ?: Undefined
                    val idx = items.indexOfFirst { sameValueZero(it, v) }
                    if (idx >= 0) {
                        items.removeAt(idx)
                        global.wrap(true)
                    } else global.wrap(false)
                })
                inst.setHidden("clear", fn(global, "clear") { _, _ -> items.clear(); Undefined })
                inst.setHidden("forEach", fn(global, "forEach") { _, args ->
                    val cb = args.getOrNull(0) as? BaseFunction ?: return@fn Undefined
                    val thisArg = args.getOrNull(1) ?: Undefined
                    for (v in items.toList()) cb.call(thisArg, v, v, inst)
                    Undefined
                })
                inst.setHidden("values", fn(global, "values") { _, _ ->
                    GenericArray(global, global.Array, items.toTypedArray<BaseObject?>())
                })
                inst.setHidden("keys", inst.get("values"))
                val entriesFn = fn(global, "entries") { _, _ ->
                    GenericArray(global, global.Array, items.map {
                        GenericArray(global, global.Array, arrayOf(it, it)) as BaseObject?
                    }.toTypedArray())
                }
                inst.setHidden("entries", entriesFn)

                val initArg = params.getOrNull(0)
                if (initArg != null && initArg !== Undefined && initArg !== Null) {
                    val len = initArg.get("length").toInt()
                    for (i in 0 until len) {
                        val v = initArg.get(i)
                        if (!containsItem(v)) items.add(v)
                    }
                }
                return inst
            }
            override fun name(): String = "Set"
        }
        global.setHidden("Set", setCtor)

        // Make Array.from accept Map / Set (has .values()) cleanly
        global.Array.setHidden("from", fn(global, "Array_from") { _, params ->
            val src = params.getOrNull(0) ?: return@fn GenericArray(global)
            val mapFn = params.getOrNull(1) as? BaseFunction
            val thisArg = params.getOrNull(2) ?: Undefined
            val valuesMethod = src.get("values", Scopable.OR_NULL) as? BaseFunction
            val arrayLike = if (src !is GenericArray && src !is AbstractArray<*> && valuesMethod != null) {
                valuesMethod.call(src)
            } else {
                src
            }
            val len = arrayLike.get("length").toInt()
            val arr = arrayOfNulls<BaseObject>(len)
            for (i in 0 until len) {
                val item = arrayLike.get(i)
                arr[i] = if (mapFn != null) mapFn.call(thisArg, item, global.wrap(i.toDouble())) else item
            }
            GenericArray(global, global.Array, arr)
        })
    }

    private fun installArrayBufferAndTypedArrayHelpers(global: Global) {
        val rawUint8Ctor = global.get("Uint8Array", Scopable.OR_NULL) as? Uint8Array
        val abCtor = object : AbstractFunction(global) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                val byteLen = (params.getOrNull(0)?.toInt() ?: 0).coerceAtLeast(0)
                val backing = ByteArray(byteLen)
                return GenericObject(this, global).apply {
                    setHidden("byteLength", global.wrap(byteLen.toDouble()))
                    setHidden("__bytes", Uint8Array.Instance(global, rawUint8Ctor, backing))
                    setHidden("slice", fn(global, "slice") { _, args ->
                        val start = (args.getOrNull(0)?.toInt() ?: 0).let { if (it < 0) byteLen + it else it }.coerceIn(0, byteLen)
                        val end = (args.getOrNull(1)?.takeIf { it !== Undefined }?.toInt() ?: byteLen).let { if (it < 0) byteLen + it else it }.coerceIn(start, byteLen)
                        val sub = backing.copyOfRange(start, end)
                        val outObj = construct(global.wrap(sub.size.toDouble())) as GenericObject
                        val u = outObj.get("__bytes") as? Uint8Array.Instance
                        sub.copyInto(u?.arrayStorage ?: ByteArray(0))
                        outObj
                    })
                }
            }
            override fun name(): String = "ArrayBuffer"
        }
        global.setHidden("ArrayBuffer", abCtor)

        val dataViewCtor = object : AbstractFunction(global) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
            override fun construct(vararg params: BaseObject): BaseObject {
                val buf = params.getOrNull(0)
                    ?: throw JSError.JavaException("TypeError", "First argument to DataView constructor must be an ArrayBuffer")
                val rawBytes = (buf.get("__bytes", Scopable.OR_NULL) as? Uint8Array.Instance)?.arrayStorage
                    ?: throw JSError.JavaException("TypeError", "First argument to DataView constructor must be an ArrayBuffer")
                val byteOffset = (params.getOrNull(1)?.takeIf { it !== Undefined }?.toInt() ?: 0).coerceIn(0, rawBytes.size)
                val byteLength = (params.getOrNull(2)?.takeIf { it !== Undefined }?.toInt() ?: (rawBytes.size - byteOffset))
                    .coerceIn(0, rawBytes.size - byteOffset)

                val inst = GenericObject(this, global)
                inst.setHidden("buffer", buf)
                inst.setHidden("byteOffset", global.wrap(byteOffset.toDouble()))
                inst.setHidden("byteLength", global.wrap(byteLength.toDouble()))

                fun writeBytesBE(off: Int, count: Int, v: Long) {
                    val base = byteOffset + off
                    if (base < 0 || base + count > byteOffset + byteLength) throw JSError.JavaException("RangeError", "Offset is outside the bounds of the DataView")
                    for (i in 0 until count) {
                        rawBytes[base + i] = ((v ushr ((count - 1 - i) * 8)) and 0xFFL).toByte()
                    }
                }
                fun writeBytesLE(off: Int, count: Int, v: Long) {
                    val base = byteOffset + off
                    if (base < 0 || base + count > byteOffset + byteLength) throw JSError.JavaException("RangeError", "Offset is outside the bounds of the DataView")
                    for (i in 0 until count) {
                        rawBytes[base + i] = ((v ushr (i * 8)) and 0xFFL).toByte()
                    }
                }
                fun readBytesBE(off: Int, count: Int): Long {
                    val base = byteOffset + off
                    if (base < 0 || base + count > byteOffset + byteLength) throw JSError.JavaException("RangeError", "Offset is outside the bounds of the DataView")
                    var out = 0L
                    for (i in 0 until count) {
                        out = (out shl 8) or (rawBytes[base + i].toLong() and 0xFFL)
                    }
                    return out
                }
                fun readBytesLE(off: Int, count: Int): Long {
                    val base = byteOffset + off
                    if (base < 0 || base + count > byteOffset + byteLength) throw JSError.JavaException("RangeError", "Offset is outside the bounds of the DataView")
                    var out = 0L
                    for (i in 0 until count) {
                        out = out or ((rawBytes[base + i].toLong() and 0xFFL) shl (i * 8))
                    }
                    return out
                }

                inst.setHidden("getUint8", fn(global, "getUint8") { _, p ->
                    val off = p.getOrNull(0)?.toInt() ?: 0
                    global.wrap((readBytesBE(off, 1) and 0xFFL).toDouble())
                })
                inst.setHidden("getInt8", fn(global, "getInt8") { _, p ->
                    val off = p.getOrNull(0)?.toInt() ?: 0
                    global.wrap(readBytesBE(off, 1).toByte().toDouble())
                })
                inst.setHidden("setUint8", fn(global, "setUint8") { _, p ->
                    writeBytesBE(p.getOrNull(0)?.toInt() ?: 0, 1, p.getOrNull(1)?.toLong() ?: 0L)
                    Undefined
                })
                inst.setHidden("setInt8", inst.get("setUint8"))
                inst.setHidden("getUint16", fn(global, "getUint16") { _, p ->
                    val off = p.getOrNull(0)?.toInt() ?: 0
                    val le = p.getOrNull(1)?.toBool() == true
                    val v = if (le) readBytesLE(off, 2) else readBytesBE(off, 2)
                    global.wrap((v and 0xFFFFL).toDouble())
                })
                inst.setHidden("getInt16", fn(global, "getInt16") { _, p ->
                    val off = p.getOrNull(0)?.toInt() ?: 0
                    val le = p.getOrNull(1)?.toBool() == true
                    val v = if (le) readBytesLE(off, 2) else readBytesBE(off, 2)
                    global.wrap(v.toShort().toDouble())
                })
                inst.setHidden("setUint16", fn(global, "setUint16") { _, p ->
                    val off = p.getOrNull(0)?.toInt() ?: 0
                    val v = p.getOrNull(1)?.toLong() ?: 0L
                    val le = p.getOrNull(2)?.toBool() == true
                    if (le) writeBytesLE(off, 2, v) else writeBytesBE(off, 2, v)
                    Undefined
                })
                inst.setHidden("setInt16", inst.get("setUint16"))
                inst.setHidden("getUint32", fn(global, "getUint32") { _, p ->
                    val off = p.getOrNull(0)?.toInt() ?: 0
                    val le = p.getOrNull(1)?.toBool() == true
                    val v = if (le) readBytesLE(off, 4) else readBytesBE(off, 4)
                    global.wrap((v and 0xFFFFFFFFL).toDouble())
                })
                inst.setHidden("getInt32", fn(global, "getInt32") { _, p ->
                    val off = p.getOrNull(0)?.toInt() ?: 0
                    val le = p.getOrNull(1)?.toBool() == true
                    val v = if (le) readBytesLE(off, 4) else readBytesBE(off, 4)
                    global.wrap(v.toInt().toDouble())
                })
                inst.setHidden("setUint32", fn(global, "setUint32") { _, p ->
                    val off = p.getOrNull(0)?.toInt() ?: 0
                    val v = p.getOrNull(1)?.toDouble()?.toLong() ?: 0L
                    val le = p.getOrNull(2)?.toBool() == true
                    if (le) writeBytesLE(off, 4, v) else writeBytesBE(off, 4, v)
                    Undefined
                })
                inst.setHidden("setInt32", inst.get("setUint32"))
                inst.setHidden("getFloat32", fn(global, "getFloat32") { _, p ->
                    val off = p.getOrNull(0)?.toInt() ?: 0
                    val le = p.getOrNull(1)?.toBool() == true
                    val bits = (if (le) readBytesLE(off, 4) else readBytesBE(off, 4)).toInt()
                    global.wrap(Float.fromBits(bits).toDouble())
                })
                inst.setHidden("setFloat32", fn(global, "setFloat32") { _, p ->
                    val off = p.getOrNull(0)?.toInt() ?: 0
                    val f = p.getOrNull(1)?.toFloat() ?: 0f
                    val le = p.getOrNull(2)?.toBool() == true
                    val bits = f.toRawBits().toLong() and 0xFFFFFFFFL
                    if (le) writeBytesLE(off, 4, bits) else writeBytesBE(off, 4, bits)
                    Undefined
                })
                inst.setHidden("getFloat64", fn(global, "getFloat64") { _, p ->
                    val off = p.getOrNull(0)?.toInt() ?: 0
                    val le = p.getOrNull(1)?.toBool() == true
                    val bits = if (le) readBytesLE(off, 8) else readBytesBE(off, 8)
                    global.wrap(Double.fromBits(bits))
                })
                inst.setHidden("setFloat64", fn(global, "setFloat64") { _, p ->
                    val off = p.getOrNull(0)?.toInt() ?: 0
                    val d = p.getOrNull(1)?.toDouble() ?: 0.0
                    val le = p.getOrNull(2)?.toBool() == true
                    val bits = d.toRawBits()
                    if (le) writeBytesLE(off, 8, bits) else writeBytesBE(off, 8, bits)
                    Undefined
                })
                return inst
            }
            override fun name(): String = "DataView"
        }
        global.setHidden("DataView", dataViewCtor)

        // Wrap TypedArrays to support Array literals, ArrayBuffer, .buffer, .subarray(), and .set()
        for (typedName in listOf("Uint8Array", "Uint8ClampedArray", "Int8Array", "Uint16Array", "Int16Array", "Uint32Array", "Int32Array", "Float32Array", "Float64Array")) {
            val baseCtor = global.get(typedName, Scopable.OR_NULL) as? AbstractFunction ?: continue
            val bpe = when (typedName) {
                "Int16Array", "Uint16Array" -> 2
                "Uint32Array", "Int32Array", "Float32Array" -> 4
                "Float64Array" -> 8
                else -> 1
            }
            val wrapped = object : AbstractFunction(global) {
                override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = construct(*params)
                override fun construct(vararg params: BaseObject): BaseObject {
                    val arg0 = params.getOrNull(0)
                    val inst = if (arg0 is GenericArray || arg0 is AbstractArray<*>) {
                        val len = arg0.get("length").toInt().coerceAtLeast(0)
                        val arr = baseCtor.construct(global.wrap(len.toDouble()))
                        for (i in 0 until len) arr.set(i, arg0.get(i))
                        arr
                    } else if (arg0 != null && arg0.get("__bytes", Scopable.OR_NULL) != null) {
                        val uBytes = (arg0.get("__bytes") as Uint8Array.Instance).arrayStorage ?: ByteArray(0)
                        val byteOffset = (params.getOrNull(1)?.takeIf { it !== Undefined }?.toInt() ?: 0).coerceIn(0, uBytes.size)
                        val elemCount = params.getOrNull(2)?.takeIf { it !== Undefined }?.toInt()?.coerceAtLeast(0)
                            ?: ((uBytes.size - byteOffset) / bpe).coerceAtLeast(0)
                        val arr = if ((typedName == "Uint8Array" || typedName == "Uint8ClampedArray") && byteOffset == 0 && elemCount == uBytes.size) {
                            if (typedName == "Uint8Array") {
                                Uint8Array.Instance(global, rawUint8Ctor, uBytes)
                            } else {
                                Uint8ClampedArray.Instance(global, global.get("Uint8ClampedArray", Scopable.OR_NULL) as? Uint8ClampedArray, uBytes)
                            }
                        } else {
                            val allocated = baseCtor.construct(global.wrap(elemCount.toDouble()))
                            for (i in 0 until elemCount) {
                                val off = byteOffset + i * bpe
                                val numVal: Double = when (typedName) {
                                    "Int8Array" -> uBytes[off].toDouble()
                                    "Uint8Array", "Uint8ClampedArray" -> (uBytes[off].toInt() and 0xFF).toDouble()
                                    "Int16Array" -> ((uBytes[off].toInt() and 0xFF) or (uBytes[off + 1].toInt() shl 8)).toDouble()
                                    "Uint16Array" -> ((uBytes[off].toInt() and 0xFF) or ((uBytes[off + 1].toInt() and 0xFF) shl 8)).toDouble()
                                    "Int32Array" -> ((uBytes[off].toInt() and 0xFF) or ((uBytes[off + 1].toInt() and 0xFF) shl 8) or ((uBytes[off + 2].toInt() and 0xFF) shl 16) or (uBytes[off + 3].toInt() shl 24)).toDouble()
                                    "Uint32Array" -> (((uBytes[off].toInt() and 0xFF).toLong()) or ((uBytes[off + 1].toInt() and 0xFF).toLong() shl 8) or ((uBytes[off + 2].toInt() and 0xFF).toLong() shl 16) or ((uBytes[off + 3].toInt() and 0xFF).toLong() shl 24)).toDouble()
                                    "Float32Array" -> {
                                        val bits = (uBytes[off].toInt() and 0xFF) or ((uBytes[off + 1].toInt() and 0xFF) shl 8) or ((uBytes[off + 2].toInt() and 0xFF) shl 16) or ((uBytes[off + 3].toInt() and 0xFF) shl 24)
                                        Float.fromBits(bits).toDouble()
                                    }
                                    "Float64Array" -> {
                                        var bits = 0L
                                        for (b in 0 until 8) bits = bits or ((uBytes[off + b].toLong() and 0xFFL) shl (b * 8))
                                        Double.fromBits(bits)
                                    }
                                    else -> 0.0
                                }
                                allocated.set(i, global.wrap(numVal))
                            }
                            allocated
                        }
                        if (arr is GenericObject) {
                            arr.setHidden("__sourceBuffer", arg0)
                        }
                        arr
                    } else {
                        baseCtor.construct(*params)
                    }
                    if (inst is GenericObject) {
                        attachTypedArrayMethods(global, this, abCtor, inst, typedName)
                    }
                    return inst
                }
                override fun name(): String = typedName
            }
            baseCtor.setHidden("from", fn(global, "from") { _, p -> wrapped.construct(p.getOrNull(0) ?: GenericArray(global)) })
            wrapped.setHidden("from", baseCtor.get("from"))
            wrapped.setPrototype(baseCtor.prototype())
            global.setHidden(typedName, wrapped)
        }
    }

    private fun attachTypedArrayMethods(global: Global, ctor: BaseFunction, abCtor: BaseFunction, inst: GenericObject, name: String) {
        val bpe = when (name) {
            "Int16Array", "Uint16Array" -> 2
            "Uint32Array", "Int32Array", "Float32Array" -> 4
            "Float64Array" -> 8
            else -> 1
        }
        val len = inst.get("length").toInt()
        inst.setHidden("BYTES_PER_ELEMENT", global.wrap(bpe.toDouble()))
        inst.setHidden("byteLength", global.wrap((len * bpe).toDouble()))
        inst.setHidden("byteOffset", global.wrap(0.0))
        inst.defineProperty("buffer", fn(global, "get_buffer") { _, _ ->
            val bufObj = abCtor.construct(global.wrap((len * bpe).toDouble())) as GenericObject
            val raw = (bufObj.get("__bytes") as? Uint8Array.Instance)?.arrayStorage
            if (raw != null && inst is Uint8Array.Instance) {
                inst.arrayStorage?.copyInto(raw, 0, 0, minOf(raw.size, inst.length()))
            }
            bufObj
        }, global.NOOP)
        inst.setHidden("subarray", fn(global, "subarray") { _this, args ->
            val l = _this.get("length").toInt()
            val begin = (args.getOrNull(0)?.toInt() ?: 0).let { if (it < 0) l + it else it }.coerceIn(0, l)
            val end = (args.getOrNull(1)?.takeIf { it !== Undefined }?.toInt() ?: l).let { if (it < 0) l + it else it }.coerceIn(begin, l)
            val out = ctor.construct(global.wrap((end - begin).toDouble()))
            for (i in begin until end) out.set(i - begin, _this.get(i))
            out
        })
        inst.setHidden("slice", inst.get("subarray"))
        inst.setHidden("set", fn(global, "set") { _this, args ->
            val src = args.getOrNull(0) ?: return@fn Undefined
            val offset = (args.getOrNull(1)?.toInt() ?: 0).coerceAtLeast(0)
            val sl = src.get("length").toInt()
            for (i in 0 until sl) _this.set(offset + i, src.get(i))
            Undefined
        })
    }

    private fun installStructuredCloneAndMicrotask(global: Global, scheduler: NjsEventScheduler) {
        global.setHidden("queueMicrotask", fn(global, "queueMicrotask") { _, params ->
            val cb = params.getOrNull(0) as? BaseFunction
            if (cb != null) scheduler.queueMicrotask { cb.call(Undefined) }
            Undefined
        })
        global.setHidden("structuredClone", fn(global, "structuredClone") { _, params ->
            deepCloneJsValue(global, params.getOrNull(0) ?: Undefined)
        })
    }

    private fun deepCloneJsValue(global: Global, value: BaseObject): BaseObject {
        return when (value) {
            Undefined, Null -> value
            is JSBoolean.Instance, is JSNumber.Instance, is JSString.Instance -> value
            is Date.Instance -> {
                val dateCtor = global.get("Date") as? Date ?: Date(global)
                Date.Instance(dateCtor, global.Symbol.iteratorSymbol, global.String!!, global.Number!!, value.timestamp)
            }
            is GenericArray -> {
                val len = value.length()
                val arr = arrayOfNulls<BaseObject>(len)
                for (i in 0 until len) arr[i] = deepCloneJsValue(global, value.get(i))
                GenericArray(global, global.Array, arr)
            }
            is Uint8Array.Instance -> {
                val len = value.length()
                val clone = Uint8Array.Instance(global, global.get("Uint8Array") as? Uint8Array, value.arrayStorage?.copyOf())
                for (i in 0 until len) clone.set(i, value.get(i))
                clone
            }
            is GenericObject -> {
                val copy = GenericObject(global)
                for (k in value.keys()) {
                    copy.set(k, deepCloneJsValue(global, value.get(k)))
                }
                copy
            }
            else -> value
        }
    }

    private fun sameValueZero(a: BaseObject, b: BaseObject): Boolean {
        if (a is JSNumber.Instance && b is JSNumber.Instance && a.value.isNaN() && b.value.isNaN()) return true
        return strictEq(a, b)
    }

    private fun strictEq(a: BaseObject, b: BaseObject): Boolean {
        if (a === b) return true
        if (a is JSNumber.Instance && b is JSNumber.Instance) return a.value == b.value
        if (a is JSString.Instance && b is JSString.Instance) return a.string == b.string
        if (a is JSBoolean.Instance && b is JSBoolean.Instance) return a.value == b.value
        return false
    }

    private fun percentEncode(input: String, keepUriReserved: Boolean): String {
        val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()"
        val reserved = ";/?:@&=+$,#"
        val bytes = input.encodeToByteArray()
        val sb = StringBuilder(bytes.size)
        for (b in bytes) {
            val code = b.toInt() and 0xFF
            val ch = code.toChar()
            if (code < 128 && (ch in unreserved || (keepUriReserved && ch in reserved))) {
                sb.append(ch)
            } else {
                sb.append('%')
                val hex = code.toString(16).uppercase()
                if (hex.length == 1) sb.append('0')
                sb.append(hex)
            }
        }
        return sb.toString()
    }

    private fun percentDecode(input: String): String {
        val bytes = ArrayList<Byte>(input.length)
        var i = 0
        while (i < input.length) {
            if (input[i] == '%' && i + 2 < input.length) {
                val hex = input.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    bytes.add(hex.toByte())
                    i += 3
                    continue
                }
            }
            for (b in input[i].toString().encodeToByteArray()) bytes.add(b)
            i++
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun fn(global: Global, name: String, body: (BaseObject, Array<out BaseObject>) -> BaseObject): AbstractFunction =
        object : AbstractFunction(global) {
            override fun call(_this: BaseObject, vararg params: BaseObject): BaseObject = body(_this, params)
            override fun name(): String = name
        }
}
