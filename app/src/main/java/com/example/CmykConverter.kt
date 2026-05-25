package com.example

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.min

data class CmykSettings(
    val profileMode: ProfileMode = ProfileMode.ISO_COATED,
    val blackStrength: Float = 1.0f,
    val blackStartThreshold: Float = 0.1f, // 0 means black starts immediately, 0.5 means only shadows
    val tacLimit: Int = 300, // Total Ink Coverage limit (0 - 400%)
    val tintSeparations: Boolean = true // True for colored tints, False for classic film negatives (grayscale)
)

enum class ProfileMode(val displayName: String, val defaultTacLimit: Int, val description: String) {
    ISO_COATED("ISO Coated v2 (Fogra 39)", 300, "Standard for high-quality commercial print on coated paper."),
    SWOP_COATED("SWOP Web Coated v2", 320, "Standard for US magazine/rotary press publication with light/medium coated paper."),
    SNAP_NEWSPRINT("SNAP Newsprint", 220, "Uncoated newsprint standard with high ink absorption and 220% ink limit."),
    GRAVURE_PSR("PSR Gravure LWC", 270, "Rotogravure printing on lightweight coated paper."),
    CUSTOM_GCR("Custom GCR Model", 330, "Gray Component Replacement. Set black start threshold and strength."),
    CUSTOM_UCR("Custom UCR Model", 280, "Under Color Removal. Set black generation limited to deep shadow neutral regions.")
}

data class PixelColorInfo(
    val x: Int,
    val y: Int,
    val r: Int,
    val g: Int,
    val b: Int,
    val hex: String,
    val c: Int, // 0 - 100
    val m: Int,
    val yCmyk: Int,
    val k: Int,
    val tac: Int // Total Ink Coverage (C+M+Y+K)
)

object CmykConverter {

    /**
     * Converts a single RGB color (0-1 floats) to CMYK floats based on the supplied settings.
     * Respects TAC (Total Ink Coverage) policies.
     */
    fun rgbToCmyk(r: Float, g: Float, b: Float, settings: CmykSettings): FloatArray {
        val cPrime = 1f - r
        val mPrime = 1f - g
        val yPrime = 1f - b

        // Find the maximum neutral component (gray core)
        val neutral = minOf(cPrime, mPrime, yPrime)

        var k = 0f
        var c = cPrime
        var m = mPrime
        var y = yPrime

        when (settings.profileMode) {
            ProfileMode.ISO_COATED -> {
                // ISO Coated Standard: Medium GCR.
                // Black generation starts after ~15% grey density.
                val kStart = 0.15f
                if (neutral > kStart) {
                    k = ((neutral - kStart) / (1f - kStart)) * settings.blackStrength
                }
                k = k.coerceIn(0f, neutral)
                c = cPrime - k
                m = mPrime - k
                y = yPrime - k
            }
            ProfileMode.SWOP_COATED -> {
                // SWOP Coated: Light GCR.
                // Black starts later (around 30% grey thickness) to preserve colorful dynamic ranges.
                val kStart = 0.30f
                if (neutral > kStart) {
                    k = ((neutral - kStart) / (1f - kStart)) * settings.blackStrength
                }
                k = k.coerceIn(0f, neutral)
                c = cPrime - k
                m = mPrime - k
                y = yPrime - k
            }
            ProfileMode.SNAP_NEWSPRINT -> {
                // SNAP Newsprint: Heavy GCR.
                // Replaces neutral tones aggressively from 0% start because wet inks blotch easily.
                val kStart = 0.0f
                k = neutral * settings.blackStrength
                k = k.coerceIn(0f, neutral)
                c = cPrime - k
                m = mPrime - k
                y = yPrime - k
            }
            ProfileMode.GRAVURE_PSR -> {
                // Gravure LWC
                val kStart = 0.20f
                if (neutral > kStart) {
                    k = ((neutral - kStart) / (1f - kStart)) * settings.blackStrength
                }
                k = k.coerceIn(0f, neutral)
                c = cPrime - k
                m = mPrime - k
                y = yPrime - k
            }
            ProfileMode.CUSTOM_GCR -> {
                // Interactive GCR Sliders
                val kStart = settings.blackStartThreshold.coerceIn(0f, 0.99f)
                if (neutral >= kStart) {
                    k = ((neutral - kStart) / (1f - kStart)) * settings.blackStrength
                }
                k = k.coerceIn(0f, neutral)
                c = cPrime - k
                m = mPrime - k
                y = yPrime - k
            }
            ProfileMode.CUSTOM_UCR -> {
                // UCR: Under Color Removal.
                // Black replaces primary ink ONLY inside dark shadows, not in mild highlights.
                val kStart = maxOf(0.40f, settings.blackStartThreshold)
                if (neutral > kStart) {
                    k = ((neutral - kStart) / (1f - kStart)) * settings.blackStrength
                }
                k = k.coerceIn(0f, neutral)
                c = cPrime - k
                m = mPrime - k
                y = yPrime - k
            }
        }

        // Clip individual plates to 0..1 range
        c = c.coerceIn(0f, 1f)
        m = m.coerceIn(0f, 1f)
        y = y.coerceIn(0f, 1f)
        k = k.coerceIn(0f, 1f)

        // Enforce TAC (Total Ink Coverage) Limit
        val currentTacPercent = (c + m + y + k) * 100f
        val limitPercent = settings.tacLimit.toFloat()

        if (currentTacPercent > limitPercent && currentTacPercent > 0f) {
            val excessRatio = limitPercent / currentTacPercent
            // Reduce colored plates while maintaining Black (K) density to keep details sharp
            val cmySum = c + m + y
            if (cmySum > 0f) {
                val reductionAmountFloat = (currentTacPercent - limitPercent) / 100f
                val scale = (cmySum - reductionAmountFloat) / cmySum
                val clampedScale = scale.coerceIn(0.1f, 1.0f)
                c *= clampedScale
                m *= clampedScale
                y *= clampedScale
            }
        }

        return floatArrayOf(c.coerceIn(0f, 1f), m.coerceIn(0f, 1f), y.coerceIn(0f, 1f), k.coerceIn(0f, 1f))
    }

