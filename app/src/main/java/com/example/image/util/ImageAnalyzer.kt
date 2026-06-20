package com.example.image.util

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import com.example.image.data.model.ImageAnalysis
import com.example.image.data.model.ImageOrientation
import kotlin.math.max

object ImageAnalyzer {
    fun analyze(bitmap: Bitmap): ImageAnalysis {
        val sampleStep = max(1, max(bitmap.width, bitmap.height) / 80)
        var redTotal = 0L
        var greenTotal = 0L
        var blueTotal = 0L
        var count = 0L

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                redTotal += AndroidColor.red(pixel)
                greenTotal += AndroidColor.green(pixel)
                blueTotal += AndroidColor.blue(pixel)
                count++
                x += sampleStep
            }
            y += sampleStep
        }

        val red = (redTotal / count).toInt()
        val green = (greenTotal / count).toInt()
        val blue = (blueTotal / count).toInt()
        val hsv = FloatArray(3)
        AndroidColor.RGBToHSV(red, green, blue, hsv)

        val brightness = hsv[2]
        val saturation = hsv[1]
        val warmth = ((red - blue) / 255f).coerceIn(-1f, 1f)
        val orientation = when {
            bitmap.height > bitmap.width * 1.12f -> ImageOrientation.Portrait
            bitmap.width > bitmap.height * 1.12f -> ImageOrientation.Landscape
            else -> ImageOrientation.Square
        }
        val colorName = colorName(hsv[0], saturation, brightness)
        val lightName = lightName(brightness)
        val saturationName = saturationName(saturation)
        val temperatureName = if (warmth >= 0f) "暖色" else "冷色"
        val title = "${orientation.label}${temperatureName}${lightName}素材"
        val tags = listOf(orientation.label, temperatureName, lightName, saturationName, colorName)
        val description = "系统从图片像素中采样分析，判断这是一张${orientation.label}，整体${lightName}、${temperatureName}，主色接近${colorName}，色彩${saturationName}。"

        return ImageAnalysis(
            title = title,
            description = description,
            tags = tags,
            mainColor = Color(red, green, blue),
            rgbText = "R$red / G$green / B$blue",
            brightness = brightness,
            saturation = saturation,
            warmth = warmth,
            orientation = orientation
        )
    }

    private fun colorName(hue: Float, saturation: Float, brightness: Float): String {
        if (brightness < 0.18f) return "黑色"
        if (saturation < 0.12f) return "灰白色"
        return when (hue) {
            in 0f..25f -> "红色"
            in 25f..55f -> "橙黄色"
            in 55f..90f -> "黄色"
            in 90f..160f -> "绿色"
            in 160f..210f -> "青色"
            in 210f..260f -> "蓝色"
            in 260f..320f -> "紫色"
            else -> "红色"
        }
    }

    private fun lightName(brightness: Float): String {
        return when {
            brightness >= 0.68f -> "亮调"
            brightness <= 0.38f -> "暗调"
            else -> "中性调"
        }
    }

    private fun saturationName(saturation: Float): String {
        return when {
            saturation >= 0.55f -> "浓郁"
            saturation <= 0.22f -> "柔和"
            else -> "自然"
        }
    }
}
