package com.engine.nativeai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engine.nativeai.ModelAvailability
import com.engine.nativeai.ModelCostTier
import com.engine.nativeai.ModelDescriptor
import com.engine.nativeai.LocalModelEntry
import com.engine.nativeai.ModelKind
import com.engine.nativeai.ModelStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dynamic model picker (spec: model picker). Search + capability filters,
 * FREE / LOCAL / OTHER sections, live availability dots, capability chips
 * only where metadata is confirmed, and a cached-catalog timestamp. The list
 * always comes from the registry (discovery refresh), never a hard-coded UI.
 */
@Composable
fun ModelPickerDialog(
    models: List<ModelDescriptor>,
    selectedId: String?,
    lastUpdated: Long,
    favorites: Set<String>,
    onSelect: (ModelDescriptor) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRefresh: () -> Unit,
    onConfigure: ((ModelDescriptor) -> Unit)? = null,
    localEntries: List<LocalModelEntry> = emptyList(),
    onPickLocal: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(PickerFilter.ALL) }
    val hasCoding = models.any { it.codingScore != null }
    val hasReasoning = models.any { it.supportsReasoning || it.reasoningScore != null }
    val hasVision = models.any { it.supportsVision }
    val hasTools = models.any { it.supportsTools }
    val hasFree = models.any { it.costTier == ModelCostTier.FREE }
    val hasLocal = models.any { it.kind == ModelKind.LOCAL }

    val q = query.trim().lowercase()
    val filtered = models.filter { d ->
        val matchesQuery = q.isEmpty() ||
            d.id.lowercase().contains(q) ||
            d.displayName.lowercase().contains(q) ||
            d.provider.lowercase().contains(q)
        val matchesFilter = when (filter) {
            PickerFilter.ALL -> true
            PickerFilter.FREE -> d.costTier == ModelCostTier.FREE
            PickerFilter.LOCAL -> d.kind == ModelKind.LOCAL
            PickerFilter.CODING -> d.codingScore != null
            PickerFilter.REASONING -> d.supportsReasoning || d.reasoningScore != null
            PickerFilter.VISION -> d.supportsVision
            PickerFilter.TOOLS -> d.supportsTools
        }
        matchesQuery && matchesFilter
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OpBg,
        title = { Text("Pick a model", color = OpText, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.height(520.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search models…", color = OpTextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OpRed,
                        unfocusedBorderColor = OpDivider,
                        cursorColor = OpRed,
                    ),
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip("All", filter == PickerFilter.ALL) { filter = PickerFilter.ALL }
                    if (hasFree) FilterChip("Free", filter == PickerFilter.FREE) { filter = PickerFilter.FREE }
                    if (hasLocal) FilterChip("Local", filter == PickerFilter.LOCAL) { filter = PickerFilter.LOCAL }
                    if (hasCoding) FilterChip("Coding", filter == PickerFilter.CODING) { filter = PickerFilter.CODING }
                    if (hasReasoning) FilterChip("Reasoning", filter == PickerFilter.REASONING) { filter = PickerFilter.REASONING }
                    if (hasVision) FilterChip("Vision", filter == PickerFilter.VISION) { filter = PickerFilter.VISION }
                    if (hasTools) FilterChip("Tools", filter == PickerFilter.TOOLS) { filter = PickerFilter.TOOLS }
                }
                Spacer(Modifier.height(8.dp))

                LazyColumn(Modifier.weight(1f)) {
                    val free = filtered.filter { it.kind == ModelKind.REMOTE && it.costTier == ModelCostTier.FREE }
                    val local = filtered.filter { it.kind == ModelKind.LOCAL }
                    val other = filtered.filter { it.kind == ModelKind.REMOTE && it.costTier != ModelCostTier.FREE }
                    if (free.isNotEmpty()) {
                        item { SectionHeader("FREE") }
                        items(free) {
                            ModelRow(it, selectedId, favorites, onSelect, onToggleFavorite, onConfigure)
                        }
                    }
                    if (local.isNotEmpty() || onPickLocal != null) {
                        item { SectionHeader("LOCAL") }
                        if (onPickLocal != null &&
                            (filter == PickerFilter.ALL || filter == PickerFilter.LOCAL) &&
                            pickRowVisible(q)
                        ) {
                            item { PickLocalRow(onPickLocal) }
                        }
                        items(local) {
                            ModelRow(
                                it, selectedId, favorites, onSelect, onToggleFavorite, onConfigure,
                                subtitle = localSubtitle(it, localEntries),
                            )
                        }
                    }
                    if (other.isNotEmpty()) {
                        item { SectionHeader("OTHER") }
                        items(other) {
                            ModelRow(it, selectedId, favorites, onSelect, onToggleFavorite, onConfigure)
                        }
                    }
                    if (filtered.isEmpty()) {
                        item { Text("No models match.", color = OpTextSecondary, fontSize = 12.sp) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (lastUpdated > 0) {
                            "Last updated: ${formatTime(lastUpdated)}"
                        } else {
                            "Not refreshed yet"
                        },
                        color = OpTextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRefresh) {
                        Text("Refresh", color = OpLinkAccent)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = OpTextSecondary)
            }
        },
    )
}

