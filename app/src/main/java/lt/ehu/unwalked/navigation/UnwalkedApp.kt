package lt.ehu.unwalked.navigation

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import java.time.ZoneId
import kotlin.reflect.KClass
import lt.ehu.unwalked.AppContainer
import lt.ehu.unwalked.core.ui.map.HeatMap
import lt.ehu.unwalked.core.ui.map.MapMarker
import lt.ehu.unwalked.core.ui.map.MapPlace
import lt.ehu.unwalked.core.ui.motion.Motion
import lt.ehu.unwalked.core.ui.theme.Palette
import lt.ehu.unwalked.data.history.Cells
import lt.ehu.unwalked.data.history.SampleWalks
import lt.ehu.unwalked.domain.recommendation.CandidateCells
import lt.ehu.unwalked.domain.recommendation.RecommendationEngine
import lt.ehu.unwalked.domain.route.LatLon
import lt.ehu.unwalked.domain.route.RoutePlanner
import lt.ehu.unwalked.domain.route.RoutePreference
import lt.ehu.unwalked.domain.route.RouteShape
import lt.ehu.unwalked.domain.stats.Periods
import lt.ehu.unwalked.domain.stats.StatsCalculator
import lt.ehu.unwalked.feature.auth.AuthMode
import lt.ehu.unwalked.feature.auth.AuthScreen
import lt.ehu.unwalked.feature.auth.AuthViewModel
import lt.ehu.unwalked.feature.discover.DiscoverActions
import lt.ehu.unwalked.feature.discover.DiscoverScreen
import lt.ehu.unwalked.feature.discover.DiscoverViewModel
import lt.ehu.unwalked.feature.discover.PlacePreviewScreen
import lt.ehu.unwalked.feature.map.MapActions
import lt.ehu.unwalked.feature.map.MapScreen
import lt.ehu.unwalked.feature.map.MapViewModel
import lt.ehu.unwalked.feature.privacy.PrivacyActions
import lt.ehu.unwalked.feature.privacy.PrivacyScreen
import lt.ehu.unwalked.feature.privacy.PrivacyViewModel
import lt.ehu.unwalked.feature.stats.StatsScreen
import lt.ehu.unwalked.feature.stats.StatsViewModel
import lt.ehu.unwalked.feature.walk.RoutePreviewScreen
import lt.ehu.unwalked.feature.walk.RouteRequest
import lt.ehu.unwalked.feature.walk.RouteViewModel
import lt.ehu.unwalked.feature.walk.WalkActions
import lt.ehu.unwalked.feature.walk.WalkScreen
import lt.ehu.unwalked.feature.walk.WalkViewModel
import lt.ehu.unwalked.tracking.LocationService
import lt.ehu.unwalked.tracking.Permissions

@Composable
fun UnwalkedApp(container: AppContainer) {
    val session by container.auth.session.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        container.errors.messages.collect { message ->
            val result = snackbars.showSnackbar(message.text, message.actionLabel, duration = if (message.onAction != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
        }
    }
    LaunchedEffect(Unit) {
        container.errors.signOuts.collect { container.auth.signOut() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbars) },
        containerColor = Palette.Background,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Crossfade(session == null, label = "session") { signedOut -> if (signedOut) AuthFlow(container) else MainFlow(container) }
        }
    }
}

@Composable
private fun AuthFlow(container: AppContainer) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = SignIn, enterTransition = Motion.enter, exitTransition = Motion.exit, popEnterTransition = Motion.popEnter, popExitTransition = Motion.popExit) {
        composable<SignIn> {
            val vm = rememberViewModel { AuthViewModel(AuthMode.SignIn, container.auth, container.errors) }
            val state by vm.state.collectAsStateWithLifecycle()
            AuthScreen(state, vm::onEmail, vm::onPassword, vm::onConfirmation, vm::submit, onSwitchMode = { nav.navigate(Register) })
        }
        composable<Register> {
            val vm = rememberViewModel { AuthViewModel(AuthMode.Register, container.auth, container.errors) }
            val state by vm.state.collectAsStateWithLifecycle()
            AuthScreen(state, vm::onEmail, vm::onPassword, vm::onConfirmation, vm::submit, onSwitchMode = { nav.popBackStack() })
        }
    }
}

private data class Tab(val route: Any, val type: KClass<*>, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(MapHome, MapHome::class, "Map", Icons.Outlined.Map),
    Tab(Stats, Stats::class, "Stats", Icons.Outlined.BarChart),
    Tab(Walk, Walk::class, "Walk", Icons.AutoMirrored.Outlined.DirectionsWalk),
)

