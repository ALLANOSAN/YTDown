package com.example.ytdown

import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject

fun runOnMainThread(action: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        action()
        return
    }
    Handler(Looper.getMainLooper()).post(action)
}

fun respondWithMap(result: MethodChannel.Result, payload: Map<String, Any?>) {
    runOnMainThread { result.success(payload) }
}

fun respondSuccess(result: MethodChannel.Result, payload: Any?) {
    runOnMainThread { result.success(payload) }
}

fun isRetryableBridgeError(error: Throwable?): Boolean {
    val message = error?.message?.lowercase() ?: return false
    val retryableTokens = listOf(
        "timeout",
        "timed out",
        "temporar",
        "network",
        "connection",
        "429",
        "503",
        "504",
    )
    return retryableTokens.any { token -> message.contains(token) }
}

fun respondStructuredFailure(
    result: MethodChannel.Result,
    stage: String,
    code: String,
    error: Throwable?,
) {
    val payload = JSONObject().apply {
        put("success", false)
        put("error", error?.message ?: "Falha inesperada no bridge Kotlin")
        put("stage", stage)
        put("retryable", isRetryableBridgeError(error))
        put("platformCode", code)
    }.toString()

    respondSuccess(result, payload)
}
