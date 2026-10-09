package com.example.mytrackerapp.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mytrackerapp.ui.components.BottomBar
import com.example.mytrackerapp.ui.screens.catalog.CatalogEditRoute
import com.example.mytrackerapp.ui.screens.circuit.CircuitRoute
import com.example.mytrackerapp.ui.screens.complete.CycleCompleteRoute
import com.example.mytrackerapp.ui.screens.exercise.ExerciseDetailRoute
import com.example.mytrackerapp.ui.screens.library.LibraryRoute
import com.example.mytrackerapp.ui.screens.measure.MeasureRoute
import com.example.mytrackerapp.ui.screens.measure.MetricCatalogRoute
import com.example.mytrackerapp.ui.screens.measure.MetricHistoryRoute
import com.example.mytrackerapp.ui.screens.program.ProgramRoute
import com.example.mytrackerapp.ui.screens.programedit.CircuitEditRoute
import com.example.mytrackerapp.ui.screens.programedit.ProgramEditRoute
import com.example.mytrackerapp.ui.screens.progress.ProgressRoute
import com.example.mytrackerapp.ui.screens.rules.RulesRoute
import com.example.mytrackerapp.ui.screens.settings.SettingsRoute
import com.example.mytrackerapp.ui.screens.routine.RoutineRoute
import com.example.mytrackerapp.ui.screens.today.TodayRoute
import com.example.mytrackerapp.ui.theme.Canvas as CanvasColor
import androidx.compose.ui.graphics.Color
import com.example.mytrackerapp.ui.theme.glassBackdrop

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier.glassBackdrop(),
        containerColor = Color.Transparent,
        bottomBar = {
            // Hidden on circuit, routine, exercise, settings and cycleComplete.
            if (route in Routes.TABBED) {
                BottomBar(currentRoute = route) { target ->
                    nav.navigate(target) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = nav,
            startDestination = Routes.TODAY,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.TODAY) {
                TodayRoute(
                    onOpenCircuit = { programId, week, day, circuit ->
                        nav.navigate(Routes.circuit(programId, week, day, circuit))
                    },
                    onOpenRoutine = { programId, type -> nav.navigate(Routes.routine(programId, type)) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onCycleComplete = { programId -> nav.navigate(Routes.cycleComplete(programId)) },
                    onOpenPrograms = {
                        nav.navigate(Routes.PROGRAM) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Routes.PROGRAM) {
                ProgramRoute(
                    onOpenCircuit = { programId, week, day, circuit ->
                        nav.navigate(Routes.circuit(programId, week, day, circuit))
                    },
                    onEditProgram = { programId -> nav.navigate(Routes.programEdit(programId)) }
                )
            }
            composable(
                route = Routes.PROGRAM_EDIT_PATTERN,
                arguments = listOf(navArgument(Routes.ARG_PROGRAM) { type = NavType.LongType })
            ) { entry ->
                val programId = entry.arguments?.getLong(Routes.ARG_PROGRAM) ?: 1L
                ProgramEditRoute(
                    programId = programId,
                    onBack = { nav.popBackStack() },
                    onEditCircuit = { key -> nav.navigate(Routes.circuitEdit(programId, key)) },
                    onOpenRules = { nav.navigate(Routes.rules(programId)) }
                )
            }
            composable(
                route = Routes.CIRCUIT_EDIT_PATTERN,
                arguments = listOf(
                    navArgument(Routes.ARG_PROGRAM) { type = NavType.LongType },
                    navArgument(Routes.ARG_KEY) { type = NavType.StringType }
                )
            ) { entry ->
                CircuitEditRoute(
                    programId = entry.arguments?.getLong(Routes.ARG_PROGRAM) ?: 1L,
                    circuitKey = entry.arguments?.getString(Routes.ARG_KEY).orEmpty(),
                    onDone = { nav.popBackStack() }
                )
            }
            composable(Routes.PROGRESS) {
                ProgressRoute(onOpenMeasure = { nav.navigate(Routes.MEASURE) })
            }
            composable(Routes.LIBRARY) {
                LibraryRoute(
                    onOpenExercise = { id -> nav.navigate(Routes.exercise(id)) },
                    onAddExercise = { nav.navigate(Routes.exerciseEdit()) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsRoute(
                    onBack = { nav.popBackStack() },
                    // Settings keeps its shortcut to Home's rules; every program's are in its editor.
                    onOpenRules = { nav.navigate(Routes.rules(1)) },
                    onOpenMeasure = { nav.navigate(Routes.MEASURE) }
                )
            }
            composable(
                route = Routes.RULES_PATTERN,
                arguments = listOf(navArgument(Routes.ARG_PROGRAM) { type = NavType.LongType })
            ) { entry ->
                RulesRoute(
                    onBack = { nav.popBackStack() },
                    programId = entry.arguments?.getLong(Routes.ARG_PROGRAM) ?: 1L
                )
            }
            composable(Routes.MEASURE) {
                MeasureRoute(
                    onBack = { nav.popBackStack() },
                    onOpenHistory = { metricId -> nav.navigate(Routes.metricHistory(metricId)) },
                    onEditMetrics = { nav.navigate(Routes.MEASURE_CATALOG) }
                )
            }
            composable(Routes.MEASURE_CATALOG) {
                MetricCatalogRoute(onBack = { nav.popBackStack() })
            }
            composable(
                route = Routes.METRIC_HISTORY_PATTERN,
                arguments = listOf(navArgument(Routes.ARG_METRIC_ID) { type = NavType.StringType })
            ) { entry ->
                val metricId = entry.arguments?.getString(Routes.ARG_METRIC_ID).orEmpty()
                MetricHistoryRoute(metricId = metricId, onBack = { nav.popBackStack() })
            }
            composable(
                route = Routes.CYCLE_COMPLETE_PATTERN,
                arguments = listOf(navArgument(Routes.ARG_PROGRAM) { type = NavType.LongType })
            ) { entry ->
                CycleCompleteRoute(
                    programId = entry.arguments?.getLong(Routes.ARG_PROGRAM) ?: 1L,
                    onStartNewCycle = {
                        nav.navigate(Routes.TODAY) {
                            popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                        }
                    },
                    onBack = { nav.popBackStack() }
                )
            }

            composable(
                route = Routes.CIRCUIT_PATTERN,
                arguments = listOf(
                    navArgument(Routes.ARG_PROGRAM) { type = NavType.LongType },
                    navArgument(Routes.ARG_WEEK) { type = NavType.IntType },
                    navArgument(Routes.ARG_DAY) { type = NavType.IntType },
                    navArgument(Routes.ARG_CIRCUIT) { type = NavType.IntType }
                )
            ) { entry ->
                val args = entry.arguments
                CircuitRoute(
                    programId = args?.getLong(Routes.ARG_PROGRAM) ?: 1L,
                    week = args?.getInt(Routes.ARG_WEEK) ?: 1,
                    day = args?.getInt(Routes.ARG_DAY) ?: 1,
                    circuit = args?.getInt(Routes.ARG_CIRCUIT) ?: 1,
                    onExit = { nav.popBackStack() }
                )
            }

            composable(
                route = Routes.ROUTINE_PATTERN,
                arguments = listOf(
                    navArgument(Routes.ARG_PROGRAM) { type = NavType.LongType },
                    navArgument(Routes.ARG_TYPE) { type = NavType.StringType }
                )
            ) { entry ->
                val type = RoutineType.fromSlug(entry.arguments?.getString(Routes.ARG_TYPE))
                RoutineRoute(
                    type = type,
                    onExit = { nav.popBackStack() },
                    programId = entry.arguments?.getLong(Routes.ARG_PROGRAM) ?: 1L
                )
            }

            composable(
                route = Routes.EXERCISE_PATTERN,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString(Routes.ARG_ID).orEmpty()
                ExerciseDetailRoute(
                    id = id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.exerciseEdit(id)) }
                )
            }

            composable(
                route = Routes.EXERCISE_EDIT_PATTERN,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.StringType })
            ) { entry ->
                CatalogEditRoute(
                    id = entry.arguments?.getString(Routes.ARG_ID) ?: Routes.EXERCISE_EDIT_NEW_ID,
                    onDone = { nav.popBackStack() }
                )
            }
        }
    }
}
