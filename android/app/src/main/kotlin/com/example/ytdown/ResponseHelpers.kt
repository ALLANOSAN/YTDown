package com.example.ytdown

import android.os.Handler
import android.os.Looper

fun runOnMainThread(action: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        action()
        return
    }
    Handler(Looper.getMainLooper()).post(action)
}
