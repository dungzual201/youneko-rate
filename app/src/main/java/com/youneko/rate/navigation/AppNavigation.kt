package com.youneko.rate.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.youneko.rate.R
import com.youneko.rate.ui.adaptive.YnWindowSize
import com.youneko.rate.ui.adaptive.rememberYnWindowInfo
import com.youneko.rate.ui.analyze.AudioAnalysisScreen
import com.youneko.rate.ui.coversearch.CoverSearchScreen
import com.youneko.rate.ui.export.ExportScreen
import com.youneko.rate.ui.importer.ImportScreen
import com.youneko.rate.ui.media.MediaAccessGate
import com.youneko.rate.ui.phase12.AdvancedSearchScreen
import com.youneko.rate.ui.phase12.ArtistPageScreen
import com.youneko.rate.ui.phase12.CollectionsScreen
import com.youneko.rate.ui.rate.AlbumDetailScreen
import com.youneko.rate.ui.rate.AlbumEditorScreen
import com.youneko.rate.ui.rate.LibraryScreen
import com.youneko.rate.ui.rate.RateScreen
import com.youneko.rate.ui.rate.SettingsScreen
import com.youneko.rate.ui.stats.StatsScreen

private data class AppDestination(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
)

private val destinations = listOf(
    AppDestination("library", R.string.library, Icons.Default.LibraryMusic),
    AppDestination("rate", R.string.rate, Icons.Default.Star),
    AppDestination("analyze", R.string.analyze, Icons.Default.GraphicEq),
    AppDestination("stats", R.string.stats, Icons.Default.BarChart),
)

private const val ANALYZE_URI_KEY = "analyzeUri"

@Composable
fun YounekoNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val window = rememberYnWindowInfo()
    val isTopLevel = destinations.any { it.route == currentRoute }
    val showBottomBar = window.size == YnWindowSize.COMPACT && isTopLevel
    val showRail = window.size != YnWindowSize.COMPACT

    val openDestination: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val openAnalyzer: (String?) -> Unit = { uri ->
        if (!uri.isNullOrBlank()) {
            navController.currentBackStackEntry?.savedStateHandle?.set(ANALYZE_URI_KEY, uri)
        }
        navController.navigate("analyze") { launchSingleTop = true }
    }

    MediaAccessGate(content = {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            containerColor = MaterialTheme.colorScheme.surface,
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        destinations.forEach { destination ->
                            NavigationBarItem(
                                selected = currentRoute == destination.route,
                                onClick = { openDestination(destination.route) },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(stringResource(destination.label)) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(Modifier.fillMaxSize().padding(innerPadding)) {
                if (showRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        windowInsets = NavigationRailDefaults.windowInsets,
                        header = { YnRailHeader() },
                    ) {
                        destinations.forEach { destination ->
                            NavigationRailItem(
                                selected = currentRoute == destination.route,
                                onClick = { openDestination(destination.route) },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(stringResource(destination.label), maxLines = 1) },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        NavigationRailItem(
                            selected = currentRoute == "settings",
                            onClick = { navController.navigate("settings") { launchSingleTop = true } },
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            label = { Text(stringResource(R.string.settings), maxLines = 1) },
                        )
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .then(if (showRail || !showBottomBar) Modifier.navigationBarsPadding() else Modifier),
                ) {
                    AppNavGraph(
                        navController = navController,
                        openAnalyzer = openAnalyzer,
                    )
                }
            }
        }
    })
}

@Composable
private fun YnRailHeader() {
    Column(
        Modifier.padding(vertical = 16.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Pets,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.width(72.dp).padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppNavGraph(
    navController: NavHostController,
    openAnalyzer: (String?) -> Unit,
) {
    NavHost(navController = navController, startDestination = "library", modifier = Modifier.fillMaxSize()) {
        composable("library") {
            LibraryScreen(
                onOpenAlbum = { navController.navigate("album/$it") },
                onAddAlbum = { navController.navigate("addAlbum") },
                onOpenAdvancedSearch = { navController.navigate("advancedSearch") },
                onOpenCollections = { navController.navigate("collections") },
                onOpenSettings = { navController.navigate("settings") },
                onAnalyzeTrack = { uri -> openAnalyzer(uri) },
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
                onOpenSettings = { navController.navigate("settings") { launchSingleTop = true } },
            )
        }
        composable("analyze") {
            val previous = navController.previousBackStackEntry
            val initialUri = previous?.savedStateHandle?.get<String>(ANALYZE_URI_KEY)
            previous?.savedStateHandle?.remove<String>(ANALYZE_URI_KEY)
            AudioAnalysisScreen(initialUri = initialUri, onOpenSettings = { navController.navigate("settings") { launchSingleTop = true } })
        }
        composable("stats") { StatsScreen(onOpenSettings = { navController.navigate("settings") { launchSingleTop = true } }) }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
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
            ImportScreen(
                onBack = { navController.popBackStack() },
                onDone = {
                    navController.navigate("rate") {
                        popUpTo("importTags") { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable("addAlbum") {
            AlbumEditorScreen(
                onSaved = { albumId ->
                    navController.navigate("album/$albumId") {
                        popUpTo("library")
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(
            route = "editAlbum/{albumId}",
            arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
        ) {
            AlbumEditorScreen(
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
        composable("coverSearch/{albumId}", arguments = listOf(navArgument("albumId") { type = NavType.StringType })) {
            CoverSearchScreen(onBack = { navController.popBackStack() })
        }
        composable("album/{albumId}", arguments = listOf(navArgument("albumId") { type = NavType.StringType })) {
            AlbumDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenArtist = { navController.navigate("artist/$it") },
                onAnalyzeTrack = { uri -> openAnalyzer(uri) },
                onSearchCover = { albumId -> navController.navigate("coverSearch/$albumId") { launchSingleTop = true } },
                onEditAlbum = { albumId -> navController.navigate("editAlbum/$albumId") { launchSingleTop = true } },
            )
        }
        composable("artist/{artistId}", arguments = listOf(navArgument("artistId") { type = NavType.StringType })) { entry ->
            ArtistPageScreen(
                artistId = checkNotNull(entry.arguments?.getString("artistId")),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