@Composable
private fun MainFlow(container: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val calculator = remember { StatsCalculator(childrenPerArea = 343, gazetteer = { container.gazetteer }) }

    Scaffold(
        containerColor = Palette.Background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar(containerColor = Palette.Panel) {
                tabs.forEach { tab ->
                    val selected = destination?.hasRoute(tab.type) == true ||
                        (tab.type == MapHome::class && (destination?.hasRoute(Discover::class) == true || destination?.hasRoute(Privacy::class) == true)) ||
                        (tab.type == Walk::class && destination?.hasRoute(RoutePreview::class) == true)
                    NavigationBarItem(
                        selected = selected,
                        onClick = { nav.switchTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Palette.AccentDeep),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            nav,
            startDestination = MapHome,
            modifier = Modifier.padding(padding),
            enterTransition = Motion.enter,
            exitTransition = Motion.exit,
            popEnterTransition = Motion.popEnter,
            popExitTransition = Motion.popExit,
        ) {
            composable<MapHome> { MapRoute(container, nav, calculator) }
            composable<Stats> {
                val vm = rememberViewModel { StatsViewModel(container.history, calculator, container.places, container.h3) }
                val state by vm.state.collectAsStateWithLifecycle()
                StatsScreen(state, vm::select, onOpenMap = { nav.switchTab(MapHome) })
            }
            composable<Walk> {
                val vm = rememberViewModel { WalkViewModel() }
                val state by vm.state.collectAsStateWithLifecycle()
                WalkScreen(
                    state,
                    WalkActions(
                        onLength = vm::onLength,
                        onShape = vm::onShape,
                        onPreference = vm::onPreference,
                        onBuild = { vm.request().let { nav.navigate(RoutePreview(it.kilometres, it.shape.name, it.preference.name)) } },
                    ),
                )
            }
            composable<RoutePreview> { backStack ->
                val args = backStack.toRoute<RoutePreview>()
                val destination = if (args.toLat.isNaN()) null else LatLon(args.toLat, args.toLon)
                val request = RouteRequest(args.kilometres, RouteShape.valueOf(args.shape), RoutePreference.valueOf(args.preference), destination)
                val vm = rememberViewModel {
                    RouteViewModel(request, container.history, container.history::lastKnownPosition, container.routes, container.places, RoutePlanner(), container.h3, container.errors)
                }
                val state by vm.state.collectAsStateWithLifecycle()
                val cells by container.history.cells.collectAsStateWithLifecycle(emptyList())
                val streaks by container.shownStreaks.collectAsStateWithLifecycle()
                RoutePreviewScreen(
                    state,
                    onBack = { nav.popBackStack() },
                    onChange = { nav.popBackStack() },
                    onStart = { route ->
                        container.activeWalk.value = route
                        nav.switchTab(MapHome)
                    },
                    onRetry = vm::build,
                    onAccept = vm::accept,
                    map = { modifier, route, selected, onSelect ->
                        HeatMap(
                            container.h3,
                            cells.map { lt.ehu.unwalked.core.ui.map.HeatCell(it.h3, it.passes) },
                            modifier,
                            streaks = streaks,
                            route = route?.path.orEmpty(),
                            position = route?.path?.firstOrNull()?.let { MapMarker(it.lat, it.lon) },
                            fitToRoute = true,
                            coveredBottom = ROUTE_SHEET_SHARE,
                            walkedStreets = container.walkedStreets::lines,
                            showLoading = false,
                            places = route?.places?.map { MapPlace(it.id, it.lat, it.lon) }.orEmpty(),
                            selectedPlace = selected,
                            onPlaceClick = onSelect,
                        )
                    },
                )
            }
            composable<Discover> { backStack ->
                val args = backStack.toRoute<Discover>()
                val vm = rememberViewModel {
                    DiscoverViewModel(
                        container.history,
                        container.places,
                        container.database.hiddenPlaces(),
                        container.feedback,
                        CandidateCells(container.h3, Cells.NEAR_QUERY_RESOLUTION),
                        RecommendationEngine(),
                        container.errors,
                        since = { container.shownSince.value },
                    )
                }
                val state by vm.state.collectAsStateWithLifecycle()
                DiscoverScreen(
                    state,
                    DiscoverActions(
                        onBack = { nav.popBackStack() },
                        onQuery = vm::onQuery,
                        onGroup = vm::onGroup,
                        onOrder = vm::onOrder,
                        onNotNow = vm::notNow,
                        onOpen = { item ->
                            vm.go(item)
                            nav.navigate(PlacePreview(item.place.id, item.place.name, item.place.category, item.reason, item.place.lat, item.place.lon))
                        },
                        photo = { item -> container.photos.thumbnail(item.place.id, item.place.lat, item.place.lon, item.place.name) },
                        photoCredit = true,
                        onUndo = vm::undoHide,
                        onUndoTimeout = vm::dismissUndo,
                        onRetry = vm::load,
                        onWiden = vm::widen,
                        onOpenMap = { nav.popBackStack() },
                    ),
                    focusSearch = args.focusSearch,
                )
            }
            composable<PlacePreview> { backStack ->
                val args = backStack.toRoute<PlacePreview>()
                val cells by container.history.cells.collectAsStateWithLifecycle(emptyList())
                val streaks by container.shownStreaks.collectAsStateWithLifecycle()
                PlacePreviewScreen(
                    name = args.name,
                    category = args.category,
                    reason = args.reason,
                    photo = { container.photos.thumbnail(args.id, args.lat, args.lon, args.name) },
                    onBack = { nav.popBackStack() },
                    onRoute = { nav.navigate(RoutePreview(0.0, RouteShape.OneWay.name, RoutePreference.Places.name, args.lat, args.lon)) },
                ) { modifier ->
                    HeatMap(
                        container.h3,
                        cells.map { lt.ehu.unwalked.core.ui.map.HeatCell(it.h3, it.passes) },
                        modifier,
                        streaks = streaks,
                        marker = MapMarker(args.lat, args.lon, args.name),
                        walkedStreets = container.walkedStreets::lines,
                        showLoading = false,
                    )
                }
            }
            composable<Privacy> {
                val context = LocalContext.current
                val vm = rememberViewModel {
                    PrivacyViewModel(container.preferences, container.history, container.auth, container.database.feedbackOutbox(), container.errors, { LocationService.stop(context) }, SampleWalks(container.routes))
                }
                val state by vm.state.collectAsStateWithLifecycle()
                val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                    uri?.let { target -> context.contentResolver.openOutputStream(target)?.let(vm::export) }
                }
                val timeline = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let { source -> context.contentResolver.openInputStream(source)?.let(vm::importGoogleTimeline) }
                }
                PrivacyScreen(
                    state,
                    online = SystemState.online(context),
                    actions = PrivacyActions(
                        onBack = { nav.popBackStack() },
                        onStudyConsent = vm::setStudyConsent,
                        onRetention = vm::setRetention,
                        onExport = { exporter.launch("unwalked-export.json") },
                        onImportTimeline = { timeline.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                        onOpenLocationSettings = { runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } },
                        onDelete = vm::deleteAll,
                        onCancelImport = vm::cancelImport,
                        onSample = vm::loadSample,
                        onSignOut = vm::signOut,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MapRoute(container: AppContainer, nav: NavHostController, calculator: StatsCalculator) {
    val context = LocalContext.current
    val vm = rememberViewModel {
        MapViewModel(container.history, container.preferences, container.activeWalk, { SystemState.batterySaver(context) }, calculator, container.routes, container.errors)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val recording by container.preferences.recording.collectAsStateWithLifecycle()
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (Permissions.hasLocation(context)) {
            vm.startRecording()
            LocationService.start(context)
        } else {
            container.errors.info("Location is off for Unwalked, so walks cannot be recorded.")
        }
    }
    LaunchedEffect(recording) {
        if (recording && Permissions.hasLocation(context)) LocationService.start(context)
    }
    LaunchedEffect(state.streaks) {
        container.shownStreaks.value = state.streaks
    }
    LaunchedEffect(state.period) {
        container.shownSince.value = Periods.range(state.period, System.currentTimeMillis(), ZoneId.systemDefault(), null).start
    }
    MapScreen(
        state,
        MapActions(
            onSearch = { nav.navigate(Discover()) },
            onProfile = { nav.navigate(Privacy) },
            onPeriod = vm::selectPeriod,
            onFindNew = {
                vm.findSomethingNew()
                nav.navigate(Discover())
            },
            onStartRecording = { permissions.launch(Permissions.foreground) },
            onFixRecording = { batterySaver -> if (batterySaver) SystemState.openBatterySaver(context) else SystemState.openLocationSettings(context) },
            onLater = vm::snoozeReminder,
            onEndWalk = vm::endWalk,
        ),
    ) { modifier ->
        HeatMap(
            container.h3,
            state.cells,
            modifier,
            streaks = state.streaks,
            route = state.walk?.path.orEmpty(),
            position = state.walk?.path?.firstOrNull()?.let { MapMarker(it.lat, it.lon) },
            fitToRoute = state.walk != null,
            coveredBottom = 0.3f,
            walkedStreets = container.walkedStreets::lines,
        )
    }
}

private fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}

private const val ROUTE_SHEET_SHARE = 0.35f
