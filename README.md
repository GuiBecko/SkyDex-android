# SkyDex — Android app

Part of [SkyDex](https://github.com/GuiBecko/skydex): a gamified camera app for
capturing meteorological events and sharing them with friends. Start there for
the architecture and screenshots.

This is the client: camera capture, the dex of collected phenomena, the friend
feed, and the map. Kotlin, Jetpack Compose, MVVM.

## Building it

Requires Android Studio and JDK 17. Minimum SDK 26, target 36.

1. Start the backend — see the [umbrella repository](https://github.com/GuiBecko/skydex).
2. Point the app at it. Create `local.properties` in the project root:

       sdk.dir=/path/to/your/Android/Sdk
       API_BASE_URL="http://10.0.2.2:3002"

   `10.0.2.2` is the emulator's alias for your host machine and is the default
   if you set nothing. **On a physical device it will not work** — use your
   machine's LAN address instead — `"http://<your-lan-ip>:3002"`, where
   `<your-lan-ip>` is what `ip addr` (or `ipconfig`) reports for your machine on
   the network the phone is on.

   The quotes are required: the value is injected verbatim as a `BuildConfig`
   string.

3. Build and run:

       ./gradlew assembleDebug

## Testing

    ./gradlew testDebugUnitTest

25 unit test classes covering the view models, repositories, the API layer and
the session store. The view-model tests use fakes rather than a mocking
framework, so they exercise real state transitions.

## Notes

The UI is in Portuguese, and its strings are literals in the composables rather
than `strings.xml`. That is a known limitation, not an oversight: the app was
built for Brazilian users and never needed a second locale.

Map tiles come from OpenStreetMap under
[ODbL](https://www.openstreetmap.org/copyright), fetched with an identifying
`User-Agent` and `Referer` as their
[tile usage policy](https://operations.osmfoundation.org/policies/tiles/)
requires.

## Licence

MIT — see `LICENSE`.
