package com.puito.cryptoapp.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.puito.cryptoapp.data.binance.BinanceClient
import com.puito.cryptoapp.data.model.*
import com.puito.cryptoapp.domain.sim.EventSim
import com.puito.cryptoapp.domain.strategy.StrategyEngine
import java.util.UUID

class AppRepository(context: Context) {
    private val prefs = context.getSharedPreferences("crypto_app", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val binance = BinanceClient()
    private val engine = StrategyEngine()
    private val sim = EventSim()

    var candles: List<Candle> = emptyList()
        private set
    var signals: List<SignalMark> = emptyList()
        private set
    var trades: List<SimTrade> = emptyList()
        private set
    var stats: BacktestStats = BacktestStats()
        private set
    var strategyRunning: Boolean = false
        private set

    fun loadSettings(): AppSettings {
        val json = prefs.getString("settings", null) ?: return AppSettings()
        return runCatching { gson.fromJson(json, AppSettings::class.java) }.getOrDefault(AppSettings())
    }

    fun saveSettings(s: AppSettings) {
        prefs.edit().putString("settings", gson.toJson(s)).apply()
        binance.updateBaseUrl(s.binanceBaseUrl)
    }

    fun loadConfigs(): List<StrategyConfig> {
        val json = prefs.getString("configs", null) ?: return defaultConfigs()
        val type = object : TypeToken<List<StrategyConfig>>() {}.type
        return runCatching { gson.fromJson<List<StrategyConfig>>(json, type) }.getOrElse { defaultConfigs() }
    }

    fun saveConfigs(list: List<StrategyConfig>) {
        prefs.edit().putString("configs", gson.toJson(list)).apply()
    }

    fun enabledConfig(): StrategyConfig? = loadConfigs().find { it.enabled }

    private fun defaultConfigs(): List<StrategyConfig> {
        val c = StrategyConfig(
            id = UUID.randomUUID().toString(),
            title = "RSI 超买超卖",
            enabled = true,
            buyRules = listOf(Rule(IndicatorType.RSI, CompareOp.LT, 30.0)),
            sellRules = listOf(Rule(IndicatorType.RSI, CompareOp.GT, 70.0)),
        )
        saveConfigs(listOf(c))
        return listOf(c)
    }

    suspend fun refreshCandles(symbol: String, interval: Interval, limit: Int): List<Candle> {
        val settings = loadSettings()
        binance.updateBaseUrl(settings.binanceBaseUrl)
        candles = binance.fetchKlines(symbol, interval, limit)
        return candles
    }

    fun startStrategy(symbol: String, interval: Interval): Result<Unit> {
        val cfg = enabledConfig() ?: return Result.failure(IllegalStateException("请先启用一套配置"))
        if (candles.isEmpty()) return Result.failure(IllegalStateException("请先加载 K 线"))
        val marks = engine.generateSignals(candles, cfg)
        signals = marks
        val (t, st) = sim.backtest(candles, marks, symbol, interval.code, holdBars = 1)
        trades = t
        stats = st
        strategyRunning = true
        // 按 interval 隔离存储模拟数据
        prefs.edit()
            .putString("sim_${interval.code}", gson.toJson(t))
            .putString("stats_${interval.code}", gson.toJson(st))
            .putBoolean("running", true)
            .apply()
        return Result.success(Unit)
    }

    fun stopStrategy(interval: Interval) {
        strategyRunning = false
        signals = emptyList()
        trades = emptyList()
        stats = BacktestStats()
        prefs.edit()
            .remove("sim_${interval.code}")
            .remove("stats_${interval.code}")
            .putBoolean("running", false)
            .apply()
    }

    fun restoreSim(interval: Interval) {
        strategyRunning = prefs.getBoolean("running", false)
        if (!strategyRunning) return
        val tjson = prefs.getString("sim_${interval.code}", null)
        val sjson = prefs.getString("stats_${interval.code}", null)
        if (tjson != null) {
            val type = object : TypeToken<List<SimTrade>>() {}.type
            trades = runCatching { gson.fromJson<List<SimTrade>>(tjson, type) }.getOrDefault(emptyList())
        }
        if (sjson != null) {
            stats = runCatching { gson.fromJson(sjson, BacktestStats::class.java) }.getOrDefault(BacktestStats())
        }
    }
}
