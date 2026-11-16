package com.ming.mchat.core.common

/**
 * 统一的领域层结果封装，避免各层直接依赖 kotlin.Result。
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: Throwable) : AppResult<Nothing>
    data object Loading : AppResult<Nothing>
}

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(block: (Throwable) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) block(error)
    return this
}

fun <T> runCatchingApp(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (c: kotlin.coroutines.cancellation.CancellationException) {
    throw c
} catch (t: Throwable) {
    AppResult.Failure(t)
}
