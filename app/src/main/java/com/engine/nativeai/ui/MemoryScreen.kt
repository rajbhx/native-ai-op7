package com.engine.nativeai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engine.nativeai.Experience
import com.engine.nativeai.GgufMetaCache
import com.engine.nativeai.MemoryBudget
import com.engine.nativeai.ModelPreferencesStore
import com.engine.nativeai.StoragePaths
import com.engine.nativeai.Fact
import com.engine.nativeai.MemoryDatabase
import com.engine.nativeai.Message
import com.engine.nativeai.SessionInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Memory screen (vision M1b): search conversations, experiences and facts,
 * browse recent sessions, and reopen a session's messages after restart.
 * Read-only; all writes stay in the agent. No new UI dependencies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(onBack: () -> Unit, prefs: ModelPreferencesStore? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val memory = remember {
        MemoryDatabase(context.applicationContext, StoragePaths.memoryDbPath(context.applicationContext, prefs))
    }
    var query by remember { mutableStateOf("") }
    var selectedSession by remember { mutableStateOf<SessionInfo?>(null) }
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<SessionInfo>>(emptyList()) }
    var convResults by remember { mutableStateOf<List<Message>>(emptyList()) }
    var expResults by remember { mutableStateOf<List<Experience>>(emptyList()) }
    var factResults by remember { mutableStateOf<List<Fact>>(emptyList()) }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(query, selectedSession, refresh) {
        withContext(Dispatchers.IO) {
            val session = selectedSession
            if (session != null) {
                messages = memory.recentMessages(session.id, 100)
            } else if (query.isBlank()) {
                sessions = memory.recentSessions(15)
            } else {
                convResults = memory.searchMessages(query, 10)
                expResults = memory.searchSimilarExperiences(query, 5)
                val q = query.trim().lowercase()
                factResults = memory.queryFacts().filter {
                    it.subject.lowercase().contains(q) ||
                        it.predicate.lowercase().contains(q) ||
                        it.`object`.lowercase().contains(q)
                }.take(5)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OpBg)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "\u2039 back",
                color = OpTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("MEMORY", color = OpText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                "REFRESH",
                color = OpTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { refresh++ }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search memory\u2026", color = OpTextSecondary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OpRed,
                unfocusedBorderColor = OpBorder,
                focusedContainerColor = OpBg,
                unfocusedContainerColor = OpBg,
                focusedTextColor = OpText,
                unfocusedTextColor = OpText,
            ),
            shape = RoundedCornerShape(8.dp),
        )
        Spacer(Modifier.height(12.dp))

        when {
            selectedSession != null -> SessionMessages(
                session = selectedSession!!,
                messages = messages,
                onBackToList = { selectedSession = null },
            )
            query.isBlank() -> Column {
                ModelMemoryBudget()
                Spacer(Modifier.height(12.dp))
                RecentSessions(sessions, onOpen = { selectedSession = it })
            }
            else -> SearchResults(convResults, expResults, factResults)
        }
    }
}

