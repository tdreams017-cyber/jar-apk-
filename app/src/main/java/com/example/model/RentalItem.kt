package com.example.model

data class RentalItem(
    val id: String = "",
    val name: String,
    val price: String, // السعر بالدينار العراقي (e.g. "١٥,٠٠٠" or "15,000")
    val ownerPhone: String, // رقم هاتف المالك الأصلي (للمشرفين فقط)
    val imageBase64: String? = null // صورة الغرض مشفرة بـ Base64
)
