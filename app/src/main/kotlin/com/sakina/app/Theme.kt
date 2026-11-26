package com.sakina.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

val Emerald = Color(0xFF1FA67A)
val EmeraldDeep = Color(0xFF0F7A58)
val Gold = Color(0xFFD4B26A)
val GoldDeep = Color(0xFF9C7A2E)
val Danger = Color(0xFFE5645A)
val GoldInk = Color(0xFF7A5A12)
val EmeraldInk = Color(0xFF0F7A58)
val DangerInk = Color(0xFFB3261E)
/** Text-safe accent colors: bright in dark theme, deeper in light theme so they stay readable. */
val GoldT: Color @Composable get() = if (isDark()) Gold else GoldInk
val EmeraldT: Color @Composable get() = if (isDark()) Emerald else EmeraldInk
val DangerT: Color @Composable get() = if (isDark()) Danger else DangerInk

val Tajawal = FontFamily(Font(R.font.tajawal_regular, FontWeight.Normal), Font(R.font.tajawal_medium, FontWeight.Medium), Font(R.font.tajawal_bold, FontWeight.Bold))
val Amiri = FontFamily(Font(R.font.amiri_regular, FontWeight.Normal), Font(R.font.amiri_bold, FontWeight.Bold))

internal val DarkScheme = darkColorScheme(
    primary = Emerald, onPrimary = Color(0xFF00110A), secondary = Gold, onSecondary = Color(0xFF1B1405),
    background = Color(0xFF0A0F0D), onBackground = Color(0xFFE8EFEA), surface = Color(0xFF121A16), onSurface = Color(0xFFE8EFEA),
    surfaceVariant = Color(0xFF1A2420), onSurfaceVariant = Color(0xFFA9B8AF), outline = Color(0xFF34423B), error = Danger,
    primaryContainer = Color(0xFF0E3A2C), onPrimaryContainer = Color(0xFFBDF2DC)
)
internal val LightScheme = lightColorScheme(
    primary = EmeraldDeep, onPrimary = Color.White, secondary = GoldDeep, onSecondary = Color.White,
    background = Color(0xFFF6F3EA), onBackground = Color(0xFF14201B), surface = Color(0xFFFFFFFF), onSurface = Color(0xFF14201B),
    surfaceVariant = Color(0xFFEAE6D8), onSurfaceVariant = Color(0xFF55645B), outline = Color(0xFFC9C4B0), error = Color(0xFFB3261E),
    primaryContainer = Color(0xFFCDEEDF), onPrimaryContainer = Color(0xFF053A2A)
)

private fun typo(): Typography {
    fun s(size: Int, w: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = Tajawal, fontWeight = w, fontSize = size.sp, lineHeight = (size * 1.45).sp)
    return Typography(
        displayLarge = s(44, FontWeight.Bold), displayMedium = s(34, FontWeight.Bold), headlineMedium = s(26, FontWeight.Bold), headlineSmall = s(22, FontWeight.Bold),
        titleLarge = s(20, FontWeight.Bold), titleMedium = s(17, FontWeight.Medium), titleSmall = s(15, FontWeight.Medium),
        bodyLarge = s(16), bodyMedium = s(14), bodySmall = s(12), labelLarge = s(14, FontWeight.Medium), labelMedium = s(12, FontWeight.Medium), labelSmall = s(11)
    )
}

@Composable fun SakinaTheme(dark: Boolean, content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = typo(), content = content)

@Composable fun isDark() = MaterialTheme.colorScheme.background.red < 0.5f

@Composable fun appBackground(): Brush {
    val cs = MaterialTheme.colorScheme
    return if (isDark()) Brush.verticalGradient(listOf(Color(0xFF0C1713), cs.background, Color(0xFF080C0A)))
    else Brush.verticalGradient(listOf(Color(0xFFEFF3E8), cs.background, Color(0xFFF3EEDC)))
}

@Composable fun Glass(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, pad: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    val dark = isDark()
    val shape = RoundedCornerShape(20.dp)
    val bg = if (dark) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.075f), Color.White.copy(alpha = 0.03f)))
    else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.75f)))
    val border = if (dark) Color.White.copy(alpha = 0.10f) else Emerald.copy(alpha = 0.18f)
    var m = modifier.clip(shape).background(bg).border(BorderStroke(1.dp, border), shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { Column(m.animateContentSize().padding(pad.dp), content = content) }
}

