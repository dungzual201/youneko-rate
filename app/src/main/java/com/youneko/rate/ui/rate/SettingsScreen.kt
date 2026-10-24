package com.youneko.rate.ui.rate

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import com.youneko.rate.BuildConfig
import com.youneko.rate.R
import com.youneko.rate.data.CrashLogStore
import com.youneko.rate.data.SettingsDataStore
import com.youneko.rate.data.credits.CreditSourceId
import com.youneko.rate.data.scan.MediaStoreScanWorker
import com.youneko.rate.domain.usecase.RatingScale
import com.youneko.rate.domain.usecase.ScoreMode
import com.youneko.rate.ui.ThemeMode
import com.youneko.rate.ui.YnDimens
import com.youneko.rate.ui.adaptive.rememberYnPagePadding
import com.youneko.rate.ui.components.YnSectionCard
import com.youneko.rate.ui.components.YnTopBar
import com.youneko.rate.ui.media.MediaScanRootManager
import kotlinx.coroutines.launch

@Composable
fun RatingScale.displayLabel(): String = stringResource(
    when (this) {
        RatingScale.FIVE_STARS -> R.string.rating_scale_five_stars
        RatingScale.TEN_POINT -> R.string.rating_scale_ten_points
        RatingScale.HUNDRED_POINT -> R.string.rating_scale_hundred_points
    },
)

