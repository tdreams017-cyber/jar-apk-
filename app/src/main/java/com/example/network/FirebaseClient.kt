package com.example.network

import android.util.Log
import com.example.config.FirebaseConfig
import com.example.model.RentalItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object FirebaseClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    private var isInitialized = false
    private lateinit var prefs: android.content.SharedPreferences

    // قائمة محلية افتراضية لعرضها في حال لم يقم العميل بعد بتهيئة تطبيق Firebase الخاص به.
    // يتيح هذا تجربة التطبيق بسلاسة تامة.
    private val localDemoItems = mutableListOf<RentalItem>(
        RentalItem(
            id = "demo1",
            name = "مطرقة كهربائية جدارية (هيلتي بقوة ١٥٠٠ واط)",
            price = "٢٠,٠٠٠",
            ownerPhone = "07705544332",
            imageBase64 = null
        ),
        RentalItem(
            id = "demo2",
            name = "مولد كهرباء منزلي كاتم للصوت ٣.٥ أمبير",
            price = "٣٥,٠٠٠",
            ownerPhone = "07812233445",
            imageBase64 = null
        ),
        RentalItem(
            id = "demo3",
            name = "درج ألمنيوم قابل للطي بطول ٥ أمتار كوري",
            price = "١٥,٠٠٠",
            ownerPhone = "07901112223",
            imageBase64 = null
        )
    )

    /**
     * تهيئة محاكي قاعدة البيانات المحلي واسترجاع الأغراض المحفوظة مسبقاً في الهاتف لكي لا تضيع بعد إغلاق التطبيق.
     */
    fun init(context: android.content.Context) {
        if (isInitialized) return
        try {
            prefs = context.applicationContext.getSharedPreferences("local_items_prefs", android.content.Context.MODE_PRIVATE)
            val savedItemsJson = prefs.getString("items_json", null)
            if (savedItemsJson != null) {
                val jsonArray = org.json.JSONArray(savedItemsJson)
                localDemoItems.clear()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    localDemoItems.add(
                        RentalItem(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            price = obj.getString("price"),
                            ownerPhone = obj.getString("ownerPhone"),
                            imageBase64 = if (obj.isNull("imageBase64") || obj.getString("imageBase64").isBlank()) null else obj.getString("imageBase64")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseClient", "Failed to load local items: ${e.message}")
        }
        isInitialized = true
    }

    /**
     * حفظ قائمة الأغراض محلياً في ذاكرة التخزين المشتركة للهاتف (SharedPreferences) حياً.
     */
    private fun persistLocalItems() {
        if (!::prefs.isInitialized) return
        try {
            val jsonArray = org.json.JSONArray()
            for (item in localDemoItems) {
                val obj = org.json.JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("price", item.price)
                    put("ownerPhone", item.ownerPhone)
                    put("imageBase64", item.imageBase64 ?: "")
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString("items_json", jsonArray.toString()).apply()
        } catch (e: java.lang.Exception) {
            Log.e("FirebaseClient", "Failed to persist local items: ${e.message}")
        }
    }

    private fun getFullUrl(path: String): String {
        var base = FirebaseConfig.DATABASE_URL.trim()
        if (!base.endsWith("/")) {
            base += "/"
        }
        return "$base$path"
    }

    /**
     * جلب كافة الأغراض المتاحة للتأجير.
     */
    suspend fun getItems(): List<RentalItem> = withContext(Dispatchers.IO) {
        if (FirebaseConfig.useLocalDemoFallback) {
            return@withContext localDemoItems.toList()
        }

        val url = getFullUrl("items.json")
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string()
                if (!response.isSuccessful || bodyString == null) {
                    Log.e("FirebaseClient", "فشل تحميل الأغراض: كود الاستجابة ${response.code}")
                    return@withContext emptyList()
                }

                if (bodyString.trim() == "null" || bodyString.isBlank() || bodyString.trim() == "{}") {
                    return@withContext emptyList()
                }

                val list = mutableListOf<RentalItem>()
                val jsonObject = JSONObject(bodyString)
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val itemJson = jsonObject.getJSONObject(key)
                    list.add(
                        RentalItem(
                            id = key,
                            name = itemJson.optString("name", ""),
                            price = itemJson.optString("price", ""),
                            ownerPhone = itemJson.optString("ownerPhone", ""),
                            imageBase64 = itemJson.optString("imageBase64", null)
                        )
                    )
                }
                return@withContext list.reversed() // الأحدث يظهر أولاً
            }
        } catch (e: Exception) {
            Log.e("FirebaseClient", "خطأ أثناء الاتصال بقاعدة بيانات Firebase: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    /**
     * رفع غرض تأجير جديد لقاعدة البيانات.
     */
    suspend fun addItem(item: RentalItem): Boolean = withContext(Dispatchers.IO) {
        if (FirebaseConfig.useLocalDemoFallback) {
            val newItem = item.copy(id = "demo_${System.currentTimeMillis()}")
            localDemoItems.add(0, newItem)
            persistLocalItems()
            return@withContext true
        }

        val url = getFullUrl("items.json")
        try {
            val json = JSONObject().apply {
                put("name", item.name)
                put("price", item.price)
                put("ownerPhone", item.ownerPhone)
                put("imageBase64", item.imageBase64 ?: "")
            }

            val requestBody = json.toString().toRequestBody(mediaTypeJson)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return@withContext true
                } else {
                    Log.e("FirebaseClient", "فشل إضافة الغرض: كود الاستجابة ${response.code}")
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseClient", "خطأ في الرفع للسيرفر: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * حذف غرض ما (متاح للمشرف فقط).
     */
    suspend fun deleteItem(id: String): Boolean = withContext(Dispatchers.IO) {
        if (FirebaseConfig.useLocalDemoFallback) {
            val found = localDemoItems.removeAll { it.id == id }
            if (found) {
                persistLocalItems()
            }
            return@withContext found
        }

        val url = getFullUrl("items/$id.json")
        try {
            val request = Request.Builder()
                .url(url)
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return@withContext true
                } else {
                    Log.e("FirebaseClient", "فشل حذف الغرض: كود الاستجابة ${response.code}")
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseClient", "خطأ أثناء الحذف من السيرفر: ${e.message}", e)
            return@withContext false
        }
    }
}
