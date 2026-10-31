package com.example.nordcallblocker

import android.content.Context
import android.os.CancellationSignal
import android.provider.ContactsContract
import android.util.Log
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal object ContactLookup {
    private const val TAG = "NordCallBlocker"
    private const val TIMEOUT_MILLIS = 2_000L

    enum class Result {
        SAVED,
        NOT_SAVED,
        UNKNOWN
    }

    /**
     * Looks up a phone number without exposing it to logs. The bounded wait keeps the screening
     * response inside Android's five-second deadline. Any uncertainty must fail open.
     */
    fun find(context: Context, number: String): Result {
        val cancellationSignal = CancellationSignal()
        val lookup = FutureTask {
            query(context, number, cancellationSignal)
        }
        Thread(lookup, "NordCallBlocker-contact-lookup").start()

        return try {
            lookup.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            cancellationSignal.cancel()
            lookup.cancel(true)
            Log.w(TAG, "Contact lookup timed out; allowing call")
            Result.UNKNOWN
        } catch (error: Exception) {
            cancellationSignal.cancel()
            lookup.cancel(true)
            Log.w(TAG, "Contact lookup failed (${error.javaClass.simpleName}); allowing call")
            Result.UNKNOWN
        }
    }

    private fun query(
        context: Context,
        number: String,
        cancellationSignal: CancellationSignal
    ): Result {
        val uri = android.net.Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(number)
        )

        return try {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null,
                null,
                null,
                cancellationSignal
            ) ?: return Result.UNKNOWN

            cursor.use {
                if (it.moveToFirst()) Result.SAVED else Result.NOT_SAVED
            }
        } catch (error: Exception) {
            Log.w(TAG, "Contact provider error (${error.javaClass.simpleName}); allowing call")
            Result.UNKNOWN
        }
    }
}
