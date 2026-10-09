package com.example.mytrackerapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.mytrackerapp.ui.theme.Accent
import com.example.mytrackerapp.ui.theme.MinTouchTarget
import com.example.mytrackerapp.ui.theme.OnAccent
import com.example.mytrackerapp.ui.theme.Outline
import com.example.mytrackerapp.ui.theme.Radius
import com.example.mytrackerapp.ui.theme.Spacing
import com.example.mytrackerapp.ui.theme.TextSecondary

/** A horizontally scrolling row of single-select pill chips. */
@Composable
fun <T> ChipRow(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        options.forEach { option ->
            Chip(label(option), option == selected) { onSelect(option) }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = MinTouchTarget)
            .clip(RoundedCornerShape(Radius.pill))
            .background(if (selected) Accent else Color.Transparent)
            .border(1.dp, if (selected) Accent else Outline, RoundedCornerShape(Radius.pill))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.md),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) OnAccent else TextSecondary
        )
    }
}