private enum class PickerFilter { ALL, FREE, LOCAL, CODING, REASONING, VISION, TOOLS }

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) OpText else OpTextSecondary,
        fontSize = 11.sp,
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .background(if (selected) OpCard else Color.Transparent, RoundedCornerShape(14.dp))
            .border(
                BorderStroke(1.dp, if (selected) OpBorder else OpDivider),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 0.dp)
            .wrapContentHeight(Alignment.CenterVertically),
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = OpTextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun ModelRow(
    d: ModelDescriptor,
    selectedId: String?,
    favorites: Set<String>,
    onSelect: (ModelDescriptor) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onConfigure: ((ModelDescriptor) -> Unit)?,
    subtitle: String? = null,
) {
    val selected = d.id == selectedId
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(if (selected) OpCard.copy(alpha = 0.6f) else OpCard, RoundedCornerShape(10.dp))
            .clickable { onSelect(d) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    d.displayName,
                    color = OpText,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
                if (selected) {
                    Text(" ✓", color = OpLinkAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.provider, color = OpTextSecondary, fontSize = 10.sp)
                TierPill(d.costTier)
                AvailabilityDot(d.availability)
                CapabilityChips(d)
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = OpTextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (d.kind == ModelKind.REMOTE && onConfigure != null) {
            Text(
                text = "Key",
                color = OpTextSecondary,
                fontSize = 10.sp,
                modifier = Modifier
                    .clickable { onConfigure(d) }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
        Text(
            text = if (favorites.contains(d.id)) "★" else "☆",
            color = if (favorites.contains(d.id)) OpAmber else OpTextSecondary,
            fontSize = 16.sp,
            modifier = Modifier
                .clickable { onToggleFavorite(d.id) }
                .padding(6.dp),
        )
    }
}

@Composable
private fun PickLocalRow(onPickLocal: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(OpCard, RoundedCornerShape(10.dp))
            .clickable(onClick = onPickLocal)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "\uff0b Pick GGUF from storage\u2026",
            color = OpLinkAccent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun pickRowVisible(q: String): Boolean =
    q.isEmpty() || listOf("pick", "gguf", "import", "storage", "local", "add").any { it in q }

private fun localSubtitle(d: ModelDescriptor, entries: List<LocalModelEntry>): String? {
    if (d.kind != ModelKind.LOCAL) return null
    val entry = entries.firstOrNull { it.id == d.id } ?: return null
    val mb = entry.sizeBytes / (1024L * 1024L)
    val quant = ModelStatus.quantTag(entry.file.name)
    return "GGUF \u00b7 ${mb} MB" + (quant?.let { " \u00b7 $it" } ?: "")
}

@Composable
private fun TierPill(tier: ModelCostTier) {
    val (label, color) = when (tier) {
        ModelCostTier.FREE -> "FREE" to OpAmber
        ModelCostTier.PAID -> "PAID" to OpTextSecondary
        ModelCostTier.UNKNOWN -> "\u2014" to OpTextSecondary.copy(alpha = 0.45f)
    }
    Text(
        text = label,
        color = color,
        fontSize = 9.sp,
        modifier = Modifier
            .padding(start = 6.dp)
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun AvailabilityDot(availability: ModelAvailability) {
    val (color, label) = when (availability) {
        ModelAvailability.AVAILABLE -> Color(0xFF2ECC71) to "Available"
        ModelAvailability.LIMITED -> Color(0xFFF39C12) to "Temporarily unavailable"
        ModelAvailability.UNAVAILABLE -> Color(0xFFE74C3C) to "Offline"
        ModelAvailability.UNKNOWN -> OpTextSecondary.copy(alpha = 0.45f) to "Unknown"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .padding(start = 6.dp)
                .width(7.dp)
                .height(7.dp)
                .background(color, RoundedCornerShape(4.dp)),
        )
        Text(" $label", color = OpTextSecondary, fontSize = 9.sp)
    }
}

@Composable
private fun CapabilityChips(d: ModelDescriptor) {
    Row {
        if (d.supportsTools) CapChip("tools")
        if (d.supportsVision) CapChip("vision")
        if (d.supportsReasoning || d.reasoningScore != null) CapChip("reasoning")
        if (d.codingScore != null) CapChip("coding ${d.codingScore}")
        if (d.contextLength != null) CapChip("ctx ${d.contextLength}")
    }
}

@Composable
private fun CapChip(label: String) {
    Text(
        text = label,
        color = OpTextSecondary,
        fontSize = 9.sp,
        modifier = Modifier
            .padding(start = 4.dp)
            .background(OpDivider.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

private fun formatTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
