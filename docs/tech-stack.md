# Tech stack and why

| Area | Choice | Why |
|---|---|---|
| Language | Kotlin | The official Android language, coroutines for background work |
| UI | Jetpack Compose with Material 3 | Declarative, component-based, the current Android standard. Material 3 gives accessible components and states out of the box |
| Architecture | MVVM with `StateFlow` | Recommended by the Android architecture guide. ViewModels survive configuration changes and are testable without a device |
| Navigation | Navigation Compose, type-safe routes | Back stack, deep links and saved state per tab without custom code |
| Network | Retrofit, OkHttp, kotlinx.serialization | A typed interface for the API, interceptors for auth and logging, no reflection for JSON |
| Local storage | Room | Location history is relational (points, cells, stays) and queried by time. Room gives compile-time checked SQL and Flows |
| Token storage | Android Keystore with AES-GCM | The token never lies in plain text on disk |
| Map | MapLibre Native | Open source, renders vector tiles and custom layers, no API key or usage fees |
| Tiles | OpenFreeMap (OpenStreetMap data) | Free vector tiles without a key for the demo build. Protomaps PMTiles remain the plan for an offline region pack |
| Grid | Uber H3, resolution 11 | Hexagons of about 25 m edge, roughly a street segment. Equal neighbours make distance and coverage simple |
| Location | Fused Location Provider in a foreground service | Balanced accuracy and battery use, keeps running when the app is closed |
| Routing | OSRM foot profile at routing.openstreetmap.de | Free walking routes on OpenStreetMap |
| Tests | JUnit 4, kotlinx-coroutines-test, MockWebServer, Robolectric, Compose UI test, Roborazzi | Unit, integration and UI tests all run on the JVM in seconds, no emulator needed in CI |

## Rejected alternatives

| Alternative | Why not |
|---|---|
| A web SPA | Background location collection is the core of the product and needs a native service. The administration confirmed in writing on 29.10.2026 that a native client counts for this criterion |
| Unity, .NET MAUI | Unity pauses in the background and still needs a native Android service. MAUI does not remove the platform work and its map library has no heat map layer |
| Hilt or Koin | About fifteen objects to wire. A hand-written container is shorter and has no generated code |
| Google Maps SDK | Its terms do not allow building a lasting place catalogue, and it needs an API key in the app |
| Redux-style single store | Screens share little state. Per-screen ViewModels plus a few app-wide flows are enough |
