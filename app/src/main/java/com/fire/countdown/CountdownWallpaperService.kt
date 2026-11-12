package com.fire.countdown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

internal class Ember(var x: Float, var y: Float, var vy: Float, var size: Float, var phase: Float)

class CountdownWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = FireEngine()

    inner class FireEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var w = 0
        private var h = 0

        private val bg = Paint()
        private val ember = Paint(Paint.ANTI_ALIAS_FLAG)
        private val timeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFE9B0")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        private val labelP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF8A2A")
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.3f
        }

        // ---- realistic fire simulation ----
        private val fireW = 110
        private var fireH = 0
        private var heat = FloatArray(0)
        private var pixels = IntArray(0)
        private var fireBmp: Bitmap? = null
        private val fireRect = RectF()
        private val firePaint = Paint().apply {
            isFilterBitmap = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
        private val lut = IntArray(256)
        private val cool = 0.011f

        private val embers = ArrayList<Ember>()
        private val start = System.currentTimeMillis()

        init {
            val stops = floatArrayOf(0f, 0.18f, 0.35f, 0.55f, 0.75f, 0.9f, 1f)
            val r = intArrayOf(0, 70, 175, 255, 255, 255, 255)
            val g = intArrayOf(0, 8, 28, 95, 160, 215, 245)
            val b = intArrayOf(0, 0, 0, 0, 10, 70, 190)
            for (i in 0 until 256) {
                val v = i / 255f
                var k = 0
                while (k < stops.size - 2 && v > stops[k + 1]) k++
                val f = ((v - stops[k]) / (stops[k + 1] - stops[k])).coerceIn(0f, 1f)
                lut[i] = Color.rgb(
                    (r[k] + (r[k + 1] - r[k]) * f).toInt(),
                    (g[k] + (g[k + 1] - g[k]) * f).toInt(),
                    (b[k] + (b[k + 1] - b[k]) * f).toInt()
                )
            }
        }

        private val tick = object : Runnable {
            override fun run() {
                draw()
                if (visible) handler.postDelayed(this, 33)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width; h = height
            bg.shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(Color.parseColor("#050507"), Color.parseColor("#120806"), Color.parseColor("#2A0A03")),
                floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP
            )
            val regionH = h * 0.5f
            fireH = (fireW * regionH / w).toInt().coerceAtLeast(40)
            heat = FloatArray(fireW * fireH)
            pixels = IntArray(fireW * fireH)
            fireBmp = Bitmap.createBitmap(fireW, fireH, Bitmap.Config.ARGB_8888)
            fireRect.set(0f, h - regionH, w.toFloat(), h.toFloat())
            for (i in 0 until 200) stepFire(i * 0.03f)

            embers.clear()
            repeat(30) { embers.add(newEmber(true)) }
            draw()
        }

        private fun stepFire(t: Float) {
            val fw = fireW
            val fh = fireH
            val baseRow = (fh - 1) * fw
            for (x in 0 until fw) {
                val s1 = 0.5f + 0.5f * sin(x * 0.21f + t * 1.7f)
                val s2 = 0.6f + 0.4f * sin(x * 0.067f - t * 0.9f + 1.3f)
                val v = 0.3f + 0.9f * s1 * s2 + (Random.nextFloat() - 0.5f) * 0.3f
                heat[baseRow + x] = v.coerceIn(0f, 1.2f)
            }
            for (y in 0 until fh - 1) {
                val row = y * fw
                val below = (y + 1) * fw
                for (x in 0 until fw) {
                    val r = Random.nextInt(8)
                    var sx = if (r == 0) x - 1 else if (r == 1) x + 1 else x
                    if (sx < 0) sx = 0 else if (sx >= fw) sx = fw - 1
                    val v = heat[below + sx] - cool * Random.nextFloat() * 2f
                    heat[row + x] = if (v > 0f) v else 0f
                }
            }
        }

        private fun drawFire(c: Canvas, t: Float) {
            val bmp = fireBmp ?: return
            stepFire(t)
            stepFire(t + 0.016f)
            for (i in pixels.indices) {
                val v = heat[i]
                val idx = if (v <= 0f) 0 else if (v >= 1f) 255 else (v * 255f).toInt()
                pixels[i] = lut[idx]
            }
            bmp.setPixels(pixels, 0, fireW, 0, 0, fireW, fireH)
            c.drawBitmap(bmp, null, fireRect, firePaint)
        }

        private fun newEmber(anywhere: Boolean): Ember = Ember(
            Random.nextFloat() * w,
            if (anywhere) h * (0.5f + Random.nextFloat() * 0.5f) else h + 10f,
            2f + Random.nextFloat() * 4f,
            2f + Random.nextFloat() * 4f,
            Random.nextFloat() * 6.28f
        )

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            handler.removeCallbacks(tick)
            if (v) handler.post(tick)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(tick)
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(tick)
        }

        private fun draw() {
            val holder = surfaceHolder
            var c: Canvas? = null
            try {
                c = holder.lockCanvas()
                if (c != null) render(c)
            } catch (e: Exception) {
            } finally {
                if (c != null) {
                    try { holder.unlockCanvasAndPost(c) } catch (e: Exception) {}
                }
            }
        }

        private fun drawEmbers(c: Canvas, t: Float) {
            for (e in embers) {
                e.y -= e.vy
                e.x += sin(t * 2f + e.phase) * 1.2f
                if (e.y < h * 0.3f) {
                    val n = newEmber(false)
                    e.x = n.x; e.y = n.y; e.vy = n.vy; e.size = n.size; e.phase = n.phase
                }
                val life = ((e.y - h * 0.3f) / (h * 0.7f)).coerceIn(0f, 1f)
                val alpha = (life * 255).toInt().coerceIn(0, 255)
                ember.color = Color.argb(alpha, 255, (120 + 100 * life).toInt().coerceAtMost(255), 30)
                c.drawCircle(e.x, e.y, e.size * (0.4f + life), ember)
            }
        }

        private fun render(c: Canvas) {
            val t = (System.currentTimeMillis() - start) / 1000f
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
            drawFire(c, t)
            drawEmbers(c, t)

            val prefs = getSharedPreferences("cd", Context.MODE_PRIVATE)
            val target = prefs.getLong("target", 0L)
            val mode = prefs.getInt("mode", 0)
            val cx = w / 2f
            val cy = h * 0.36f

            labelP.textSize = w * 0.045f
            if (target == 0L) {
                timeP.textSize = w * 0.07f
                timeP.clearShadowLayer()
                c.drawText("OPEN THE APP", cx, cy, timeP)
                c.drawText("TO SET A DATE", cx, cy + w * 0.1f, timeP)
                return
            }

            var diff = (target - System.currentTimeMillis()) / 1000
            val done = diff <= 0
            if (done) diff = 0
            val d = diff / 86400
            val hh = (diff % 86400) / 3600
            val mm = (diff % 3600) / 60
            val ss = diff % 60
            val totalH = diff / 3600

            val glow = 0.75f + 0.25f * abs(sin(t * 2.2f))
            timeP.setShadowLayer(w * 0.05f * glow, 0f, 0f, Color.parseColor("#FF5A00"))

            c.drawText(if (done) "TIME'S UP" else "TIME LEFT", cx, cy - w * 0.22f, labelP)

            if (mode == 1) {
                val str = String.format("%02d:%02d:%02d", totalH, mm, ss)
                timeP.textSize = w * 0.2f
                val mw = timeP.measureText(str)
                val maxW = w * 0.92f
                if (mw > maxW) timeP.textSize = timeP.textSize * maxW / mw
                c.drawText(str, cx, cy + w * 0.04f, timeP)
                labelP.textSize = w * 0.035f
                c.drawText("HOURS : MIN : SEC", cx, cy + w * 0.15f, labelP)
            } else if (d > 0) {
                timeP.textSize = w * 0.16f
                val numStr = d.toString()
                val numW = timeP.measureText(numStr)
                labelP.textSize = w * 0.045f
                val lab = if (d == 1L) "DAY" else "DAYS"
                val labW = labelP.measureText(lab)
                val gap = w * 0.04f
                val startX = cx - (numW + gap + labW) / 2f
                c.drawText(numStr, startX + numW / 2f, cy - w * 0.02f, timeP)
                c.drawText(lab, startX + numW + gap + labW / 2f, cy - w * 0.02f, labelP)
                timeP.textSize = w * 0.15f
                c.drawText(String.format("%02d:%02d:%02d", hh, mm, ss), cx, cy + w * 0.18f, timeP)
            } else {
                timeP.textSize = w * 0.17f
                c.drawText(String.format("%02d:%02d:%02d", hh, mm, ss), cx, cy, timeP)
            }
        }
    }
}
