package com.puito.cryptoapp.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puito.cryptoapp.data.AppRepository
import com.puito.cryptoapp.data.model.Interval
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repo: AppRepository,
    onConfig: () -> Unit,
    onSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settings = remember { repo.loadSettings() }
    var symbol by remember { mutableStateOf(settings.defaultSymbol) }
    var interval by remember { mutableStateOf(Interval.fromCode(settings.defaultInterval)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(repo.strategyRunning) }
    var tick by remember { mutableIntStateOf(0) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            try {
                repo.refreshCandles(symbol, interval, repo.loadSettings().defaultLimit)
                repo.restoreSim(interval)
                running = repo.strategyRunning
                tick++
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(symbol, interval) { reload() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SymbolPicker(symbol) { symbol = it }
            IntervalPicker(interval) { interval = it }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onConfig) { Text("配置") }
            TextButton(onClick = onSettings) { Text("设置") }
        }

        // Strategy bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (running) "策略：运行中" else "策略：已停止",
                color = if (running) Color(0xFF0ECB81) else MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.width(12.dp))
            if (!running) {
                Button(onClick = {
                    repo.startStrategy(symbol, interval).onSuccess {
                        running = true
                        tick++
                    }.onFailure { error = it.message }
                }) { Text("启动策略") }
            } else {
                OutlinedButton(onClick = {
                    repo.stopStrategy(interval)
                    running = false
                    tick++
                }) { Text("停止并清除模拟") }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { reload() }, enabled = !loading) {
                Text(if (loading) "加载中…" else "刷新")
            }
        }

        error?.let {
            Text(it, color = Color(0xFFF6465D), modifier = Modifier.padding(12.dp))
        }

        // Chart
        key(tick, symbol, interval) {
            CandleChart(
                candles = repo.candles,
                signals = if (running) repo.signals else emptyList(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(8.dp),
            )
        }

        // Stats
        val st = repo.stats
        Text(
            "统计：成交 ${st.signals} | 胜 ${st.wins} | 负 ${st.losses} | 胜率 ${"%.1f".format(st.winRate * 100)}% | 收益 ${"%.2f".format(st.totalReturnPct)}%",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            fontSize = 13.sp,
        )

        Text(
            "交易历史（${interval.code}，最近 5 条）",
            modifier = Modifier.padding(horizontal = 12.dp),
            fontWeight = FontWeight.SemiBold,
        )
        val rows = repo.trades.takeLast(5).reversed()
        LazyColumn(Modifier.padding(12.dp)) {
            items(rows, key = { it.id }) { t ->
                val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(fmt.format(Date(t.entryTime)), Modifier.weight(1.2f), fontSize = 12.sp)
                    Text(t.side, Modifier.weight(0.4f), color = if (t.side == "B") Color(0xFF0ECB81) else Color(0xFFF6465D))
                    Text("%.1f".format(t.entryPrice), Modifier.weight(0.9f), fontSize = 12.sp)
                    Text("%.1f".format(t.exitPrice), Modifier.weight(0.9f), fontSize = 12.sp)
                    Text(
                        if (t.win) "盈" else "亏",
                        Modifier.weight(0.4f),
                        color = if (t.win) Color(0xFF0ECB81) else Color(0xFFF6465D),
                        fontSize = 12.sp,
                    )
                }
                HorizontalDivider()
            }
            if (rows.isEmpty()) {
                item { Text("暂无模拟成交（启动策略后生成）", color = MaterialTheme.colorScheme.secondary) }
            }
        }
    }
}

@Composable
private fun SymbolPicker(value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("BTCUSDT", "ETHUSDT")
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(value.replace("USDT", "/USDT")) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach {
                DropdownMenuItem(text = { Text(it) }, onClick = { onChange(it); expanded = false })
            }
        }
    }
}

@Composable
private fun IntervalPicker(value: Interval, onChange: (Interval) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(value.code) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            Interval.entries.forEach {
                DropdownMenuItem(text = { Text(it.code) }, onClick = { onChange(it); expanded = false })
            }
        }
    }
}

@Composable
fun CandleChart(
    candles: List<com.puito.cryptoapp.data.model.Candle>,
    signals: List<com.puito.cryptoapp.data.model.SignalMark>,
    modifier: Modifier = Modifier,
) {
    val bull = Color(0xFF0ECB81)
    val bear = Color(0xFFF6465D)
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF12161C))) {
        if (candles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无 K 线", color = Color.Gray)
            }
        } else {
            Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                val data = candles.takeLast(80)
                val maxH = data.maxOf { it.high }
                val minL = data.minOf { it.low }
                val range = (maxH - minL).coerceAtLeast(1e-6)
                val w = size.width / data.size
                data.forEachIndexed { i, c ->
                    val x = i * w + w / 2
                    val yHigh = ((maxH - c.high) / range * size.height).toFloat()
                    val yLow = ((maxH - c.low) / range * size.height).toFloat()
                    val yOpen = ((maxH - c.open) / range * size.height).toFloat()
                    val yClose = ((maxH - c.close) / range * size.height).toFloat()
                    val col = if (c.close >= c.open) bull else bear
                    drawLine(col, Offset(x, yHigh), Offset(x, yLow), strokeWidth = 2f)
                    val top = minOf(yOpen, yClose)
                    val bot = maxOf(yOpen, yClose)
                    drawRect(col, Offset(x - w * 0.3f, top), androidx.compose.ui.geometry.Size(w * 0.6f, (bot - top).coerceAtLeast(2f)))
                }
                val sigMap = signals.associateBy { it.openTime }
                data.forEachIndexed { i, c ->
                    val s = sigMap[c.openTime] ?: return@forEachIndexed
                    val x = i * w + w / 2
                    val y = ((maxH - s.price) / range * size.height).toFloat()
                    val col = if (s.side == "B") bull else bear
                    // triangle mark
                    val path = Path().apply {
                        if (s.side == "B") {
                            moveTo(x, y + 12f)
                            lineTo(x - 8f, y + 28f)
                            lineTo(x + 8f, y + 28f)
                        } else {
                            moveTo(x, y - 12f)
                            lineTo(x - 8f, y - 28f)
                            lineTo(x + 8f, y - 28f)
                        }
                        close()
                    }
                    drawPath(path, col)
                }
            }
        }
    }
}
