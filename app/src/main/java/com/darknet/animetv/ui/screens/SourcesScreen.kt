package com.darknet.animetv.ui.screens

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.darknet.animetv.data.model.CustomSource
import com.darknet.animetv.data.model.PingRating
import com.darknet.animetv.data.model.StreamBenchmarkResult
import com.darknet.animetv.data.repository.AnimeRepository
import com.darknet.animetv.data.source.SourceHealthResult
import com.darknet.animetv.data.storage.AppStorage
import com.darknet.animetv.ui.components.tvFocusableItem
import com.darknet.animetv.ui.theme.TvAccentPurple
import com.darknet.animetv.ui.theme.TvCardBorder
import com.darknet.animetv.ui.theme.TvDarkBackground
import com.darknet.animetv.ui.theme.TvErrorRed
import com.darknet.animetv.ui.theme.TvGoldStar
import com.darknet.animetv.ui.theme.TvPrimaryRed
import com.darknet.animetv.ui.theme.TvSuccessGreen
import com.darknet.animetv.ui.theme.TvSurfaceColor
import com.darknet.animetv.ui.theme.TvSurfaceFocusedColor
import com.darknet.animetv.ui.theme.TvTextPrimary
import com.darknet.animetv.ui.theme.TvTextSecondary
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URI