    /**
     * Converts a single CMYK float coordinate back to simulated subtractive RGB Int.
     */
    fun cmykToRgb(c: Float, m: Float, y: Float, k: Float): Int {
        val r = ((1f - c) * (1f - k) * 255f).toInt().coerceIn(0, 255)
        val g = ((1f - m) * (1f - k) * 255f).toInt().coerceIn(0, 255)
        val b = ((1f - y) * (1f - k) * 255f).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    /**
     * Analyzes image point to produce full color metrics.
     */
    fun analyzePixel(rInt: Int, gInt: Int, bInt: Int, x: Int, y: Int, settings: CmykSettings): PixelColorInfo {
        val rF = rInt / 255f
        val gF = gInt / 255f
        val bF = bInt / 255f

        val cmyk = rgbToCmyk(rF, gF, bF, settings)
        val cPct = (cmyk[0] * 100f).toInt().coerceIn(0, 100)
        val mPct = (cmyk[1] * 100f).toInt().coerceIn(0, 100)
        val yPct = (cmyk[2] * 100f).toInt().coerceIn(0, 100)
        val kPct = (cmyk[3] * 100f).toInt().coerceIn(0, 100)

        val hex = String.format("#%02X%02X%02X", rInt, gInt, bInt)
        return PixelColorInfo(
            x = x,
            y = y,
            r = rInt,
            g = gInt,
            b = bInt,
            hex = hex,
            c = cPct,
            m = mPct,
            yCmyk = yPct,
            k = kPct,
            tac = cPct + mPct + yPct + kPct
        )
    }

    data class SeparationOutput(
        val simulatedCombined: Bitmap,
        val cyanPlate: Bitmap,
        val magentaPlate: Bitmap,
        val yellowPlate: Bitmap,
        val blackPlate: Bitmap,
        val averageC: Float,
        val averageM: Float,
        val averageY: Float,
        val averageK: Float,
        val averageTac: Float,
        val peakTac: Int,
        val exceededTacCount: Int // Pixels exceeding the desired TAC limit
    )

    /**
     * Highly optimized method to separate a design Bitmap into standard C, M, Y, K separations.
     * Uses 1D primitive primitive IntArrays and direct byte manipulation on background threads.
     */
    fun separateBitmap(sourceBitmap: Bitmap, settings: CmykSettings): SeparationOutput {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        val totalPixels = width * height
        val rgbPixels = IntArray(totalPixels)
        sourceBitmap.getPixels(rgbPixels, 0, width, 0, 0, width, height)

        val combinedPixels = IntArray(totalPixels)
        val cyanPixels = IntArray(totalPixels)
        val magentaPixels = IntArray(totalPixels)
        val yellowPixels = IntArray(totalPixels)
        val blackPixels = IntArray(totalPixels)

        var totalC = 0f
        var totalM = 0f
        var totalY = 0f
        var totalK = 0f
        var peakTacValue = 0
        var excessTacCount = 0

        val tint = settings.tintSeparations

        // Let's loop pixel by pixel
        for (i in 0 until totalPixels) {
            val pixel = rgbPixels[i]
            val rInt = Color.red(pixel)
            val gInt = Color.green(pixel)
            val bInt = Color.blue(pixel)

            // Convert
            val rF = rInt / 255f
            val gF = gInt / 255f
            val bF = bInt / 255f

            val cmyk = rgbToCmyk(rF, gF, bF, settings)
            val c = cmyk[0]
            val m = cmyk[1]
            val y = cmyk[2]
            val k = cmyk[3]

            // Sum stats
            totalC += c
            totalM += m
            totalY += y
            totalK += k

            val tacPct = ((c + m + y + k) * 100f).toInt()
            if (tacPct > peakTacValue) {
                peakTacValue = tacPct
            }
            if (tacPct > settings.tacLimit) {
                excessTacCount++
            }

            // Real simulated screen overlaps (subtractive CMYK combined preview)
            combinedPixels[i] = cmykToRgb(c, m, y, k)

            // Make plate pixels
            // c, m, y, k fields are 0f..1f.
            // Grayscale densities (1 = full thickness, 0 = pure white):
            // continuous tone film is displayed as (1f - weight), so 1.0 ink = RGB(0,0,0) Black.
            if (tint) {
                // Cyan Plate Tint: R decreases as C increases. Full C is Cyan RGB(0,255,255)
                val cComp = ((1f - c) * 255f).toInt().coerceIn(0, 255)
                cyanPixels[i] = Color.rgb(cComp, 255, 255)

                // Magenta Plate Tint: G decreases as M increases. Full M is Magenta RGB(255,0,255)
                val mComp = ((1f - m) * 255f).toInt().coerceIn(0, 255)
                magentaPixels[i] = Color.rgb(255, mComp, 255)

                // Yellow Plate Tint: B decreases as Y increases. Full Y is Yellow RGB(255,255,0)
                val yComp = ((1f - y) * 255f).toInt().coerceIn(0, 255)
                yellowPixels[i] = Color.rgb(255, 255, yComp)

                // Black Plate is simply Black on White structure
                val kComp = ((1f - k) * 255f).toInt().coerceIn(0, 255)
                blackPixels[i] = Color.rgb(kComp, kComp, kComp)
            } else {
                // Pure Grayscale positive plates (perfect for professional mechanical screening)
                val cComp = ((1f - c) * 255f).toInt().coerceIn(0, 255)
                cyanPixels[i] = Color.rgb(cComp, cComp, cComp)

                val mComp = ((1f - m) * 255f).toInt().coerceIn(0, 255)
                magentaPixels[i] = Color.rgb(mComp, mComp, mComp)

                val yComp = ((1f - y) * 255f).toInt().coerceIn(0, 255)
                yellowPixels[i] = Color.rgb(yComp, yComp, yComp)

                val kComp = ((1f - k) * 255f).toInt().coerceIn(0, 255)
                blackPixels[i] = Color.rgb(kComp, kComp, kComp)
            }
        }

        // Create bitmaps
        val config = Bitmap.Config.ARGB_8888
        val simCombined = Bitmap.createBitmap(combinedPixels, width, height, config)
        val plateC = Bitmap.createBitmap(cyanPixels, width, height, config)
        val plateM = Bitmap.createBitmap(magentaPixels, width, height, config)
        val plateY = Bitmap.createBitmap(yellowPixels, width, height, config)
        val plateK = Bitmap.createBitmap(blackPixels, width, height, config)

        val avgC = (totalC / totalPixels) * 100f
        val avgM = (totalM / totalPixels) * 100f
        val avgY = (totalY / totalPixels) * 100f
        val avgK = (totalK / totalPixels) * 100f
        val avgTac = avgC + avgM + avgY + avgK

        return SeparationOutput(
            simulatedCombined = simCombined,
            cyanPlate = plateC,
            magentaPlate = plateM,
            yellowPlate = plateY,
            blackPlate = plateK,
            averageC = avgC,
            averageM = avgM,
            averageY = avgY,
            averageK = avgK,
            averageTac = avgTac,
            peakTac = peakTacValue,
            exceededTacCount = excessTacCount
        )
    }

    /**
     * Programmatically generates a gorgeous calibration target image.
     * Includes basic primary gradients, key CMYK/RGB swatches, and a radial photographic style blend.
     * This provides a complete hands-on experience without needing an upload on first use!
     */
    fun generateCalibrationTarget(size: Int = 512): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint()

        // Background paper: Warm-toned Offwhite paper block
        canvas.drawColor(Color.rgb(253, 252, 250))

        val half = size / 2f
        val third = size / 3f

        // Grid 1: Pure Primaries (RGB) and Secondaries (CMY) and Key (Black) swatches
        val swatches = arrayOf(
            Color.rgb(255, 0, 0),     // Red
            Color.rgb(0, 255, 0),     // Green
            Color.rgb(0, 0, 255),     // Blue
            Color.rgb(0, 255, 255),   // Cyan
            Color.rgb(255, 0, 255),   // Magenta
            Color.rgb(255, 255, 0),   // Yellow
            Color.rgb(0, 0, 0),       // Black
            Color.rgb(128, 128, 128), // Gray
            Color.rgb(255, 255, 255)  // White
        )

        val rows = 3
        val cols = 3
        val colW = third
        val rowH = third / 1.5f

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val idx = row * cols + col
                if (idx < swatches.size) {
                    paint.color = swatches[idx]
                    val left = col * colW + 10f
                    val top = row * rowH + 10f
                    val right = left + colW - 20f
                    val bottom = top + rowH - 20f
                    canvas.drawRect(left, top, right, bottom, paint)

                    // Draw a subtle dark border around white or light swatches
                    if (idx == 8 || idx == 5) { // White, Yellow
                        paint.color = Color.rgb(210, 205, 195)
                        paint.style = android.graphics.Paint.Style.STROKE
                        paint.strokeWidth = 2f
                        canvas.drawRect(left, top, right, bottom, paint)
                        paint.style = android.graphics.Paint.Style.FILL
                    }
                }
            }
        }

        // Section 2: Smooth Gradients for testing GCR start/threshold points.
        // Left gradient: Black-to-White
        // Center-right gradient: Multi-color blend (Red-Green-Blue)
        val gradTop = rows * rowH + 10f
        val gradHeight = size - gradTop - 40f

        // Draw RGB gradient bar
        for (x in 0 until size) {
            val ratio = x.toFloat() / size

            // Gray scale density on LHS
            if (x < size / 2) {
                val grayRatio = x.toFloat() / (size / 2f)
                val gVal = (grayRatio * 255f).toInt()
                paint.color = Color.rgb(gVal, gVal, gVal)
            } else {
                // Color transition on RHS
                val colorRatio = (x - size / 2f) / (size / 2f)
                // Red to Cyan, or standard multicolor:
                // Let's create a beautiful spectrum
                val r = if (colorRatio < 0.33f) 255 else if (colorRatio < 0.66f) ((1f - (colorRatio - 0.33f)/0.33f) * 255f).toInt() else 0
                val g = if (colorRatio < 0.33f) (colorRatio/0.33f * 255f).toInt() else if (colorRatio < 0.66f) 255 else ((1f - (colorRatio - 0.66f)/0.34f) * 255f).toInt()
                val b = if (colorRatio < 0.33f) 0 else if (colorRatio < 0.66f) ((colorRatio - 0.33f)/0.33f * 255f).toInt() else 255
                paint.color = Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
            }

            canvas.drawRect(x.toFloat(), gradTop, x.toFloat() + 1f, gradTop + gradHeight, paint)
        }

        // Draw a classic test targets overlay: Register Marks, Crosshairs & Textures
        paint.color = Color.rgb(30, 30, 30)
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 3f

        // Draw simple concentric circles for alignment checks in center bottom
        val centerX = half
        val centerY = gradTop + gradHeight / 2f
        canvas.drawCircle(centerX, centerY, 30f, paint)
        canvas.drawCircle(centerX, centerY, 15f, paint)
        canvas.drawLine(centerX - 40f, centerY, centerX + 40f, centerY, paint)
        canvas.drawLine(centerX, centerY - 40f, centerX, centerY + 40f, paint)

        // Reset styling paint
        paint.style = android.graphics.Paint.Style.FILL

        // Add some textual identifiers
        paint.color = Color.rgb(60, 60, 60)
        paint.textSize = 14f
        paint.isAntiAlias = true
        canvas.drawText("RGB GRAY SCALE (L) / SPECTRUM (R)", 15f, gradTop + gradHeight + 20f, paint)
        canvas.drawText("CMYK CALIBRATION SEPARATIONS PROOF", 15f, 20f, paint)

        return bitmap
    }
}
