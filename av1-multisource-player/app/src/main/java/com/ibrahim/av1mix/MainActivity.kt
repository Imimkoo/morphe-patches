package com.ibrahim.av1mix

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.ibrahim.av1mix.data.FeedMode
import com.ibrahim.av1mix.data.SiteCatalog
import com.ibrahim.av1mix.data.SiteFeedScanner
import com.ibrahim.av1mix.data.SiteRepository
import com.ibrahim.av1mix.data.StreamCandidate
import com.ibrahim.av1mix.data.VideoItem
import com.ibrahim.av1mix.player.Av1PlayerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val AppBg = Color(0xFF090B0E)
private val Panel = Color(0xFF15191F)
private val Accent = Color(0xFFB7F36B)
private val Muted = Color(0xFF9BA4AE)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 11, 14)
        window.navigationBarColor = android.graphics.Color.rgb(9, 11, 14)
        setContent { AV1MixApp() }
    }
}

@Composable
private fun AV1MixApp() {
    val repository = remember { SiteRepository() }
    var mode by remember { mutableStateOf(FeedMode.SHORT) }
    var selected by remember { mutableStateOf<VideoItem?>(null) }
    var stream by remember { mutableStateOf<StreamCandidate?>(null) }
    var resolving by remember { mutableStateOf(false) }
    var useWebFallback by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    MaterialTheme(colorScheme = darkColorScheme(background = AppBg, surface = Panel, primary = Accent, onBackground = Color.White, onSurface = Color.White)) {
        when {
            selected != null && stream != null -> {
                BackHandler { selected = null; stream = null; useWebFallback = false }
                PlayerScreen(selected!!, stream!!) { selected = null; stream = null; useWebFallback = false }
            }
            selected != null && useWebFallback -> {
                BackHandler { selected = null; useWebFallback = false }
                SiteWebFallback(
                    item = selected!!,
                    onBack = { selected = null; useWebFallback = false },
                    onStreamFound = { url, _, _ ->
                        val current = selected
                        if (current != null && stream == null && url.startsWith("https://")) {
                            val mime = if (url.contains(".m3u8")) "application/x-mpegURL" else "video/mp4"
                            stream = StreamCandidate(url, mime, "Codec pending actual player inspection", current.pageUrl, current.siteName)
                            useWebFallback = false
                        }
                    }
                )
            }
            else -> FeedScreen(mode, repository, { mode = it }, { item ->
                selected = item
                resolving = true
                scope.launch {
                    val resolved = repository.resolve(item)
                    stream = resolved
                    resolving = false
                    if (resolved == null) useWebFallback = true
                }
            }, resolving)
        }
    }
}

