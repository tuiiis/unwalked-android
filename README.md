# Unwalked: Android client

Android app for the bachelor thesis "Mobile Application for Recommending New Places and Walking Routes Based on the Analysis of the User's Habitual Geography". Server: [unwalked-backend](https://github.com/tuiiis/unwalked-backend). Business analysis: [unwalked-thesis](https://github.com/tuiiis/unwalked-thesis).

The app records where you walk, lights up the streets you have walked on a map, and suggests places and walking routes in the parts of the city you have not walked yet. Location history stays on the phone.

Kotlin, Jetpack Compose with Material 3, MVVM with `StateFlow`, Room, Retrofit, MapLibre.

## Try it

Install the debug APK from the [latest release](https://github.com/tuiiis/unwalked-android/releases/latest). It talks to the public API at https://unwalked-api-7820623db8c8.herokuapp.com. Sign in with `ona@example.test` and the password `Unwalked-test-1`, or create an account.

To get a map without walking first, open Data and privacy from the profile icon and tap Generate sample walks under Developer options. It builds half a year of walks on real streets around your last recorded location, so it works in any city.

## Build and run

Requires JDK 17 or newer and Android SDK 36. Point Gradle at the SDK with `ANDROID_HOME` or `sdk.dir` in `local.properties`.

```bash
./gradlew :app:installDebug
```

By default the app talks to a backend on the host machine at `http://10.0.2.2:18080/`, which is how the emulator reaches a local [backend](https://github.com/tuiiis/unwalked-backend#running-locally). For a phone or the public server pass another address.

```bash
./gradlew :app:installDebug -Punwalked.apiBaseUrl=https://unwalked-api-7820623db8c8.herokuapp.com/
```

## Documentation

| Topic | Where |
|---|---|
| Architecture, modules, state, navigation, map, photos | [docs/architecture.md](docs/architecture.md) |
| User flows and navigation diagram | [docs/user-flows.md](docs/user-flows.md) |
| Tech stack and why | [docs/tech-stack.md](docs/tech-stack.md) |
| API the app uses | [Backend API docs](https://github.com/tuiiis/unwalked-backend/tree/main/docs/api), client side in `core/network` |

## Project layout

```text
app/src/main/java/lt/ehu/unwalked/
  core/        theme, components, map, network, errors, session
  data/        repositories, Room, preferences, photos, sample walks
  domain/      recommendations, routes, stats, cell aggregation, validation
  feature/     auth, map, discover, stats, walk, privacy
  navigation/  routes and the NavHost
  tracking/    foreground location service
```

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

207 tests in 36 classes, all on the JVM, the Compose UI tests included through Robolectric. No emulator is needed. The report is written to `app/build/reports/tests/testDebugUnitTest/index.html`.

| Kind | Examples |
|---|---|
| Domain logic | `RecommendationEngineTest`, `RoutePlannerTest`, `OffRouteTest`, `StatsCalculatorTest`, `StayDetectorTest`, `CellAggregatorTest` |
| ViewModels | `AuthViewModelTest`, `WalkViewModelTest`, `DiscoverViewModelTest`, `RouteToPlaceTest` |
| Data and database | `TimelineImportTest` and `SampleWalksTest` on an in-memory Room database, `GoogleTimelineTest`, `FeedbackRepositoryTest` |
| API client | `ApiErrorHandlingTest` and `AreaPreparationTest` run the real Retrofit client against MockWebServer |
| UI | `AuthScreenTest`, `WalkScreenTest`, `MapScreenTest`, `PrivacyScreenTest` drive the real screens |
| Screenshots | `ScreenshotTest` renders 14 screen states. Add `-Pscreenshots` to write them to `app/build/screenshots` |

## Continuous integration

[ci.yml](.github/workflows/ci.yml) runs ktlint, Android Lint, all tests and a debug build on every push and pull request. The reports and the APK are attached to each run. Run `./gradlew ktlintFormat` before committing.

## Where the Front-end criterion is implemented

An Android app counts for this criterion instead of a web SPA, as confirmed by the administration on 29.10.2026.

| Requirement | Where |
|---|---|
| Architecture, approach, state management | [docs/architecture.md](docs/architecture.md) |
| Components, screens, services | `core/ui/components`, `feature/*` screens and ViewModels, `data/*` repositories |
| Routing, 9 routes | `navigation/Routes.kt`, `navigation/UnwalkedApp.kt` |
| Forms with validation | Sign in and registration in `feature/auth`, rules in `domain/validation` |
| List with search, filter and sort | Places for you in `feature/discover` |
| Global error handling | `core/network/SafeApiCall` maps every failure to `AppError`, `core/error/ErrorReporter` shows snackbars and signs out on 401 |
| Loading and empty states | `SkeletonBlock` placeholders, empty states on every screen that loads |
| Linter and formatter, logging | ktlint and Android Lint in CI, Timber in debug builds |
| Beyond the minimum | Animated transitions, charts and a globe on the Stats tab, dark and light themes, TalkBack descriptions and headings |

## Data and licences

| Data | Source |
|---|---|
| Places | Overture Maps (CDLA Permissive 2.0) for Vilnius, OpenStreetMap (ODbL) for other areas, through the backend |
| Map tiles | OpenStreetMap data served by OpenFreeMap |
| Walking routes | OSRM foot profile on OpenStreetMap data |
| Place photos | Wikimedia Commons, Mapillary (CC BY-SA) |
