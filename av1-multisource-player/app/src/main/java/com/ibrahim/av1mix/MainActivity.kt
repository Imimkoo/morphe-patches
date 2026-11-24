package com.ibrahim.av1mix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AppBackground = Color(0xFF090C10)
private val AppSurface = Color(0xFF151B22)
private val AppAccent = Color(0xFFB8F078)
private val AppMuted = Color(0xFFA0AAB5)

private enum class MainTab { HOME, SEARCH, HISTORY, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 12, 16)
        window.navigationBarColor = android.graphics.Color.rgb(9, 12, 16)
        setContent { AV1MixApp() }
    }
}

@Composable
private fun AV1MixApp() {
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var query by rememberSaveable { mutableStateOf("") }
    var refreshCount by remember { mutableIntStateOf(0) }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = AppAccent,
        background = AppBackground,
        surface = AppSurface,
        onPrimary = Color(0xFF10150A),
        onBackground = Color.White,
        onSurface = Color.White
    )) {
        Scaffold(
            containerColor = AppBackground,
            bottomBar = {
                NavigationBar(containerColor = AppSurface) {
                    NavigationBarItem(
                        selected = tab == MainTab.HOME,
                        onClick = { tab = MainTab.HOME },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("الرئيسية") }
                    )
                    NavigationBarItem(
                        selected = tab == MainTab.SEARCH,
                        onClick = { tab = MainTab.SEARCH },
                        icon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("بحث") }
                    )
                    NavigationBarItem(
                        selected = tab == MainTab.HISTORY,
                        onClick = { tab = MainTab.HISTORY },
                        icon = { Icon(Icons.Default.History, contentDescription = null) },
                        label = { Text("السجل") }
                    )
                    NavigationBarItem(
                        selected = tab == MainTab.SETTINGS,
                        onClick = { tab = MainTab.SETTINGS },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("الإعدادات") }
                    )
                }
            }
        ) { padding ->
            when (tab) {
                MainTab.HOME -> HomeScreen(refreshCount, Modifier.padding(padding)) { refreshCount++ }
                MainTab.SEARCH -> SearchScreen(query, { query = it }, Modifier.padding(padding))
                MainTab.HISTORY -> HistoryScreen(Modifier.padding(padding))
                MainTab.SETTINGS -> SettingsScreen(Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun PageHeader(subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp)) {
        Text("AV1 MIX", color = AppAccent, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
        Text(subtitle, color = AppMuted, fontSize = 13.sp)
    }
}

@Composable
private fun HomeScreen(refreshCount: Int, modifier: Modifier = Modifier, onRefresh: () -> Unit) {
    Column(modifier.fillMaxSize()) {
        PageHeader("اكتشاف موحّد · مشغّل واحد")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppSurface),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(color = Color(0xFF263322), shape = RoundedCornerShape(18.dp), modifier = Modifier.size(58.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = AppAccent, modifier = Modifier.size(30.dp))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("مكتبتك الموحدة", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "مكان واحد للبحث والاكتشاف والمشاهدة عبر المصادر المتصلة.",
                            color = AppMuted, fontSize = 13.sp, textAlign = TextAlign.Center
                        )
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text("المصادر", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "لا يوجد حاليًا مصدر متصل اجتاز مراجعة شروط الاستخدام وحقوق التشغيل. لن نعرض نتائج وهمية أو نستخرج روابط تشغيل غير مصرح بها.",
                            color = AppMuted, fontSize = 13.sp, lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onRefresh,
                            colors = ButtonDefaults.buttonColors(containerColor = AppAccent),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("إعادة فحص المصادر", fontWeight = FontWeight.Bold) }
                        if (refreshCount > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text("آخر فحص محلي: $refreshCount", color = AppMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
            item {
                Text("ماذا سيعمل عند توصيل المصادر؟", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                FeatureLine("بحث موحّد", "نتائج من أكثر من مصدر في قائمة واحدة")
                FeatureLine("توصيات ذكية", "اقتراحات تتعلم من المشاهدة والتخطي")
                FeatureLine("مشغّل واحد", "معرفة الترميز والمفكك المستخدمين فعليًا")
                FeatureLine("تخزين ذكي", "ذاكرة مؤقتة منفصلة عن التنزيلات الدائمة")
            }
        }
    }
}

@Composable
private fun FeatureLine(title: String, description: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(AppAccent, RoundedCornerShape(50)))
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(description, color = AppMuted, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        PageHeader("ابحث عبر المكتبة كلها")
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            label = { Text("اكتب كلمة البحث") },
            singleLine = true
        )
        Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
            Text(
                if (query.isBlank()) "اكتب ما تريد البحث عنه." else "البحث جاهز، لكنه ينتظر توصيل مصدر معتمد.",
                color = AppMuted, textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HistoryScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        PageHeader("سجل المشاهدة")
        Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
            Text("لا توجد مشاهدات مسجلة بعد.", color = AppMuted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        PageHeader("إعدادات التشغيل والخصوصية")
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsCard("التشغيل", "سيتم إظهار الترميز والمفكك الفعليين بعد تشغيل مصدر متوافق.")
            SettingsCard("التخزين المؤقت", "سيتم فصل ذاكرة التشغيل عن الاحتفاظ بالمقاطع السابقة والتنزيلات.")
            SettingsCard("الخصوصية", "التصميم يعتمد على حفظ سجل المشاهدة والتفضيلات محليًا افتراضيًا.")
            SettingsCard("إصدار التطبيق", "0.2.0 · واجهة المنتج الأساسية")
        }
    }
}

@Composable
private fun SettingsCard(title: String, description: String) {
    Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(description, color = AppMuted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}