@Composable
private fun FeedScreen(
    mode: FeedMode,
    repository: SiteRepository,
    onModeChange: (FeedMode) -> Unit,
    onOpen: (VideoItem) -> Unit,
    resolving: Boolean
) {
    var items by remember(mode) { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember(mode) { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var scanDone by remember(mode) { mutableIntStateOf(0) }
    val sites = remember(mode) { SiteCatalog.sites.filter { it.mode == mode } }

    LaunchedEffect(mode, refreshKey) {
        loading = true
        scanDone = 0
        items = withContext(Dispatchers.IO) { repository.loadFeed(mode) }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(AppBg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AV1 MIX", color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                Text("مصادر متعددة · مشغل واحد", color = Muted, fontSize = 12.sp)
            }
            IconButton(onClick = { refreshKey++ }) { Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = Color.White) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip("فيديوهات بالطول", mode == FeedMode.SHORT) { onModeChange(FeedMode.SHORT) }
            ModeChip("فيديوهات طويلة", mode == FeedMode.LONG) { onModeChange(FeedMode.LONG) }
        }
        Spacer(Modifier.height(12.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Accent)
                    Spacer(Modifier.height(12.dp))
                    Text("بنبحث في المصادر...", color = Muted)
                }
            }
            items.isEmpty() -> Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (scanDone < sites.size) "بنقرأ المصادر من المتصفح..." else "المصادر لم ترجع فيديوهات", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("بنستخدم فحص الصفحة داخل WebView للمواقع اللي بتمنع جلب الصفحات العادي.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { refreshKey++ }, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black)) { Text("إعادة المحاولة") }
                }
            }
            else -> LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = { it.id }) { item -> VideoCard(item) { onOpen(item) } }
            }
        }
        if (resolving) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
        if (!loading && scanDone < sites.size) {
            sites.forEach { site ->
                key(refreshKey, site.id) {
                    SiteFeedScanner(
                        site = site,
                        onItems = { found -> items = (items + found).distinctBy { it.pageUrl } },
                        onDone = { scanDone++ }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(color = if (selected) Accent else Panel, shape = RoundedCornerShape(50), modifier = Modifier.clickable(onClick = onClick)) {
        Text(label, Modifier.padding(horizontal = 18.dp, vertical = 10.dp), color = if (selected) Color.Black else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun VideoCard(item: VideoItem, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(if (item.mode == FeedMode.SHORT) 300.dp else 205.dp).background(Color(0xFF20262E))) {
            if (!item.thumbnailUrl.isNullOrBlank()) AsyncImage(model = item.thumbnailUrl, contentDescription = item.title, modifier = Modifier.fillMaxSize())
            Surface(color = Color(0xCC090B0E), shape = RoundedCornerShape(6.dp), modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                Text(item.siteName, color = Accent, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(item.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text(item.codecEvidence, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PlayerScreen(item: VideoItem, stream: StreamCandidate, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var decoderName by remember { mutableStateOf("جاري تحديد المفكك...") }
    var codecName by remember { mutableStateOf(stream.codec) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    val player = remember(stream.url) { Av1PlayerFactory.create(context, stream) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.errorCodeName + ": " + (error.message ?: "playback error")
            }
        }
        val analytics = object : AnalyticsListener {
            override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
                codecName = (format.sampleMimeType ?: "unknown") + " · " + (format.codecs ?: "codec details unavailable")
            }
            override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderNameValue: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                decoderName = decoderNameValue
            }
        }
        player.addListener(listener)
        player.addAnalyticsListener(analytics)
        onDispose {
            player.removeListener(listener)
            player.removeAnalyticsListener(analytics)
            player.release()
        }
    }
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.pause()
            if (event == Lifecycle.Event.ON_RESUME) player.play()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxSize().background(AppBg)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(item.siteName, color = Accent, fontWeight = FontWeight.Bold)
                Text(item.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
            }
        }
        AndroidView(Modifier.fillMaxWidth().weight(1f), factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            }
        }, update = { it.player = player })
        Column(Modifier.fillMaxWidth().background(Panel).padding(14.dp)) {
            Text("معلومات التشغيل", color = Accent, fontWeight = FontWeight.Bold)
            Text("Codec: $codecName", color = Color.White, fontSize = 12.sp)
            Text("Decoder: $decoderName", color = Color.White, fontSize = 12.sp)
            Text("Source: " + stream.siteName, color = Muted, fontSize = 11.sp)
            playbackError?.let { Text("Playback error: $it", color = Color(0xFFFF8A80), fontSize = 11.sp) }
        }
    }
}

@Composable
private fun SiteWebFallback(
    item: VideoItem,
    onBack: () -> Unit,
    onStreamFound: (String, Int, Int) -> Unit
) {
    Column(Modifier.fillMaxSize().background(AppBg)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color.White) }
            Text(item.siteName + " · اكتشاف مصدر الفيديو", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        AndroidView(Modifier.fillMaxSize(), factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onVideoSource(url: String, width: Int, height: Int) {
                        if (url.startsWith("https://") && !url.startsWith("blob:")) {
                            Handler(Looper.getMainLooper()).post { onStreamFound(url, width, height) }
                        }
                    }
                }, "AV1MixBridge")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        val script = """
                            (function() {
                              const scan = function() {
                                const v = document.querySelector('video');
                                if (!v) return;
                                const src = v.currentSrc || v.src || (v.querySelector('source') && v.querySelector('source').src);
                                if (!src || !/^https?:/.test(src) || src === window.__av1mixLastSource) return;
                                window.__av1mixLastSource = src;
                                if (window.AV1MixBridge) window.AV1MixBridge.onVideoSource(src, v.videoWidth || 0, v.videoHeight || 0);
                              };
                              document.addEventListener('loadedmetadata', scan, true);
                              document.addEventListener('canplay', scan, true);
                              new MutationObserver(scan).observe(document.documentElement, {subtree:true, childList:true, attributes:true});
                              let n = 0;
                              const timer = setInterval(function(){ scan(); if (++n > 30) clearInterval(timer); }, 1000);
                              scan();
                            })();
                        """.trimIndent()
                        view.evaluateJavascript(script, null)
                    }
                }
                loadUrl(item.pageUrl)
            }
        })
    }
}
