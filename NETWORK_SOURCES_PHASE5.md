# Phase 5 network sources

## MusicBrainz API

Source: https://musicbrainz.org/doc/MusicBrainz_API

The MusicBrainz REST lookup pattern is `/ws/2/<entity>/<mbid>?inc=<includes>`. The documentation states that release-group lookups can include releases and that linked entities are limited to 25 in a lookup. Release-group search can use `/ws/2/release-group/?query=...`, and release lookup can request `recordings` and related data through `inc` parameters. The implementation should send JSON requests with a descriptive User-Agent and retain the existing token-bucket rate limiting.

## Cover Art Archive API

Source: https://musicbrainz.org/doc/Cover_Art_Archive/API

Cover Art Archive requests must go through `coverartarchive.org`. The release listing endpoint is `/release/{mbid}/` and returns JSON with an `images` array, a `front` boolean, and `thumbnails` entries including `250`, `500`, and `1200`. The direct front endpoint is `/release/{mbid}/front`; thumbnail endpoints include `/release/{mbid}/front-500` and `/release/{mbid}/front-250`. A 404 means the release or selected front artwork is unavailable, so the app should try `front-500`, then `front-250`, then its bundled cat fallback. Downloaded bytes must be copied to app-local storage; the app must not hotlink the remote URL in the saved album.

## Design consequence

Phase 5 should search release-groups, let the user choose a concrete release for tracklist and cover metadata, persist the selected release MBID/release-group MBID, and fetch cover bytes through an OkHttp client before writing a local file URI.
