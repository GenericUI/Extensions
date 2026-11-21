package com.nexustools.genericui.ext.schema

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Lightweight Kotlin Multiplatform promise/callback wrapper unifying synchronous results,
 * callback-driven asynchronous tasks, and coroutine `suspend` loaders.
 */
@OptIn(ExperimentalAtomicApi::class)
class SchemaAsyncResult<T> private constructor() {

    private sealed interface State<out T> {
        data class Pending<T>(val listeners: List<Listener<T>>) : State<T>
        data class Success<T>(val value: T) : State<T>
        data class Failure(val error: Throwable) : State<Nothing>
    }

    private data class Listener<T>(
        val onSuccess: (T) -> Unit,
        val onFailure: (Throwable) -> Unit
    )

    private val state = AtomicReference<State<T>>(State.Pending(emptyList()))

    val isSettled: Boolean
        get() = state.load() !is State.Pending

    val isSuccess: Boolean
        get() = state.load() is State.Success

    val isFailure: Boolean
        get() = state.load() is State.Failure

    fun getOrNull(): T? = (state.load() as? State.Success<T>)?.value

    fun exceptionOrNull(): Throwable? = (state.load() as? State.Failure)?.error

    internal fun resolve(value: T): Boolean {
        while (true) {
            val current = state.load()
            if (current !is State.Pending<T>) return false
            val next = State.Success(value)
            if (state.compareAndSet(current, next)) {
                for (listener in current.listeners) {
                    listener.onSuccess(value)
                }
                return true
            }
        }
    }

    internal fun reject(error: Throwable): Boolean {
        while (true) {
            val current = state.load()
            if (current !is State.Pending<T>) return false
            val next = State.Failure(error)
            if (state.compareAndSet(current, next)) {
                for (listener in current.listeners) {
                    listener.onFailure(error)
                }
                return true
            }
        }
    }

    fun onComplete(
        onSuccess: (T) -> Unit,
        onFailure: (Throwable) -> Unit = {}
    ): SchemaAsyncResult<T> {
        while (true) {
            when (val current = state.load()) {
                is State.Success<T> -> {
                    onSuccess(current.value)
                    return this
                }
                is State.Failure -> {
                    onFailure(current.error)
                    return this
                }
                is State.Pending<T> -> {
                    val updated = State.Pending(current.listeners + Listener(onSuccess, onFailure))
                    if (state.compareAndSet(current, updated)) {
                        return this
                    }
                }
            }
        }
    }

    fun onSuccess(callback: (T) -> Unit): SchemaAsyncResult<T> =
        onComplete(onSuccess = callback)

    fun onFailure(callback: (Throwable) -> Unit): SchemaAsyncResult<T> =
        onComplete(onSuccess = {}, onFailure = callback)

    fun <R> map(transform: (T) -> R): SchemaAsyncResult<R> {
        val mapped = SchemaAsyncResult<R>()
        onComplete(
            onSuccess = { value ->
                try {
                    mapped.resolve(transform(value))
                } catch (t: Throwable) {
                    mapped.reject(t)
                }
            },
            onFailure = { error ->
                mapped.reject(error)
            }
        )
        return mapped
    }

    suspend fun await(): T = suspendCancellableCoroutine { cont ->
        onComplete(
            onSuccess = { value ->
                if (cont.isActive) cont.resume(value)
            },
            onFailure = { error ->
                if (cont.isActive) cont.resumeWithException(error)
            }
        )
    }

    companion object {
        fun <T> resolved(value: T): SchemaAsyncResult<T> =
            SchemaAsyncResult<T>().apply { resolve(value) }

        fun <T> failed(error: Throwable): SchemaAsyncResult<T> =
            SchemaAsyncResult<T>().apply { reject(error) }

        fun <T> failed(message: String): SchemaAsyncResult<T> =
            failed(IllegalStateException(message))

        fun <T> async(
            executor: (accept: (T) -> Unit, reject: (Throwable) -> Unit) -> Unit
        ): SchemaAsyncResult<T> {
            val result = SchemaAsyncResult<T>()
            try {
                executor(
                    { value -> result.resolve(value) },
                    { error -> result.reject(error) }
                )
            } catch (t: Throwable) {
                result.reject(t)
            }
            return result
        }

        fun <T> fromSuspend(
            scope: CoroutineScope,
            block: suspend () -> T
        ): SchemaAsyncResult<T> {
            val result = SchemaAsyncResult<T>()
            scope.launch {
                try {
                    result.resolve(block())
                } catch (ce: CancellationException) {
                    result.reject(ce)
                    throw ce
                } catch (t: Throwable) {
                    result.reject(t)
                }
            }
            return result
        }
    }
}
