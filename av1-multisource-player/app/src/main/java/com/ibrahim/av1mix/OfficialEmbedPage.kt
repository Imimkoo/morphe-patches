package com.ibrahim.av1mix

import java.net.URI

/**
 * AdultEmbed documents its player as an iframe integration. Do not navigate the WebView
 * directly to the player endpoint: host it in the same responsive iframe shape they document.
 */
internal object OfficialEmbedPage {
    fun build(playerUrl: String): String {
        require(isAllowedPlayerUrl(playerUrl)) { "Untrusted or invalid official player URL" }
        val safeUrl = playerUrl
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
              <meta charset="utf-8">\n              <title>AV1Mix player loading</title>
              <style>
                html, body { margin:0; padding:0; width:100%; height:100%; background:#000; overflow:hidden; }
                .frame { position:relative; width:100%; height:100%; min-height:100%; background:#000; }
                iframe { position:absolute; inset:0; width:100%; height:100%; border:0; background:#000; }
              </style>
            </head>
            <body>
              <div class="frame">
                <iframe
                  src="$safeUrl"
                  title="Official video player"
                  allow="autoplay; fullscreen; encrypted-media; picture-in-picture"
                  allowfullscreen
                  referrerpolicy="origin"
                  scrolling="no"
                  onload="document.title='AV1Mix player frame loaded';"
                  onerror="document.title='AV1Mix player frame failed';">
                </iframe>
              </div>
            </body>
            </html>
        """.trimIndent()
    }

    fun origin(playerUrl: String): String {
        require(isAllowedPlayerUrl(playerUrl)) { "Untrusted or invalid official player URL" }
        val uri = URI(playerUrl)
        val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
        return "https://${uri.host}$port/"
    }

    fun isAllowedPlayerUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        val path = uri.path ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            host in setOf("adultembed.com", "pornalizer.app") &&
            uri.userInfo == null &&
            path.matches(Regex("^/player/embed/[0-9]+/$"))
    }
}
