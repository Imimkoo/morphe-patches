package com.ibrahim.av1mix

import android.os.Bundle
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.ConsoleMessage
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.ibrahim.av1mix.data.AdultEmbedProvider
import com.ibrahim.av1mix.data.NormalizedVideo
import com.ibrahim.av1mix.data.LocalPreferenceStore
import com.ibrahim.av1mix.data.ProviderRegistry
import com.ibrahim.av1mix.data.WatchHistoryEntry
import com.ibrahim.av1mix.data.WatchHistoryStore
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val registry = remember { ProviderRegistry(listOf(AdultEmbedProvider())) }
    val history = remember { WatchHistoryStore(context) }
    val localPreferences = remember { LocalPreferenceStore(context) }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(MainTab.HOME) }
    var query by remember { mutableStateOf("") }
    var selectedVideo by remember { mutableStateOf<NormalizedVideo?>(null) }
    var historyVersion by remember { mutableIntStateOf(0) }
    val privacyPreferences = remember {
        context.getSharedPreferences("av1mix_privacy", android.content.Context.MODE_PRIVATE)
    }
    var ageConfirmed by remember {
        mutableStateOf(privacyPreferences.getBoolean("age_confirmed", false))
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = AppAccent,
        background = AppBackground,
        surface = AppSurface,
        onPrimary = Color(0xFF10150A),
        onBackground = Color.White,
        onSurface = Color.White
    )) {
        if (!ageConfirmed) {
            AgeGateScreen {
                privacyPreferences.edit().putBoolean("age_confirmed", true).apply()
                ageConfirmed = true
            }
        } else if (selectedVideo != null) {
            BackHandler { selectedVideo = null }
            PlayerScreen(video = selectedVideo!!, registry = registry, preferences = localPreferences, onBack = { selectedVideo = null }, onOpenRelated = { related -> history.add(related); historyVersion++; selectedVideo = related })
        } else {
            Scaffold(
                containerColor = AppBackground,
                bottomBar = {
                    NavigationBar(containerColor = AppSurface) {
                        NavigationBarItem(tab == MainTab.HOME, { tab = MainTab.HOME }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("الرئيسية") })
                        NavigationBarItem(tab == MainTab.SEARCH, { tab = MainTab.SEARCH }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("بحث") })
                        NavigationBarItem(tab == MainTab.HISTORY, { tab = MainTab.HISTORY; historyVersion++ }, icon = { Icon(Icons.Default.History, null) }, label = { Text("السجل") })
                        NavigationBarItem(tab == MainTab.SETTINGS, { tab = MainTab.SETTINGS }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("الإعدادات") })
                    }
                }
            ) { padding ->
                when (tab) {
                    MainTab.HOME -> HomeScreen(
                        registry = registry,
                        preferences = localPreferences,
                        modifier = Modifier.padding(padding),
                        onOpen = { video ->
                            history.add(video)
                            historyVersion++
                            selectedVideo = video
                        }
                    )
                    MainTab.SEARCH -> SearchScreen(
                        registry = registry,
                        preferences = localPreferences,
                        query = query,
                        onQueryChange = { query = it },
                        modifier = Modifier.padding(padding),
                        onOpen = { video ->
                            history.add(video)
                            historyVersion++
                            selectedVideo = video
                        }
                    )
                    MainTab.HISTORY -> HistoryScreen(
                        history = history,
                        version = historyVersion,
                        modifier = Modifier.padding(padding),
                        onOpen = { entry ->
                            selectedVideo = entry.toVideo()
                        },
                        onClear = { history.clear(); historyVersion++ }
                    )
                    MainTab.SETTINGS -> SettingsScreen(preferences = localPreferences, modifier = Modifier.padding(padding))
                }
            }
        }
    }
}


