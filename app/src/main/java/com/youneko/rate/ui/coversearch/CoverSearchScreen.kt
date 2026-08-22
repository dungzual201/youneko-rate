package com.youneko.rate.ui.coversearch

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.youneko.rate.R
import com.youneko.rate.ui.YounekoEmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverSearchScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cover_search_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.a11y_back)) } },
            )
        },
    ) { padding ->
        YounekoEmptyState(
            title = stringResource(R.string.cover_search_official_pending),
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}
