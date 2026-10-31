package com.example.nordcallblocker

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Date

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var history: LinearLayout
    private lateinit var toggle: Switch
    private lateinit var permissionsButton: Button
    private val roleManager by lazy { getSystemService(RoleManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_PAGE)
        }
        applySystemBarInsets(root)

        root.addView(createHeader())

        status = TextView(this).apply {
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(16.dp, 14.dp, 16.dp, 14.dp)
        }
        root.addView(status, matchWidth(top = 14))

        permissionsButton = primaryButton("Configurar protección").apply {
            setOnClickListener { continuePermissionSetup() }
        }
        root.addView(permissionsButton, matchWidth(top = 10, height = 52))

        root.addView(createProtectionCard(), matchWidth(top = 14))

        root.addView(TextView(this).apply {
            text = "Privacidad primero"
            textSize = 13f
            setTextColor(COLOR_TEAL_DARK)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(2.dp, 14.dp, 2.dp, 3.dp)
        })
        root.addView(TextView(this).apply {
            text = "Los contactos se consultan únicamente en este teléfono. Antes del primer desbloqueo se permiten todas las llamadas para no bloquear personas guardadas."
            textSize = 13f
            setTextColor(COLOR_MUTED)
            setLineSpacing(2.dp.toFloat(), 1f)
            setPadding(2.dp, 0, 2.dp, 14.dp)
        })

        root.addView(createHistoryHeader())

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 4.dp, 0, 8.dp)
        }
        history = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(history)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setContentView(root)
        // Some OEM implementations do not create DecorView's insets controller until the
        // content view is attached. Deferring this avoids a startup crash on OxygenOS.
        root.post { useDarkSystemBarIcons() }
    }

    private fun applySystemBarInsets(root: View) {
        val horizontalPadding = 18.dp
        val contentTopPadding = 18.dp
        val contentBottomPadding = 14.dp

        root.setOnApplyWindowInsetsListener { view, insets ->
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
            view.setPadding(
                horizontalPadding,
                systemTop + contentTopPadding,
                horizontalPadding,
                systemBottom + contentBottomPadding
            )
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

    private fun createHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18.dp, 18.dp, 18.dp, 18.dp)
            background = rounded(COLOR_NAVY, 22)
            elevation = 3.dp.toFloat()
        }

        header.addView(TextView(this).apply {
            text = "NC"
            gravity = Gravity.CENTER
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = rounded(COLOR_TEAL, 18)
        }, LinearLayout.LayoutParams(54.dp, 54.dp))

        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp, 0, 0, 0)
        }
        titles.addView(TextView(this).apply {
            text = "Nord Call Blocker"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        titles.addView(TextView(this).apply {
            text = "Tus llamadas, bajo control"
            textSize = 14f
            setTextColor(COLOR_HEADER_MUTED)
            setPadding(0, 3.dp, 0, 0)
        })
        header.addView(titles)
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
            text = "Bloquear números no guardados"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_TEXT)
            isChecked = BlockerStore.enabled(this@MainActivity)
            thumbTintList = ColorStateList.valueOf(COLOR_TEAL)
            setOnCheckedChangeListener { _, checked ->
                if (!BlockerStore.setEnabled(this@MainActivity, checked)) {
                    Toast.makeText(
                        this@MainActivity,
                        "No se pudo guardar el ajuste",
                        Toast.LENGTH_LONG
                    ).show()
                }
                refreshStatus()
            }
        }
        card.addView(toggle)
        card.addView(TextView(this).apply {
            text = "Los contactos guardados y las decisiones inciertas siempre se permiten."
            textSize = 13f
            setTextColor(COLOR_MUTED)
            setPadding(0, 7.dp, 0, 0)
        })
        return card
    }

    private fun createHistoryHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(2.dp, 2.dp, 0, 5.dp)
        }
        row.addView(TextView(this).apply {
            text = "Historial de bloqueos"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_TEXT)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(secondaryButton("Actualizar").apply {
            setOnClickListener { showHistory() }
        })
        row.addView(secondaryButton("Borrar").apply {
            setOnClickListener {
                if (!BlockerStore.clear(this@MainActivity)) {
                    Toast.makeText(
                        this@MainActivity,
                        "No se pudo borrar el historial",
                        Toast.LENGTH_LONG
                    ).show()
                }
                showHistory()
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            42.dp
        ).apply { marginStart = 6.dp })
        return row
    }

    private fun refreshStatus() {
        val roleAvailable = roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
        val roleGranted = roleAvailable && roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        val contactsGranted = hasContactsPermission()
        val enabled = BlockerStore.enabled(this)

        val (message, backgroundColor, textColor) = when {
            !roleAvailable -> Triple(
                "No disponible · el sistema no ofrece filtrado de llamadas",
                COLOR_DANGER_SOFT,
                COLOR_DANGER
            )
            !roleGranted -> Triple(
                "Configuración pendiente · falta acceso de filtrado",
                COLOR_WARNING_SOFT,
                COLOR_WARNING
            )
            !contactsGranted -> Triple(
                "Protección pausada · falta acceso a contactos",
                COLOR_WARNING_SOFT,
                COLOR_WARNING
            )
            enabled -> Triple(
                "Protección activa · contactos permitidos",
                COLOR_SUCCESS_SOFT,
                COLOR_SUCCESS
            )
            else -> Triple(
                "Protección desactivada",
                COLOR_NEUTRAL_SOFT,
                COLOR_MUTED
            )
        }

        status.text = message
        status.setTextColor(textColor)
        status.background = rounded(backgroundColor, 14)

        permissionsButton.text = when {
            !roleGranted -> "Configurar protección"
            !contactsGranted -> "Completar acceso a contactos"
            else -> "Permisos configurados"
        }
        permissionsButton.isEnabled = roleAvailable && !(roleGranted && contactsGranted)
        permissionsButton.alpha = if (permissionsButton.isEnabled) 1f else 0.68f
    }

    private fun continuePermissionSetup() {
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this, "Tu sistema no ofrece el rol de filtrado", Toast.LENGTH_LONG).show()
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
        requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), CONTACTS_REQUEST_CODE)
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
                    "Protección configurada"
                } else {
                    "Sin contactos, las llamadas se permitirán por seguridad"
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
                text = "Sin llamadas bloqueadas\nCuando se bloquee un número no guardado, aparecerá aquí."
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(COLOR_MUTED)
                setLineSpacing(4.dp.toFloat(), 1f)
                setPadding(18.dp, 25.dp, 18.dp, 25.dp)
                background = rounded(Color.WHITE, 16, COLOR_BORDER)
            }, matchWidth(top = 5))
            return
        }

        for ((number, timestamp) in entries) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(15.dp, 12.dp, 15.dp, 12.dp)
                background = rounded(Color.WHITE, 14, COLOR_BORDER)
            }
            card.addView(TextView(this).apply {
                text = number
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(COLOR_TEXT)
            })
            card.addView(TextView(this).apply {
                text = DateFormat.format("dd/MM/yyyy · HH:mm:ss", Date(timestamp))
                textSize = 13f
                setTextColor(COLOR_MUTED)
                setPadding(0, 4.dp, 0, 0)
            })
            history.addView(card, matchWidth(top = 7))
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
            42.dp
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
    }
}