@Composable
private fun AgeGateScreen(onConfirm: () -> Unit) {
    Box(Modifier.fillMaxSize().background(AppBackground).padding(28.dp), contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = AppSurface),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("AV1 MIX", color = AppAccent, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(18.dp))
                Text("محتوى للبالغين فقط", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(
                    "باستخدام التطبيق، تؤكد أنك تبلغ 18 سنة أو أكثر وأن استخدامك للمحتوى مسموح به في مكان إقامتك.",
                    color = AppMuted,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppAccent)
                ) { Text("أنا 18 سنة أو أكثر", color = Color.Black, fontWeight = FontWeight.Bold) }
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
private fun HomeScreen(
    registry: ProviderRegistry,
    preferences: LocalPreferenceStore,
    modifier: Modifier = Modifier,
    onOpen: (NormalizedVideo) -> Unit
) {
    var videos by remember { mutableStateOf<List<NormalizedVideo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh) {
        loading = true
        error = null
        try {
            videos = preferences.rank(registry.discover())
        } catch (exception: Exception) {
            error = exception.message ?: "تعذر الاتصال بالمصدر"
        } finally {
            loading = false
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { PageHeader("مقترحاتك · تتطور مع اختياراتك") }
            Button(onClick = { refresh++ }, colors = ButtonDefaults.buttonColors(containerColor = AppAccent)) {
                Text("تحديث", color = Color.Black)
            }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AppAccent)
                    Spacer(Modifier.height(12.dp))
                    Text("بنحمّل الفيديوهات...", color = AppMuted)
                }
            }
            error != null -> EmptyState(error ?: "خطأ", "راجع اتصال الإنترنت وحاول مرة أخرى") { refresh++ }
            videos.isEmpty() -> EmptyState("مفيش نتائج حاليًا", "المصدر لم يرجع فيديوهات في هذا التحديث") { refresh++ }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(videos, key = { it.providerId + ":" + it.canonicalId }) { video ->
                    VideoCard(video) { onOpen(video) }
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(
    registry: ProviderRegistry,
    preferences: LocalPreferenceStore,
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (NormalizedVideo) -> Unit
) {
    var results by remember { mutableStateOf<List<NormalizedVideo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize()) {
        PageHeader("ابحث في المصادر المتصلة")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                label = { Text("كلمة البحث") },
                singleLine = true
            )
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        searched = true
                        error = null
                        try { results = preferences.rank(registry.search(query, 1)) }
                        catch (exception: Exception) { error = exception.message ?: "تعذر البحث" }
                        finally { loading = false }
                    }
                },
                enabled = query.isNotBlank() && !loading,
                colors = ButtonDefaults.buttonColors(containerColor = AppAccent, disabledContainerColor = AppSurface)
            ) { Text("بحث", color = Color.Black) }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppAccent) }
            error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(error ?: "", color = AppMuted) }
            searched && results.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text("مفيش نتائج مطابقة.", color = AppMuted) }
            results.isNotEmpty() -> LazyColumn(
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) { items(results, key = { it.providerId + ":" + it.canonicalId }) { video -> VideoCard(video) { onOpen(video) } } }
            else -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text("اكتب كلمة واضغط بحث.", color = AppMuted) }
        }
    }
}

