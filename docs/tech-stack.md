# Tech stack and why

| Area | Choice | Why |
|---|---|---|
| Language | Kotlin | The official Android language, coroutines for background work |
| UI | Jetpack Compose with Material 3 | Declarative, component-based, the current Android standard. Material 3 gives accessible components and states out of the box |
| Architecture | MVVM with `StateFlow` | Recommended by the Android architecture guide. ViewModels survive configuration changes and are testable without a device |
| Navigation | Navigation Compose, type-safe routes | Back stack and typed arguments without custom code. Routes are plain serializable classes, so a wrong argument fails at compile time |
| Network | Retrofit, OkHttp, kotlinx.serialization | A typed interface for the API, interceptors for auth and logging, no reflection for JSON |
| Logging | Timber | Logs go to Logcat only in debug builds. Release builds log nothing, so locations and errors never reach the system log |
| Local storage | Room | Location history is relational (points, cells, stays) and queried by time. Room gives compile-time checked SQL and Flows |
| Token storage | Android Keystore with AES-GCM | The token never lies in plain text on disk |
| Map | MapLibre Native | Open source, renders vector tiles and custom layers, no API key or usage fees |
| Tiles | OpenFreeMap (OpenStreetMap data) | Free vector tiles without a key for the demo build. Protomaps PMTiles remain the plan for an offline region pack |
| Grid | Uber H3, resolution 11 | Hexagons of about 25 m edge, roughly a street segment. Equal neighbours make distance and coverage simple |
| Location | Fused Location Provider in a foreground service | Balanced accuracy and battery use, keeps running when the app is closed |
| Routing | OSRM foot profile at routing.openstreetmap.de | Free walking routes on OpenStreetMap |
| Walked streets | Own Mapbox Vector Tile decoder and street graph with A* search | The street network comes from the tiles the map already downloads, so snapping walks to streets needs no extra service or key. A small decoder for the one layer we need is lighter than a full geometry library |
| Place photos | Wikidata and Wikimedia Commons, Mapillary as a fallback, Coil for loading | Free photos of the building itself with a clear licence. Coil is the Compose-native image loader |
| Cities and countries | GeoNames cities15000 bundled with the app (CC BY 4.0) | Naming the cities of a walk works offline and sends no location to a third party |
| Tests | JUnit 4, kotlinx-coroutines-test, MockWebServer, Robolectric, Compose UI test, Roborazzi | Unit, integration and UI tests all run on the JVM in seconds, no emulator needed in CI |

## Rejected alternatives

| Alternative | Why not |
|---|---|
| A web SPA | Background location collection is the core of the product and needs a native service. The administration confirmed in writing on 29.10.2026 that a native client counts for this criterion |
| Unity, .NET MAUI | Unity pauses in the background and still needs a native Android service. MAUI does not remove the platform work and its map library has no heat map layer |
| Hilt or Koin | About fifteen objects to wire. A hand-written container is shorter and has no generated code |
| Google Maps SDK | Its terms do not allow building a lasting place catalogue, and it needs an API key in the app |
| Redux-style single store | Screens share little state. Per-screen ViewModels plus a few app-wide flows are enough |
