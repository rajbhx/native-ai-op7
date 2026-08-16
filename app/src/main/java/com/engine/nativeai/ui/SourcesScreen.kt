package com.engine.nativeai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engine.nativeai.MemoryDatabase
import com.engine.nativeai.ModelPreferencesStore
import com.engine.nativeai.StoragePaths
import com.engine.nativeai.Source
import com.engine.nativeai.SourceCapabilities
import com.engine.nativeai.SourceChunk
import com.engine.nativeai.SourceFile
import com.engine.nativeai.SourceChunker
import com.engine.nativeai.SourceCollection
import com.engine.nativeai.SourceRegistry
import com.engine.nativeai.SourceSearch
import com.engine.nativeai.SourceSearchHit
import com.engine.nativeai.SourceSeedLoader
import com.engine.nativeai.SourceStatus
import com.engine.nativeai.SourceType
import com.engine.nativeai.SourceUpdater
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Source Knowledge Base screen (roadmap Phase 4-6): browse collections,
 * add sources (GitHub repo / web page / raw text / local file), refresh
 * with the uBO-style serial updater, and search the local chunk index.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(onBack: () -> Unit, prefs: ModelPreferencesStore? = null) {
    val context = LocalContext.current
    val db = remember {
        MemoryDatabase(context.applicationContext, StoragePaths.memoryDbPath(context.applicationContext, prefs))
    }
    val registry = remember {
        val loader = SourceSeedLoader(context.applicationContext)
        SourceRegistry(db).apply { seed(loader.load(), loader.catalogVersion()) }
    }
    val updater = remember { SourceUpdater(registry, db) }
    val search = remember { SourceSearch(db) }
    val scope = rememberCoroutineScope()

    var sources by remember { mutableStateOf<List<Source>>(emptyList()) }
    var collections by remember { mutableStateOf<List<SourceCollection>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SourceSearchHit>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var statusLine by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var viewerSource by remember { mutableStateOf<Source?>(null) }
    var viewerFileId by remember { mutableStateOf<Long?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    // P5: open a source (from its row or from a knowledge hit) and read the
    // indexed chunks locally — sources are readable knowledge, not metadata.
    fun openSource(s: Source, fileId: Long?) {
        viewerSource = s
        viewerFileId = fileId
    }

    fun openHit(hit: SourceSearchHit) {
        scope.launch {
            val target = withContext(Dispatchers.IO) {
                val chunk = db.chunkById(hit.chunkId)
                val file = chunk?.let { db.sourceFileById(it.sourceFileId) }
                val s = file?.let { db.sourceById(it.sourceId) }
                if (s != null) s to file.id else null
            }
            if (target != null) {
                openSource(target.first, target.second)
            } else {
                statusLine = "Could not open the hit \u2014 chunk metadata missing; re-index the source."
            }
        }
    }

    LaunchedEffect(refreshTick) {
        withContext(Dispatchers.IO) {
            sources = registry.sources()
            collections = registry.collections()
        }
    }
    LaunchedEffect(query) {
        if (query.isBlank()) {
            hits = emptyList()
        } else {
            hits = search.search(query, 8)
        }
    }

    fun refreshAll() {
        scope.launch {
            running = true
            statusLine = "Updating\u2026"
            val report = withContext(Dispatchers.IO) {
                updater.updateOnce()
            }
            running = false
            statusLine = when {
                report.skippedBusy -> "Update skipped \u2014 already running"
                report.stopped -> "Stopped \u2014 updated ${report.updated}, failed ${report.failed}"
                else -> "Updated ${report.updated}, failed ${report.failed}"
            }
            refreshTick++
        }
    }

    fun refreshOne(id: Long) {
        scope.launch {
            running = true
            val report = withContext(Dispatchers.IO) { updater.updateSource(id) }
            running = false
            statusLine = if (report.updated > 0) "Source updated" else "Source failed / unchanged"
            refreshTick++
        }
    }

    fun addRawText(title: String, text: String, collection: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val colId = db.upsertSourceCollection(collection)
                val id = registry.addSource(
                    Source(0, colId, title, SourceType.RAW_TEXT, updateAfterHours = 24),
                )
                val chunks = SourceChunker.chunk(text)
                val fileId = db.upsertSourceFile(
                    com.engine.nativeai.SourceFile(0, id, "root.txt", "raw", sizeBytes = text.length.toLong()),
                )
                db.replaceSourceChunks(id, fileId, chunks)
                db.touchSourceWrite(id, "raw", null, null, SourceStatus.INDEXED, 1, text.length.toLong())
            }
            refreshTick++
        }
    }

    Column(
        modifier = Modifier
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
            Text("SOURCES", color = OpText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                "ADD",
                color = OpRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showAdd = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (running) "UPDATING\u2026" else "REFRESH",
                color = OpTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !running, onClick = ::refreshAll)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search indexed knowledge\u2026", color = OpTextSecondary) },
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
        if (statusLine.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(statusLine, color = OpTextSecondary, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))

        if (query.isNotBlank()) {
            Text("KNOWLEDGE HITS", color = OpTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (hits.isEmpty()) {
                Text(
                    "No matches found \u2014 nothing indexed yet. Tap REFRESH or ADD to index sources.",
                    color = OpTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                LazyColumn {
                    items(hits) { hit ->
                        SourceHitRow(hit, onClick = { openHit(hit) })
                    }
                }
            }
        } else {
            val rows: List<Any> = collections.flatMap { col ->
                val members = sources.filter { it.collectionId == col.id }
                if (members.isEmpty()) emptyList() else listOf<Any>(col.name) + members
            }
            LazyColumn {
                items(rows) { row ->
                    when (row) {
                        is String -> Text(
                            row.uppercase(),
                            color = OpTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                        )
                        is Source -> SourceRow(
                            row, running,
                            onRefresh = { id -> refreshOne(id) },
                            onView = { openSource(row, null) },
                            onDelete = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { registry.removeSource(row.id) }
                                    refreshTick++
                                }
                            },
                        )
                    }
                }
                if (sources.isEmpty()) {
                    item {
                        Text(
                            "No sources yet. Tap ADD to index a repo, page, text or file.",
                            color = OpTextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
        }
    }

    viewerSource?.let { s ->
        SourceViewerDialog(
            source = s,
            db = db,
            initialFileId = viewerFileId,
            onDismiss = { viewerSource = null; viewerFileId = null },
        )
    }

    if (showAdd) {
        AddSourceDialog(
            onDismiss = { showAdd = false },
            onAddGithub = { title, owner, repo, collection ->
                scope.launch {
                    val colId = withContext(Dispatchers.IO) { db.upsertSourceCollection(collection) }
                    val id = withContext(Dispatchers.IO) {
                        registry.addSource(
                            Source(0, colId, title, SourceType.GITHUB_REPO, owner = owner, repo = repo),
                        )
                    }
                    refreshOne(id)
                    showAdd = false
                }
            },
            onAddUrl = { title, url, collection ->
                scope.launch {
                    val colId = withContext(Dispatchers.IO) { db.upsertSourceCollection(collection) }
                    val id = withContext(Dispatchers.IO) {
                        registry.addSource(
                            Source(0, colId, title, SourceType.WEB_PAGE, contentUrl = url),
                        )
                    }
                    refreshOne(id)
                    showAdd = false
                }
            },
            onAddSite = { title, url, collection ->
                scope.launch {
                    val colId = withContext(Dispatchers.IO) { db.upsertSourceCollection(collection) }
                    val id = withContext(Dispatchers.IO) {
                        registry.addSource(
                            Source(0, colId, title, SourceType.SITE, contentUrl = url),
                        )
                    }
                    refreshOne(id)
                    showAdd = false
                }
            },
            onAddText = { title, text, collection ->
                addRawText(title, text, collection)
                showAdd = false
            },
            onAddLocal = { title, path, collection ->
                scope.launch {
                    val colId = withContext(Dispatchers.IO) { db.upsertSourceCollection(collection) }
                    val id = withContext(Dispatchers.IO) {
                        registry.addSource(
                            Source(0, colId, title, SourceType.LOCAL_FILE, contentUrl = path),
                        )
                    }
                    refreshOne(id)
                    showAdd = false
                }
            },
            onAddDocument = { title, urlOrPath, collection ->
                scope.launch {
                    val colId = withContext(Dispatchers.IO) { db.upsertSourceCollection(collection) }
                    val id = withContext(Dispatchers.IO) {
                        registry.addSource(
                            Source(0, colId, title, SourceType.DOCUMENT, contentUrl = urlOrPath),
                        )
                    }
                    refreshOne(id)
                    showAdd = false
                }
            },
        )
    }
}

@Composable
private fun SourceRow(
    s: Source,
    running: Boolean,
    onRefresh: (Long) -> Unit,
    onView: () -> Unit,
    onDelete: () -> Unit,
) {
    val statusColor = when (s.status) {
        SourceStatus.INDEXED -> OpSuccess
        SourceStatus.ERROR -> OpRed
        SourceStatus.STALE, SourceStatus.NEW -> OpAmber
    }
    Surface(
        color = OpCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, OpDivider),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.title, color = OpText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(s.status.name, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                typeLine(s) + " \u00b7 " + (if (s.lastUpdated > 0) "updated ${ago(s.lastUpdated)}" else "not indexed yet"),
                color = OpTextSecondary,
                fontSize = 11.sp,
            )
            Text(
                "${s.fileCount} files \u00b7 refresh every ${s.updateAfterHours}h \u00b7 cap ${SourceCapabilities.EVICT_KEEP} sources",
                color = OpTextSecondary,
                fontSize = 11.sp,
            )
            if (s.status == SourceStatus.ERROR && s.error != null) {
                Spacer(Modifier.height(4.dp))
                Text(s.error, color = OpRed, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(
                    onClick = onView,
                    border = BorderStroke(1.dp, OpDivider),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Text("View", color = OpStatusInfo, fontSize = 11.sp)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { onRefresh(s.id) },
                    enabled = !running,
                    border = BorderStroke(1.dp, OpDivider),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Text("Refresh", color = OpTextSecondary, fontSize = 11.sp)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onDelete,
                    border = BorderStroke(1.dp, OpDivider),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Text("Delete", color = OpRed, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun SourceHitRow(hit: SourceSearchHit, onClick: () -> Unit) {
    Surface(
        color = OpCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, OpDivider),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "${hit.sourceTitle} / ${hit.filePath}",
                color = OpText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(hit.content, color = OpTextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text("tap to open the source", color = OpStatusInfo, fontSize = 9.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddSourceDialog(
    onDismiss: () -> Unit,
    onAddGithub: (String, String, String, String) -> Unit,
    onAddUrl: (String, String, String) -> Unit,
    onAddSite: (String, String, String) -> Unit,
    onAddText: (String, String, String) -> Unit,
    onAddLocal: (String, String, String) -> Unit,
    onAddDocument: (String, String, String) -> Unit,
) {
    var kind by remember { mutableStateOf(SourceType.GITHUB_REPO) }
    var title by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var collection by remember { mutableStateOf("General") }

    fun canSubmit(): Boolean = when (kind) {
        SourceType.GITHUB_REPO -> title.isNotBlank() && owner.isNotBlank() && repo.isNotBlank()
        SourceType.WEB_PAGE -> title.isNotBlank() && url.startsWith("http")
        SourceType.RAW_TEXT -> title.isNotBlank() && text.isNotBlank()
        SourceType.LOCAL_FILE -> title.isNotBlank() && path.isNotBlank()
        SourceType.DOCUMENT -> title.isNotBlank() && url.isNotBlank()
        SourceType.SITE -> title.isNotBlank() && url.startsWith("http")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OpCard,
        title = { Text("ADD SOURCE", color = OpText, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                FlowRow {
                    listOf(SourceType.GITHUB_REPO, SourceType.WEB_PAGE, SourceType.SITE, SourceType.RAW_TEXT, SourceType.LOCAL_FILE, SourceType.DOCUMENT)
                        .forEach { t ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { kind = t }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                            ) {
                                RadioButton(
                                    selected = kind == t,
                                    onClick = { kind = t },
                                    colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = OpRed),
                                )
                                Text(t.name.replace("_", " "), color = OpTextSecondary, fontSize = 10.sp)
                            }
                        }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title", color = OpTextSecondary) },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                when (kind) {
                    SourceType.GITHUB_REPO -> {
                        OutlinedTextField(value = owner, onValueChange = { owner = it }, label = { Text("Owner (github user/org)", color = OpTextSecondary) }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = repo, onValueChange = { repo = it }, label = { Text("Repo", color = OpTextSecondary) }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                    }
                    SourceType.WEB_PAGE -> {
                        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("https://…", color = OpTextSecondary) }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                    }
                    SourceType.SITE -> {
                        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("https://\u2026/sitemap.xml", color = OpTextSecondary) }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text("Site ingestion: one source = every page in the sitemap, refreshed on its cadence (FMHY-style external knowledge).", color = OpTextSecondary, fontSize = 10.sp)
                    }
                    SourceType.RAW_TEXT -> {
                        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Paste content", color = OpTextSecondary) }, minLines = 3, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                    }
                    SourceType.LOCAL_FILE -> {
                        OutlinedTextField(value = path, onValueChange = { path = it }, label = { Text("/sdcard/… absolute path", color = OpTextSecondary) }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                    }
                    SourceType.DOCUMENT -> {
                        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("https://…pdf or /sdcard/…path", color = OpTextSecondary) }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text("PDF text extraction needs Termux + pdftotext (termux install poppler); otherwise the source stays metadata-only.", color = OpTextSecondary, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = collection, onValueChange = { collection = it }, label = { Text("Collection", color = OpTextSecondary) }, singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(
                    "GitHub uses the public API (60 req/h anonymous). Add source = index once, then auto-refresh on its cadence.",
                    color = OpTextSecondary,
                    fontSize = 10.sp,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (kind) {
                        SourceType.GITHUB_REPO -> onAddGithub(title.trim(), owner.trim(), repo.trim(), collection.trim())
                        SourceType.WEB_PAGE -> onAddUrl(title.trim(), url.trim(), collection.trim())
                        SourceType.RAW_TEXT -> onAddText(title.trim(), text, collection.trim())
                        SourceType.LOCAL_FILE -> onAddLocal(title.trim(), path.trim(), collection.trim())
                        SourceType.DOCUMENT -> onAddDocument(title.trim(), url.trim(), collection.trim())
                        SourceType.SITE -> onAddSite(title.trim(), url.trim(), collection.trim())
                    }
                },
                enabled = canSubmit(),
                colors = ButtonDefaults.buttonColors(containerColor = OpRed),
            ) {
                Text("Add", color = Color.White)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OpTextSecondary) } },
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = OpRed,
    unfocusedBorderColor = OpBorder,
    focusedContainerColor = OpBg,
    unfocusedContainerColor = OpBg,
    focusedTextColor = OpText,
    unfocusedTextColor = OpText,
)

private fun typeLine(s: Source): String = when (s.type) {
    SourceType.GITHUB_REPO -> "GITHUB \u00b7 ${s.owner}/${s.repo}"
    SourceType.WEB_PAGE -> "WEB \u00b7 ${s.contentUrl ?: "?"}"
    SourceType.RAW_TEXT -> "TEXT"
    SourceType.LOCAL_FILE -> "LOCAL \u00b7 ${s.contentUrl ?: "?"}"
    SourceType.DOCUMENT -> "DOCUMENT \u00b7 ${s.contentUrl ?: "?"}"
    SourceType.SITE -> "SITE \u00b7 ${s.contentUrl ?: "?"}"
}

private fun ago(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    val mins = diff / 60000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 1440 -> "${mins / 60}h ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
    }
}


/** P5: read the indexed chunks of a source locally. Files are expandable,
 *  chunked reads are capped with "show more", and the open file can be
 *  copied — sources are readable knowledge, not opaque metadata. */
@Composable
private fun SourceViewerDialog(
    source: Source,
    db: MemoryDatabase,
    initialFileId: Long?,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var files by remember(source.id) { mutableStateOf<List<SourceFile>>(emptyList()) }
    var expandedFile by remember(source.id) { mutableStateOf(initialFileId) }
    var chunks by remember { mutableStateOf<List<SourceChunk>>(emptyList()) }
    var chunkPage by remember { mutableStateOf(10) }
    var copyStatus by remember { mutableStateOf("") }

    LaunchedEffect(source.id) {
        withContext(Dispatchers.IO) { files = db.sourceFiles(source.id) }
    }
    LaunchedEffect(expandedFile) {
        chunks = emptyList()
        chunkPage = 10
        if (expandedFile != null) {
            withContext(Dispatchers.IO) { chunks = db.chunksForFile(expandedFile!!) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OpBg,
        title = {
            Column {
                Text(source.title, color = OpText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(typeLine(source), color = OpTextSecondary, fontSize = 10.sp)
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (files.isEmpty()) {
                    Text(
                        "No indexed files yet \u2014 refresh the source first.",
                        color = OpTextSecondary,
                        fontSize = 12.sp,
                    )
                } else {
                    files.forEach { f ->
                        Surface(
                            color = OpCard,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, OpDivider),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        f.path,
                                        color = OpText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(
                                        onClick = {
                                            expandedFile = if (expandedFile == f.id) null else f.id
                                        },
                                    ) {
                                        Text(
                                            if (expandedFile == f.id) "hide" else "read",
                                            color = OpStatusInfo,
                                            fontSize = 10.sp,
                                        )
                                    }
                                }
                                if (expandedFile == f.id) {
                                    if (chunks.isEmpty()) {
                                        Text(
                                            "No chunks for this file.",
                                            color = OpTextSecondary,
                                            fontSize = 11.sp,
                                        )
                                    } else {
                                        Text(
                                            chunks.take(chunkPage).joinToString("\n") { it.content },
                                            color = OpTextSecondary,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                        Row {
                                            if (chunks.size > chunkPage) {
                                                TextButton(onClick = { chunkPage += 10 }) {
                                                    Text(
                                                        "show ${chunks.size - chunkPage} more chunks",
                                                        color = OpStatusInfo,
                                                        fontSize = 10.sp,
                                                    )
                                                }
                                            }
                                            TextButton(
                                                onClick = {
                                                    clipboard.setText(
                                                        AnnotatedString(chunks.joinToString("\n") { it.content }),
                                                    )
                                                    copyStatus = "copied ${chunks.size} chunks"
                                                },
                                            ) {
                                                Text("copy all", color = OpStatusInfo, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (copyStatus.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(copyStatus, color = OpTextSecondary, fontSize = 10.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = OpTextSecondary) }
        },
    )
}
