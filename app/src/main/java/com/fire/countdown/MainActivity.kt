package com.fire.countdown

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0B0B0F"))
            setPadding(60, 60, 60, 60)
        }

        val title = TextView(this).apply {
            text = "FIRE COUNTDOWN"
            textSize = 28f
            setTextColor(Color.parseColor("#FF6A00"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        status = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.parseColor("#FFD27A"))
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 60)
        }

        val pick = Button(this).apply {
            text = "Pick date & time"
            setOnClickListener { pickDateTime() }
        }

        val set = Button(this).apply {
            text = "Set as live wallpaper"
            setOnClickListener { openWallpaperPicker() }
        }

        root.addView(title)
        root.addView(status)
        root.addView(pick)
        root.addView(set)
        setContentView(root)
        refreshStatus()
    }

    private fun prefs() = getSharedPreferences("cd", MODE_PRIVATE)

    private fun refreshStatus() {
        val t = prefs().getLong("target", 0L)
        status.text = if (t == 0L) "No target set yet.\nTap 'Pick date & time'."
        else "Counting down to\n" +
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(t))
    }

    private fun pickDateTime() {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            TimePickerDialog(this, { _, h, min ->
                val target = Calendar.getInstance().apply {
                    set(y, m, d, h, min, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                prefs().edit().putLong("target", target.timeInMillis).apply()
                refreshStatus()
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun openWallpaperPicker() {
        try {
            val i = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, CountdownWallpaperService::class.java)
                )
            }
            startActivity(i)
        } catch (e: Exception) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }
}
