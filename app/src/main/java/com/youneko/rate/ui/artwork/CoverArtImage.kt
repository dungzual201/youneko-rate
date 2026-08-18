package com.youneko.rate.ui.artwork

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.youneko.rate.R

@HiltViewModel
class CoverArtImageViewModel @Inject constructor(
    val imageLoader: ImageLoader,
) : ViewModel()

@Composable
fun CoverArtImage(
    model: Any?,
    modifier: Modifier = Modifier,
    @DrawableRes placeholder: Int = R.drawable.ic_cat_cover,
) = CoverArtImage(listOfNotNull(model), modifier, placeholder)

@Composable
fun CoverArtImage(
    models: List<Any>,
    modifier: Modifier = Modifier,
    @DrawableRes placeholder: Int = R.drawable.ic_cat_cover,
) {
    val imageLoader = hiltViewModel<CoverArtImageViewModel>().imageLoader
    val context = androidx.compose.ui.platform.LocalContext.current
    var index by remember(models) { mutableIntStateOf(0) }
    val clipped = modifier.clip(RoundedCornerShape(8.dp))
    val model = models.getOrNull(index)
    if (model == null) {
        CoverArtPlaceholder(clipped, placeholder)
    } else {
        AsyncImage(
            model = ImageRequest.Builder(context).data(model).crossfade(true).build(),
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
private fun CoverArtPlaceholder(modifier: Modifier, @DrawableRes placeholder: Int) {
    Box(modifier) {
        Image(
            painter = painterResource(placeholder),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}
