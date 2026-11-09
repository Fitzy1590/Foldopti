package com.fitzy.foldopti.ui.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitzy.foldopti.ui.theme.Radius
import com.fitzy.foldopti.ui.theme.Space

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.card),
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(Space.x5), verticalArrangement = Arrangement.spacedBy(Space.x3), content = content)
    }
}

@Composable
fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)

@Composable
fun SectionLabel(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground,
    modifier = Modifier.padding(top = Space.x2))

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

@Composable
fun Mono(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

@Composable
fun Stat(label: String, value: String, note: String? = null, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Mono(label)
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        note?.let { Mono(it) }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false) {
    Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled && !busy,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.control)) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text(text)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.control),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) { Text(text) }
}

@Composable
fun RowItem(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            subtitle?.let { Mono(it) }
        }
        trailing?.invoke()
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChecked: (Boolean) -> Unit) =
    RowItem(title, subtitle, trailing = { Switch(checked, onChecked) })

@Composable
fun ProgressBlock(p: com.fitzy.foldopti.core.optimizer.Progress?) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
        if (p != null && p.total > 0) LinearProgressIndicator(progress = { p.done / p.total.toFloat() }, modifier = Modifier.fillMaxWidth())
        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Mono(if (p == null) "Working" else if (p.total > 0) "${p.label}: ${p.done} of ${p.total}" else p.label)
    }
}
