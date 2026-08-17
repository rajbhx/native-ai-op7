package com.engine.nativeai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engine.nativeai.ui.EngineScreen
import com.engine.nativeai.ui.MemoryScreen
import com.engine.nativeai.ui.SourcesScreen
import com.engine.nativeai.ui.OxygenOSTheme
import com.engine.nativeai.ui.OpCard
import com.engine.nativeai.ui.OpText
import com.engine.nativeai.ui.OpTextSecondary
import com.engine.nativeai.ui.OpRed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment


/**
 * Root activity with bottom tab navigation.
 * Golden standard: all features one tap away, no hidden settings.
 */
class MainActivity : ComponentActivity() {
    private val engine = NativeEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialPrompt = intent?.getStringExtra("prompt")
        val providerRegistry = ProviderRegistry()
        val prefs = ModelPreferences(this)
        val localLibrary = LocalModelLibrary(StoragePaths.modelsDir(this, prefs))
        providerRegistry.setBaseUrl(ModelCatalog.ZEN_PROVIDER, prefs.zenBaseUrl)
        val registry = ModelRegistry(StoragePaths.catalogFile(this, prefs)).apply {
            localLibrary.syncInto(
                this,
                engine,
                applicationInfo.nativeLibraryDir,
            )
            ModelCatalog.freeRemoteSeeds().forEach { addDescriptor(it) }
            loadCatalog()
        }
        RemoteProviderBootstrap.ensurePersistedSelection(
            registry,
            providerRegistry,
            prefs.lastSelectedModelId,
        )
        val discovery = ModelDiscoveryService(registry, providerRegistry)
        setContent {
            OxygenOSTheme {
                var selectedTab by rememberSaveable { mutableStateOf(0) }

                Scaffold(
                    containerColor = Color(0xFF121212),
                    bottomBar = {
                        GoldenBottomBar(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                        )
                    },
                ) { padding ->
                    when (selectedTab) {
                        0 -> EngineScreen(
                            engine = engine,
                            registry = registry,
                            providerRegistry = providerRegistry,
                            prefs = prefs,
                            discovery = discovery,
                            localLibrary = localLibrary,
                            initialPrompt = initialPrompt,
                            modifier = Modifier.padding(padding),
                        )
                        1 -> MemoryScreen(
                            onBack = { selectedTab = 0 },
                            prefs = prefs,
                            modifier = Modifier.padding(padding),
                        )
                        2 -> SourcesScreen(
                            onBack = { selectedTab = 0 },
                            prefs = prefs,
                            modifier = Modifier.padding(padding),
                        )
                        3 -> ErrorLogTab(
                            modifier = Modifier.padding(padding),
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        engine.close()
        super.onDestroy()
    }
}

@Composable
private fun GoldenBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    val tabs = listOf("ENGINE", "MEMORY", "SOURCES", "ERRORS")
    val icons = listOf("⚡", "🧠", "📚", "⚠")
    NavigationBar(
        containerColor = OpCard,
        contentColor = OpText,
        tonalElevation = 0.dp,
    ) {
        tabs.forEachIndexed { i, label ->
            NavigationBarItem(
                selected = selectedTab == i,
                onClick = { onTabSelected(i) },
                icon = {
                    Text(
                        icons[i],
                        fontSize = 16.sp,
                    )
                },
                label = {
                    Text(
                        label,
                        fontSize = 10.sp,
                        fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = OpRed,
                    selectedTextColor = OpRed,
                    unselectedIconColor = OpTextSecondary,
                    unselectedTextColor = OpTextSecondary,
                    indicatorColor = OpRed.copy(alpha = 0.12f),
                ),
            )
        }
    }
}

@Composable
private fun ErrorLogTab(modifier: Modifier = Modifier) {
    val entries = CoreErrors.log.all()
    Column(
        modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ERRORS", color = OpText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            if (entries.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = OpRed.copy(alpha = 0.15f),
                ) {
                    Text(
                        "${entries.size}",
                        color = OpRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (entries.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                color = OpCard,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("✓", fontSize = 32.sp, color = Color(0xFF2ECC71))
                    Spacer(Modifier.height(8.dp))
                    Text("No errors", color = OpText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Everything is running clean.",
                        color = OpTextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                items(entries) { entry ->
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(10.dp),
                        color = OpCard,
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = OpRed.copy(alpha = 0.2f),
                                ) {
                                    Text(
                                        entry.source,
                                        color = OpRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                        .format(java.util.Date(entry.atMs)),
                                    color = OpTextSecondary,
                                    fontSize = 10.sp,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                entry.message,
                                color = OpText,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                            )
                            if (!entry.detail.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    entry.detail,
                                    color = OpTextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    lineHeight = 14.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
