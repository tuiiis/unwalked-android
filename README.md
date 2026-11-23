# Unwalked: Android client

Android app for the bachelor thesis "Mobile Application for Recommending New Places and Walking Routes Based on the Analysis of the User's Habitual Geography". Documentation and business analysis: [unwalked-thesis](https://github.com/tuiiis/unwalked-thesis). Server: [unwalked-backend](https://github.com/tuiiis/unwalked-backend).

The app records where you walk, shows it as a heat map of H3 cells, and suggests places and walking routes in the parts of the city you have not walked yet. Location history stays on the phone.

## Try it

A debug build is attached to the [latest release](https://github.com/tuiiis/unwalked-android/releases/latest). It talks to the public API at https://unwalked-api.onrender.com, which sleeps after 15 minutes without traffic, so the first request after a pause can take about a minute.

## Build and run

Requirements: JDK 17 or newer, Android SDK 36, and the backend running locally for the server features.

1. Start the backend as described in its [Getting started](https://github.com/tuiiis/unwalked-backend/blob/main/docs/api/getting-started.md). It listens on port 18080
2. Point Gradle at your Android SDK, either with `ANDROID_HOME` or a `local.properties` file containing `sdk.dir=/path/to/sdk`
3. Build and install on an emulator or a phone

```bash
./gradlew :app:installDebug
```

The emulator reaches the backend on the host at `http://10.0.2.2:18080/`, which is the default. For a phone or a deployed server pass another base URL.

```bash
./gradlew :app:assembleDebug -Punwalked.apiBaseUrl=https://api.example.org/
```

Sign in with a seeded test account (`ona@example.test`, password `Unwalked-test-1`) or create a new one. To see the map, stats and recommendations without walking for a week, tap "Try a sample week in Vilnius" on the empty map, or "Load a sample week" in Data and privacy.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

All tests run on the JVM, including the Compose UI tests (Robolectric). No emulator is needed. The HTML report is written to `app/build/reports/tests/testDebugUnitTest/index.html`.

| Kind | Tests |
|---|---|
| Unit, domain | `CredentialsValidatorTest`, `WalkLengthTest`, `RecommendationEngineTest`, `CandidateCellsTest`, `StatsCalculatorTest`, `RoutePlannerTest`, `RecordingStatusRulesTest`, plus the tracking tests `CellAggregatorTest`, `StayDetectorTest`, `GpxParserTest`, `TrackStatsTest` |
| Unit, ViewModels | `AuthViewModelTest`, `WalkViewModelTest`, `DiscoverViewModelTest` |
| Integration, API client | `ApiErrorHandlingTest` runs the real Retrofit client against MockWebServer, `FeedbackRepositoryTest` covers the offline outbox |
| UI | `AuthScreenTest`, `WalkScreenTest` drive the real screens and ViewModels with Compose UI test |
| Screenshots | `ScreenshotTest` renders 15 screen states. Run with `-Pscreenshots` to write them to `app/build/screenshots` |

## Where the Front-end criterion is implemented

| Requirement | Where |
|---|---|
| Modern framework | Jetpack Compose, `app/src/main/java/lt/ehu/unwalked` |
| Architecture diagram, approach, state management | [docs/architecture.md](docs/architecture.md) |
| User flows and navigation diagram | [docs/user-flows.md](docs/user-flows.md) |
| Tech stack justification | [docs/tech-stack.md](docs/tech-stack.md) |
| API the client uses | [Backend API docs](https://github.com/tuiiis/unwalked-backend/tree/main/docs/api), client side in `core/network` |
| Screens, reusable components, services | `feature/*` screens and ViewModels, `core/ui/components`, `data/*` repositories |
| Routing, 7 routes | `navigation/Routes.kt`, `navigation/UnwalkedApp.kt` |
| Forms with validation | Sign in and registration (`feature/auth`), walk length (`feature/walk`), rules in `domain/validation` |
| Lists with search, filter and sort | Places for you (`feature/discover`) |
| Global error handling | `core/network/SafeApiCall` maps every failure to `AppError`, `core/error/ErrorReporter` shows snackbars and signs out on 401, collected once in `navigation/UnwalkedApp.kt` |
| Loading states | `SkeletonBlock` placeholders on every screen that loads, spinners on buttons |
| Tests | `app/src/test`, see above |

## Project layout

```text
app/src/main/java/lt/ehu/unwalked/
  core/        theme, components, map, network, errors, session
  data/        repositories, Room, preferences
  domain/      validation, recommendations, stats, routes, cell aggregation
  feature/     auth, map, discover, stats, walk, privacy
  navigation/  routes and the NavHost
  tracking/    foreground location service
docs/          architecture, flows, stack
```

## Data and licences

Places come from Overture Maps (CDLA Permissive 2.0) through the backend. Map tiles are OpenStreetMap data served by OpenFreeMap, routes come from the OSRM foot profile on OpenStreetMap data.
