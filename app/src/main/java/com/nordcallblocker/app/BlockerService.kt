package com.nordcallblocker.app

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.telecom.Call
import android.telecom.CallScreeningService

class BlockerService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            AppLog.d(this, "Ignoring non-incoming screening callback")
            return
        }

        val userUnlocked = BlockerStore.isUserUnlocked(this)
        val roleHeld = runCatching {
            getSystemService(RoleManager::class.java)
                ?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        }.getOrDefault(false)
        val enabled = BlockerStore.enabled(this)
        val settingInitialized = BlockerStore.isEnabledSettingInitialized(this)
        val contactsPermission = checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        val number = callDetails.handle?.schemeSpecificPart

        val (block, reason) = when {
            !enabled -> false to "disabled"
            !userUnlocked -> false to "direct_boot"
            !contactsPermission -> false to "contacts_permission_missing"
            number.isNullOrBlank() -> false to "number_unavailable"
            else -> when (ContactLookup.find(applicationContext, number)) {
                ContactLookup.Result.SAVED -> false to "saved_contact"
                ContactLookup.Result.NOT_SAVED -> true to "not_saved"
                ContactLookup.Result.UNKNOWN -> false to "contact_lookup_uncertain"
            }
        }

        // Deliberately omit the phone number and Call.Details from diagnostics.
        AppLog.i(
            this,
            "Screening callback: userUnlocked=$userUnlocked roleHeld=$roleHeld " +
                "settingInitialized=$settingInitialized enabled=$enabled " +
                "contactsPermission=$contactsPermission decision=$reason"
        )

        val response = CallResponse.Builder()
            .setDisallowCall(block)
            .setRejectCall(block)
            .setSkipCallLog(false)
            .setSkipNotification(block)
            .build()
        // Respond first: Android allows a maximum of five seconds.
        respondToCall(callDetails, response)
        AppLog.i(this, "Screening response sent: blocked=$block")

        if (block && number != null) {
            if (!BlockerStore.append(applicationContext, number)) {
                AppLog.w(this, "Unable to persist blocked-call history entry")
            }
        }
    }

}
