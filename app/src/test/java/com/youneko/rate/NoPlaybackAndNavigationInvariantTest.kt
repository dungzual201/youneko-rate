package com.youneko.rate

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class NoPlaybackAndNavigationInvariantTest {
    @Test
    fun mainSourceContainsNoPlaybackClassesOrPreviewActions() {
        val root = sequenceOf(File("app/src/main/java"), File("src/main/java"), File("../app/src/main/java"))
            .firstOrNull { it.exists() }
            ?: return
        val source = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.readText() }
        listOf(
            "Media" + "Player",
            "Exo" + "Player",
            "androidx." + "media3",
            "Media" + "Session",
            "Audio" + "Track",
            "preview" + "_url",
            "Play" + " preview",
            "preview" + "Url",
            "MANAGE_EXTERNAL_STORAGE",
            "lyrics" + "Web",
        ).forEach { banned -> assertFalse("Found banned playback token: $banned", source.contains(banned)) }
    }
}
