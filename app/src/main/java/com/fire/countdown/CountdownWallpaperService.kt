package com.fire.countdown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
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
        private val flame = Paint(Paint.ANTI_ALIAS_FLAG)
        private val flameCore = Paint(Paint.ANTI_ALIAS_FLAG)
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

        private val embers = ArrayList<Ember>()
        private val path = Path()
        private val start = System.currentTimeMillis()

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
                intArrayOf(Color.parseColor("#050507"), Color.parseColor("#120806"), Color.parseColor("#3A0E04")),
                floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP
            )
            flame.shader = LinearGradient(
                0f, h.toFloat(), 0f, h * 0.62f,
                intArrayOf(Color.parseColor("#E6FFB000"), Color.parseColor("#CCFF5A00"), Color.parseColor("#00B01000")),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
            )
            flameCore.shader = LinearGradient(
                0f, h.toFloat(), 0f, h * 0.78f,
                intArrayOf(Color.parseColor("#F0FFF2B0"), Color.parseColor("#00FFB000")),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
            embers.clear()
            repeat(45) { embers.add(newEmber(true)) }
            draw()
        }

        private fun newEmber(anywhere: Boolean): Ember = Ember(
            Random.nextFloat() * w,
            if (anywhere) Random.nextFloat() * h else h + 10f,
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

        private fun drawFlames(c: Canvas, t: Float) {
            val cols = 9
            val colW = w / cols.toFloat()
            for (i in 0..cols) {
                val cx = i * colW
                val n = sin(t * 3.1f + i * 1.7f) * 0.5f + sin(t * 5.3f + i * 0.9f) * 0.5f
                val height = h * (0.20f + 0.07f * n + 0.03f * (i % 3))
                val sway = sin(t * 2f + i) * colW * 0.35f
                path.reset()
                path.moveTo(cx - colW * 0.9f, h.toFloat())
                path.quadTo(cx - colW * 0.3f + sway * 0.4f, h - height * 0.55f, cx + sway, h - height)
                path.quadTo(cx + colW * 0.3f + sway * 0.4f, h - height * 0.55f, cx + colW * 0.9f, h.toFloat())
                path.close()
                c.drawPath(path, flame)

                val ih = height * 0.55f
                path.reset()
                path.moveTo(cx - colW * 0.45f, h.toFloat())
                path.quadTo(cx - colW * 0.1f + sway * 0.3f, h - ih * 0.55f, cx + sway * 0.6f, h - ih)
                path.quadTo(cx + colW * 0.1f + sway * 0.3f, h - ih * 0.55f, cx + colW * 0.45f, h.toFloat())
                path.close()
                c.drawPath(path, flameCore)
            }
        }

        private fun drawEmbers(c: Canvas, t: Float) {
            for (e in embers) {
                e.y -= e.vy
                e.x += sin(t * 2f + e.phase) * 1.2f
                if (e.y < -10f) {
                    val n = newEmber(false)
                    e.x = n.x; e.y = n.y; e.vy = n.vy; e.size = n.size; e.phase = n.phase
                }
                val life = (e.y / h).coerceIn(0f, 1f)
                val alpha = (life * 255).toInt().coerceIn(0, 255)
                ember.color = Color.argb(alpha, 255, (120 + 100 * life).toInt().coerceAtMost(255), 30)
                c.drawCircle(e.x, e.y, e.size * (0.4f + life), ember)
            }
        }

        private fun render(c: Canvas) {
            val t = (System.currentTimeMillis() - start) / 1000f
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
            drawFlames(c, t)
            drawEmbers(c, t)

            val target = getSharedPreferences("cd", Context.MODE_PRIVATE).getLong("target", 0L)
            val cx = w / 2f
            val cy = h * 0.40f

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

            val glow = 0.75f + 0.25f * abs(sin(t * 2.2f))
            timeP.setShadowLayer(w * 0.05f * glow, 0f, 0f, Color.parseColor("#FF5A00"))

            c.drawText(if (done) "TIME'S UP" else "TIME LEFT", cx, cy - w * 0.22f, labelP)

            if (d > 0) {
                timeP.textSize = w * 0.16f
                c.drawText("$d", cx - w * 0.18f, cy - w * 0.02f, timeP)
                labelP.textSize = w * 0.04f
                c.drawText(if (d == 1L) "DAY" else "DAYS", cx + w * 0.2f, cy - w * 0.02f, labelP)
                timeP.textSize = w * 0.15f
                c.drawText(String.format("%02d:%02d:%02d", hh, mm, ss), cx, cy + w * 0.18f, timeP)
            } else {
                timeP.textSize = w * 0.17f
                c.drawText(String.format("%02d:%02d:%02d", hh, mm, ss), cx, cy, timeP)
            }
        }
    }
}
