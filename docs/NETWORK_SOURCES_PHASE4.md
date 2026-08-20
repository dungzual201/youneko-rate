# Phase 4 network sources

## MusicBrainz API

1. MusicBrainz API overview: https://musicbrainz.org/doc/MusicBrainz_API

The official documentation states that JSON can be requested with `fmt=json`, the API root is `https://musicbrainz.org/ws/2/`, and search/lookup endpoints cover artist, recording, release and release-group entities. The documentation also requires a meaningful User-Agent and limits each client application to no more than one call per second.

2. MusicBrainz search: https://musicbrainz.org/doc/MusicBrainz_API/Search

Search requests accept `query`, `limit`, `offset` and `fmt=json`; the documented limit range is 1–100 and the default is 25. The app requirement deliberately uses limit 25 and offset pagination.

## Retrofit Kotlin serialization converter

Maven Central: https://central.sonatype.com/artifact/com.jakewharton.retrofit/retrofit2-kotlinx-serialization-converter

Verified coordinates: `com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0`, Apache 2.0 license. It is a Retrofit converter for Kotlin serialization. The Retrofit and OkHttp versions will be pinned in the version catalog and validated by Gradle before commit.
