package com.remotedroid.server

/**
 * PWA statik dosya servisi yardımcıları (saf). İstek yolunu güvenli bir asset
 * yoluna indirger ve MIME tipini belirler.
 */
object AssetContent {
    /** `/` → index.html; sorgu/parça at; `..` ve `.` parçalarını ele (path traversal koruması). */
    fun normalize(path: String): String {
        val clean = path.substringBefore('?').substringBefore('#').trimStart('/')
        val safe = clean.split('/')
            .filter { it.isNotEmpty() && it != ".." && it != "." }
            .joinToString("/")
        return if (safe.isEmpty()) "index.html" else safe
    }

    fun mimeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html; charset=utf-8"
        "js", "mjs" -> "text/javascript; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "webmanifest" -> "application/manifest+json; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "ico" -> "image/x-icon"
        "woff2" -> "font/woff2"
        else -> "application/octet-stream"
    }
}
