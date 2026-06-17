package com.remotedroid.ui

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.remotedroid.service.RemoteAccessibilityService
import com.remotedroid.service.ServerService
import com.remotedroid.store.Settings
import java.net.NetworkInterface
import java.util.Collections

@Composable
fun SetupScreen() {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    var token by remember { mutableStateOf(settings.token) }
    val ip = remember { localIp() ?: "—" }
    val url = "http://$ip:${settings.port}/?t=$token"
    val a11yOn = RemoteAccessibilityService.instance != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("RemoteDroid", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Scan the QR with your phone's camera; the remote opens in the browser.",
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))

        val bmp = remember(url) { runCatching { qrBitmap(url, 600) }.getOrNull() }
        if (bmp != null) {
            Image(bitmap = bmp.asImageBitmap(), contentDescription = "Pairing QR code")
        }
        Spacer(Modifier.height(8.dp))
        Text(url, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))

        Button(onClick = { ServerService.start(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("Start server")
        }
        OutlinedButton(onClick = { ServerService.stop(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("Stop")
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                context.startActivity(
                    Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open accessibility settings")
        }
        OutlinedButton(
            onClick = { token = settings.regenerateToken() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Regenerate token")
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (a11yOn) "Accessibility service: connected ✓" else "Accessibility service: off — enable it",
            color = if (a11yOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

private fun localIp(): String? = runCatching {
    Collections.list(NetworkInterface.getNetworkInterfaces())
        .filter { it.isUp && !it.isLoopback }
        .flatMap { Collections.list(it.inetAddresses) }
        .firstOrNull { !it.isLoopbackAddress && it.address.size == 4 && !it.isLinkLocalAddress }
        ?.hostAddress
}.getOrNull()
