package com.youneko.rate

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCapabilityGuardTest {
    @Test
    fun sourceDoesNotContainPlaybackCapability() {
        val current = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        val root = generateSequence(current) { it.parentFile }
            .firstOrNull { File(it, "app/build.gradle.kts").isFile }
            ?: error("Missing project root")
        val forbidden = listOf(
            "Media" + "Player",
            "Exo" + "Player",
            "Media" + "3",
            "Media" + "Session",
            "Audio" + "Track",
            "preview" + "Url",
            "preview" + "_url",
            "audio/" + "preview",
        )
        val files = buildList {
            addAll(File(root, "app/src/main").walkTopDown().filter { it.isFile }.toList())
            add(File(root, "app/build.gradle.kts"))
            add(File(root, "gradle/libs.versions.toml"))
        }
        val violations = files.flatMap { file ->
            val text = file.readText()
            forbidden.filter { token -> text.contains(token, ignoreCase = true) }.map { token -> "${file.relativeTo(root)}: $token" }
        }
        assertTrue("Playback capability found: ${violations.joinToString()}", violations.isEmpty())
    }
}

private fun File.resolve(path: String): File = File(this, path)