@Composable
fun SettingsScreen(
    onOpenExport: () -> Unit = {},
    onOpenImport: () -> Unit = onOpenExport,
    onBack: () -> Unit = {},
    viewModel: ScoreSettingsViewModel = hiltViewModel(),
) {
    val mode by viewModel.scoreMode.collectAsStateWithLifecycle()
    val ratingScale by viewModel.ratingScale.collectAsStateWithLifecycle()
    val offlineOnly by viewModel.offlineOnly.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val reducedMotion by viewModel.reducedMotion.collectAsStateWithLifecycle()
    val discogsEnabled by viewModel.discogsEnabled.collectAsStateWithLifecycle()
    val discogsToken by viewModel.discogsToken.collectAsStateWithLifecycle()
    val lastFmEnabled by viewModel.lastFmEnabled.collectAsStateWithLifecycle()
    val lastFmApiKey by viewModel.lastFmApiKey.collectAsStateWithLifecycle()
    val geniusEnabled by viewModel.geniusEnabled.collectAsStateWithLifecycle()
    val geniusToken by viewModel.geniusToken.collectAsStateWithLifecycle()
    val showCreditSources by viewModel.showCreditSources.collectAsStateWithLifecycle()
    val creditSourceOrder by viewModel.creditSourceOrder.collectAsStateWithLifecycle()
    val activeCreditSources by viewModel.activeCreditSources.collectAsStateWithLifecycle()
    val creditsMergeMode by viewModel.creditsMergeMode.collectAsStateWithLifecycle()
    val tokenTestResult by viewModel.tokenTestResult.collectAsStateWithLifecycle()
    val applicationLanguage = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val currentLanguage = applicationLanguage.substringBefore('-')
    val appContext = LocalContext.current.applicationContext
    val settingsStore = remember(appContext) { SettingsDataStore(appContext) }
    val themeModeName by settingsStore.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM.name)
    val settingsScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val noCrashLogMessage = stringResource(R.string.no_crash_log)
    val scanWorkInfos by viewModel.scanWorkInfos.collectAsStateWithLifecycle()
    val scanWork = scanWorkInfos.firstOrNull()
    val scanActive = scanWork?.state == WorkInfo.State.ENQUEUED || scanWork?.state == WorkInfo.State.RUNNING
    var showRescanConfirmation by rememberSaveable { mutableStateOf(false) }
    var providersExpanded by rememberSaveable { mutableStateOf(false) }
    var creditsExpanded by rememberSaveable { mutableStateOf(false) }
    val pagePadding = rememberYnPagePadding(maxContentWidth = YnDimens.readingMaxWidth)
    BackHandler(onBack = onBack)

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            YnTopBar(
                title = stringResource(R.string.settings),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = pagePadding, end = pagePadding, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                YnSectionCard(title = stringResource(R.string.appearance)) {
                    Text(stringResource(R.string.language), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = applicationLanguage.isBlank(),
                            onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) },
                            label = { Text(stringResource(R.string.language_system), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                        FilterChip(
                            selected = currentLanguage == "en",
                            onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) },
                            label = { Text(stringResource(R.string.language_english)) },
                        )
                        FilterChip(
                            selected = currentLanguage == "vi",
                            onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("vi")) },
                            label = { Text(stringResource(R.string.language_vietnamese)) },
                        )
                    }
                    Text(stringResource(R.string.theme_mode), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { option ->
                            FilterChip(
                                selected = themeModeName == option.name,
                                onClick = { settingsScope.launch { settingsStore.setThemeMode(option.name) } },
                                label = {
                                    Text(
                                        stringResource(
                                            when (option) {
                                                ThemeMode.SYSTEM -> R.string.theme_system
                                                ThemeMode.LIGHT -> R.string.theme_light
                                                ThemeMode.DARK -> R.string.theme_dark
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                    ToggleRow(
                        title = stringResource(R.string.dynamic_color),
                        subtitle = stringResource(R.string.dynamic_color_body),
                        checked = dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                    ToggleRow(
                        title = stringResource(R.string.reduce_motion),
                        subtitle = stringResource(R.string.reduce_motion_body),
                        checked = reducedMotion,
                        onCheckedChange = viewModel::setReducedMotion,
                    )
                }
            }
            item {
                YnSectionCard(title = stringResource(R.string.rating_settings)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = mode == ScoreMode.SIMPLE,
                            onClick = { viewModel.setScoreMode(ScoreMode.SIMPLE) },
                            label = { Text(stringResource(R.string.simple_average)) },
                        )
                        FilterChip(
                            selected = mode == ScoreMode.WEIGHTED_BY_DURATION,
                            onClick = { viewModel.setScoreMode(ScoreMode.WEIGHTED_BY_DURATION) },
                            label = { Text(stringResource(R.string.weighted_average)) },
                        )
                    }
                    Text(stringResource(R.string.rating_scale), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RatingScale.entries.forEach { scale ->
                            FilterChip(
                                selected = ratingScale == scale,
                                onClick = { viewModel.setRatingScale(scale) },
                                label = { Text(scale.displayLabel()) },
                            )
                        }
                    }
                }
            }
            item {
                YnSectionCard(title = stringResource(R.string.library_and_scanning)) {
                    MediaScanRootManager()
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    ActionRow(
                        title = stringResource(R.string.refresh_music_data),
                        icon = Icons.Default.Refresh,
                        onClick = viewModel::refreshMusicData,
                        enabled = !scanActive,
                    )
                    ActionRow(
                        title = stringResource(R.string.rescan_all_music),
                        icon = Icons.Default.Search,
                        onClick = { showRescanConfirmation = true },
                        enabled = !scanActive,
                    )
                    ActionRow(
                        title = stringResource(R.string.reload_all_covers),
                        icon = Icons.Default.Image,
                        onClick = viewModel::reloadAllCovers,
                        enabled = !scanActive,
                    )
                    ActionRow(
                        title = stringResource(R.string.reextract_artwork),
                        icon = Icons.Default.CloudDownload,
                        onClick = viewModel::reextractArtwork,
                        enabled = !scanActive,
                    )
                    ActionRow(
                        title = stringResource(R.string.metadata_cache_clear),
                        icon = Icons.Default.CleaningServices,
                        onClick = viewModel::clearMetadataCache,
                    )
                    if (scanActive) {
                        val done = scanWork?.progress?.getInt(MediaStoreScanWorker.KEY_DONE, 0) ?: 0
                        val total = scanWork?.progress?.getInt(MediaStoreScanWorker.KEY_TOTAL, 0) ?: 0
                        Text(stringResource(R.string.rescan_all_progress, done, total), style = MaterialTheme.typography.bodySmall)
                        if (total > 0) {
                            LinearProgressIndicator(
                                progress = { (done.toFloat() / total).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            item {
                YnSectionCard(
                    title = stringResource(R.string.online_metadata),
                    subtitle = if (offlineOnly) stringResource(R.string.offline_mode) else null,
                ) {
                    ToggleRow(
                        title = stringResource(R.string.offline_mode),
                        subtitle = null,
                        checked = offlineOnly,
                        onCheckedChange = viewModel::setOfflineOnly,
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { providersExpanded = !providersExpanded }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.provider_settings), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.provider_settings_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            if (providersExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                        )
                    }
                    if (providersExpanded) {
                        ProviderBlock(
                            name = stringResource(R.string.provider_discogs),
                            enabled = discogsEnabled,
                            token = discogsToken,
                            help = stringResource(R.string.credits_token_help_discogs),
                            testResult = tokenTestResult["discogs"],
                            onEnabledChange = viewModel::setDiscogsEnabled,
                            onTokenChange = viewModel::setDiscogsToken,
                            onTest = { viewModel.testDiscogsToken(discogsToken) },
                        )
                        HorizontalDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.provider_lastfm), style = MaterialTheme.typography.titleSmall)
                            ToggleRow(
                                title = if (lastFmEnabled) stringResource(R.string.provider_enabled) else stringResource(R.string.provider_need_key),
                                subtitle = null,
                                checked = lastFmEnabled,
                                onCheckedChange = viewModel::setLastFmEnabled,
                            )
                            OutlinedTextField(
                                value = lastFmApiKey,
                                onValueChange = viewModel::setLastFmApiKey,
                                label = { Text(stringResource(R.string.provider_api_key)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        HorizontalDivider()
                        ProviderBlock(
                            name = stringResource(R.string.provider_genius),
                            enabled = geniusEnabled,
                            token = geniusToken,
                            help = stringResource(R.string.credits_token_help_genius),
                            testResult = tokenTestResult["genius"],
                            onEnabledChange = viewModel::setGeniusEnabled,
                            onTokenChange = viewModel::setGeniusToken,
                            onTest = { viewModel.testGeniusToken(geniusToken) },
                        )
                    }
                }
            }
            item {
                YnSectionCard(
                    title = stringResource(R.string.credits_sources_settings),
                    action = {
                        IconButton(onClick = { creditsExpanded = !creditsExpanded }) {
                            Icon(
                                if (creditsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        }
                    },
                ) {
                    ToggleRow(
                        title = stringResource(R.string.credits_show_sources),
                        subtitle = null,
                        checked = showCreditSources,
                        onCheckedChange = viewModel::setShowCreditSources,
                    )
                    ToggleRow(
                        title = stringResource(R.string.credits_view_merged),
                        subtitle = null,
                        checked = creditsMergeMode,
                        onCheckedChange = viewModel::setCreditsMergeMode,
                    )
                    if (creditsExpanded) {
                        Text(stringResource(R.string.credits_source_order), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        creditSourceOrder.forEachIndexed { index, id ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = id in activeCreditSources || id == CreditSourceId.FILE_TAG,
                                    onClick = { viewModel.setCreditSourceEnabled(id, id !in activeCreditSources) },
                                    label = { Text(id.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                )
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { viewModel.moveCreditSource(id, -1) }, enabled = index > 0) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.credits_move_up))
                                }
                                IconButton(onClick = { viewModel.moveCreditSource(id, 1) }, enabled = index < creditSourceOrder.lastIndex) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.credits_move_down))
                                }
                            }
                        }
                    }
                }
            }
            item {
                YnSectionCard(title = stringResource(R.string.backup_title)) {
                    Text(stringResource(R.string.backup_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onOpenExport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_export)) }
                    OutlinedButton(onClick = onOpenImport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_import)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onOpenExport, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.backup_export_csv), maxLines = 1) }
                        OutlinedButton(onClick = onOpenExport, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.backup_export_json), maxLines = 1) }
                    }
                    Text(stringResource(R.string.backup_no_token_music), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                YnSectionCard(title = stringResource(R.string.diagnostics)) {
                    ActionRow(
                        title = stringResource(R.string.send_crash_log),
                        icon = Icons.Default.BugReport,
                        onClick = {
                            val crashFile = CrashLogStore.latest(appContext)
                            if (crashFile == null) {
                                settingsScope.launch { snackbarHostState.showSnackbar(noCrashLogMessage) }
                            } else {
                                val uri = androidx.core.content.FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", crashFile)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                appContext.startActivity(Intent.createChooser(shareIntent, appContext.getString(R.string.crash_log_share_title)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                    )
                }
            }
            item {
                YnSectionCard(title = stringResource(R.string.about)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(stringResource(R.string.settings_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.settings_auto_saved), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showRescanConfirmation) {
        AlertDialog(
            onDismissRequest = { showRescanConfirmation = false },
            title = { Text(stringResource(R.string.rescan_all_title)) },
            text = { Text(stringResource(R.string.rescan_all_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showRescanConfirmation = false
                    viewModel.rescanAllMusic()
                    settingsScope.launch { snackbarHostState.showSnackbar(appContext.getString(R.string.rescan_all_started)) }
                }) { Text(stringResource(R.string.rescan_all_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showRescanConfirmation = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ActionRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ProviderBlock(
    name: String,
    enabled: Boolean,
    token: String,
    help: String,
    testResult: Int?,
    onEnabledChange: (Boolean) -> Unit,
    onTokenChange: (String) -> Unit,
    onTest: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        OutlinedTextField(
            value = token,
            onValueChange = onTokenChange,
            label = { Text(stringResource(R.string.provider_api_key)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(help, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onTest, enabled = token.isNotBlank()) { Text(stringResource(R.string.credits_test_token)) }
            testResult?.let { code ->
                Spacer(Modifier.width(10.dp))
                Text(
                    if (code == 200) stringResource(R.string.credits_token_valid) else stringResource(R.string.credits_token_invalid, code),
                    color = if (code == 200) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
