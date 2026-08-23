package com.youneko.rate.navigation

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.youneko.rate.R
import com.youneko.rate.ui.rate.AlbumDetailScreen
import com.youneko.rate.ui.rate.AlbumEditorScreen
import com.youneko.rate.ui.importer.ImportScreen
import com.youneko.rate.ui.rate.LibraryScreen
import com.youneko.rate.ui.rate.RateScreen
import com.youneko.rate.ui.components.YnSharedTopAppBar
import com.youneko.rate.ui.rate.SettingsScreen
import com.youneko.rate.ui.analyze.AudioAnalysisScreen
import com.youneko.rate.ui.stats.StatsScreen
import com.youneko.rate.ui.export.ExportScreen
import com.youneko.rate.ui.phase12.AdvancedSearchScreen
import com.youneko.rate.ui.phase12.CollectionsScreen
import com.youneko.rate.ui.phase12.ArtistPageScreen
import com.youneko.rate.ui.media.MediaAccessGate
import com.youneko.rate.ui.coversearch.CoverSearchScreen

private data class AppDestination(
    val route: String,
    @StringRes val label: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val destinations = listOf(
    AppDestination("library", R.string.library, Icons.Default.LibraryMusic),
    AppDestination("rate", R.string.rate, Icons.Default.Star),
    AppDestination("analyze", R.string.analyze, Icons.Default.GraphicEq),
    AppDestination("stats", R.string.stats, Icons.Default.BarChart),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YounekoNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    DisposableEffect(navController) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { controller, destination, _ ->
            Log.d("NAVSTACK", "route=${destination.route} current=${controller.currentDestination?.route}")
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    val isSettings = currentDestination?.route == "settings"
    val isLibrary = currentDestination?.route == "library"
    val isAnalyze = currentDestination?.route == "analyze"
    val isDetail = currentDestination?.route?.startsWith("album/") == true
    val isEditor = currentDestination?.route == "addAlbum"
    val isImport = currentDestination?.route == "importTags"
    val isExport = currentDestination?.route == "export"
    val isCoverSearch = currentDestination?.route?.startsWith("coverSearch/") == true

    MediaAccessGate(content = {
        Scaffold(
        topBar = {
            if (!isLibrary && !isAnalyze && !isDetail && !isEditor && !isImport && !isExport && !isCoverSearch) {
                YnSharedTopAppBar(
                    screenName = if (currentDestination?.route == "rate") "Rate" else "Stats",
                    actions = {
                        if (!isSettings) IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), modifier = Modifier.size(24.dp))
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (!isSettings && !isDetail && !isEditor && !isImport && !isExport && !isCoverSearch) {
                NavigationBar {
                    destinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = stringResource(destination.label)) },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(navController, startDestination = "library", modifier = Modifier.padding(innerPadding)) {
            composable("library") {
                LibraryScreen(
                    onOpenAlbum = { navController.navigate("album/$it") },
                    onAddAlbum = { navController.navigate("addAlbum") },
                    onOpenAdvancedSearch = { navController.navigate("advancedSearch") },
                    onOpenCollections = { navController.navigate("collections") },
                    onOpenSettings = { navController.navigate("settings") },
                    onAnalyzeTrack = { uri ->
                        navController.currentBackStackEntry?.savedStateHandle?.set("analyzeUri", uri)
                        navController.navigate("analyze") { launchSingleTop = true }
                    },
                )
            }
            composable("rate") {
                RateScreen(
                    onAddAlbum = { navController.navigate("addAlbum") },
                    onImportTags = {
                        navController.navigate("importTags") {
                            popUpTo("rate") { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onOpenAlbum = { navController.navigate("album/$it") },
                )
            }
            composable("analyze") {
                val initialUri = navController.previousBackStackEntry?.savedStateHandle?.get<String>("analyzeUri")
                AudioAnalysisScreen(initialUri = initialUri)
            }
            composable("stats") { StatsScreen() }
            composable("settings") {
                SettingsScreen(
                    onOpenExport = { navController.navigate("export?intent=export") },
                    onOpenImport = { navController.navigate("export?intent=import") },
                )
            }
            composable(
                route = "export?intent={intent}",
                arguments = listOf(navArgument("intent") { type = NavType.StringType; nullable = true; defaultValue = "export" }),
            ) { entry ->
                ExportScreen(
                    onBack = { navController.popBackStack() },
                    openImportOnStart = entry.arguments?.getString("intent") == "import",
                )
            }
            composable("advancedSearch") { AdvancedSearchScreen(onBack = { navController.popBackStack() }) }
            composable("collections") { CollectionsScreen(onBack = { navController.popBackStack() }) }
            composable("importTags") {
                ImportScreen(onDone = {
                    Log.d("ImportNavigation", "before success route=${backStackEntry?.destination?.route}")
                    navController.navigate("rate") {
                        popUpTo("importTags") { inclusive = true }
                        launchSingleTop = true
                    }
                    Log.d("ImportNavigation", "after success route=${navController.currentDestination?.route}")
                })
            }
            composable("addAlbum") {
                AlbumEditorScreen(onSaved = { navController.navigate("album/$it") { popUpTo("library") } }, onCancel = { navController.popBackStack() })
            }
            composable("coverSearch/{albumId}", arguments = listOf(navArgument("albumId") { type = NavType.StringType })) {
                CoverSearchScreen(onBack = { navController.popBackStack() })
            }
            composable("album/{albumId}", arguments = listOf(navArgument("albumId") { type = NavType.StringType })) {
                AlbumDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { navController.navigate("artist/$it") },
                    onAnalyzeTrack = { navController.navigate("analyze") },
                    onSearchCover = { albumId -> navController.navigate("coverSearch/$albumId") { launchSingleTop = true } },
                )
            }
            composable("artist/{artistId}", arguments = listOf(navArgument("artistId") { type = NavType.StringType })) { entry ->
                ArtistPageScreen(artistId = checkNotNull(entry.arguments?.getString("artistId")), onBack = { navController.popBackStack() })
            }
                }
        }
    })
}

@StringRes
private fun mainTopBarTitle(route: String?): Int = when (route) {
    "rate" -> R.string.rate
    "analyze" -> R.string.analyze
    "stats" -> R.string.stats
    "settings" -> R.string.settings
    else -> R.string.app_name
}

@Composable
private fun AudioQualityPlaceholder() {

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Pets, contentDescription = null, modifier = Modifier.padding(12.dp).size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.analyze), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.audio_quality_phase8_body), modifier = Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PlaceholderScreen(@StringRes title: Int, @StringRes body: Int, mascot: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(mascot, style = MaterialTheme.typography.displaySmall)
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(body), modifier = Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyLarge)
    }
}
