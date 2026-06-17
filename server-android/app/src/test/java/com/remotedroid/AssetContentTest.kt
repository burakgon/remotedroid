package com.remotedroid

import com.remotedroid.server.AssetContent
import org.junit.Assert.assertEquals
import org.junit.Test

class AssetContentTest {
    @Test
    fun rootBecomesIndex() {
        assertEquals("index.html", AssetContent.normalize("/"))
        assertEquals("index.html", AssetContent.normalize(""))
    }

    @Test
    fun stripsTraversalAndQuery() {
        assertEquals("assets/app.js", AssetContent.normalize("/assets/app.js?v=1"))
        assertEquals("secret.txt", AssetContent.normalize("/../secret.txt"))
    }

    @Test
    fun mimeTypes() {
        assertEquals("text/javascript; charset=utf-8", AssetContent.mimeFor("/assets/x.js"))
        assertEquals("application/manifest+json; charset=utf-8", AssetContent.mimeFor("/manifest.webmanifest"))
        assertEquals("text/css; charset=utf-8", AssetContent.mimeFor("a.css"))
        assertEquals("text/html; charset=utf-8", AssetContent.mimeFor("index.html"))
        assertEquals("application/octet-stream", AssetContent.mimeFor("noext"))
    }
}
