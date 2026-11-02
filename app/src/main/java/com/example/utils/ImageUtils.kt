package com.example.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageUtils {
    /**
     * تقليص حجم الصورة للمحافظة على نسبة العرض إلى الارتفاع وجودة الأداء وسرعة التحميل.
     */
    fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val aspectRatio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / aspectRatio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * aspectRatio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * تحويل الصورة (Bitmap) إلى نص مشفر بـ Base64 لسهولة التخزين في Firebase Realtime Database.
     */
    fun bitmapToBase64(bitmap: Bitmap): String {
        val scaled = scaleBitmapDown(bitmap, 300) // حجم مثالي وخفيف جداً لـ Realtime DB (أقل من ٣٠ كيلوبايت)
        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * تحويل النص المشفر بـ Base64 مجدداً إلى صورة (Bitmap) لعرضها في التطبيق.
     */
    fun base64ToBitmap(base64Str: String): Bitmap? {
        if (base64Str.isBlank()) return null
        return try {
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
