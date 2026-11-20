package com.fire.countdown

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var photoInfo: TextView
    private lateinit var bgGroup: RadioGroup
    private val reqPhoto = 301

    private fun prefs() = getSharedPreferences("cd", MODE_PRIVATE)
    private fun photoFile() = File(filesDir, "bg.jpg")

    private fun sectionLabel(t: String, top: Int): TextView = TextView(this).apply {
        text = t
        textSize = 14f
        setTextColor(Color.parseColor("#FF8A2A"))
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, top, 0, 10)
    }

    private fun radio(rid: Int, label: String): RadioButton = RadioButton(this).apply {
        id = rid
        text = label
        setTextColor(Color.parseColor("#FFD27A"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#0B0B0F"))
            setPadding(60, 80, 60, 80)
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
            setPadding(0, 40, 0, 20)
        }

        val taskInput = EditText(this).apply {
            hint = "e.g. Maths exam"
            setHintTextColor(Color.parseColor("#80FFD27A"))
            setTextColor(Color.parseColor("#FFE9B0"))
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(40))
            setText(prefs().getString("task", ""))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    prefs().edit().putString("task", s?.toString() ?: "").apply()
                }
            })
        }

        // display format
        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        group.addView(radio(101, "Days + hours (2 days 10:58:09)"))
        group.addView(radio(102, "Hours + minutes + seconds (58:59:12)"))
        group.check(if (prefs().getInt("mode", 0) == 0) 101 else 102)
        group.setOnCheckedChangeListener { _, checkedId ->
            prefs().edit().putInt("mode", if (checkedId == 101) 0 else 1).apply()
        }

        // background
        bgGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        bgGroup.addView(radio(201, "Fire (default)"))
        bgGroup.addView(radio(202, "My photo"))
        bgGroup.check(if (prefs().getInt("bgMode", 0) == 1 && photoFile().exists()) 202 else 201)
        bgGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == 202 && !photoFile().exists()) {
                Toast.makeText(this, "Choose a photo first", Toast.LENGTH_SHORT).show()
                bgGroup.check(201)
            } else {
                prefs().edit().putInt("bgMode", if (checkedId == 202) 1 else 0).apply()
            }
        }

        photoInfo = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#B0FFD27A"))
            setPadding(0, 0, 0, 10)
        }

        val pick = Button(this).apply {
            text = "Pick date & time"
            setOnClickListener { pickDateTime() }
        }
        val choosePhoto = Button(this).apply {
            text = "Choose photo"
            setOnClickListener { choosePhoto() }
        }
        val setLock = Button(this).apply {
            text = "Set on lock screen only"
            setOnClickListener { applyWallpaper(true) }
        }
        val setBoth = Button(this).apply {
            text = "Set on home + lock screen"
            setOnClickListener { applyWallpaper(false) }
        }
        val pause = Button(this).apply {
            text = "Pause (back to default wallpaper)"
            setOnClickListener { pauseWallpaper() }
        }

        val full = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        root.addView(title)
        root.addView(status)
        root.addView(pick, full)
        root.addView(sectionLabel("Task name (shown under the time)", 40))
        root.addView(taskInput, full)
        root.addView(sectionLabel("Display format", 40))
        root.addView(group)
        root.addView(sectionLabel("Background", 40))
        root.addView(bgGroup)
        root.addView(photoInfo)
        root.addView(choosePhoto, full)
        root.addView(sectionLabel("Apply wallpaper", 40))
        root.addView(setLock, full)
        root.addView(setBoth, full)
        root.addView(sectionLabel("Pause", 40))
        root.addView(pause, full)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0B0F"))
            isFillViewport = true
            addView(root)
        }
        setContentView(scroll)
        refreshStatus()
    }

    private fun refreshStatus() {
        val t = prefs().getLong("target", 0L)
        status.text = if (t == 0L) "No target set yet.\nTap 'Pick date & time'."
        else "Counting down to\n" +
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(t))
        photoInfo.text = if (photoFile().exists()) "A photo is saved." else "No photo chosen yet."
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

    private fun choosePhoto() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(i, reqPhoto)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqPhoto && resultCode == RESULT_OK) {
            data?.data?.let { saveBackground(it) }
        }
    }

    private fun saveBackground(uri: Uri) {
        Thread {
            var ok = false
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                var bmp: Bitmap? = contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                if (bmp != null) {
                    val orient = try {
                        contentResolver.openInputStream(uri)?.use {
                            ExifInterface(it).getAttributeInt(
                                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                            )
                        } ?: ExifInterface.ORIENTATION_NORMAL
                    } catch (e: Exception) {
                        ExifInterface.ORIENTATION_NORMAL
                    }
                    val deg = when (orient) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                    if (deg != 0f) {
                        val m = Matrix().apply { postRotate(deg) }
                        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                    }
                    FileOutputStream(photoFile()).use { bmp!!.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    prefs().edit()
                        .putLong("bgVer", System.currentTimeMillis())
                        .putInt("bgMode", 1)
                        .apply()
                    ok = true
                }
            } catch (e: Exception) {
            } catch (e: OutOfMemoryError) {
            }
            runOnUiThread {
                if (ok) {
                    bgGroup.check(202)
                    Toast.makeText(this, "Photo saved", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Could not use that photo", Toast.LENGTH_SHORT).show()
                }
                refreshStatus()
            }
        }.start()
    }

    private fun applyWallpaper(lockOnly: Boolean) {
        val choice = if (lockOnly) "'Lock screen'" else "'Home screen and lock screen'"
        AlertDialog.Builder(this)
            .setTitle("One more step")
            .setMessage(
                "On the next screen tap 'Set wallpaper', then choose $choice.\n\n" +
                    "Android decides where a live wallpaper applies, so the final choice is made there."
            )
            .setPositiveButton("Continue") { _, _ -> openWallpaperPicker() }
            .setNegativeButton("Cancel", null)
            .show()
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

    private fun pauseWallpaper() {
        val wm = WallpaperManager.getInstance(this)
        val info = wm.wallpaperInfo
        if (info == null || info.packageName != packageName) {
            Toast.makeText(this, "Fire Countdown is not your current wallpaper", Toast.LENGTH_LONG).show()
            return
        }
        try {
            wm.clear(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            Toast.makeText(
                this,
                "Paused. Default wallpaper is back. Tap a Set button to resume.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Pick another wallpaper to pause", Toast.LENGTH_LONG).show()
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }
}
