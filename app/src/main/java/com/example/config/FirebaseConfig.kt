package com.example.config

import android.content.Context
import android.content.SharedPreferences

object FirebaseConfig {
    private const val PREFS_NAME = "firebase_config_prefs"
    private const val KEY_DB_URL = "firebase_db_url"

    private lateinit var prefs: SharedPreferences

    /**
     * تهيئة إعدادات Firebase من التخزين المحلي المشترك بالـ Android OS لضمان مرونة ومرونة كاملة للتطبيق حياً.
     */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        DATABASE_URL = prefs.getString(KEY_DB_URL, "https://jairani-rental-default-rtdb.firebaseio.com/") ?: "https://jairani-rental-default-rtdb.firebaseio.com/"
    }

    /**
     * رابط قاعدة بيانات الـ Firebase Realtime Database النشطة.
     * يمكن تغييره ديناميكياً من لوحة الإعدادات أو لوحة المشرف في التطبيق نفسه مباشرة بدون الحاجة لإعادة التجميع!
     */
    var DATABASE_URL: String = "https://jairani-rental-default-rtdb.firebaseio.com/"
        private set

    /**
     * تحديث وتخزين رابط Firebase Realtime Database الجديد بشكل مستمر وتلقائي.
     */
    fun updateDatabaseUrl(context: Context, newUrl: String) {
        val cleanUrl = newUrl.trim()
        DATABASE_URL = cleanUrl
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DB_URL, cleanUrl)
            .apply()
    }

    /**
     * هل يجب محاكاة قاعدة البيانات محلياً إذا كان الرابط غير مهيأ أو افتراضي؟
     * يتيح هذا الخيار تجربة التطبيق بسلاسة تامة مع حفظ الأغراض محلياً في ذاكرة الهاتف.
     */
    val useLocalDemoFallback: Boolean
        get() = DATABASE_URL.contains("jairani-rental-default-rtdb") || DATABASE_URL.isBlank()
}
