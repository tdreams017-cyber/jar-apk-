package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.config.FirebaseConfig
import com.example.network.FirebaseClient
import com.example.ui.screens.MainCompose
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // تهيئة ميزات وإعدادات التخزين لـ Firebase والمحاكي المحلي المتنقل في العراق
    FirebaseConfig.init(this)
    FirebaseClient.init(this)
    
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        MainCompose()
      }
    }
  }
}
