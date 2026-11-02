package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.config.FirebaseConfig
import com.example.model.RentalItem
import com.example.network.FirebaseClient
import com.example.utils.ImageUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// حالات التنقل بين شاشات التطبيق الأساسية
sealed class AppScreen {
    object Catalog : AppScreen()
    object AddItem : AppScreen()
    object Admin : AppScreen()
}

// ألوان مخصصة مستوحاة من البيئة العراقية (الخضار الوفير والذهبي النهرين)
val IraqiEmeraldGreen = Color(0xFF0F5A3E)       // لون رئيسي مريح
val IraqiEmeraldLight = Color(0xFF1B7A56)       // درجات فاتحة
val IraqiGoldAccent = Color(0xFFE5A93B)         // لون العملات والدينار
val WarmBg = Color(0xFFFAF9F6)                  // لون خلفية دافئ ومستقر
val CardSurface = Color(0xFFFFFFFF)

class RentalViewModel : ViewModel() {
    private val _items = MutableStateFlow<List<RentalItem>>(emptyList())
    val items: StateFlow<List<RentalItem>> = _items

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadItems()
    }

    fun loadItems() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val fetched = FirebaseClient.getItems()
                _items.value = fetched
            } catch (e: Exception) {
                _error.value = e.message ?: "حدث خطأ أثناء تحميل البيانات"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun uploadItem(
        name: String,
        price: String,
        phone: String,
        base64Image: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val item = RentalItem(
                    name = name,
                    price = price,
                    ownerPhone = phone,
                    imageBase64 = base64Image
                )
                val success = FirebaseClient.addItem(item)
                if (success) {
                    loadItems()
                    onSuccess()
                } else {
                    onError("فشل رفع الغرض. الرجاء التحقق من الرابط وإعدادات Firebase.")
                }
            } catch (e: Exception) {
                onError(e.message ?: "حدث خطأ غير متوقع أثناء الاتصال")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteItem(id: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val success = FirebaseClient.deleteItem(id)
                if (success) {
                    loadItems()
                    onSuccess()
                } else {
                    onError("فشل حذف الغرض من قاعدة البيانات.")
                }
            } catch (e: Exception) {
                onError(e.message ?: "حدث خطأ أثناء محاولة الحذف")
            } finally {
                _isLoading.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainCompose(
    viewModel: RentalViewModel = viewModel(),
    onExitApp: () -> Unit = {}
) {
    // إجبار واجهة التطبيقات على دعم التنسيق العربي من اليمين لليسار (RTL) بشكل تام ومثالي
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val stateItems by viewModel.items.collectAsState()
        val stateLoading by viewModel.isLoading.collectAsState()
        val stateError by viewModel.error.collectAsState()

        val context = LocalContext.current
        var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Catalog) }

        // حالة لعداد اللمسات على الشعار للوصول السري للوحة الإدارة
        var logoTapCount by remember { mutableStateOf(0) }
        var showPasswordDialog by remember { mutableStateOf(false) }
        var passwordInput by remember { mutableStateOf("") }

        // تفاصيل الغرض النشط لعرض نافذة الاتصال به
        var selectedItemForBooking by remember { mutableStateOf<RentalItem?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = IraqiEmeraldGreen,
                        titleContentColor = Color.White
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // شعار التطبيق (تفاعلي سري عند الضغط عليه ه مرات تفتح نافذة كلمة المرور)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .clickable(
                                        interactionSource = remember { CustomMutableInteractionSource() },
                                        indication = LocalIndication.current
                                    ) {
                                        logoTapCount++
                                        if (logoTapCount >= 5) {
                                            logoTapCount = 0
                                            showPasswordDialog = true
                                        }
                                    }
                                    .testTag("app_logo_secret_trigger"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "جيراني للتأجير",
                                    tint = IraqiGoldAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "جيراني بالتأجير",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "تأجير آمن بين الجيران في العراق 🇮🇶",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    },
                    actions = {
                        // عرض تنبيه لوضعية التجربة المحلية في البار العلوي كـ Badge دافئ
                        if (FirebaseConfig.useLocalDemoFallback) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .background(IraqiGoldAccent, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "وضع تجريبي",
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // زر تحديث القائمة اليدوي
                        IconButton(onClick = {
                            viewModel.loadItems()
                            Toast.makeText(context, "تمت إعادة تحميل القائمة", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = Color.White)
                        }
                    }
                )
            },
            floatingActionButton = {
                // يظهر الزر العائم فقط في شاشة العرض الرئيسية لتسهيل الإضافة
                if (currentScreen is AppScreen.Catalog) {
                    ExtendedFloatingActionButton(
                        onClick = { currentScreen = AppScreen.AddItem },
                        containerColor = IraqiGoldAccent,
                        contentColor = Color.Black,
                        icon = { Icon(Icons.Default.Add, contentDescription = "+") },
                        text = { Text("أضف غرضك للتأجير", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .testTag("add_item_fab")
                    )
                }
            },
            containerColor = WarmBg
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentScreen) {
                    is AppScreen.Catalog -> {
                        CatalogScreenContent(
                            items = stateItems,
                            isLoading = stateLoading,
                            error = stateError,
                            onItemClicked = { selectedItemForBooking = it },
                            onRetry = { viewModel.loadItems() }
                        )
                    }
                    is AppScreen.AddItem -> {
                        AddItemScreenContent(
                            isLoading = stateLoading,
                            onBackClicked = { currentScreen = AppScreen.Catalog },
                            onUploadClicked = { name, price, phone, imgBase64 ->
                                viewModel.uploadItem(
                                    name = name,
                                    price = price,
                                    phone = phone,
                                    base64Image = imgBase64,
                                    onSuccess = {
                                        Toast.makeText(context, "تم رفع غرضك بنجاح للجميع!", Toast.LENGTH_LONG).show()
                                        currentScreen = AppScreen.Catalog
                                    },
                                    onError = { errorText ->
                                        Toast.makeText(context, errorText, Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        )
                    }
                    is AppScreen.Admin -> {
                        AdminScreenContent(
                            items = stateItems,
                            isLoading = stateLoading,
                            onBackClicked = { currentScreen = AppScreen.Catalog },
                            onDatabaseUrlChanged = { viewModel.loadItems() },
                            onDeleteClicked = { item ->
                                viewModel.deleteItem(
                                    id = item.id,
                                    onSuccess = {
                                        Toast.makeText(context, "تم حذف الغرض بنجاح", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { errorText ->
                                        Toast.makeText(context, errorText, Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        )
                    }
                }

                // ================= حوار كلمة مرور الإدارة المخفي =================
                if (showPasswordDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showPasswordDialog = false
                            passwordInput = ""
                        },
                        title = {
                            Text(
                                "نظام حماية واجهة الإدارة",
                                fontWeight = FontWeight.Bold,
                                color = IraqiEmeraldGreen,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        text = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("يرجى إدخال كلمة المرور الثابتة والوحيدة الخاصة بالإدارة للترخيص:", fontSize = 14.sp)
                                OutlinedTextField(
                                    value = passwordInput,
                                    onValueChange = { passwordInput = it },
                                    label = { Text("كلمة المرور") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("admin_password_input"),
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (passwordInput == "123452009sadq") {
                                        showPasswordDialog = false
                                        passwordInput = ""
                                        currentScreen = AppScreen.Admin
                                        Toast.makeText(context, "مرحباً بك يا مدير التطبيق العظيم!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "كلمة المرور خاطئة! تم رفض دخولك.", Toast.LENGTH_LONG).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = IraqiEmeraldGreen)
                            ) {
                                Text("دخول الإدارة", color = Color.White)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showPasswordDialog = false
                                passwordInput = ""
                            }) {
                                Text("إلغاء", color = Color.Gray)
                            }
                        }
                    )
                }

                // ================= حوار الاتصال والوساطة للمستأجر =================
                selectedItemForBooking?.let { item ->
                    AlertDialog(
                        onDismissRequest = { selectedItemForBooking = null },
                        title = {
                            Text(
                                text = "حجز وتأجير الغرض",
                                fontWeight = FontWeight.Bold,
                                color = IraqiEmeraldGreen,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        text = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // معلومات الغرض
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = IraqiEmeraldGreen.copy(alpha = 0.05f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // صورة مصغرة
                                        val bitmap = remember(item.imageBase64) {
                                            item.imageBase64?.let { ImageUtils.base64ToBitmap(it) }
                                        }
                                        if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(50.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(50.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(IraqiEmeraldGreen.copy(alpha = 0.1f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Build, contentDescription = null, tint = IraqiEmeraldGreen, modifier = Modifier.size(24.dp))
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.name,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "${item.price} د.ع",
                                                color = IraqiEmeraldLight,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "للتنسيق وحجز هذا الغرض مباشرة وضمان استلامه من جارك، يرجى الاتصال فوراً بالرقم الموحد للتنسيق والدعم الفني لكافة المناطق:",
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    color = Color.DarkGray
                                )

                                // رقم وساطة العميل الثابت والوحيد بدون أي كشف لرقم المالك الحقيقي كما طلب بدقة
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = IraqiGoldAccent.copy(alpha = 0.15f)),
                                    border = BorderStroke(1.dp, IraqiGoldAccent),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "الرقم المباشر للحجز والتنسيق",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.Black.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = "07875929178",
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Black,
                                            color = IraqiEmeraldGreen,
                                            letterSpacing = 1.sp,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                        Text(
                                            text = "(اتصل بنا الآن لتنسيق موعد الاستلام والحفاظ على حقوقك وعمولتنا)",
                                            fontSize = 9.sp,
                                            textAlign = TextAlign.Center,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    // الاتصال الفعلي بالرقم
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:07875929178")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "لم نتمكن من فتح لوحة الاتصال تلقائياً", Toast.LENGTH_SHORT).show()
                                    }
                                    selectedItemForBooking = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = IraqiEmeraldGreen)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("اتصل الآن", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { selectedItemForBooking = null }) {
                                Text("إلغاء", color = Color.Gray)
                            }
                        }
                    )
                }

            }
        }
    }
}

// ================= الشاشة الكتالوج الرئيسية (للمستأجرين) =================
@Composable
fun CatalogScreenContent(
    items: List<RentalItem>,
    isLoading: Boolean,
    error: String?,
    onItemClicked: (RentalItem) -> Unit,
    onRetry: () -> Unit
) {
    if (isLoading && items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(color = IraqiEmeraldGreen)
                Text("شغل جيراني.. يتم جلب الأغراض الآن..", fontSize = 14.sp, color = Color.Gray)
            }
        }
    } else if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(IraqiEmeraldGreen.copy(alpha = 0.05f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = IraqiEmeraldGreen.copy(alpha = 0.5f),
                        modifier = Modifier.size(54.dp)
                    )
                }

                Text(
                    text = "لا توجد أغراض معروضة للتأجير حالياً!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = IraqiEmeraldGreen,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "كن أول من يرفع غرضاً لمساعدة جيرانه في المنطقة واكسب بالدينار العراقي كأرباح إضافية!",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                if (error != null) {
                    Text(
                        text = "ملاحظة الفحص: لم نتمكن من سحب البيانات من السيرفر. ربما تحتاج لوضع الرابط الخاص بك في الكود.",
                        color = Color.Red.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = IraqiEmeraldGreen)
                ) {
                    Text("إعادة المحاولة / تحديث", color = Color.White)
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // شريط إرشادي ملون ومحفز للأمان والوساطة في العراق
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(IraqiEmeraldGreen, IraqiEmeraldLight)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = IraqiGoldAccent, modifier = Modifier.size(16.dp))
                    Text(
                        text = "يرجى الضغط على الغرض للاتصال الفوري مع المندوب الموحد لحجز موعد استلامك.",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("catalog_list")
            ) {
                items(items) { item ->
                    RentalItemRow(item = item, onClick = { onItemClicked(item) })
                }
            }
        }
    }
}

@Composable
fun RentalItemRow(
    item: RentalItem,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("item_row_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // صورة الغرض (تدعم فك التشفير من Base64 المخزن بـ Firebase)
            val bitmap = remember(item.imageBase64) {
                item.imageBase64?.let { ImageUtils.base64ToBitmap(it) }
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.LightGray)
                )
            } else {
                // عنصر تصميم بديل وأنيق في حال غياب الصورة
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(IraqiEmeraldGreen.copy(alpha = 0.1f), IraqiEmeraldLight.copy(alpha = 0.05f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "غرض تأجير",
                            tint = IraqiEmeraldGreen,
                            modifier = Modifier.size(28.dp)
                        )
                        Text("بدون صورة", fontSize = 10.sp, color = IraqiEmeraldGreen)
                    }
                }
            }

            // وصف وتفاصيل الغرض
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "التوفر: فوري بالمحافظة",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // السعر بالدينار العراقي فقط كما اشترط
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = item.price,
                            color = IraqiEmeraldGreen,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "د.ع / يوم",
                            color = IraqiEmeraldLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    // زر طلب حجز
                    Box(
                        modifier = Modifier
                            .background(IraqiEmeraldGreen, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = IraqiGoldAccent, modifier = Modifier.size(12.dp))
                            Text(
                                text = "احجز الآن",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ================= شاشة إضافة غرض جديد (للمؤجرين) =================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemScreenContent(
    isLoading: Boolean,
    onBackClicked: () -> Unit,
    onUploadClicked: (name: String, price: String, phone: String, imgBase64: String?) -> Unit
) {
    var itemName by remember { mutableStateOf("") }
    var itemPrice by remember { mutableStateOf("") }
    var ownerPhone by remember { mutableStateOf("") }

    // لتخزين الصورة المختارة
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var base64ImageString by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    // مشغل التقاط الصورة من معرض الصور المدمج بالأندرويد
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        val base64 = ImageUtils.bitmapToBase64(bitmap)
                        base64ImageString = base64
                    } else {
                        Toast.makeText(context, "لم يتم التعرف على الصورة المحددة", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "فشل في تشفير الصورة: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // شريط العودة للخلف
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBackClicked) {
                Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
            }
            Text(
                text = "إضافة غرض جديد للإيجار",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = IraqiEmeraldGreen
            )
        }

        Text(
            text = "قم بملء البيانات بالكامل لتأجير غرضك. سيتم إيداع العروض فوراً بـ Firebase لتظهر لجميع الجيران بالمنطقة.",
            fontSize = 13.sp,
            color = Color.Gray,
            lineHeight = 18.sp
        )

        // رفع الصورة
        Card(
            onClick = { galleryLauncher.launch("image/*") },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.5.dp, IraqiEmeraldGreen.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .testTag("btn_select_image")
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (base64ImageString != null) {
                    val bitmap = remember(base64ImageString) {
                        ImageUtils.base64ToBitmap(base64ImageString!!)
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "الصورة المختارة",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        // ملصق للتعديل
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("اضغط لتغيير الصورة", color = Color.White, fontSize = 11.sp)
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "أضف صورة",
                            tint = IraqiEmeraldGreen,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "اضغط لاختيار صورة الغرض من المعرض",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = IraqiEmeraldGreen
                        )
                        Text(
                            text = "(يوصى برفع صور واضحة وقريبة لجارك)",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        // اسم الغرض
        OutlinedTextField(
            value = itemName,
            onValueChange = { itemName = it },
            label = { Text("اسم وعنوان الغرض (مثال: دريل شحن طابوكة)") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = IraqiEmeraldGreen,
                focusedLabelColor = IraqiEmeraldGreen
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_item_name")
        )

        // السعر بالدينار العراقي فقط
        OutlinedTextField(
            value = itemPrice,
            onValueChange = { itemPrice = it },
            label = { Text("سعر التأجير لليوم الواحد (بالدينار العراقي فقط)") },
            placeholder = { Text("مثال: ١٥,٠٠٠") },
            singleLine = true,
            trailingIcon = {
                Text(
                    text = "د.ع",
                    color = IraqiEmeraldGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = IraqiEmeraldGreen,
                focusedLabelColor = IraqiEmeraldGreen
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_item_price")
        )

        // رقم هاتف المالك الأصلي الشرعي (هذا لن يظهر للمستأجر وسيظل للإستخدام الداخلي للمدير)
        OutlinedTextField(
            value = ownerPhone,
            onValueChange = { ownerPhone = it },
            label = { Text("رقم هاتف المالك الأصلي للغرض (مخفي للخصوصية)") },
            placeholder = { Text("مثال: 0770xxxxxxx") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = IraqiEmeraldGreen,
                focusedLabelColor = IraqiEmeraldGreen
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_item_phone")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // تنبيه قانوني وتعهدي بخصوص رقم المالك
        Card(
            colors = CardDefaults.cardColors(containerColor = IraqiEmeraldGreen.copy(alpha = 0.05f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = IraqiEmeraldGreen, modifier = Modifier.size(16.dp))
                Text(
                    text = "نظام أمان جيراني: يتم تشفير وحماية رقم المالك الأصلي تماماً، لتنسيق التأمينات والدفع المباشر والعمولة مع المشرف فقط.",
                    fontSize = 11.sp,
                    color = IraqiEmeraldGreen,
                    lineHeight = 16.sp
                )
            }
        }

        // زر رفع الغرض البطل
        Button(
            onClick = {
                if (itemName.isBlank() || itemPrice.isBlank() || ownerPhone.isBlank()) {
                    Toast.makeText(context, "الرجاء ملء كافة حقول المعلومات الأساسية أولاً!", Toast.LENGTH_SHORT).show()
                } else {
                    onUploadClicked(
                        itemName.trim(),
                        itemPrice.trim(),
                        ownerPhone.trim(),
                        base64ImageString
                    )
                }
            },
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = IraqiEmeraldGreen),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("btn_upload_item")
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(
                    text = "رفع الغرض الآن لـ Firebase حياً 🚀",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
            }
        }
    }
}

// ================= شاشة لوحة تحكم الإدارة الآمنة والخاصة بالمدير فقط =================
@Composable
fun AdminScreenContent(
    items: List<RentalItem>,
    isLoading: Boolean,
    onBackClicked: () -> Unit,
    onDatabaseUrlChanged: () -> Unit,
    onDeleteClicked: (RentalItem) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // العودة للخلف
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBackClicked) {
                Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
            }
            Text(
                text = "شاشة الإدارة المفتوحة للمشرف",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = IraqiEmeraldGreen
            )
        }

        // الترحيب بالإدارة ووصف اللوحة
        Card(
            colors = CardDefaults.cardColors(containerColor = IraqiEmeraldGreen),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "مرحباً يا بطل، التحكم الكامل بيدك!",
                    color = IraqiGoldAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "هنا ترى كافة المعروضات جنباً إلى جنب مع رقم هاتف مالك الغرض الشرعي الفعلي لتنسيق العملية وكسب عمولتك. كما تتوفر ميزة حذف العينة لضبط الأسعار والجودة.",
                    color = Color.White,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // لوحة إعدادات ربط Firebase
        var inputDbUrl by remember { mutableStateOf(FirebaseConfig.DATABASE_URL) }
        var isEditingFirebase by remember { mutableStateOf(false) }

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, IraqiEmeraldGreen.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = IraqiEmeraldGreen, modifier = Modifier.size(20.dp))
                        Text(
                            text = "إعدادات الربط المباشر مع Firebase",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = IraqiEmeraldGreen
                        )
                    }
                    
                    TextButton(
                        onClick = { isEditingFirebase = !isEditingFirebase }
                    ) {
                        Text(if (isEditingFirebase) "إخفاء" else "تعديل الرابط 🛠️", fontSize = 12.sp, color = IraqiEmeraldLight)
                    }
                }

                if (isEditingFirebase) {
                    Text(
                        text = "أدخل رابط قاعدة بيانات Firebase Realtime Database الخاصة بك لتشغيل التطبيق سحابياً بدلاً من التخزين الداخلي الفوري:",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        lineHeight = 16.sp
                    )

                    OutlinedTextField(
                        value = inputDbUrl,
                        onValueChange = { inputDbUrl = it },
                        placeholder = { Text("https://your-app-default-rtdb.firebaseio.com/") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IraqiEmeraldGreen,
                            focusedLabelColor = IraqiEmeraldGreen
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                FirebaseConfig.updateDatabaseUrl(context, inputDbUrl)
                                onDatabaseUrlChanged()
                                isEditingFirebase = false
                                Toast.makeText(context, "تمت الترقية والاتصال بـ Firebase الجديد وحفظ الرابط!", Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = IraqiEmeraldGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("حفظ وتحديث الاتصال", fontSize = 12.sp, color = Color.White)
                        }

                        OutlinedButton(
                            onClick = {
                                inputDbUrl = "https://jairani-rental-default-rtdb.firebaseio.com/"
                                FirebaseConfig.updateDatabaseUrl(context, inputDbUrl)
                                onDatabaseUrlChanged()
                                isEditingFirebase = false
                                Toast.makeText(context, "تمت إعادة رابط التخزين المحلي والافتراضي بنجاح", Toast.LENGTH_LONG).show()
                            },
                            border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(0.7f)
                        ) {
                            Text("إعادة للافتراضي", fontSize = 11.sp, color = Color.Red)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(IraqiEmeraldGreen.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("الرابط النشط حالياً:", fontSize = 10.sp, color = Color.Gray)
                        Text(
                            text = FirebaseConfig.DATABASE_URL,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = IraqiEmeraldGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    if (FirebaseConfig.useLocalDemoFallback) IraqiGoldAccent else IraqiEmeraldGreen,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (FirebaseConfig.useLocalDemoFallback) "وضع محلي" else "ربط سحابي حقيقي",
                                color = if (FirebaseConfig.useLocalDemoFallback) Color.Black else Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "الأغراض المتاحة بموجب التحكم (${items.size}):",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )

        if (isLoading && items.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = IraqiEmeraldGreen)
            }
        } else if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("لا توجد أغراض حالياً للتحكم بها.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("admin_list")
            ) {
                items(items) { item ->
                    AdminItemRow(
                        item = item,
                        onCallOwner = {
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${item.ownerPhone}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "فشل الاتصال", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = { onDeleteClicked(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun AdminItemRow(
    item: RentalItem,
    onCallOwner: () -> Unit,
    onDelete: () -> Unit
) {
    var showConfirmDelete by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // صورة صغيرة
                val bitmap = remember(item.imageBase64) {
                    item.imageBase64?.let { ImageUtils.base64ToBitmap(it) }
                }

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(IraqiEmeraldGreen.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = IraqiEmeraldGreen, modifier = Modifier.size(18.dp))
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "السعر: ${item.price} د.ع",
                        fontSize = 12.sp,
                        color = IraqiEmeraldLight,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.LightGray.copy(alpha = 0.5f))

            // هاتف المالك الأصلي المسموح بمشاهدته للمدير حصراً
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "رقم هاتف المالك الأصلي الشرعي:",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = item.ownerPhone,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = IraqiEmeraldGreen,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // اتصال بالمالك الأصلي للتنسيق
                    IconButton(
                        onClick = onCallOwner,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = IraqiEmeraldGreen.copy(alpha = 0.1f))
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = "اتصل بالمالك", tint = IraqiEmeraldGreen)
                    }

                    // حذف الغرض
                    IconButton(
                        onClick = { showConfirmDelete = true },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Red.copy(alpha = 0.1f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف الغرض", tint = Color.Red)
                    }
                }
            }
        }
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("هل أنت متأكد من حذف الغرض؟", fontWeight = FontWeight.Bold, color = Color.Red) },
            text = { Text("سيؤدي هذا إلى مسح الغرض نهائياً وحياً من قاعدة بيانات Firebase.") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("نعم، احذف فوراً", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("إلغاء", color = Color.Gray)
                }
            }
        )
    }
}

// فئة مخصصة لمنع توليد Ripple غير مناسب أثناء الضغط السريع المتتالي
class CustomMutableInteractionSource : androidx.compose.foundation.interaction.MutableInteractionSource {
    override val interactions = kotlinx.coroutines.flow.emptyFlow<androidx.compose.foundation.interaction.Interaction>()
    override suspend fun emit(interaction: androidx.compose.foundation.interaction.Interaction) {}
    override fun tryEmit(interaction: androidx.compose.foundation.interaction.Interaction): Boolean = true
}
