package com.youneko.rate.ui.artwork

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.youneko.rate.R
import com.youneko.rate.ui.YnDimens
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CoverArtImageViewModel @Inject constructor(
    val imageLoader: ImageLoader,
) : ViewModel()

@Composable
fun CoverArtImage(
    model: Any?,
    modifier: Modifier = Modifier,
    @DrawableRes placeholder: Int = R.drawable.ic_cat_cover,
    placeholderSeed: String = "",
    placeholderLabel: String? = null,
) = CoverArtImage(listOfNotNull(model), modifier, placeholder, placeholderSeed, placeholderLabel)

@Composable
fun CoverArtImage(
    models: List<Any>,
    modifier: Modifier = Modifier,
    @DrawableRes placeholder: Int = R.drawable.ic_cat_cover,
    placeholderSeed: String = "",
    placeholderLabel: String? = null,
) {
    val imageLoader = hiltViewModel<CoverArtImageViewModel>().imageLoader
    val context = androidx.compose.ui.platform.LocalContext.current
    var index by remember(models) { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(YnDimens.radiusMd)
    val clipped = modifier
        .clip(shape)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), shape)
    val model = models.getOrNull(index)
    if (model == null) {
        CoverArtPlaceholder(clipped, placeholderSeed, placeholderLabel, placeholder)
    } else {
        AsyncImage(
            model = ImageRequest.Builder(context).data(model).size(512).crossfade(220).build(),
            imageLoader = imageLoader,
            contentDescription = null,
            modifier = clipped,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(placeholder),
            error = painterResource(placeholder),
            onError = { if (index < models.lastIndex) index++ },
        )
    }
}

@Composable
private fun CoverArtPlaceholder(
    modifier: Modifier,
    seed: String,
    label: String?,
    @DrawableRes fallback: Int,
) {
    if (seed.isBlank() && label.isNullOrBlank()) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
            Image(painter = painterResource(fallback), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        return
    }
    val hash = "${seed}\u0000${label.orEmpty()}".hashCode().toUInt().toLong()
    val hue = (hash % 360L).toFloat()
    val first = Color.hsv(hue, 0.42f, 0.78f)
    val second = Color.hsv((hue + 42f) % 360f, 0.34f, 0.62f)
    val letter = label.orEmpty().trim().firstOrNull()?.uppercase() ?: "?"
    val foreground = if ((first.red * 0.299f + first.green * 0.587f + first.blue * 0.114f) > 0.58f) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.inverseOnSurface
    Box(modifier.background(Brush.linearGradient(listOf(first, second))), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Pets, contentDescription = null, tint = foreground.copy(alpha = 0.08f), modifier = Modifier.fillMaxSize(0.72f))
        Text(letter, color = foreground, style = MaterialTheme.typography.displaySmall)
    }
}
