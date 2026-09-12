package com.inkora.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp

enum class InkoraSymbol { PEN, HIGHLIGHT, ERASE, SELECT, HAND, SHAPE, TEXT, NOTE, IMAGE, UNDO, REDO, PLUS, MINUS, FIT, GRID, BOOK, PDF, BOARD, SEARCH, BACK, SPLIT, MORE, CHECK, STAR, MOON, SUN, FOLDER, TRASH, DOWNLOAD, ACCOUNT }

/** Small vector icon family; no font glyphs, emoji, or platform-dependent symbol fallbacks. */
@Composable
fun InkoraIcon(symbol: InkoraSymbol, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier.size(20.dp)) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            fun line(vararg p: Float) {
                val path = Path().apply { moveTo(p[0], p[1]); for (i in 2 until p.size step 2) lineTo(p[i], p[i + 1]) }
                drawPath(path, tint, style = Stroke(1.7f, cap = StrokeCap.Round))
            }
            fun rect(x: Float, y: Float, w: Float, h: Float) = drawRoundRect(tint, Offset(x,y), Size(w,h), androidx.compose.ui.geometry.CornerRadius(1.5f), style = Stroke(1.7f))
            when (symbol) {
                InkoraSymbol.PEN -> { line(4f,20f,5f,14f,16f,3f,21f,8f,10f,19f,4f,20f); line(13f,6f,18f,11f) }
                InkoraSymbol.HIGHLIGHT -> { line(5f,15f,13f,3f,21f,9f,13f,20f,5f,15f); line(4f,17f,8f,20f); line(3f,22f,16f,22f) }
                InkoraSymbol.ERASE -> { line(3f,14f,13f,4f,21f,12f,13f,20f,9f,20f,3f,14f); line(8f,9f,16f,17f); line(13f,20f,22f,20f) }
                InkoraSymbol.SELECT -> { line(5f,3f,5f,20f,10f,15f,14f,22f,17f,20f,13f,13f,20f,13f,5f,3f) }
                InkoraSymbol.HAND -> { line(5f,13f,3f,11f,2f,13f,7f,21f,16f,21f,20f,17f,20f,9f,17f,9f,17f,13f,17f,5f,14f,5f,14f,12f,14f,3f,11f,3f,11f,12f,11f,5f,8f,5f,8f,14f,5f,13f) }
                InkoraSymbol.SHAPE -> { rect(3f,3f,11f,11f); drawCircle(tint,5.5f,Offset(16f,16f),style=Stroke(1.7f)) }
                InkoraSymbol.TEXT -> { line(4f,6f,4f,3f,20f,3f,20f,6f); line(12f,3f,12f,21f); line(8f,21f,16f,21f) }
                InkoraSymbol.NOTE -> { line(4f,3f,20f,3f,20f,15f,14f,21f,4f,21f,4f,3f); line(14f,21f,14f,15f,20f,15f); line(8f,8f,16f,8f); line(8f,12f,13f,12f) }
                InkoraSymbol.IMAGE -> { rect(3f,3f,18f,18f); line(3f,18f,9f,11f,14f,16f,17f,12f,21f,17f); drawCircle(tint,1.5f,Offset(16f,7f),style=Stroke(1.7f)) }
                InkoraSymbol.UNDO -> { line(8f,4f,3f,9f,8f,14f); line(3f,9f,14f,9f,19f,12f,19f,17f,16f,20f,12f,20f) }
                InkoraSymbol.REDO -> { line(16f,4f,21f,9f,16f,14f); line(21f,9f,10f,9f,5f,12f,5f,17f,8f,20f,12f,20f) }
                InkoraSymbol.PLUS -> { line(5f,12f,19f,12f); line(12f,5f,12f,19f) }
                InkoraSymbol.MINUS -> line(5f,12f,19f,12f)
                InkoraSymbol.FIT -> { line(3f,9f,3f,3f,9f,3f); line(15f,3f,21f,3f,21f,9f); line(21f,15f,21f,21f,15f,21f); line(9f,21f,3f,21f,3f,15f) }
                InkoraSymbol.GRID -> { for (x in listOf(5f,12f,19f)) for (y in listOf(5f,12f,19f)) drawCircle(tint,1.2f,Offset(x,y)) }
                InkoraSymbol.BOOK -> { rect(4f,3f,16f,18f); line(8f,3f,8f,21f); line(12f,8f,16f,8f) }
                InkoraSymbol.PDF -> { line(5f,3f,14f,3f,20f,9f,20f,21f,5f,21f,5f,3f); line(14f,3f,14f,9f,20f,9f); line(8f,14f,16f,14f); line(8f,17f,14f,17f) }
                InkoraSymbol.BOARD -> { rect(3f,3f,18f,14f); line(12f,17f,12f,22f); line(7f,22f,17f,22f) }
                InkoraSymbol.SEARCH -> { drawCircle(tint,7f,Offset(10f,10f),style=Stroke(1.7f)); line(15f,15f,21f,21f) }
                InkoraSymbol.BACK -> { line(10f,5f,3f,12f,10f,19f); line(3f,12f,21f,12f) }
                InkoraSymbol.SPLIT -> { rect(3f,4f,18f,16f); line(12f,4f,12f,20f) }
                InkoraSymbol.MORE -> for (x in listOf(5f,12f,19f)) drawCircle(tint,1.5f,Offset(x,12f))
                InkoraSymbol.CHECK -> line(4f,12f,9f,17f,20f,6f)
                InkoraSymbol.STAR -> line(12f,2f,15f,8f,22f,9f,17f,14f,18f,21f,12f,18f,6f,21f,7f,14f,2f,9f,9f,8f,12f,2f)
                InkoraSymbol.MOON -> { val path=Path().apply { moveTo(19f,15f); cubicTo(8f,19f,5f,8f,11f,3f); cubicTo(-1f,4f,1f,24f,15f,21f); cubicTo(18f,20f,20f,18f,19f,15f) }; drawPath(path,tint,style=Stroke(1.7f)) }
                InkoraSymbol.SUN -> { drawCircle(tint,4f,Offset(12f,12f),style=Stroke(1.7f)); line(12f,2f,12f,5f);line(12f,19f,12f,22f);line(2f,12f,5f,12f);line(19f,12f,22f,12f);line(5f,5f,7f,7f);line(17f,17f,19f,19f);line(5f,19f,7f,17f);line(17f,7f,19f,5f) }
                InkoraSymbol.FOLDER -> line(3f,6f,9f,6f,11f,9f,21f,9f,21f,20f,3f,20f,3f,6f)
                InkoraSymbol.TRASH -> { line(3f,6f,21f,6f);line(6f,6f,7f,21f,17f,21f,18f,6f);line(9f,6f,9f,3f,15f,3f,15f,6f);line(10f,10f,10f,17f);line(14f,10f,14f,17f) }
                InkoraSymbol.DOWNLOAD -> { line(12f,3f,12f,16f,7f,11f);line(12f,16f,17f,11f);line(4f,16f,4f,21f,20f,21f,20f,16f) }
                InkoraSymbol.ACCOUNT -> { drawCircle(tint, 4f, Offset(12f, 8f), style = Stroke(1.7f)); drawArc(tint, 0f, 180f, false, Offset(4f, 12f), Size(16f, 10f), style = Stroke(1.7f)) }
            }
        }
    }
}

@Composable
fun WorkspaceAction(label: String, symbol: InkoraSymbol, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick, modifier = modifier.heightIn(min = 44.dp), enabled = enabled) {
        InkoraIcon(symbol); Spacer(Modifier.width(6.dp)); Text(label, maxLines = 1)
    }
}

@Composable
fun DrawingToolButton(label: String, symbol: InkoraSymbol, active: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(10.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { selected = active; contentDescription = label }.heightIn(min = 48.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            InkoraIcon(symbol); Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
