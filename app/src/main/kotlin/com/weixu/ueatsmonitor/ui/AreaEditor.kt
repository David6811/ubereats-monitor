package com.weixu.ueatsmonitor.ui

import android.annotation.SuppressLint
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.weixu.ueatsmonitor.action.Cloud
import com.weixu.ueatsmonitor.domain.EditorLink

/** Data. Where opening the editor has got to. */
private sealed interface EditorPage {
    data object Opening : EditorPage
    data object SignedOut : EditorPage
    data class Ready(val url: String) : EditorPage
}

/**
 * The laptop's rules editor, full screen, already signed in.
 *
 * It is the same page, not a second editor: the sets are drawn on a map, and
 * a phone editor without one was built once and taken out for exactly that.
 * Saving writes the cloud row; the phone's own copy follows through the sync
 * that already watches it, and [onClose] is where the caller reads it back.
 */
@Composable
fun AreaEditor(onClose: () -> Unit) {
    val words = words()
    var page: EditorPage by remember { mutableStateOf(EditorPage.Opening) }
    LaunchedEffect(Unit) {
        page = Cloud.handOver()?.let { EditorPage.Ready(EditorLink.url(it)) } ?: EditorPage.SignedOut
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Dash.Ground)
                .systemBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = words.editAreasHere,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = Dash.Ink,
                )
                TextButton(onClick = onClose) { Text(words.closeEditor, color = Dash.Gold) }
            }
            when (val now = page) {
                EditorPage.Opening -> Notice(words.editorOpening)
                EditorPage.SignedOut -> Notice(words.editorSignedOut)
                is EditorPage.Ready -> EditorWebView(now.url, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Dash.Muted)
    }
}

/**
 * Action. The page itself. JavaScript and storage for the page to run at all;
 * location so its "use where I am" works - the app already holds the
 * permission, so the page is not asked again.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EditorWebView(url: String, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setGeolocationEnabled(true)
                webViewClient = WebViewClient()
                webChromeClient = object : WebChromeClient() {
                    override fun onGeolocationPermissionsShowPrompt(
                        origin: String,
                        callback: GeolocationPermissions.Callback,
                    ) = callback.invoke(origin, true, false)
                }
                loadUrl(url)
            }
        },
        onRelease = { it.destroy() },
    )
}
