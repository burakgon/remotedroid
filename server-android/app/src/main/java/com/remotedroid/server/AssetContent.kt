package com.remotedroid.server

/**
 * Pure helpers for serving the PWA's static files. Reduces a request path to a safe
 * asset path and determines its MIME type.
 */
object AssetContent {
    /** `/` → index.html; drop query/fragment; strip `..` and `.` segments (path-traversal protection). */
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
