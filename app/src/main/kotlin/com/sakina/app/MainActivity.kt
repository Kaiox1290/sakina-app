package com.sakina.app

import androidx.compose.ui.unit.dp
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var startRoute by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Settings.init(this)
        Notify.createChannels(this)
        Notify.ensureWorker(this)
        Notify.refreshAsync(this)
        startRoute = intent?.getStringExtra("route")
        setContent {
            val dark = when (Settings.int("theme", 0)) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
            val ar = Settings.isAr
            val dens = androidx.compose.ui.platform.LocalDensity.current
            val fs = when (Settings.int("font_size", 0)) { 1 -> 1.15f; 2 -> 1.3f; else -> 1f }
            CompositionLocalProvider(LocalLayoutDirection provides if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr,
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(dens.density, dens.fontScale * fs)) {
                SakinaTheme(dark) {
                    Box(Modifier.fillMaxSize().background(appBackground())) {
                        AppRoot(startRoute) { startRoute = null }
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); startRoute = intent.getStringExtra("route") }
}

typealias Go = (String) -> Unit

fun io(scope: CoroutineScope, block: suspend () -> Unit) { scope.launch(Dispatchers.IO) { block() } }

private data class Tab(val route: String, val ar: String, val en: String, val icon: ImageVector)

@Composable fun AppRoot(pendingRoute: String?, consumed: () -> Unit) {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    val onboarded = Settings.bool("onboarded", false)
    val tabs = listOf(
        Tab("home", "الرئيسية", "Home", Icons.Default.Home), Tab("prayer", "الصلاة", "Prayer", Icons.Default.Notifications),
        Tab("quran", "القرآن", "Quran", Icons.Default.Favorite), Tab("tasks", "المهام", "Tasks", Icons.Default.CheckCircle), Tab("more", "المزيد", "More", Icons.Default.Menu)
    )
    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null && onboarded) { runCatching { nav.navigate(pendingRoute) }; consumed() }
    }
    val back by nav.currentBackStackEntryAsState()
    val cur = back?.destination?.route
    val go: Go = { r -> nav.navigate(r) { launchSingleTop = true; if (tabs.any { it.route == r }) { popUpTo("home") { saveState = true }; restoreState = true } } }
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            if (onboarded && tabs.any { it.route == cur }) NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
                tabs.forEach { tb ->
                    NavigationBarItem(selected = cur == tb.route, onClick = { go(tb.route) }, icon = { Icon(tb.icon, null) },
                        label = { Text(tr(tb.ar, tb.en), style = MaterialTheme.typography.labelSmall, maxLines = 1) })
                }
            }
        }
    ) { pad ->
        NavHost(
            nav, startDestination = if (onboarded) "home" else "onboarding", modifier = Modifier.padding(bottom = if (onboarded && tabs.any { it.route == cur }) pad.calculateBottomPadding() else 0.dp),
            enterTransition = { fadeIn() + slideInHorizontally { it / 12 } }, exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() }, popExitTransition = { fadeOut() + slideOutHorizontally { it / 12 } }
        ) {
            val back2: () -> Unit = { nav.popBackStack() }
            composable("onboarding") { Onboarding { nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } } }
            composable("home") { HomeScreen(go) }
            composable("prayer") { PrayerScreen(go) }
            composable("qibla") { QiblaScreen(back2) }
            composable("hijri") { HijriCalendarScreen(back2) }
            composable("quran") { QuranHome(go) }
            composable("quran/{s}?a={a}", listOf(navArgument("s") { type = NavType.IntType }, navArgument("a") { type = NavType.IntType; defaultValue = 1 })) {
                SurahScreen(it.arguments!!.getInt("s"), it.arguments!!.getInt("a"), back2)
            }
            composable("tasks") { TasksScreen(go) }
            composable("calendar") { CalendarScreen(back2) }
            composable("more") { MoreScreen(go) }
            composable("adhkar") { AdhkarHome(go, back2) }
            composable("adhkar/{key}") { AdhkarGroupScreen(it.arguments!!.getString("key")!!, back2) }
            composable("adhkarcat/{id}") { AdhkarCatScreen(it.arguments!!.getString("id")!!.toInt(), back2) }
            composable("favorites") { FavoritesScreen(back2) }
            composable("tasbih") { TasbihScreen(back2) }
            composable("planner") { PlannerScreen(back2) }
            composable("study") { StudyScreen(back2) }
            composable("coding") { CodingScreen(back2) }
            composable("habits") { HabitsScreen(back2) }
            composable("notifications") { NotificationsScreen(back2) }
            composable("assistant") { AssistantScreen(back2) }
            composable("stats") { StatsScreen(back2) }
            composable("settings") { SettingsScreen(go, back2) }
            composable("search") { SearchScreen(go, back2) }
            composable("about") { AboutScreen(back2) }
            composable("nawawi") { NawawiScreen(back2) }
            composable("khatma") { KhatmaScreen(go, back2) }
            composable("levels") { LevelsScreen(back2) }
            composable("ramadan") { RamadanScreen(back2) }
            composable("privacy") { PrivacyScreen(back2) }
        }
    }
}
