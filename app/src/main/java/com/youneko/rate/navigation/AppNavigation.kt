package com.youneko.rate.navigation

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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.youneko.rate.ui.rate.SettingsScreen
import com.youneko.rate.ui.credits.CreditsScreen

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
    val isSettings = currentDestination?.route == "settings"
    val isDetail = currentDestination?.route?.startsWith("album/") == true
    val isCredits = currentDestination?.route?.startsWith("credits/") == true
    val isEditor = currentDestination?.route == "addAlbum"
    val isImport = currentDestination?.route == "importTags"

    Scaffold(
        topBar = {
            if (!isDetail && !isCredits && !isEditor && !isImport) {
                TopAppBar(
                    title = { Text(if (isSettings) stringResource(R.string.settings) else stringResource(R.string.app_name)) },
                    actions = {
                        if (!isSettings) IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (!isSettings && !isDetail && !isCredits && !isEditor && !isImport) {
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
                )
            }
            composable("rate") {
                RateScreen(
                    onAddAlbum = { navController.navigate("addAlbum") },
                    onImportTags = { navController.navigate("importTags") },
                    onOpenAlbum = { navController.navigate("album/$it") },
                )
            }
            composable("analyze") { AudioQualityPlaceholder() }
            composable("stats") { PlaceholderScreen(R.string.stats, R.string.stats_empty_body, "▥  ▥  ▥") }
            composable("settings") { SettingsScreen() }
            composable("importTags") { ImportScreen(onDone = { navController.popBackStack() }) }
            composable("addAlbum") {
                AlbumEditorScreen(onSaved = { navController.navigate("album/$it") { popUpTo("library") } }, onCancel = { navController.popBackStack() })
            }
            composable("album/{albumId}", arguments = listOf(navArgument("albumId") { type = NavType.StringType })) {
                AlbumDetailScreen(
                    onBack = { navController.popBackStack() },
                    onViewCredits = { albumId, trackId, releaseMbid ->
                        val trackPart = trackId?.let { "?trackId=$it" }.orEmpty()
                        val releasePart = releaseMbid?.let { if (trackPart.isEmpty()) "?releaseMbid=$it" else "&releaseMbid=$it" }.orEmpty()
                        navController.navigate("credits/$albumId$trackPart$releasePart")
                    },
                )
            }
            composable(
                route = "credits/{albumId}?trackId={trackId}&releaseMbid={releaseMbid}",
                arguments = listOf(
                    navArgument("albumId") { type = NavType.StringType },
                    navArgument("trackId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("releaseMbid") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                val releaseMbid = entry.arguments?.getString("releaseMbid")
                CreditsScreen(
                    onBack = { navController.popBackStack() },
                    releaseUrl = releaseMbid?.let { "https://musicbrainz.org/release/$it" },
                )
            }
        }
    }
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