@Composable
private fun VideoCard(video: NormalizedVideo, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column {
            if (!video.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.fillMaxWidth().height(100.dp).background(Color(0xFF202832)), contentAlignment = Alignment.Center) {
                    Text("AV1 MIX", color = AppAccent, fontWeight = FontWeight.Black)
                }
            }
            Column(Modifier.padding(13.dp)) {
                Text(video.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Text(
                    "AdultEmbed · " + (video.durationSeconds?.let { formatDuration(it) } ?: "المدة غير محددة"),
                    color = AppMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun PlayerScreen(video: NormalizedVideo, registry: ProviderRegistry, preferences: LocalPreferenceStore, onBack: () -> Unit, onOpenRelated: (NormalizedVideo) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var playerWebView by remember(video.providerId, video.canonicalId) { mutableStateOf<WebView?>(null) }
    var relatedVideos by remember(video.providerId, video.canonicalId) { mutableStateOf<List<NormalizedVideo>>(emptyList()) }
    var pageLoading by remember(video.providerId, video.canonicalId) { mutableStateOf(true) }
    var playerError by remember(video.providerId, video.canonicalId) { mutableStateOf<String?>(null) }
    var reloadKey by remember(video.providerId, video.canonicalId) { mutableIntStateOf(0) }
    var feedbackMessage by remember(video.providerId, video.canonicalId) { mutableStateOf("") }
    val openedAt = remember(video.providerId, video.canonicalId) { SystemClock.elapsedRealtime() }

    LaunchedEffect(video.providerId, video.canonicalId) {
        relatedVideos = runCatching { registry.related(video) }.getOrDefault(emptyList())
    }
    DisposableEffect(video.providerId, video.canonicalId) {
        onDispose {
            val secondsOnScreen = ((SystemClock.elapsedRealtime() - openedAt) / 1000L).coerceAtLeast(0L)
            preferences.recordDwell(video, secondsOnScreen)
        }
    }
    DisposableEffect(lifecycleOwner, playerWebView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> playerWebView?.onPause()
                Lifecycle.Event.ON_RESUME -> playerWebView?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxSize().background(AppBackground)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = AppSurface)) { Text("رجوع") }
            Text(video.title, modifier = Modifier.weight(1f).padding(start = 10.dp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    CookieManager.getInstance().setAcceptCookie(true)
                    WebView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        settings.setSupportZoom(false)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                pageLoading = true
                                playerError = null
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                pageLoading = false
                            }
                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                if (request?.isForMainFrame == true) {
                                    pageLoading = false
                                    playerError = "تعذر تحميل صفحة المشغّل (${error?.errorCode ?: "شبكة"})"
                                }
                            }
                            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                                if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 200) >= 400) {
                                    pageLoading = false
                                    playerError = "المصدر رجّع خطأ HTTP ${errorResponse?.statusCode}"
                                }
                            }
                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                handler?.cancel()
                                pageLoading = false
                                playerError = "تعذّر التحقق من شهادة أمان المشغّل"
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                android.util.Log.d("AV1MixPlayer", consoleMessage?.message().orEmpty())
                                return true
                            }
                        }
                    }.also { playerWebView = it }
                },
                update = { view ->
                    val target = video.playbackUrl ?: video.canonicalPageUrl
                    val fingerprint = "${target}#${reloadKey}"
                    if (view.tag != fingerprint) {
                        view.tag = fingerprint
                        pageLoading = true
                        playerError = null
                        view.loadDataWithBaseURL(OfficialEmbedPage.origin(target), OfficialEmbedPage.build(target), "text/html", "UTF-8", null)
                    }
                },
                onRelease = { view ->
                    view.stopLoading()
                    view.onPause()
                    view.removeAllViews()
                    view.destroy()
                    if (playerWebView === view) playerWebView = null
                }
            )
            if (pageLoading && playerError == null) {
                Box(Modifier.fillMaxSize().background(Color(0xCC090C10)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AppAccent)
                        Spacer(Modifier.height(8.dp))
                        Text("بنحمّل المشغّل الرسمي...", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
            if (playerError != null) {
                Box(Modifier.fillMaxSize().background(Color(0xF2090C10)).padding(18.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(playerError ?: "تعذّر تشغيل المشغّل", color = Color.White, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { reloadKey++ }, colors = ButtonDefaults.buttonColors(containerColor = AppAccent)) {
                            Text("إعادة تحميل المشغّل", color = Color.Black)
                        }
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.playbackUrl ?: video.canonicalPageUrl))) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppSurface)
                        ) { Text("فتح في المتصفح") }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(AppSurface).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("المشغّل الرسمي", color = AppAccent, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Button(onClick = { reloadKey++ }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF303943))) {
                    Text("تحديث المشغّل", color = Color.White)
                }
            }
            Text(
                "لو الصورة فضلت سوداء، جرّب إعادة التحميل. هذا المصدر لا يتيح رابط بث مباشر مستقلًا للتطبيق؛ لذلك لا يمكن التحكم في ترميزه أو Buffer من هنا.",
                color = AppMuted, fontSize = 11.sp, lineHeight = 16.sp
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        preferences.recordFeedback(video, liked = true)
                        feedbackMessage = "تم حفظ تفضيلك على الجهاز"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppAccent),
                    modifier = Modifier.weight(1f)
                ) { Text("👍 يعجبني", color = Color.Black) }
                Button(
                    onClick = {
                        preferences.recordFeedback(video, liked = false)
                        feedbackMessage = "هنقلّل ظهور المحتوى المشابه"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF303943)),
                    modifier = Modifier.weight(1f)
                ) { Text("مش مهتم", color = Color.White) }
            }
            if (feedbackMessage.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(feedbackMessage, color = AppMuted, fontSize = 11.sp)
            }
        }
        if (relatedVideos.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp)) {
                Text("اختيارات قريبة من الفيديو", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(relatedVideos, key = { it.providerId + ":" + it.canonicalId }) { related ->
                        Column(Modifier.width(170.dp).clickable { onOpenRelated(related) }) {
                            if (!related.thumbnailUrl.isNullOrBlank()) {
                                AsyncImage(model = related.thumbnailUrl, contentDescription = related.title, modifier = Modifier.fillMaxWidth().height(92.dp), contentScale = ContentScale.Crop)
                            } else {
                                Box(Modifier.fillMaxWidth().height(92.dp).background(Color(0xFF202832)), contentAlignment = Alignment.Center) {
                                    Text("AV1 MIX", color = AppAccent)
                                }
                            }
                            Text(related.title, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HistoryScreen(
    history: WatchHistoryStore,
    version: Int,
    modifier: Modifier = Modifier,
    onOpen: (WatchHistoryEntry) -> Unit,
    onClear: () -> Unit
) {
    val entries = remember(version) { history.all() }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { PageHeader("سجل المشاهدة محفوظ على الجهاز") }
            if (entries.isNotEmpty()) {
                Button(onClick = onClear, colors = ButtonDefaults.buttonColors(containerColor = AppSurface)) { Text("مسح") }
            }
        }
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("لا توجد مشاهدات مسجلة بعد.", color = AppMuted)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(entries, key = { it.providerId + ":" + it.canonicalId }) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(entry) },
                        colors = CardDefaults.cardColors(containerColor = AppSurface)
                    ) {
                        Text(entry.title, Modifier.padding(16.dp), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(preferences: LocalPreferenceStore, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        PageHeader("إعدادات التشغيل والخصوصية")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsCard("المصادر", "يتم استخدام واجهة البحث ومشغّل AdultEmbed الرسمي. لا يتم استخراج روابط الفيديو أو تجاوز مشغلات المواقع.")
            SettingsCard("AV1 والجودة", "هذا المصدر يشغّل الفيديو داخل مشغّله الرسمي؛ اختيار الترميز والجودة يخضع للمصدر ولا يمكن فرض AV1 من التطبيق.")
            SettingsCard("التخزين المؤقت", "المشغّل الرسمي يدير التخزين المؤقت داخليًا. التحكم المتقدم في Buffer والتنزيل الدائم غير متاحين لهذا المصدر.")
            SettingsCard("التوصيات", "تتأثر الاقتراحات بإعجاباتك، وعدم اهتمامك، ومدة بقائك على صفحة الفيديو. إشارات المدة تقريبية لأن المشغّل الرسمي لا يرسل حالة التشغيل للتطبيق.")
            Button(
                onClick = { preferences.clear() },
                colors = ButtonDefaults.buttonColors(containerColor = AppSurface),
                modifier = Modifier.fillMaxWidth()
            ) { Text("مسح تفضيلات التوصيات") }
            SettingsCard("الخصوصية", "سجل المشاهدة وتفضيلات التوصيات محفوظان محليًا على الجهاز.")
            SettingsCard("إصدار التطبيق", "0.3.0")
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

@Composable
private fun EmptyState(title: String, description: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text(description, color = AppMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = AppAccent)) {
                Text("إعادة المحاولة", color = Color.Black)
            }
        }
    }
}

private fun WatchHistoryEntry.toVideo() = NormalizedVideo(
    providerId = providerId,
    canonicalId = canonicalId,
    title = title,
    durationSeconds = durationSeconds,
    thumbnailUrl = thumbnailUrl,
    canonicalPageUrl = playbackUrl,
    tags = emptyList(),
    performerIds = emptyList(),
    studioId = null,
    updatedAtEpochSeconds = null,
    playbackUrl = playbackUrl
)

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remaining)
    else "%d:%02d".format(minutes, remaining)
}