/**
 * Android TV Sources Management Screen (Feature F15):
 * - View all active and installed scrapers & metadata sources
 * - Live latency benchmarking and health probing (AnimeRepository.testAllSources())
 * - Toggle enabled/disabled source states with SQLite persistence
 * - Source reordering & priority manipulation via Up/Down arrows or ▲/▼ buttons
 * - Declarative JSON DSL custom source creator with URL validation
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SourcesScreen(
    repository: AnimeRepository,
    storage: AppStorage,
    modifier: Modifier = Modifier,
    onNavigateUp: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var sourcesList by remember { mutableStateOf<List<CustomSource>>(emptyList()) }
    var healthResults by remember { mutableStateOf<Map<String, SourceHealthResult>>(emptyMap()) }
    var benchmarkResults by remember { mutableStateOf<Map<String, StreamBenchmarkResult>>(emptyMap()) }
    var isTestingHealth by remember { mutableStateOf(false) }
    var isBenchmarking by remember { mutableStateOf(false) }
    var benchmarkProgress by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var reorderingSourceId by remember { mutableStateOf<Long?>(null) }
    var pendingFocusTarget by remember { mutableStateOf<Pair<Long, String>?>(null) }

    fun refreshSources() {
        sourcesList = storage.getSources()
    }

    LaunchedEffect(Unit) {
        refreshSources()
    }

    fun runHealthCheck() {
        coroutineScope.launch {
            isTestingHealth = true
            try {
                healthResults = repository.testAllSources()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isTestingHealth = false
            }
        }
    }

    fun runBenchmark() {
        coroutineScope.launch {
            isBenchmarking = true
            benchmarkProgress = "Starting stream throughput benchmark..."
            try {
                val results = repository.benchmarkAllSources { partial ->
                    benchmarkResults = benchmarkResults + (partial.sourceId.lowercase() to partial)
                    benchmarkProgress = "Benchmarked ${partial.sourceName}: ${partial.throughputMbps} Mbps"
                }
                val map = results.associateBy { it.sourceId.lowercase() }
                benchmarkResults = map
                benchmarkProgress = "Completed benchmark across ${results.size} sources"
            } catch (e: Exception) {
                e.printStackTrace()
                benchmarkProgress = "Benchmark error: ${e.message}"
            } finally {
                isBenchmarking = false
            }
        }
    }

    fun autoRankSourcesBySpeed() {
        if (benchmarkResults.isEmpty()) return
        val ranked = sourcesList.sortedWith(
            compareByDescending<CustomSource> { src ->
                val bm = benchmarkResults[src.sourceType.lowercase()]
                    ?: benchmarkResults[src.name.lowercase()]
                    ?: benchmarkResults.values.firstOrNull { src.sourceType.contains(it.sourceId, ignoreCase = true) || src.name.contains(it.sourceName, ignoreCase = true) }
                bm?.isHealthy == true
            }.thenByDescending { src ->
                val bm = benchmarkResults[src.sourceType.lowercase()]
                    ?: benchmarkResults[src.name.lowercase()]
                    ?: benchmarkResults.values.firstOrNull { src.sourceType.contains(it.sourceId, ignoreCase = true) || src.name.contains(it.sourceName, ignoreCase = true) }
                bm?.throughputMbps ?: 0.0
            }.thenBy { src ->
                val bm = benchmarkResults[src.sourceType.lowercase()]
                    ?: benchmarkResults[src.name.lowercase()]
                    ?: benchmarkResults.values.firstOrNull { src.sourceType.contains(it.sourceId, ignoreCase = true) || src.name.contains(it.sourceName, ignoreCase = true) }
                bm?.pingMs ?: Long.MAX_VALUE
            }
        )
        sourcesList = ranked
        storage.saveSourcesOrder(ranked)
        benchmarkProgress = "⚡ Sources auto-ranked by throughput & ping!"
    }

    fun toggleSource(source: CustomSource) {
        val updated = source.copy(isEnabled = !source.isEnabled)
        storage.saveSource(updated)
        refreshSources()
    }

    fun deleteSource(sourceId: Long) {
        storage.deleteSource(sourceId)
        refreshSources()
    }

    fun moveSource(fromIndex: Int, toIndex: Int, focusKeyAfterMove: String? = null) {
        if (fromIndex in sourcesList.indices && toIndex in sourcesList.indices) {
            val mutable = sourcesList.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            sourcesList = mutable
            storage.saveSourcesOrder(mutable)
            if (focusKeyAfterMove != null) {
                pendingFocusTarget = item.id to focusKeyAfterMove
            }
            coroutineScope.launch {
                val targetScrollIndex = (toIndex - 1).coerceAtLeast(0)
                listState.animateScrollToItem(targetScrollIndex)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TvDarkBackground)
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        // Screen Header & Action Triggers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Streaming Sources & Scrapers (${sourcesList.size})",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (reorderingSourceId != null)
                        "⇅ REORDER MODE ACTIVE: Use Up / Down Arrows to move source priority, then press Enter to lock in"
                    else
                        "Press Enter on any source to reorder with ↑/↓ Arrows, or use the ▲ / ▼ buttons on the right",
                    color = if (reorderingSourceId != null) TvAccentPurple else TvTextSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (reorderingSourceId != null) FontWeight.Bold else FontWeight.Normal
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Test Latency Action
                SourceHeaderActionButton(
                    text = if (isTestingHealth) "Testing..." else "⚡ Test Latency",
                    isLoading = isTestingHealth,
                    containerColor = TvSurfaceColor,
                    onClick = { runHealthCheck() },
                    onNavigateUp = onNavigateUp
                )

                // Real-Time Speed & Throughput Benchmark Action
                SourceHeaderActionButton(
                    text = if (isBenchmarking) "Testing CDN..." else "🚀 Benchmark Speed",
                    isLoading = isBenchmarking,
                    containerColor = TvAccentPurple.copy(alpha = 0.85f),
                    onClick = { runBenchmark() },
                    onNavigateUp = onNavigateUp
                )

                // Auto-Rank Action
                if (benchmarkResults.isNotEmpty()) {
                    SourceHeaderActionButton(
                        text = "⚡ Auto-Rank by Speed",
                        isLoading = false,
                        containerColor = TvSuccessGreen,
                        onClick = { autoRankSourcesBySpeed() },
                        onNavigateUp = onNavigateUp
                    )
                }

                // Add Custom Source Action
                SourceHeaderActionButton(
                    text = "+ Add Custom Source",
                    isLoading = false,
                    containerColor = TvPrimaryRed,
                    onClick = { showAddDialog = true },
                    onNavigateUp = onNavigateUp
                )
            }
        }

        if (benchmarkProgress != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TvAccentPurple.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .border(1.dp, TvAccentPurple.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isBenchmarking) {
                        CircularProgressIndicator(
                            color = TvAccentPurple,
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(
                        text = benchmarkProgress ?: "",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sources List
        if (sourcesList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No sources configured in storage",
                    color = TvTextSecondary,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 36.dp)
            ) {
                itemsIndexed(sourcesList, key = { _, s -> s.id }) { index, source ->
                    val health = healthResults.entries.firstOrNull {
                        source.sourceType.equals(it.key, ignoreCase = true) ||
                            source.name.contains(it.key, ignoreCase = true) ||
                            it.key.contains(source.name, ignoreCase = true)
                    }?.value

                    val benchmark = benchmarkResults[source.sourceType.lowercase()]
                        ?: benchmarkResults[source.name.lowercase()]
                        ?: benchmarkResults.values.firstOrNull {
                            source.sourceType.contains(it.sourceId, ignoreCase = true) ||
                                source.name.contains(it.sourceName, ignoreCase = true) ||
                                it.sourceId.contains(source.sourceType, ignoreCase = true)
                        }

                    SourceItemCard(
                        rank = index + 1,
                        source = source,
                        health = health,
                        benchmark = benchmark,
                        isTesting = isTestingHealth || isBenchmarking,
                        isReordering = reorderingSourceId == source.id,
                        canMoveUp = index > 0,
                        canMoveDown = index < sourcesList.size - 1,
                        pendingFocusKey = if (pendingFocusTarget?.first == source.id) pendingFocusTarget?.second else null,
                        onClearPendingFocus = { pendingFocusTarget = null },
                        onToggleReorder = {
                            reorderingSourceId = if (reorderingSourceId == source.id) null else source.id
                        },
                        onToggle = { toggleSource(source) },
                        onMoveUp = { key -> moveSource(index, index - 1, key) },
                        onMoveDown = { key -> moveSource(index, index + 1, key) },
                        onDelete = { deleteSource(source.id) }
                    )
                }
            }
        }
    }

    // Custom Source Dialog
    if (showAddDialog) {
        CustomSourceAdderDialog(
            onDismiss = { showAddDialog = false },
            onSave = { newSource ->
                storage.saveSource(newSource)
                showAddDialog = false
                refreshSources()
            }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SourceHeaderActionButton(
    text: String,
    isLoading: Boolean,
    containerColor: Color,
    onClick: () -> Unit,
    onNavigateUp: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .tvFocusableItem(
                onClick = onClick,
                focusedScale = 1.08f,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = TvCardBorder,
                borderWidth = 3.dp,
                shape = RoundedCornerShape(12.dp),
                onFocusChanged = { isFocused = it }
            )
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP
                ) {
                    if (onNavigateUp != null) {
                        onNavigateUp()
                        true
                    } else false
                } else false
            }
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) TvAccentPurple else containerColor)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = if (isFocused) Color.Black else Color.White,
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Focus-interactive card row representing a single streaming or catalog source.
 * Uses flat, non-nested focusable pills so D-Pad Up/Down/Left/Right and mouse clicks work cleanly.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SourceItemCard(
    rank: Int = 1,
    source: CustomSource,
    health: SourceHealthResult?,
    benchmark: StreamBenchmarkResult? = null,
    isTesting: Boolean,
    isReordering: Boolean = false,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    pendingFocusKey: String? = null,
    onClearPendingFocus: () -> Unit = {},
    onToggleReorder: () -> Unit = {},
    onToggle: () -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isCardFocused by remember { mutableStateOf(false) }
    val cardFocusRequester = remember { FocusRequester() }
    val upFocusRequester = remember { FocusRequester() }
    val downFocusRequester = remember { FocusRequester() }

    LaunchedEffect(pendingFocusKey, rank) {
        if (pendingFocusKey != null) {
            delay(30)
            try {
                when (pendingFocusKey) {
                    "CARD" -> cardFocusRequester.requestFocus()
                    "UP" -> if (canMoveUp) upFocusRequester.requestFocus() else downFocusRequester.requestFocus()
                    "DOWN" -> if (canMoveDown) downFocusRequester.requestFocus() else upFocusRequester.requestFocus()
                }
            } catch (_: Exception) {}
            onClearPendingFocus()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    isReordering -> Color(0xFF1A2B3C)
                    isCardFocused -> TvSurfaceFocusedColor
                    else -> TvSurfaceColor
                }
            )
            .border(
                width = if (isReordering) 2.dp else 1.dp,
                color = if (isReordering) TvAccentPurple else TvCardBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Focusable Source Card Info (Enter toggles Reorder Mode; Up/Down moves when in Reorder Mode)
        Row(
            modifier = Modifier
                .weight(1f)
                .focusRequester(cardFocusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    if (isReordering) {
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && canMoveUp) {
                                    onMoveUp("CARD")
                                }
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && canMoveDown) {
                                    onMoveDown("CARD")
                                }
                                true
                            }
                            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    onToggleReorder()
                                }
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .tvFocusableItem(
                    onClick = onToggleReorder,
                    focusedScale = 1.02f,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.Transparent,
                    borderWidth = 3.dp,
                    shape = RoundedCornerShape(10.dp),
                    onFocusChanged = { isCardFocused = it }
                )
                .clip(RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Priority Rank Pill (#1, #2, ...)
            Box(
                modifier = Modifier
                    .background(
                        if (isReordering) TvAccentPurple else Color(0xFF252A3E),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isReordering) "⇅ #$rank" else "#$rank",
                    color = if (isReordering) Color.Black else TvAccentPurple,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Source Identity & URL
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.name,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                if (source.sourceType == "HTML_SCRAPER") TvAccentPurple.copy(alpha = 0.25f)
                                else Color(0xFF282C3F),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = source.sourceType,
                            color = if (source.sourceType == "HTML_SCRAPER") TvAccentPurple else TvTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (isReordering) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Use ↑/↓ Arrows • Enter to Drop",
                            color = TvAccentPurple,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = source.baseUrl,
                    color = TvTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
        }

        // Health, Ping Latency & Throughput Benchmark Badges
        Column(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (benchmark != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Ping pill (<200ms Green, <500ms Yellow, >500ms Red)
                    val pingColor = Color(benchmark.pingRating.colorHex)
                    Box(
                        modifier = Modifier
                            .background(pingColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .border(1.dp, pingColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${benchmark.pingMs} ms",
                            color = pingColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Throughput pill
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF23283E), RoundedCornerShape(6.dp))
                            .border(1.dp, TvGoldStar.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "⚡ ${benchmark.throughputMbps} Mbps",
                            color = TvGoldStar,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            } else {
                // Fallback Health & Latency Badge
                Box(
                    modifier = Modifier
                        .background(Color(0x33000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    val (statusText, statusColor) = when {
                        isTesting -> "Testing..." to TvTextSecondary
                        health == null -> "Ready" to TvTextSecondary
                        health.isAvailable -> "${health.latencyMs} ms" to TvSuccessGreen
                        else -> "Offline" to TvErrorRed
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "●",
                            color = if (health == null && !isTesting) TvAccentPurple else statusColor,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Priority Reordering & Toggle Controls (Single-level focusable pills)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SourceActionPill(
                text = "▲ Up",
                enabled = canMoveUp,
                containerColor = Color(0xFF202336),
                focusRequester = upFocusRequester,
                onClick = { if (canMoveUp) onMoveUp("UP") }
            )

            SourceActionPill(
                text = "▼ Down",
                enabled = canMoveDown,
                containerColor = Color(0xFF202336),
                focusRequester = downFocusRequester,
                onClick = { if (canMoveDown) onMoveDown("DOWN") }
            )

            // Toggle Enable / Disable
            SourceActionPill(
                text = if (source.isEnabled) "✓ Enabled" else "Disabled",
                enabled = true,
                containerColor = if (source.isEnabled) TvPrimaryRed else Color(0xFF33384F),
                onClick = onToggle
            )

            // Delete custom user source
            if (source.sourceType == "HTML_SCRAPER" || source.id > 10) {
                SourceActionPill(
                    text = "Delete",
                    enabled = true,
                    containerColor = Color(0xFF3B1E2B),
                    textColor = TvErrorRed,
                    onClick = onDelete
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SourceActionPill(
    text: String,
    enabled: Boolean,
    containerColor: Color,
    textColor: Color = Color.White,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val baseModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }

    Box(
        modifier = baseModifier
            .then(
                if (enabled) {
                    Modifier.tvFocusableItem(
                        onClick = onClick,
                        focusedScale = 1.08f,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = TvCardBorder,
                        borderWidth = 3.dp,
                        shape = RoundedCornerShape(8.dp),
                        onFocusChanged = { isFocused = it }
                    )
                } else {
                    Modifier.border(1.dp, Color(0xFF1E2233), RoundedCornerShape(8.dp))
                }
            )
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    !enabled -> Color(0xFF161925)
                    isFocused -> TvAccentPurple
                    else -> containerColor
                }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> Color(0xFF555B75)
                isFocused -> Color.Black
                else -> textColor
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Custom Source JSON DSL / Declarative creator dialog.
 */