@Composable
private fun RecentSessions(
    sessions: List<SessionInfo>,
    onOpen: (SessionInfo) -> Unit,
) {
    Text("RECENT SESSIONS", color = OpTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    if (sessions.isEmpty()) {
        EmptyState("No sessions yet \u2014 run an agent task first.")
        return
    }
    LazyColumn {
        items(sessions, key = { it.id }) { session ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clickable { onOpen(session) },
                color = OpCard,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, OpBorder),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        session.meta.ifBlank { "session #${session.id}" },
                        color = OpText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        timeOf(session.startedAt) +
                            (if (session.endedAt != null) " \u00b7 ended" else " \u00b7 open"),
                        color = OpTextSecondary,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionMessages(
    session: SessionInfo,
    messages: List<Message>,
    onBackToList: () -> Unit,
) {
    Text(
        session.meta.ifBlank { "session #${session.id}" },
        color = OpText,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
    Text(
        "\u2039 all sessions",
        color = OpTextSecondary,
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onBackToList)
            .padding(vertical = 4.dp),
    )
    Spacer(Modifier.height(8.dp))
    if (messages.isEmpty()) {
        EmptyState("This session has no stored messages yet.")
        return
    }
    LazyColumn {
        items(messages, key = { it.id }) { msg ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                color = OpCard,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, OpBorder),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (msg.role == "user") "USER" else "AGENT",
                            color = if (msg.role == "user") OpAmber else OpSuccess,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(timeOf(msg.created), color = OpTextSecondary, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(msg.content, color = OpText, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    convResults: List<Message>,
    expResults: List<Experience>,
    factResults: List<Fact>,
) {
    val total = convResults.size + expResults.size + factResults.size
    if (total == 0) {
        EmptyState("No matching memory found.")
        return
    }
    LazyColumn {
        if (convResults.isNotEmpty()) {
            item { SectionHeader("CONVERSATIONS \u00b7 ${convResults.size}") }
            items(convResults, key = { "c${it.id}" }) { msg ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    color = OpCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, OpBorder),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            if (msg.role == "user") "USER" else "AGENT",
                            color = if (msg.role == "user") OpAmber else OpSuccess,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(msg.content, color = OpText, fontSize = 13.sp, lineHeight = 18.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(timeOf(msg.created), color = OpTextSecondary, fontSize = 10.sp)
                    }
                }
            }
        }
        if (expResults.isNotEmpty()) {
            item { SectionHeader("EXPERIENCES \u00b7 ${expResults.size}") }
            items(expResults, key = { "e${it.id}" }) { exp ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    color = OpCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, OpBorder),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(exp.problemSummary, color = OpText, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                        Spacer(Modifier.height(4.dp))
                        Text(exp.resultSummary, color = OpTextSecondary, fontSize = 12.sp, maxLines = 3)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "tool ${exp.toolUsed} \u00b7 " +
                                (if (exp.success) "verified" else "failed") +
                                " \u00b7 conf ${(exp.confidence * 100).toInt()}%",
                            color = if (exp.success) OpSuccess else OpRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        if (factResults.isNotEmpty()) {
            item { SectionHeader("FACTS \u00b7 ${factResults.size}") }
            items(factResults, key = { "f${it.id}" }) { fact ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    color = OpCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, OpBorder),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            "${fact.subject} \u00b7 ${fact.predicate} \u00b7 ${fact.`object`}",
                            color = OpText,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "confidence ${(fact.confidence * 100).toInt()}% \u00b7 " +
                                (if (fact.lastVerified > 0) "verified" else "unverified"),
                            color = OpTextSecondary,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        color = OpTextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
    )
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
        Text(text, color = OpTextSecondary, fontSize = 13.sp)
    }
}

private fun timeOf(epochMs: Long): String =
    SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(epochMs))


/**
 * Per-component memory budget bars (golden UX P3): weights (measured from
 * the largest local GGUF), KV cache (estimated at the configured context),
 * graph scratch and SQLite (fixed profile constants) against the hard
 * 1536 MB ceiling. Only the largest local model is shown; values are
 * labeled measured vs estimate, never fabricated.
 */
@Composable
private fun ModelMemoryBudget(nCtx: Int = 2048) {
    val context = LocalContext.current
    var modelFile by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(Unit) {
        modelFile = withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "models")
            if (dir.isDirectory) {
                dir.listFiles { f ->
                    f.isFile && f.name.endsWith(".gguf", ignoreCase = true) && !f.name.endsWith(".tmp")
                }?.maxByOrNull { it.length() }
            } else {
                null
            }
        }
    }
    val f = modelFile ?: return
    val meta = GgufMetaCache.metaFor(f)
    val budget = remember(f, nCtx) {
        MemoryBudget.estimate(
            modelBytes = f.length(),
            nCtx = nCtx,
            layers = meta?.layers,
            hiddenDim = meta?.embeddingDim,
        )
    }
    val cap = budget.limitMb.toDouble().coerceAtLeast(1.0)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = OpCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, OpBorder),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                "MEMORY BUDGET \u00b7 ${f.name}",
                color = OpTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "measured file ${budget.weightsMb.toInt()} MB \u00b7 KV est @${nCtx} ctx",
                color = OpTextSecondary,
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(8.dp))
            BudgetRow("weights", budget.weightsMb, cap, "measured", OpStatusInfo)
            BudgetRow("KV cache", budget.kvCacheMb, cap, "estimated", OpStatusWarn)
            BudgetRow("graph", budget.graphMb, cap, "fixed", OpStatusInfo)
            BudgetRow("sqlite", budget.sqliteMb, cap, "fixed", OpStatusInfo)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "TOTAL ${budget.totalMb.toInt()} / ${budget.limitMb} MB",
                    color = if (budget.withinLimit) OpStatusSuccess else OpStatusDanger,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (budget.withinLimit) "WITHIN CAP" else "EXCEEDS CAP",
                    color = if (budget.withinLimit) OpStatusSuccess else OpStatusDanger,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun BudgetRow(label: String, mb: Double, capMb: Double, source: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label.uppercase(),
            color = OpTextSecondary,
            fontSize = 10.sp,
            modifier = Modifier.width(64.dp),
        )
        LinearProgressIndicator(
            progress = { (mb / capMb).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f),
            color = color,
            trackColor = OpDivider,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${mb.toInt()} MB \u00b7 $source",
            color = OpTextSecondary,
            fontSize = 10.sp,
        )
    }
    Spacer(Modifier.height(6.dp))
}
