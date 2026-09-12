package com.inkora.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun Glyph(text: String, modifier: Modifier = Modifier, contentDescription: String? = null, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = text,
        modifier = modifier.semantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        },
        color = color,
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
fun InkoraSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun InkoraPill(text: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .3f)) else null
    ) { Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium) }
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = { Column { content() } }
    )
}

@Composable
fun DocumentThumbnail(document: InkoraDocument, modifier: Modifier = Modifier) {
    val paper = if (document.type == InkoraDocumentType.Pdf) Color(0xFFFFFAF0) else Color(0xFFFFFDF8)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(paper)
            .border(1.dp, Color.Black.copy(alpha = .08f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(.42f).height(5.dp).background(document.accent, RoundedCornerShape(4.dp)))
            repeat(5) { index ->
                Box(Modifier.fillMaxWidth(if (index == 4) .64f else .9f).height(2.dp).background(Color(0xFF9BA5AD).copy(alpha = .5f), RoundedCornerShape(2.dp)))
            }
            Spacer(Modifier.weight(1f))
            Text(document.type.glyph, color = document.accent, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, actionLabel: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Glyph("✦", color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) {
            androidx.compose.material3.FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun InspectorRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun VerticalRule(modifier: Modifier = Modifier) { HorizontalDivider(modifier = modifier, color = MaterialTheme.colorScheme.outlineVariant) }
