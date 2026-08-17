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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.youneko.rate.R

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isSettings) stringResource(R.string.settings)
                        else stringResource(R.string.app_name),
                    )
                },
                actions = {
                    if (!isSettings) {
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!isSettings) {
                NavigationBar {
                    destinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == destination.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = stringResource(destination.label),
                                )
                            },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "library",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("library") {
                PlaceholderScreen(
                    title = stringResource(R.string.library),
                    body = stringResource(R.string.library_empty_body),
                    mascot = "ฅ^•ﻌ•^ฅ",
                )
            }
            composable("rate") {
                PlaceholderScreen(
                    title = stringResource(R.string.rate),
                    body = stringResource(R.string.rate_empty_body),
                    mascot = "★  ☆  ★",
                )
            }
            composable("analyze") {
                PlaceholderScreen(
                    title = stringResource(R.string.analyze),
                    body = stringResource(R.string.analyze_empty_body),
                    mascot = "∿  ∿  ∿",
                )
            }
            composable("stats") {
                PlaceholderScreen(
                    title = stringResource(R.string.stats),
                    body = stringResource(R.string.stats_empty_body),
                    mascot = "▥  ▥  ▥",
                )
            }
            composable("settings") {
                PlaceholderScreen(
                    title = stringResource(R.string.settings),
                    body = stringResource(R.string.settings_body),
                    mascot = "⚙",
                )
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    body: String,
    mascot: String,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = mascot, style = MaterialTheme.typography.displaySmall)
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = body,
            modifier = Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