@Composable fun Title(text: String, modifier: Modifier = Modifier) = Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, modifier = modifier)
@Composable fun Sub(text: String, modifier: Modifier = Modifier) = Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
@Composable fun Body(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) = Text(text, style = MaterialTheme.typography.bodyLarge, color = color, modifier = modifier)

@Composable fun Btn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, outlined: Boolean = false) {
    val sh = RoundedCornerShape(16.dp)
    if (outlined) OutlinedButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = sh) { Text(text, style = MaterialTheme.typography.labelLarge) }
    else Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = sh) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable fun Chip(text: String, selected: Boolean, onClick: () -> Unit) =
    FilterChip(selected = selected, onClick = onClick, label = { Text(text, style = MaterialTheme.typography.labelLarge) }, shape = RoundedCornerShape(14.dp))

@Composable fun ChipRow(items: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(items) { (k, v) -> Chip(v, k == selected) { onSelect(k) } } }
}

@Composable fun Empty(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Emerald.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Text("☾", color = GoldT, style = MaterialTheme.typography.headlineSmall) }
        Sub(text, Modifier.fillMaxWidth(), )
        if (actionLabel != null && onAction != null) Btn(actionLabel, onAction)
    }
}

@Composable fun Ring(progress: Float, size: Int = 64, stroke: Int = 7, color: Color = Emerald, label: String? = null) {
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.dp.toPx(); val inset = sw / 2
            drawArc(track, 0f, 360f, false, Offset(inset, inset), Size(this.size.width - sw, this.size.height - sw), style = Stroke(sw))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), Size(this.size.width - sw, this.size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
        }
        Text(label ?: numS("${(progress * 100).toInt()}%"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable fun BarChart(values: List<Float>, labels: List<String>, modifier: Modifier = Modifier, color: Color = Emerald, unit: String = "") {
    val mx = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    Row(modifier.height(130.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (v > 0) Text(numS(if (v % 1f == 0f) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)) + unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Box(Modifier.fillMaxWidth(0.7f).height((70f * v / mx).coerceAtLeast(3f).dp).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.45f)))))
                Spacer(Modifier.height(4.dp))
                Text(labels.getOrElse(i) { "" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Screen(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}, fab: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1) },
                navigationIcon = { if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("رجوع", "Back")) } },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = { if (fab != null) FloatingActionButton(onClick = fab, containerColor = Emerald, contentColor = Color.White) { Icon(Icons.Default.Add, tr("إضافة", "Add")) } }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Screen with a lazy list for long content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LazyScreen(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}, fab: (() -> Unit)? = null, state: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(), content: LazyListScope.() -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1) },
                navigationIcon = { if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("رجوع", "Back")) } },
                actions = actions, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = { if (fab != null) FloatingActionButton(onClick = fab, containerColor = Emerald, contentColor = Color.White) { Icon(Icons.Default.Add, tr("إضافة", "Add")) } }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp), state = state, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 96.dp), content = content)
    }
}

@Composable fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, singleLine: Boolean = true, number: Boolean = false) =
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine, shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text), textStyle = MaterialTheme.typography.bodyLarge)

@Composable fun TimeField(label: String, minutes: Int, onPick: (Int) -> Unit, allowNone: Boolean = false, modifier: Modifier = Modifier) {
    val c = LocalContext.current
    OutlinedButton({
        val m = if (minutes < 0) 8 * 60 else minutes
        TimePickerDialog(c, { _, h, mm -> onPick(h * 60 + mm) }, m / 60, m % 60, true).show()
    }, modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
        Text("$label: " + (if (minutes < 0) tr("بدون", "none") else numS(fmtMin(minutes))), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable fun DateField(label: String, day: Long, onPick: (Long) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalContext.current
    OutlinedButton({
        val d = dayOf(day)
        DatePickerDialog(c, { _, y, m, dd -> onPick(LocalDate.of(y, m + 1, dd).toEpochDay()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }, modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) { Text("$label: ${dayLabel(day)}", style = MaterialTheme.typography.labelLarge) }
}

@Composable fun Confirm(title: String, text: String, onYes: () -> Unit, onNo: () -> Unit) =
    AlertDialog(onDismissRequest = onNo, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onYes) { Text(tr("تأكيد", "Confirm")) } }, dismissButton = { TextButton(onNo) { Text(tr("إلغاء", "Cancel")) } })

@Composable fun RowSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit, sub: String? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.titleSmall); if (sub != null) Sub(sub) }
        Switch(checked, onChange)
    }
}

@Composable fun SectionLabel(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, color = GoldT, modifier = Modifier.padding(top = 8.dp))
