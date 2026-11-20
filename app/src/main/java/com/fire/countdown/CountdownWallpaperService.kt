package com.fire.countdown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
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
        private val taskP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFD27A")
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.05f
        }

        // ---- user photo background ----
        private var bgBitmap: Bitmap? = null
        private var loadedVer = -1L
        private var usingPhoto = false

        // ---- procedural fire (noise based) ----
        private val fireW = 128
        private var fireH = 0
        private var pixels = IntArray(0)
        private var fireBmp: Bitmap? = null
        private val fireRect = RectF()
        private val firePaint = Paint().apply {
            isFilterBitmap = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
        private val lut = IntArray(256)
        private val colH = FloatArray(fireW)
        private val tbl = FloatArray(65536)

        private val embers = ArrayList<Ember>()
        private val start = System.currentTimeMillis()

        init {
            val rnd = java.util.Random(7)
            for (i in tbl.indices) tbl[i] = rnd.nextFloat()

            val stops = floatArrayOf(0f, 0.18f, 0.35f, 0.5f, 0.68f, 0.85f, 1f)
            val r = intArrayOf(0, 70, 175, 255, 255, 255, 255)
            val g = intArrayOf(0, 8, 28, 100, 175, 225, 248)
            val b = intArrayOf(0, 0, 0, 0, 10, 80, 200)
            for (i in 0 until 256) {
                val v = (i / 255f).pow(0.85f)
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

        private fun noise(x: Float, y: Float): Float {
            val xf = Math.floor(x.toDouble()).toInt()
            val yf = Math.floor(y.toDouble()).toInt()
            val fx = x - xf
            val fy = y - yf
            val sx = fx * fx * (3f - 2f * fx)
            val sy = fy * fy * (3f - 2f * fy)
            val x0 = xf and 255
            val x1 = (xf + 1) and 255
            val y0 = (yf and 255) shl 8
            val y1 = ((yf + 1) and 255) shl 8
            val a = tbl[y0 or x0]
            val b = tbl[y0 or x1]
            val c = tbl[y1 or x0]
            val d = tbl[y1 or x1]
            val top = a + (b - a) * sx
            val bot = c + (d - c) * sx
            return top + (bot - top) * sy
        }

        private val tick = object : Runnable {
            override fun run() {
                draw()
                if (visible) handler.postDelayed(this, if (usingPhoto) 100L else 33L)
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
            val regionH = h * 0.52f
            fireH = (fireW * regionH / w).toInt().coerceAtLeast(40)
            pixels = IntArray(fireW * fireH)
            fireBmp = Bitmap.createBitmap(fireW, fireH, Bitmap.Config.ARGB_8888)
            fireRect.set(0f, h - regionH, w.toFloat(), h.toFloat())

            // force the photo to be re-fitted to the new size
            bgBitmap?.recycle()
            bgBitmap = null
            loadedVer = -1L

            embers.clear()
            repeat(30) { embers.add(newEmber(true)) }
            draw()
        }

        private fun loadBackground(ver: Long) {
            loadedVer = ver
            bgBitmap?.recycle()
            bgBitmap = null
            val f = File(filesDir, "bg.jpg")
            if (!f.exists() || w == 0 || h == 0) return
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val src = BitmapFactory.decodeFile(f.path, opts) ?: return
                val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val cv = Canvas(out)
                val scale = max(w.toFloat() / src.width, h.toFloat() / src.height)
                val sw = w / scale
                val sh = h / scale
                val sl = (src.width - sw) / 2f
                val st = (src.height - sh) / 2f
                cv.drawBitmap(
                    src,
                    Rect(sl.toInt(), st.toInt(), (sl + sw).toInt(), (st + sh).toInt()),
                    Rect(0, 0, w, h),
                    Paint(Paint.FILTER_BITMAP_FLAG)
                )
                cv.drawColor(Color.argb(90, 0, 0, 0))
                src.recycle()
                bgBitmap = out
            } catch (e: Exception) {
            } catch (e: OutOfMemoryError) {
            }
        }

        private fun computeFire(t: Float) {
            val fw = fireW
            val fh = fireH
            for (x in 0 until fw) {
                colH[x] = noise((x + 0.5f) / fw * 3f + 40f, t * 0.35f)
            }
            var i = 0
            for (row in 0 until fh) {
                val yy = 1f - (row + 0.5f) / fh
                for (x in 0 until fw) {
                    val xx = (x + 0.5f) / fw
                    val wx = noise(xx * 3f + 5f, yy * 2.2f - t * 1.1f) - 0.5f
                    val wy = noise(xx * 3f + 9f, yy * 2.2f - t * 1.1f + 4f) - 0.5f
                    val px = xx * 8.5f + wx * 2.0f
                    val py = yy * 3.4f - t * 1.9f + wy * 1.1f

                    var s = 0f
                    var amp = 0.5f
                    var f = 1f
                    for (o in 0 until 4) {
                        s += amp * noise(px * f + 17f, py * f + 31f)
                        amp *= 0.5f
                        f *= 2f
                    }
                    val fb = s / 0.9375f

                    var rg = 0f
                    amp = 0.5f
                    f = 1f
                    for (o in 0 until 3) {
                        val n = 1f - abs(2f * noise(px * f + 51f, py * f + 7f) - 1f)
                        rg += amp * n * n
                        amp *= 0.5f
                        f *= 2f
                    }
                    rg /= 0.875f

                    val n = 0.55f * fb + 0.45f * rg
                    var v = n * 2.4f - yy * (1.85f - 1.2f * colH[x]) - 0.52f
                    if (v < 0f) v = 0f else if (v > 1f) v = 1f
                    pixels[i++] = lut[(v * 255f).toInt()]
                }
            }
        }

        private fun drawFire(c: Canvas, t: Float) {
            val bmp = fireBmp ?: return
            computeFire(t)
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
            bgBitmap?.recycle()
            bgBitmap = null
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

        private fun drawTask(c: Canvas, task: String, y: Float) {
            if (task.isBlank()) return
            taskP.textSize = w * 0.07f
            val maxW = w * 0.9f
            val mw = taskP.measureText(task)
            if (mw > maxW) taskP.textSize = taskP.textSize * maxW / mw
            taskP.setShadowLayer(w * 0.02f, 0f, 0f, Color.parseColor("#FF5A00"))
            c.drawText(task, w / 2f, y, taskP)
        }

        private fun render(c: Canvas) {
            val t = (System.currentTimeMillis() - start) / 1000f

            val prefs = getSharedPreferences("cd", Context.MODE_PRIVATE)
            val target = prefs.getLong("target", 0L)
            val mode = prefs.getInt("mode", 0)
            val task = (prefs.getString("task", "") ?: "").trim()
            val bgMode = prefs.getInt("bgMode", 0)
            val bgVer = prefs.getLong("bgVer", 0L)

            if (bgMode == 1) {
                if (loadedVer != bgVer) loadBackground(bgVer)
            } else if (bgBitmap != null) {
                bgBitmap?.recycle()
                bgBitmap = null
                loadedVer = -1L
            }
            val photo = if (bgMode == 1) bgBitmap else null
            usingPhoto = photo != null

            if (photo != null) {
                c.drawBitmap(photo, 0f, 0f, null)
            } else {
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
                drawFire(c, t)
                drawEmbers(c, t)
            }

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
                drawTask(c, task, cy + w * 0.29f)
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
                drawTask(c, task, cy + w * 0.34f)
            } else {
                timeP.textSize = w * 0.17f
                c.drawText(String.format("%02d:%02d:%02d", hh, mm, ss), cx, cy, timeP)
                drawTask(c, task, cy + w * 0.15f)
            }
        }
    }
}