@Composable
fun CustomSourceAdderDialog(
    onDismiss: () -> Unit,
    onSave: (CustomSource) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("https://") }
    var animeListSelector by remember { mutableStateOf("ul.ListAnimes a[href]") }
    var episodeSelector by remember { mutableStateOf("ul.EpisodeList a[href]") }
    var streamUrlSelector by remember { mutableStateOf("iframe, video") }
    var jsonDslInput by remember { mutableStateOf("") }
    var isJsonMode by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun validateAndSave() {
        errorMessage = null
        if (isJsonMode && jsonDslInput.isNotBlank()) {
            try {
                val parsed = Gson().fromJson(jsonDslInput, CustomSource::class.java)
                if (isValidSource(parsed)) {
                    onSave(parsed)
                } else {
                    errorMessage = "Invalid JSON DSL: requires valid non-blank name and http/https baseUrl"
                }
            } catch (e: Exception) {
                errorMessage = "Malformed JSON syntax: ${e.message}"
            }
        } else {
            val candidate = CustomSource(
                name = name.trim(),
                baseUrl = baseUrl.trim(),
                animeListSelector = animeListSelector.trim(),
                episodeSelector = episodeSelector.trim(),
                streamUrlSelector = streamUrlSelector.trim(),
                isEnabled = true,
                sourceType = "HTML_SCRAPER"
            )
            if (isValidSource(candidate)) {
                onSave(candidate)
            } else {
                errorMessage = "Validation failed: Name cannot be blank, and Base URL must be a valid http/https address"
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(580.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(TvDarkBackground)
                .border(2.dp, TvAccentPurple, RoundedCornerShape(16.dp))
                .padding(28.dp)
        ) {
            Column {
                Text(
                    text = "Add Custom Anime Source",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Configure declarative CSS selectors or paste JSON source schema",
                    color = TvTextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Mode toggle button
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { isJsonMode = false },
                        colors = ButtonDefaults.colors(
                            containerColor = if (!isJsonMode) TvAccentPurple else TvSurfaceColor
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
                    ) {
                        Text("Form Fields", fontSize = 13.sp, color = Color.White)
                    }
                    Button(
                        onClick = { isJsonMode = true },
                        colors = ButtonDefaults.colors(
                            containerColor = if (isJsonMode) TvAccentPurple else TvSurfaceColor
                        ),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
                    ) {
                        Text("JSON DSL Mode", fontSize = 13.sp, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isJsonMode) {
                    Text("Paste JSON DSL Payload:", color = Color.White, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(TvSurfaceColor, RoundedCornerShape(8.dp))
                            .border(1.dp, TvCardBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        BasicTextField(
                            value = jsonDslInput,
                            onValueChange = { jsonDslInput = it },
                            textStyle = TextStyle(color = TvTextPrimary, fontSize = 13.sp),
                            cursorBrush = SolidColor(TvPrimaryRed),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    // Form fields
                    DialogInputField(label = "Source Name", value = name, onValueChange = { name = it }, placeholder = "e.g. MyAnimeStream")
                    Spacer(modifier = Modifier.height(10.dp))
                    DialogInputField(label = "Base URL (http/https)", value = baseUrl, onValueChange = { baseUrl = it }, placeholder = "https://example.com")
                    Spacer(modifier = Modifier.height(10.dp))
                    DialogInputField(label = "Anime List CSS Selector", value = animeListSelector, onValueChange = { animeListSelector = it }, placeholder = "ul.list a")
                    Spacer(modifier = Modifier.height(10.dp))
                    DialogInputField(label = "Episode CSS Selector", value = episodeSelector, onValueChange = { episodeSelector = it }, placeholder = "ul.episodes a")
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = errorMessage ?: "", color = TvErrorRed, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Dialog Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.colors(containerColor = TvSurfaceColor),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
                    ) {
                        Text("Cancel", color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { validateAndSave() },
                        colors = ButtonDefaults.colors(containerColor = TvPrimaryRed),
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
                    ) {
                        Text("Save Source", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Column {
        Text(text = label, color = TvTextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(TvSurfaceColor, RoundedCornerShape(8.dp))
                .border(1.dp, TvCardBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(text = placeholder, color = Color(0xFF6B7280), fontSize = 13.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = TvTextPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(TvPrimaryRed),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Validates custom source parameter integrity per project contract.
 */
fun isValidSource(source: CustomSource): Boolean {
    return try {
        val uri = URI(source.baseUrl)
        source.name.isNotBlank() && (uri.scheme == "http" || uri.scheme == "https") && uri.host != null
    } catch (e: Exception) {
        false
    }
}
