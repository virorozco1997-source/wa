package com.example.wawrapper

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class ColorsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("wa", MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(40), dp(24), dp(32))
        }

        root.addView(TextView(this).apply { text = "Colores del chat"; textSize = 24f })
        root.addView(TextView(this).apply {
            text = "Se aplican al volver a WhatsApp. El primer círculo restablece el original."
            setPadding(0, dp(8), 0, 0)
        })

        root.addView(
            section(
                "Mis mensajes", "c_out",
                listOf("", "#d9fdd3", "#cfe9ff", "#e8d9ff", "#ffd9e6", "#ffe8c7", "#2563eb", "#7c3aed", "#0f766e")
            )
        )
        root.addView(
            section(
                "Mensajes recibidos", "c_in",
                listOf("", "#ffffff", "#f0f2f5", "#e9edef", "#fff4d6", "#d9e2ec", "#2a3942", "#1f2c34", "#000000")
            )
        )
        root.addView(
            section(
                "Fondo del chat", "c_bg",
                listOf("", "#ffffff", "#f7f7f8", "#efeae2", "#e6f0ff", "#fdf2f8", "#111b21", "#0b141a", "#000000")
            )
        )

        root.addView(SwitchCompat(this).apply {
            text = "Diseño simple (sin dibujos de fondo)"
            isChecked = prefs.getBoolean("simple", true)
            setPadding(0, dp(28), 0, 0)
            setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean("simple", c).apply() }
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun section(label: String, key: String, colors: List<String>): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(24), 0, 0)
        }
        box.addView(TextView(this).apply { text = label; textSize = 16f })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(3), dp(10), dp(3), dp(6))
        }
        val swatches = mutableListOf<TextView>()

        fun refresh() {
            val current = prefs.getString(key, "") ?: ""
            swatches.forEachIndexed { i, t -> paint(t, colors[i], current == colors[i]) }
        }

        colors.forEach { c ->
            val t = TextView(this).apply {
                gravity = Gravity.CENTER
                textSize = 18f
                text = if (c.isEmpty()) "⟲" else ""
                setOnClickListener {
                    prefs.edit().putString(key, c).apply()
                    refresh()
                }
            }
            row.addView(t, LinearLayout.LayoutParams(dp(42), dp(42)).apply { rightMargin = dp(10) })
            swatches.add(t)
        }
        box.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        })
        refresh()
        return box
    }

    private fun paint(t: TextView, color: String, selected: Boolean) {
        t.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (color.isEmpty()) Color.WHITE else Color.parseColor(color))
            setStroke(dp(if (selected) 3 else 1), if (selected) Color.parseColor("#25D366") else Color.GRAY)
        }
        t.setTextColor(Color.DKGRAY)
    }
}
