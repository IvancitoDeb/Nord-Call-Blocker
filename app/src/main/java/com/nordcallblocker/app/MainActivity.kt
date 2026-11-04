package com.nordcallblocker.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.window.OnBackInvokedDispatcher
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var history: LinearLayout
    private lateinit var toggle: Switch
    private lateinit var permissionsButton: Button
    private lateinit var mainContent: View
    private lateinit var drawerPanel: View
    private lateinit var drawerScrim: View
    private var drawerOpen = false
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private val roleManager by lazy { getSystemService(RoleManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            setBackgroundColor(COLOR_PAGE)
        }
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_PAGE)
        }
        mainContent = main
        root.addView(main, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        main.addView(createHeader())

        status = TextView(this).apply {
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setPadding(16.dp, 14.dp, 16.dp, 14.dp)
        }
        main.addView(status, matchWidth(top = 10))

        permissionsButton = primaryButton(getString(R.string.configure_protection)).apply {
            setOnClickListener { continuePermissionSetup() }
        }
        main.addView(permissionsButton, matchWidth(top = 8, height = 50))

        main.addView(createProtectionCard(), matchWidth(top = 10))
        main.addView(createHistoryHeader(), matchWidth(top = 14))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 4.dp, 0, 8.dp)
        }
        history = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(history)
        main.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        drawerScrim = View(this).apply {
            setBackgroundColor(0x990B1824.toInt())
            alpha = 0f
            visibility = View.INVISIBLE
            isClickable = true
            contentDescription = getString(R.string.close_menu)
            setOnClickListener { closeDrawer() }
        }
        root.addView(drawerScrim, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val drawerWidth = minOf(
            (resources.displayMetrics.widthPixels * 0.86f).toInt(),
            360.dp
        )
        drawerPanel = createInformationDrawer().apply {
            translationX = -drawerWidth.toFloat()
            visibility = View.INVISIBLE
            elevation = 16.dp.toFloat()
            isClickable = true
            accessibilityPaneTitle = getString(R.string.information)
        }
        root.addView(drawerPanel, FrameLayout.LayoutParams(
            drawerWidth,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.START
        ))

        applySystemBarInsets(root, main, drawerPanel)

        setContentView(root)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { handleBack() }
        }
        // Some OEM implementations do not create DecorView's insets controller until the
        // content view is attached. Deferring this avoids a startup crash on OxygenOS.
        root.post { useDarkSystemBarIcons() }
    }

    private fun applySystemBarInsets(root: View, main: View, drawer: View) {
        val horizontalPadding = 18.dp
        val contentTopPadding = 18.dp
        val contentBottomPadding = 14.dp

        root.setOnApplyWindowInsetsListener { _, insets ->
            val systemTop: Int
            val systemBottom: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
                systemTop = systemBars.top
                systemBottom = systemBars.bottom
            } else {
                @Suppress("DEPRECATION")
                systemTop = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                systemBottom = insets.systemWindowInsetBottom
            }
            main.setPadding(
                horizontalPadding,
                systemTop + 12.dp,
                horizontalPadding,
                systemBottom + contentBottomPadding
            )
            drawer.setPadding(20.dp, systemTop + contentTopPadding, 20.dp, systemBottom + 16.dp)
            insets
        }
        root.requestApplyInsets()
    }

    private fun useDarkSystemBarIcons() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.decorView.windowInsetsController ?: return
            controller.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        showHistory()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureStartX = event.rawX
                gestureStartY = event.rawY
            }
            MotionEvent.ACTION_UP -> {
                val horizontalDistance = event.rawX - gestureStartX
                val verticalDistance = kotlin.math.abs(event.rawY - gestureStartY)
                val isHorizontalSwipe = kotlin.math.abs(horizontalDistance) > verticalDistance
                if (!drawerOpen && gestureStartX <= DRAWER_EDGE_DP.dp &&
                    horizontalDistance >= DRAWER_SWIPE_DP.dp && isHorizontalSwipe
                ) {
                    openDrawer()
                } else if (drawerOpen && horizontalDistance <= -DRAWER_SWIPE_DP.dp &&
                    isHorizontalSwipe
                ) {
                    closeDrawer()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    @SuppressLint("GestureBackNavigation")
    @Deprecated("Used only on Android 12L and earlier")
    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (drawerOpen) closeDrawer() else super.onBackPressed()
        }
    }

    private fun handleBack() {
        if (drawerOpen) closeDrawer() else finishAfterTransition()
    }

    private fun createHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8.dp, 10.dp, 14.dp, 10.dp)
            background = rounded(COLOR_NAVY, 18)
            elevation = 3.dp.toFloat()
        }

        header.addView(TextView(this).apply {
            text = getString(R.string.menu_symbol)
            gravity = Gravity.CENTER
            textSize = 25f
            setTextColor(Color.WHITE)
            background = ripple(COLOR_NAVY, 14)
            contentDescription = getString(R.string.open_menu)
            isClickable = true
            isFocusable = true
            setOnClickListener { openDrawer() }
        }, LinearLayout.LayoutParams(48.dp, 48.dp))

        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp, 0, 0, 0)
        }
        titles.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        titles.addView(TextView(this).apply {
            text = getString(R.string.header_subtitle)
            textSize = 13f
            setTextColor(COLOR_HEADER_MUTED)
            setPadding(0, 3.dp, 0, 0)
        })
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return header
    }

    private fun createProtectionCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 14.dp, 16.dp, 14.dp)
            background = rounded(Color.WHITE, 18, COLOR_BORDER)
            elevation = 1.dp.toFloat()
        }
        toggle = Switch(this).apply {
            text = getString(R.string.block_unknown_numbers)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_TEXT)
            isChecked = BlockerStore.enabled(this@MainActivity)
            thumbTintList = ColorStateList.valueOf(COLOR_TEAL)
            setOnCheckedChangeListener { _, checked ->
                if (!BlockerStore.setEnabled(this@MainActivity, checked)) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.setting_save_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
                refreshStatus()
            }
        }
        card.addView(toggle)
        card.addView(TextView(this).apply {
            text = getString(R.string.protection_description)
            textSize = 13f
            setTextColor(COLOR_MUTED)
            setPadding(0, 7.dp, 0, 0)
        })
        return card
    }

    private fun createInformationDrawer(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val drawerHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        drawerHeader.addView(TextView(this).apply {
            text = getString(R.string.information)
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_TEXT)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        drawerHeader.addView(TextView(this).apply {
            text = getString(R.string.close_symbol)
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(COLOR_MUTED)
            contentDescription = getString(R.string.close_menu)
            background = ripple(Color.WHITE, 14)
            isClickable = true
            isFocusable = true
            setOnClickListener { closeDrawer() }
        }, LinearLayout.LayoutParams(48.dp, 48.dp))
        content.addView(drawerHeader)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        val sections = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16.dp, 0, 12.dp)
        }
        sections.addView(drawerSection(
            getString(R.string.privacy_title),
            getString(R.string.privacy_description)
        ))
        sections.addView(View(this).apply {
            setBackgroundColor(COLOR_DIVIDER)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp).apply {
            topMargin = 24.dp
            bottomMargin = 24.dp
        })
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: "—"
        sections.addView(drawerSection(
            getString(R.string.about),
            getString(R.string.about_drawer_message, versionName)
        ))
        scroll.addView(sections)
        content.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        return content
    }

    private fun drawerSection(title: String, description: String) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(COLOR_TEAL_DARK)
            })
            addView(TextView(this@MainActivity).apply {
                text = description
                textSize = 14f
                setTextColor(COLOR_MUTED)
                setLineSpacing(4.dp.toFloat(), 1f)
                setPadding(0, 8.dp, 0, 0)
            })
        }

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        drawerScrim.animate().cancel()
        drawerPanel.animate().cancel()
        drawerScrim.visibility = View.VISIBLE
        drawerPanel.visibility = View.VISIBLE
        mainContent.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        drawerScrim.animate().alpha(1f).setDuration(DRAWER_ANIMATION_MS).start()
        drawerPanel.animate().translationX(0f).setDuration(DRAWER_ANIMATION_MS).start()
    }

    private fun closeDrawer() {
        if (!drawerOpen) return
        drawerOpen = false
        mainContent.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        drawerScrim.animate().cancel()
        drawerPanel.animate().cancel()
        drawerScrim.animate()
            .alpha(0f)
            .setDuration(DRAWER_ANIMATION_MS)
            .withEndAction { if (!drawerOpen) drawerScrim.visibility = View.INVISIBLE }
            .start()
        drawerPanel.animate()
            .translationX(-drawerPanel.width.toFloat())
            .setDuration(DRAWER_ANIMATION_MS)
            .withEndAction { if (!drawerOpen) drawerPanel.visibility = View.INVISIBLE }
            .start()
    }

    private fun createHistoryHeader(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(2.dp, 0, 0, 4.dp)
        }
        container.addView(TextView(this).apply {
            text = getString(R.string.history_title)
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_TEXT)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        actions.addView(secondaryButton(getString(R.string.refresh)).apply {
            setOnClickListener { showHistory() }
        })
        actions.addView(secondaryButton(getString(R.string.delete)).apply {
            setOnClickListener { confirmHistoryDeletion() }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            48.dp
        ).apply { marginStart = 6.dp })
        container.addView(actions)
        return container
    }

    private fun confirmHistoryDeletion() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_history_title)
            .setMessage(R.string.clear_history_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.clear_history_action) { _, _ -> clearHistory() }
            .show()
    }

    private fun clearHistory() {
        if (!BlockerStore.clear(this)) {
            Toast.makeText(this, R.string.history_clear_failed, Toast.LENGTH_LONG).show()
            return
        }
        showHistory()
        Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show()
    }

    private fun refreshStatus() {
        val roleAvailable = roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
        val roleGranted = roleAvailable && roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        val contactsGranted = hasContactsPermission()
        val enabled = BlockerStore.enabled(this)

        val (message, backgroundColor, textColor) = when {
            !roleAvailable -> Triple(
                getString(R.string.status_role_unavailable),
                COLOR_DANGER_SOFT,
                COLOR_DANGER
            )
            !roleGranted -> Triple(
                getString(R.string.status_role_missing),
                COLOR_WARNING_SOFT,
                COLOR_WARNING
            )
            !contactsGranted -> Triple(
                getString(R.string.status_contacts_missing),
                COLOR_WARNING_SOFT,
                COLOR_WARNING
            )
            enabled -> Triple(
                getString(R.string.status_active),
                COLOR_SUCCESS_SOFT,
                COLOR_SUCCESS
            )
            else -> Triple(
                getString(R.string.status_disabled),
                COLOR_NEUTRAL_SOFT,
                COLOR_MUTED
            )
        }

        status.text = message
        status.setTextColor(textColor)
        status.background = rounded(backgroundColor, 14)

        permissionsButton.text = when {
            !roleGranted -> getString(R.string.configure_protection)
            !contactsGranted -> getString(R.string.complete_contacts_access)
            else -> getString(R.string.permissions_configured)
        }
        permissionsButton.isEnabled = roleAvailable && !(roleGranted && contactsGranted)
        permissionsButton.visibility = if (permissionsButton.isEnabled) View.VISIBLE else View.GONE
    }

    private fun continuePermissionSetup() {
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this, R.string.role_unavailable_toast, Toast.LENGTH_LONG).show()
            return
        }
        if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            startActivityForResult(
                roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),
                ROLE_REQUEST_CODE
            )
            return
        }
        requestContactsPermission()
    }

    private fun requestContactsPermission() {
        if (hasContactsPermission()) {
            refreshStatus()
            return
        }
        val preferences = getSharedPreferences(UI_PREFERENCES, MODE_PRIVATE)
        val requestedBefore = preferences.getBoolean(KEY_CONTACTS_REQUESTED, false)
        if (requestedBefore && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
            showContactsSettingsDialog()
            return
        }
        preferences.edit().putBoolean(KEY_CONTACTS_REQUESTED, true).apply()
        requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), CONTACTS_REQUEST_CODE)
    }

    private fun showContactsSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.contacts_settings_title)
            .setMessage(R.string.contacts_settings_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                startActivity(Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                ))
            }
            .show()
    }

    @Deprecated("Uses legacy activity result callback for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ROLE_REQUEST_CODE) {
            if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                requestContactsPermission()
            } else {
                refreshStatus()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CONTACTS_REQUEST_CODE) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            Toast.makeText(
                this,
                if (granted) {
                    getString(R.string.protection_configured)
                } else {
                    getString(R.string.contacts_denied_safe)
                },
                Toast.LENGTH_LONG
            ).show()
            refreshStatus()
        }
    }

    private fun showHistory() {
        history.removeAllViews()
        val entries = BlockerStore.history(this)
        if (entries.isEmpty()) {
            history.addView(TextView(this).apply {
                text = getString(R.string.empty_history)
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(COLOR_MUTED)
                setLineSpacing(4.dp.toFloat(), 1f)
                setPadding(18.dp, 25.dp, 18.dp, 25.dp)
                background = rounded(Color.WHITE, 16, COLOR_BORDER)
            }, matchWidth(top = 5))
            return
        }

        val entriesByDay = linkedMapOf<LocalDate, MutableList<Pair<String, Long>>>()
        for (entry in entries) {
            entriesByDay.getOrPut(localDate(entry.second)) { mutableListOf() }.add(entry)
        }

        entriesByDay.entries.forEachIndexed { index, (date, dayEntries) ->
            history.addView(
                createDaySection(date, dayEntries, index),
                matchWidth(top = if (index == 0) 5 else 12)
            )
        }
    }

    private fun createDaySection(
        date: LocalDate,
        entries: List<Pair<String, Long>>,
        position: Int
    ): View {
        val accentColor = if (position % 2 == 0) COLOR_DAY_TEAL else COLOR_DAY_BLUE
        val headerColor = if (position % 2 == 0) COLOR_DAY_TEAL_SOFT else COLOR_DAY_BLUE_SOFT
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(7.dp, 7.dp, 7.dp, 4.dp)
            background = rounded(Color.WHITE, 17, COLOR_BORDER)
            elevation = 1.dp.toFloat()
        }

        val dayHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(13.dp, 10.dp, 10.dp, 10.dp)
            background = rounded(headerColor, 12)
        }
        dayHeader.addView(TextView(this).apply {
            text = dayLabel(date)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accentColor)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        dayHeader.addView(TextView(this).apply {
            val count = entries.size
            text = resources.getQuantityString(R.plurals.blocked_calls_count, count, count)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(accentColor)
            setPadding(10.dp, 5.dp, 10.dp, 5.dp)
            background = rounded(Color.WHITE, 12)
        })
        section.addView(dayHeader)

        entries.forEachIndexed { index, (number, timestamp) ->
            val callRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(10.dp, 11.dp, 10.dp, 11.dp)
            }
            callRow.addView(TextView(this).apply {
                text = number
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(COLOR_TEXT)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            callRow.addView(TextView(this).apply {
                text = DateFormat.getTimeFormat(this@MainActivity).format(Date(timestamp))
                textSize = 13f
                setTextColor(COLOR_MUTED)
            })
            section.addView(callRow)

            if (index < entries.lastIndex) {
                section.addView(View(this).apply {
                    setBackgroundColor(COLOR_DIVIDER)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp).apply {
                    marginStart = 10.dp
                    marginEnd = 10.dp
                })
            }
        }
        return section
    }

    private fun localDate(timestamp: Long): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun dayLabel(date: LocalDate): String {
        val today = LocalDate.now(ZoneId.systemDefault())
        return when (date) {
            today -> getString(R.string.today)
            today.minusDays(1) -> getString(R.string.yesterday)
            else -> {
                val pattern = if (date.year == today.year) {
                    getString(R.string.date_pattern_current_year)
                } else {
                    getString(R.string.date_pattern_other_year)
                }
                date.format(DateTimeFormatter.ofPattern(pattern, UI_LOCALE))
                    .replaceFirstChar { character ->
                        if (character.isLowerCase()) character.titlecase(UI_LOCALE)
                        else character.toString()
                    }
            }
        }
    }

    private fun hasContactsPermission(): Boolean =
        checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun primaryButton(label: String) = Button(this).apply {
        text = label
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = ripple(COLOR_TEAL, 14)
        elevation = 1.dp.toFloat()
    }

    private fun secondaryButton(label: String) = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        minWidth = 0
        minimumWidth = 0
        setPadding(12.dp, 0, 12.dp, 0)
        setTextColor(COLOR_NAVY)
        background = ripple(COLOR_ACTION_SOFT, 12)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            48.dp
        )
    }

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radiusDp.dp.toFloat()
            if (strokeColor != null) setStroke(1.dp, strokeColor)
        }

    private fun ripple(color: Int, radiusDp: Int) = RippleDrawable(
        ColorStateList.valueOf(Color.argb(45, 255, 255, 255)),
        rounded(color, radiusDp),
        null
    )

    private fun matchWidth(top: Int = 0, height: Int? = null) =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            height?.dp ?: ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = top.dp }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private companion object {
        const val ROLE_REQUEST_CODE = 100
        const val CONTACTS_REQUEST_CODE = 101
        const val UI_PREFERENCES = "ui_preferences"
        const val KEY_CONTACTS_REQUESTED = "contacts_requested"
        const val DRAWER_EDGE_DP = 28
        const val DRAWER_SWIPE_DP = 64
        const val DRAWER_ANIMATION_MS = 220L

        const val COLOR_NAVY = 0xFF12243A.toInt()
        const val COLOR_TEAL = 0xFF18A999.toInt()
        const val COLOR_TEAL_DARK = 0xFF087F73.toInt()
        const val COLOR_PAGE = 0xFFF4F7FA.toInt()
        const val COLOR_TEXT = 0xFF172B3A.toInt()
        const val COLOR_MUTED = 0xFF627483.toInt()
        const val COLOR_BORDER = 0xFFDCE5EA.toInt()
        const val COLOR_HEADER_MUTED = 0xFFC9D7E2.toInt()
        const val COLOR_ACTION_SOFT = 0xFFE8F1F5.toInt()
        const val COLOR_SUCCESS = 0xFF087F5B.toInt()
        const val COLOR_SUCCESS_SOFT = 0xFFE4F6EE.toInt()
        const val COLOR_WARNING = 0xFF8A5A00.toInt()
        const val COLOR_WARNING_SOFT = 0xFFFFF2D5.toInt()
        const val COLOR_DANGER = 0xFFA43A3A.toInt()
        const val COLOR_DANGER_SOFT = 0xFFFFE7E7.toInt()
        const val COLOR_NEUTRAL_SOFT = 0xFFE9EEF2.toInt()
        const val COLOR_DAY_TEAL = 0xFF087F73.toInt()
        const val COLOR_DAY_TEAL_SOFT = 0xFFE4F6F2.toInt()
        const val COLOR_DAY_BLUE = 0xFF2467A6.toInt()
        const val COLOR_DAY_BLUE_SOFT = 0xFFE7F1FA.toInt()
        const val COLOR_DIVIDER = 0xFFE8EEF2.toInt()

        val UI_LOCALE: Locale = Locale.forLanguageTag("es-MX")
    }
}
