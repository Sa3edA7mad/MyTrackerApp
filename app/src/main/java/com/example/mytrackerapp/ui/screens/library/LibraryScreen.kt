package com.example.mytrackerapp.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mytrackerapp.TrackerApplication
import com.example.mytrackerapp.domain.model.Category
import com.example.mytrackerapp.domain.model.Exercise
import com.example.mytrackerapp.repo.TrackerRepository
import com.example.mytrackerapp.ui.components.LoadingState
import com.example.mytrackerapp.ui.components.SectionHeader
import com.example.mytrackerapp.ui.components.TargetBadge
import com.example.mytrackerapp.ui.components.accent
import com.example.mytrackerapp.ui.components.label
import com.example.mytrackerapp.ui.theme.Accent
import com.example.mytrackerapp.ui.theme.Canvas as CanvasColor
import com.example.mytrackerapp.ui.theme.MinTouchTarget
import com.example.mytrackerapp.ui.theme.MyTrackerAppTheme
import com.example.mytrackerapp.ui.theme.Outline
import com.example.mytrackerapp.ui.theme.OutlineStrong
import com.example.mytrackerapp.ui.theme.Radius
import com.example.mytrackerapp.ui.theme.Spacing
import com.example.mytrackerapp.ui.theme.TextPrimary
import com.example.mytrackerapp.ui.theme.TextSecondary
import com.example.mytrackerapp.ui.theme.TextTertiary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Case-insensitive match on name or muscles. Pure so it can be unit-tested. */
fun filterCatalog(catalog: List<Exercise>, query: String): List<Exercise> {
    val q = query.trim()
    if (q.isEmpty()) return catalog
    return catalog.filter {
        it.name.contains(q, ignoreCase = true) || it.muscles.contains(q, ignoreCase = true)
    }
}

class LibraryViewModel(repo: TrackerRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<List<Exercise>?> =
        combine(repo.observeCatalog(), _query) { catalog, q ->
            if (catalog.isEmpty()) null else filterCatalog(catalog, q)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onQueryChange(value: String) {
        _query.value = value
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as TrackerApplication
                LibraryViewModel(app.container.repo)
            }
        }
    }
}

@Composable
fun LibraryRoute(
    onOpenExercise: (String) -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
) {
    val results by viewModel.results.collectAsState()
    val query by viewModel.query.collectAsState()

    val list = results
    if (list == null) {
        LoadingState()
    } else {
        LibraryScreen(
            exercises = list,
            query = query,
            onQueryChange = viewModel::onQueryChange,
            onOpenExercise = onOpenExercise
        )
    }
}

@Composable
fun LibraryScreen(
    exercises: List<Exercise>,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenExercise: (String) -> Unit
) {
    val grouped = exercises.groupBy { it.category }
    val order = listOf(Category.BODYWEIGHT, Category.BAND, Category.WARMUP, Category.STRETCH)

    Column(Modifier.fillMaxSize().background(CanvasColor)) {
        Column(Modifier.padding(horizontal = Spacing.lg)) {
            Spacer(Modifier.height(Spacing.sm))
            Text("Library", style = MaterialTheme.typography.displayMedium, color = TextPrimary)
            Text(
                "All 29 moves from your program",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = {
                    Text(
                        "Search exercises or muscles",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(Radius.pill),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = OutlineStrong,
                    cursorColor = Accent,
                    focusedContainerColor = CanvasColor,
                    unfocusedContainerColor = CanvasColor
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.sm))
        }

        if (exercises.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.TopCenter) {
                Text(
                    "No exercise matches “${query.trim()}”.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = Spacing.lg)) {
                order.forEach { category ->
                    val group = grouped[category].orEmpty()
                    if (group.isNotEmpty()) {
                        item(key = "h-${category.name}") {
                            SectionHeader(
                                "${category.label} · ${group.size}",
                                color = category.accent
                            )
                        }
                        items(group, key = { it.id }) { exercise ->
                            CatalogRow(exercise) { onOpenExercise(exercise.id) }
                        }
                    }
                }
                item { Spacer(Modifier.height(Spacing.xxl)) }
            }
        }
    }
}

@Composable
private fun CatalogRow(exercise: Exercise, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clip(RoundedCornerShape(Radius.md))
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(exercise.category.accent)
        )
        Column(Modifier.weight(1f)) {
            Text(
                exercise.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (exercise.muscles.isNotEmpty()) {
                Text(
                    exercise.muscles,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        TargetBadge(exercise.targetLabel)
    }
}

/* ------------------------------------------------------------------ previews */

@Preview(showBackground = true, backgroundColor = 0xFF0B0D0C, widthDp = 400, heightDp = 880)
@Composable
private fun LibraryPreview() {
    MyTrackerAppTheme {
        LibraryScreen(
            exercises = listOf(
                Exercise("squat", "Squat", Category.BODYWEIGHT, "Legs · Glutes · Core", "", com.example.mytrackerapp.domain.model.TargetType.REPS, 5, false, "5 reps", "", 1),
                Exercise("dead_hang", "Dead Hang", Category.BODYWEIGHT, "Shoulders · Spine · Grip", "", com.example.mytrackerapp.domain.model.TargetType.SECONDS, 15, false, "15 sec", "", 3),
                Exercise("band_row", "Band Row", Category.BAND, "Back · Biceps · Rear Delts", "", com.example.mytrackerapp.domain.model.TargetType.REPS, 10, false, "10 reps", "", 13)
            ),
            query = "",
            onQueryChange = {},
            onOpenExercise = {}
        )
    }
}
