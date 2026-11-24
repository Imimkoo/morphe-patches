package com.ibrahim.av1mix.data

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun SiteFeedScanner(site: SourceSite, scanKey: Int, onItems: (List<VideoItem>) -> Unit, onDone: () -> Unit) {
    val finished = remember(site.id, scanKey) { AtomicBoolean(false) }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(1.dp).alpha(0f),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onFeed(json: String) {
                        val parsed = runCatching {
                            val array = JSONArray(json)
                            buildList {
                                for (i in 0 until array.length()) {
                                    val row = array.optJSONObject(i) ?: continue
                                    val page = row.optString("url")
                                    val title = row.optString("title")
                                    if (!page.startsWith("https://") || title.isBlank()) continue
                                    val rawThumb = row.optString("thumb")
                                    val thumb = runCatching {
                                        if (rawThumb.startsWith("http")) rawThumb else URI(site.homeUrl).resolve(rawThumb).toString()
                                    }.getOrNull()
                                    add(VideoItem(
                                        id = site.id + ":" + page.hashCode(),
                                        siteId = site.id, siteName = site.name, title = title,
                                        pageUrl = page, thumbnailUrl = thumb, mode = site.mode,
                                        codecEvidence = site.codecEvidence
                                    ))
                                }
                            }
                        }.getOrDefault(emptyList())
                        Handler(Looper.getMainLooper()).post {
                            if (parsed.isNotEmpty()) onItems(parsed)
                            if (finished.compareAndSet(false, true)) onDone()
                        }
                    }
                }, "AV1MixBridge")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        val script = """
                            (function() {
                              const sid = '__SITE_ID__';
                              const seen = new Set();
                              const out = [];
                              for (const a of Array.from(document.querySelectorAll('a[href]'))) {
                                let u;
                                try { u = new URL(a.href); } catch(e) { continue; }
                                const p = u.pathname.toLowerCase();
                                const ok = sid === 'xfree'
                                  ? p === '/video' && u.searchParams.has('id')
                                  : sid === 'eporner'
                                    ? /\\/(hd-porn|embed)\\//.test(p) || /\\/video-[^/]+/.test(p)
                                    : p.includes('/videos/');
                                if (!ok || seen.has(u.href)) continue;
                                const box = a.closest('article') || a.closest('[class*="video"]') || a.parentElement;
                                const img = a.querySelector('img') || (box && box.querySelector('img'));
                                if (!img) continue;
                                const title = a.getAttribute('title') || img.alt || a.innerText || (box && box.innerText) || '';
                                const thumb = img.currentSrc || img.src || img.dataset.src || img.getAttribute('data-src') || '';
                                if (!title.trim()) continue;
                                seen.add(u.href);
                                out.push({url:u.href, title:title.trim().slice(0,180), thumb:thumb});
                                if (out.length >= 35) break;
                              }
                              if (window.AV1MixBridge) window.AV1MixBridge.onFeed(JSON.stringify(out));
                            })();
                        """.trimIndent().replace("__SITE_ID__", site.id)
                        view.evaluateJavascript(script, null)
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (finished.compareAndSet(false, true)) onDone()
                        }, 9000)
                    }
                }
                loadUrl(site.homeUrl)
            }
        }
    )
}
