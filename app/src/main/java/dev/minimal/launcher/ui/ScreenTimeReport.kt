package dev.minimal.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.minimal.launcher.LauncherViewModel
import dev.minimal.launcher.data.AppCategories
import dev.minimal.launcher.data.AppInfo
import dev.minimal.launcher.data.IntentionSummary
import dev.minimal.launcher.data.LauncherSettings
import dev.minimal.launcher.data.ScreenTime
import dev.minimal.launcher.data.ScreenTimeMath
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Bildschirmzeit: heute (Apps + Kategorien) und die letzten 7 Tage (Diagramm, Ziel, Serie). */
@Composable
fun ScreenTimeReport(
    vm: LauncherViewModel,
    settings: LauncherSettings,
    appsByPackage: Map<String, AppInfo>,
    onDismiss: () -> Unit,
) {
    val days by produceState<List<Pair<LocalDate, Map<String, Long>>>?>(null) { value = vm.screenTimeWeek() }
    val intentions by produceState<IntentionSummary?>(null) { value = vm.intentionWeek() }
    var tab by remember { mutableIntStateOf(0) }
    val pro = isPro()
    var paywall by remember { mutableStateOf(false) }
    if (paywall) PaywallDialog(feature = "Wochenbericht", onDismiss = { paywall = false })

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Bildschirmzeit")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TabChip("Heute", tab == 0) { tab = 0 }
                    TabChip("Woche", tab == 1) { tab = 1 }
                }
            }
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                val d = days
                when {
                    d == null -> Text("Wird berechnet …")
                    tab == 0 -> TodayView(d.last().second, appsByPackage)
                    !pro -> {
                        Text("Wochenbericht, Tagesziel und Serie gehören zu Pro.")
                        TextButton(onClick = { paywall = true }) { Text("Pro freischalten") }
                    }
                    else -> {
                        WeekView(d, settings.dailyGoalMinutes * 60_000L, appsByPackage)
                        intentions?.takeIf { !it.isEmpty }?.let { IntentionsSection(it, appsByPackage) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

@Composable
private fun TabChip(text: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TodayView(usage: Map<String, Long>, appsByPackage: Map<String, AppInfo>) {
    Text(ScreenTime.format(usage.values.sum()), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(12.dp))

    val byCategory = usage.entries
        .groupBy { appsByPackage[it.key]?.category ?: -1 }
        .mapValues { (_, list) -> list.sumOf { it.value } }
        .filterValues { it >= 60_000 }
        .entries.sortedByDescending { it.value }
    if (byCategory.isNotEmpty()) {
        SectionTitle("Nach Kategorie")
        val max = byCategory.first().value
        byCategory.forEach { (cat, ms) -> UsageBarRow(AppCategories.label(cat), ms, max, icon = null) }
        Spacer(Modifier.height(12.dp))
    }

    val top = usage.entries.filter { it.value >= 60_000 }.sortedByDescending { it.value }.take(10)
    SectionTitle("Apps")
    if (top.isEmpty()) Text("Heute noch keine App länger als eine Minute genutzt.")
    val max = top.firstOrNull()?.value ?: 1L
    top.forEach { (pkg, ms) -> UsageBarRow(appsByPackage[pkg]?.label ?: pkg, ms, max, icon = appsByPackage[pkg]) }
}

@Composable
private fun WeekView(days: List<Pair<LocalDate, Map<String, Long>>>, goalMs: Long, appsByPackage: Map<String, AppInfo>) {
    val totals = days.map { it.first to it.second.values.sum() }
    var selected by remember { mutableStateOf(totals.last().first) }
    val selectedTotal = totals.firstOrNull { it.first == selected }?.second ?: 0L

    // Durchschnitt über abgeschlossene Tage mit Daten (heute läuft noch).
    val fullDays = totals.dropLast(1).filter { it.second > 0 }
    val average = if (fullDays.isNotEmpty()) fullDays.sumOf { it.second } / fullDays.size else totals.last().second
    Text("Ø ${ScreenTime.format(average)} pro Tag", style = MaterialTheme.typography.headlineSmall)
    if (goalMs > 0) {
        val streak = ScreenTimeMath.streak(totals.map { it.second }, goalMs)
        Text(
            "Ziel ${ScreenTime.format(goalMs)} (gestrichelt) · Serie: $streak ${if (streak == 1) "Tag" else "Tage"} im Ziel",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(12.dp))
    Text(
        dayLabel(selected, long = true) + " · " + ScreenTime.format(selectedTotal),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(8.dp))
    WeekBars(totals, goalMs, selected) { selected = it }
    Spacer(Modifier.height(16.dp))

    val week = HashMap<String, Long>()
    days.forEach { (_, usage) -> usage.forEach { (pkg, ms) -> week[pkg] = (week[pkg] ?: 0L) + ms } }
    val top = week.entries.filter { it.value >= 60_000 }.sortedByDescending { it.value }.take(5)
    SectionTitle("Meistgenutzt diese Woche")
    val max = top.firstOrNull()?.value ?: 1L
    top.forEach { (pkg, ms) -> UsageBarRow(appsByPackage[pkg]?.label ?: pkg, ms, max, icon = appsByPackage[pkg]) }
    Text(
        "Android bewahrt Nutzungsdaten nur einige Tage auf – ältere Tage können daher fehlen.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

/** Balkendiagramm: eine Reihe in der Akzentfarbe, Tagesziel als gestrichelte Linie. */
@Composable
private fun WeekBars(totals: List<Pair<LocalDate, Long>>, goalMs: Long, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val maxValue = maxOf(totals.maxOf { it.second }, goalMs, 60_000L).toFloat()
    val barColor = MaterialTheme.colorScheme.primary
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val baseline = MaterialTheme.colorScheme.outlineVariant

    Box(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
            totals.forEach { (date, ms) ->
                val isSelected = date == selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(date) }
                        .semantics { contentDescription = "${dayLabel(date, long = true)}: ${ScreenTime.format(ms)}" },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.5f)
                            .fillMaxHeight((ms / maxValue).coerceIn(0.01f, 1f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (isSelected) barColor else barColor.copy(alpha = 0.45f))
                    )
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            // Grundlinie
            drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            if (goalMs > 0) {
                val y = size.height * (1f - goalMs / maxValue)
                drawLine(
                    lineColor,
                    Offset(0f, y),
                    Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        totals.forEach { (date, _) ->
            Text(
                dayLabel(date, long = false),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (date == selected) FontWeight.Bold else FontWeight.Normal,
                color = if (date == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun UsageBarRow(label: String, ms: Long, max: Long, icon: AppInfo?) =
    BarRow(label, ScreenTime.format(ms), ms.toFloat() / max, icon)

/** Absichten der Woche: bewusst geöffnet vs. verzichtet, häufigste Gründe, Langeweile-Hinweis. */
@Composable
private fun IntentionsSection(summary: IntentionSummary, appsByPackage: Map<String, AppInfo>) {
    Spacer(Modifier.height(16.dp))
    SectionTitle("Absichten")
    Text(
        buildString {
            append(if (summary.opened == 1) "1 bewusste Öffnung" else "${summary.opened} bewusste Öffnungen")
            if (summary.skipped > 0) append(" · ${summary.skipped}× verzichtet")
        },
        style = MaterialTheme.typography.bodyLarge,
    )
    if (summary.boredom > 0) {
        val app = summary.boredomTopApp?.let { appsByPackage[it]?.label ?: it }
        Text(
            "${summary.boredom}× aus Langeweile" + (app?.let { " – meist bei $it" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (summary.skipped > 0) {
        Text(
            "Jedes „Lieber nicht“ ist gewonnene Zeit.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val top = summary.byIntention.take(5)
    if (top.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        val max = top.first().second.toFloat()
        top.forEach { (text, count) -> BarRow(text, "$count×", count / max, icon = null) }
    }
    val apps = summary.byApp.take(5)
    if (apps.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        SectionTitle("Nach App")
        val max = apps.maxOf { it.second + it.third }.toFloat()
        apps.forEach { (pkg, opened, skipped) ->
            BarRow(
                appsByPackage[pkg]?.label ?: pkg,
                "$opened× geöffnet" + if (skipped > 0) " · $skipped× verzichtet" else "",
                (opened + skipped) / max,
                icon = appsByPackage[pkg],
            )
        }
    }
}

@Composable
private fun BarRow(label: String, value: String, fraction: Float, icon: AppInfo?) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                AppIcon(icon, 22.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

private fun dayLabel(date: LocalDate, long: Boolean): String {
    val today = LocalDate.now()
    if (long) {
        return when (date) {
            today -> "Heute"
            today.minusDays(1) -> "Gestern"
            else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN) + ", ${date.dayOfMonth}.${date.monthValue}."
        }
    }
    return date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN).take(2)
}
