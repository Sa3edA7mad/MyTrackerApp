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
import com.example.mytrackerapp.ui.screens.circuit.CircuitRoute
import com.example.mytrackerapp.ui.screens.complete.CycleCompleteRoute
import com.example.mytrackerapp.ui.screens.exercise.ExerciseDetailRoute
import com.example.mytrackerapp.ui.screens.library.LibraryRoute
import com.example.mytrackerapp.ui.screens.program.ProgramRoute
import com.example.mytrackerapp.ui.screens.progress.ProgressRoute
import com.example.mytrackerapp.ui.screens.settings.SettingsRoute
import com.example.mytrackerapp.ui.screens.routine.RoutineRoute
import com.example.mytrackerapp.ui.screens.today.TodayRoute
import com.example.mytrackerapp.ui.theme.Canvas as CanvasColor

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    Scaffold(
        containerColor = CanvasColor,
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
                    onOpenCircuit = { week, day, circuit ->
                        nav.navigate(Routes.circuit(week, day, circuit))
                    },
                    onOpenRoutine = { type -> nav.navigate(Routes.routine(type)) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onCycleComplete = { nav.navigate(Routes.CYCLE_COMPLETE) }
                )
            }
            composable(Routes.PROGRAM) {
                ProgramRoute(
                    onOpenCircuit = { week, day, circuit ->
                        nav.navigate(Routes.circuit(week, day, circuit))
                    }
                )
            }
            composable(Routes.PROGRESS) { ProgressRoute() }
            composable(Routes.LIBRARY) {
                LibraryRoute(onOpenExercise = { id -> nav.navigate(Routes.exercise(id)) })
            }
            composable(Routes.SETTINGS) {
                SettingsRoute(onBack = { nav.popBackStack() })
            }
            composable(Routes.CYCLE_COMPLETE) {
                CycleCompleteRoute(
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
                    navArgument(Routes.ARG_WEEK) { type = NavType.IntType },
                    navArgument(Routes.ARG_DAY) { type = NavType.IntType },
                    navArgument(Routes.ARG_CIRCUIT) { type = NavType.IntType }
                )
            ) { entry ->
                val args = entry.arguments
                CircuitRoute(
                    week = args?.getInt(Routes.ARG_WEEK) ?: 1,
                    day = args?.getInt(Routes.ARG_DAY) ?: 1,
                    circuit = args?.getInt(Routes.ARG_CIRCUIT) ?: 1,
                    onExit = { nav.popBackStack() }
                )
            }

            composable(
                route = Routes.ROUTINE_PATTERN,
                arguments = listOf(navArgument(Routes.ARG_TYPE) { type = NavType.StringType })
            ) { entry ->
                val type = RoutineType.fromSlug(entry.arguments?.getString(Routes.ARG_TYPE))
                RoutineRoute(type = type, onExit = { nav.popBackStack() })
            }

            composable(
                route = Routes.EXERCISE_PATTERN,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.StringType })
            ) { entry ->
                ExerciseDetailRoute(
                    id = entry.arguments?.getString(Routes.ARG_ID).orEmpty(),
                    onBack = { nav.popBackStack() }
                )
            }
        }
    }
}
