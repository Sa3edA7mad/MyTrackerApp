package com.example.mytrackerapp.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.example.mytrackerapp.R
import com.example.mytrackerapp.ui.Routes
import com.example.mytrackerapp.ui.theme.Accent
import com.example.mytrackerapp.ui.theme.Canvas as CanvasColor
import com.example.mytrackerapp.ui.theme.TextTertiary

/**
 * Hand-drawn icon set.
 *
 * androidx.compose.material:material-icons-core is frozen at 1.7.8 and is not part of
 * the Compose BOM this project uses, so `Icons.Default.*` does not resolve at all.
 * Rather than pin a stale icons artifact against a current Compose runtime, every glyph
 * here is a local vector drawable, which also lets the stroke weight match the rest of
 * the design system.
 */
object AppIcons {
    @DrawableRes val today = R.drawable.ic_today
    @DrawableRes val program = R.drawable.ic_program
    @DrawableRes val progress = R.drawable.ic_progress
    @DrawableRes val library = R.drawable.ic_library
    @DrawableRes val close = R.drawable.ic_close
    @DrawableRes val back = R.drawable.ic_arrow_back
    @DrawableRes val settings = R.drawable.ic_settings
    @DrawableRes val more = R.drawable.ic_more_vert
    @DrawableRes val play = R.drawable.ic_play
    @DrawableRes val refresh = R.drawable.ic_refresh
}

data class TopDestination(
    val route: String,
    val label: String,
    @param:DrawableRes val icon: Int
)

val topDestinations: List<TopDestination> = listOf(
    TopDestination(Routes.TODAY, "TODAY", AppIcons.today),
    TopDestination(Routes.PROGRAM, "PROGRAM", AppIcons.program),
    TopDestination(Routes.PROGRESS, "PROGRESS", AppIcons.progress),
    TopDestination(Routes.LIBRARY, "LIBRARY", AppIcons.library)
)

@Composable
fun BottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    NavigationBar(containerColor = CanvasColor, contentColor = TextTertiary) {
        topDestinations.forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onNavigate(destination.route) },
                icon = {
                    // Null description: the visible label already names the tab, and
                    // NavigationBarItem merges them into one node for TalkBack.
                    Icon(painterResource(destination.icon), contentDescription = null)
                },
                label = {
                    Text(destination.label, style = MaterialTheme.typography.labelSmall)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Accent,
                    selectedTextColor = Accent,
                    unselectedIconColor = TextTertiary,
                    unselectedTextColor = TextTertiary,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}
