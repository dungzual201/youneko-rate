package com.youneko.rate.ui

const val MIN_SCROLL_FPS = 55

fun stableAlbumKey(id: String): String = "album:$id"
fun stableTrackKey(id: String): String = "track:$id"
fun stableCollectionKey(id: String): String = "collection:$id"
