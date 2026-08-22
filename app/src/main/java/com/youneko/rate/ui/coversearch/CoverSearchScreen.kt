package com.youneko.rate.ui.coversearch

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import com.youneko.rate.R
import com.youneko.rate.data.AlbumRepository
import com.youneko.rate.data.artwork.AppliedCover
import com.youneko.rate.data.artwork.CoverApplyService
import com.youneko.rate.data.artwork.CoverDownloadResult
import com.youneko.rate.data.artwork.CoverDownloadService
import com.youneko.rate.data.artwork.FailureReason
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import android.content.Context

private const val COVIT_HOST = "covers.musichoarders.xyz"

data class OfficialCoverUiState(
    val artist: String = "",
    val album: String = "",
    val applying: Boolean = false,
    val error: String? = null,
    val applied: Boolean = false,
)

@HiltViewModel
class CoverSearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    repository: AlbumRepository,
    private val downloader: CoverDownloadService,
    private val applier: CoverApplyService,
) : ViewModel() {
    private val albumId: String = checkNotNull(savedStateHandle["albumId"])
    private val _state = MutableStateFlow(OfficialCoverUiState())
    val state: StateFlow<OfficialCoverUiState> = _state.asStateFlow()
    private var lastApplied: AppliedCover? = null

    init {
        viewModelScope.launch {
            repository.observeAlbum(albumId).first()?.let { album ->
                _state.value = _state.value.copy(artist = album.artist?.name.orEmpty(), album = album.album.title)
            }
        }
    }

    fun remoteUrl(): String {
        val state = _state.value
        return buildString {
            append("file:///android_asset/covit_bridge.html?")
            append("theme=dark")
            append("&country=us")
            append("&artist=").append(encode(state.artist))
            append("&album=").append(encode(state.album))
            append("&remote.port=browser")
            append("&remote.agent=").append(encode("YounekoRate/${com.youneko.rate.BuildConfig.VERSION_NAME}"))
            append("&remote.text=").append(encode(context.getString(R.string.cover_remote_text, state.artist, state.album)))
        }
    }

    fun applyPickedMessage(json: String) {
        val parsed = runCatching { JSONObject(json) }.getOrNull() ?: return
        val type = parsed.optString("type")
        if (type == "error") {
            _state.value = _state.value.copy(error = parsed.optString("text").ifBlank { null })
            return
        }
        if (type != "cover" && type != "pick" && !parsed.has("url") && !parsed.has("bigCoverUrl") && !parsed.has("imageUrl")) return
        val url = listOf("url", "bigCoverUrl", "imageUrl", "coverUrl").firstNotNullOfOrNull { key -> parsed.optString(key).takeIf { it.startsWith("http") } } ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(applying = true, error = null)
            when (val downloaded = downloader.download(albumId, url, "official") {}) {
                is CoverDownloadResult.Failure -> _state.value = _state.value.copy(applying = false, error = when (downloaded.reason) {
                    FailureReason.HOTLINK_BLOCKED -> context.getString(R.string.cover_error_hotlink)
                    FailureReason.NETWORK -> context.getString(R.string.cover_error_network)
                    else -> context.getString(R.string.cover_error_apply)
                })
                is CoverDownloadResult.Success -> applier.apply(albumId, downloaded.cover).onSuccess { applied ->
                    lastApplied = applied
                    _state.value = _state.value.copy(applying = false, applied = true)
                }.onFailure { error -> _state.value = _state.value.copy(applying = false, error = error.message) }
            }
        }
    }

    fun browserUrl(): String {
        val current = _state.value
        return "https://covers.musichoarders.xyz/?artist=${encode(current.artist)}&album=${encode(current.album)}&country=us&theme=dark"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}

private class CovitBridge(private val onMessage: (String) -> Unit) {
    @JavascriptInterface
    fun onPick(json: String) = onMessage(json)
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverSearchScreen(onBack: () -> Unit, viewModel: CoverSearchViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var webView by remember { mutableStateOf<WebView?>(null) }
    val officialUrl = remember(state.artist, state.album) { viewModel.remoteUrl() }

    LaunchedEffect(state.applied) { if (state.applied) onBack() }
    DisposableEffect(Unit) { onDispose { webView?.destroy() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cover_search_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.a11y_back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                Text(stringResource(R.string.cover_fallback_hint), modifier = Modifier.padding(horizontal = 16.dp))
                Button(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(viewModel.browserUrl())) }, modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.cover_open_in_browser))
                }
            }
            AndroidView(
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccessFromFileURLs = false
                        settings.allowUniversalAccessFromFileURLs = false
                        addJavascriptInterface(CovitBridge(viewModel::applyPickedMessage), "AndroidBridge")
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val uri = request.url
                                if (uri.host == COVIT_HOST) return false
                                CustomTabsIntent.Builder().build().launchUrl(context, uri)
                                return true

                            }
                        }
                        loadUrl(officialUrl)
                    }
                },
                update = { view -> if (view.url != officialUrl) view.loadUrl(officialUrl) },
                modifier = Modifier.fillMaxSize(),
            )
            if (state.applying) androidx.compose.material3.CircularProgressIndicator()
            state.error?.let { message -> Text(message, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
        }
    }
}
