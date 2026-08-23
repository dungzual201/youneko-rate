package com.youneko.rate.ui.coversearch

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.youneko.rate.R
import com.youneko.rate.data.AlbumRepository
import com.youneko.rate.data.artwork.CoverApplyService
import com.youneko.rate.data.artwork.CoverDownloadResult
import com.youneko.rate.data.artwork.CoverDownloadService
import com.youneko.rate.data.artwork.FailureReason
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

 data class CoverSearchUiState(
    val artist: String = "",
    val album: String = "",
    val applying: Boolean = false,
    val applyError: FailureReason? = null,
    val previewUri: String? = null,
    val applied: Boolean = false,
)

@HiltViewModel
class CoverSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AlbumRepository,
    private val downloader: CoverDownloadService,
    private val applier: CoverApplyService,
) : ViewModel() {
    private val albumId: String = checkNotNull(savedStateHandle["albumId"])
    private val _state = MutableStateFlow(CoverSearchUiState())
    val state: StateFlow<CoverSearchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAlbum(albumId).collect { album ->
                if (album == null) return@collect
                val artist = album.artist?.name.orEmpty()
                val title = album.album.title
                if (_state.value.artist != artist || _state.value.album != title) {
                    _state.value = _state.value.copy(artist = artist, album = title)
                }
            }
        }
    }

    fun setArtist(value: String) { _state.value = _state.value.copy(artist = value) }
    fun setAlbum(value: String) { _state.value = _state.value.copy(album = value) }

    fun browserUrl(): String {
        val current = _state.value
        val query = "https://covers.musichoarders.xyz/?artist=${encode(current.artist)}&album=${encode(current.album)}"
        return query
    }

    fun importUri(uri: Uri) {
        if (_state.value.applying) return
        _state.value = _state.value.copy(applying = true, applyError = null)
        viewModelScope.launch {
            when (val imported = downloader.importFromUri(albumId, uri, "manual")) {
                is CoverDownloadResult.Failure -> _state.value = _state.value.copy(applying = false, applyError = imported.reason)
                is CoverDownloadResult.Success -> applier.apply(albumId, imported.cover)
                    .onSuccess {
                        _state.value = _state.value.copy(
                            applying = false,
                            previewUri = imported.cover.thumbnailFile.toURI().toString(),
                            applied = true,
                        )
                    }
                    .onFailure { _state.value = _state.value.copy(applying = false, applyError = FailureReason.WRITE_FAILED) }
            }
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverSearchScreen(onBack: () -> Unit, viewModel: CoverSearchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val importSuccessMessage = stringResource(R.string.cover_import_success)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::importUri) }

    LaunchedEffect(state.applied) {
        if (state.applied) {
            snackbarHostState.showSnackbar(importSuccessMessage, duration = SnackbarDuration.Short)
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cover_search_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.artist,
                    onValueChange = viewModel::setArtist,
                    label = { Text(stringResource(R.string.cover_search_artist)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.album,
                    onValueChange = viewModel::setAlbum,
                    label = { Text(stringResource(R.string.cover_search_album)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            CoverStepCard(
                number = 1,
                title = stringResource(R.string.cover_step_one_title),
                description = stringResource(R.string.cover_step_one_description),
                buttonLabel = stringResource(R.string.cover_step_one_button),
                enabled = !state.applying,
                icon = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
                onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(viewModel.browserUrl())) },
            )
            CoverStepCard(
                number = 2,
                title = stringResource(R.string.cover_step_two_title),
                description = stringResource(R.string.cover_step_two_description),
                buttonLabel = stringResource(R.string.cover_step_two_button),
                enabled = !state.applying,
                icon = { Icon(Icons.Default.Image, contentDescription = null) },
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )

            state.previewUri?.let { preview ->
                AsyncImage(
                    model = preview,
                    contentDescription = stringResource(R.string.cover_search_title),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)),
                )
            }
            if (state.applying) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.loading))
                }
            }
            state.applyError?.let { reason ->
                val message = when (reason) {
                    FailureReason.HOTLINK_BLOCKED -> stringResource(R.string.cover_error_hotlink)
                    FailureReason.NETWORK -> stringResource(R.string.cover_error_network)
                    else -> stringResource(R.string.cover_error_apply)
                }
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CoverStepCard(
    number: Int,
    title: String,
    description: String,
    buttonLabel: String,
    enabled: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(number.toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium)
                }
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                icon()
                Spacer(Modifier.width(8.dp))
                Text(buttonLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
