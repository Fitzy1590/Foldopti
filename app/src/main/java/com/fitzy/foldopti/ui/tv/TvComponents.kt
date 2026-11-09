@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.fitzy.foldopti.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.fitzy.foldopti.ui.theme.Radius
import com.fitzy.foldopti.ui.theme.Space

@Composable
fun TvTitle(text: String) = Text(text, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)

@Composable
fun TvSection(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = Space.x3))

@Composable
fun TvMuted(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

/** Focusable row. Focus shows a 2dp accent border and a 1.03x scale (never color alone). */
@Composable
fun TvRow(title: String, subtitle: String? = null, trailing: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(Radius.control)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, focusedContentColor = MaterialTheme.colorScheme.onSurface),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(Radius.control))),
    ) {
        Row(Modifier.padding(horizontal = Space.x4, vertical = Space.x3).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x4)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                subtitle?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            trailing?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun TvAction(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, scale = ButtonDefaults.scale(focusedScale = 1.05f),
        shape = ButtonDefaults.shape(RoundedCornerShape(Radius.control))) { Text(text) }
}

@Composable
fun TvStat(label: String, value: String, note: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card)).padding(Space.x5), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium)
        note?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
