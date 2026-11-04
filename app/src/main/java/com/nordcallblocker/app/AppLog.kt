package com.nordcallblocker.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

/** Keeps diagnostics available in debug APKs without emitting them in signed release builds. */
internal object AppLog {
    private const val TAG = "NordCallBlocker"

    fun d(context: Context, message: String) {
        if (isDebuggable(context)) Log.d(TAG, message)
    }

    fun i(context: Context, message: String) {
        if (isDebuggable(context)) Log.i(TAG, message)
    }

    fun w(context: Context, message: String) {
        if (isDebuggable(context)) Log.w(TAG, message)
    }

    private fun isDebuggable(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
