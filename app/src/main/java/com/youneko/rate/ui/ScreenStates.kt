package com.youneko.rate.ui

import androidx.compose.ui.res.stringResource
import com.youneko.rate.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun YounekoLoadingState(modifier: Modifier = Modifier, lines: Int = 3) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(YounekoSpacing.md)) {
        YounekoShimmer(Modifier.fillMaxWidth(0.86f), lines)
    }
}

@Composable
fun YounekoEmptyState(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(YounekoSpacing.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(YounekoSpacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (actionLabel != null && onAction != null) Button(onClick = onAction, modifier = Modifier.padding(top = YounekoSpacing.xs)) { Text(actionLabel) }
    }
}

@Composable
fun YounekoErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(YounekoSpacing.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(YounekoSpacing.sm)) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(stringResource(R.string.retry), modifier = Modifier.padding(start = YounekoSpacing.xs))
        }
    }
}
