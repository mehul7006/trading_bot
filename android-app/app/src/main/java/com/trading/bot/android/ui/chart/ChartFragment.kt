package com.trading.bot.android.ui.chart

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.CandleStickChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.tabs.TabLayout
import com.trading.bot.android.api.models.Candle
import com.trading.bot.android.api.models.CandleResponse
import com.trading.bot.android.api.models.Indicators
import com.trading.bot.android.api.models.Signal
import com.trading.bot.android.databinding.FragmentChartBinding
import com.trading.bot.android.viewmodel.TradingViewModel
import java.text.SimpleDateFormat
import java.util.*

class ChartFragment : Fragment() {

    private var _binding: FragmentChartBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TradingViewModel by activityViewModels()

    private val symbols = listOf("NIFTY50", "BANKNIFTY", "SENSEX")
    private var selectedSymbol = "NIFTY50"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCandleChart()
        setupVolumeChart()
        setupSymbolTabs()

        viewModel.chartData.observe(viewLifecycleOwner) { response ->
            binding.progressChart.visibility = View.GONE
            response?.let { updateCharts(it) }
        }

        viewModel.indicators.observe(viewLifecycleOwner) { ind ->
            ind?.let { updateIndicatorPanel(it) }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressChart.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.activeSignals.observe(viewLifecycleOwner) { signals ->
            // Refresh chart overlays with signal markers
            val response = viewModel.chartData.value
            response?.let { updateCharts(it, signals) }
        }

        // Initial load
        viewModel.refreshChartData(selectedSymbol)
    }

    private fun setupSymbolTabs() {
        symbols.forEach { sym ->
            binding.tabSymbols.addTab(binding.tabSymbols.newTab().setText(sym))
        }

        binding.tabSymbols.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectedSymbol = symbols[tab.position]
                binding.progressChart.visibility = View.VISIBLE
                viewModel.refreshChartData(selectedSymbol)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                viewModel.refreshChartData(selectedSymbol)
            }
        })
    }

    private fun setupCandleChart() {
        binding.candleChart.apply {
            setBackgroundColor(Color.parseColor("#161B22"))
            setNoDataText("Loading chart data...")
            setNoDataTextColor(Color.parseColor("#8B949E"))
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setPinchZoom(true)
            isDoubleTapToZoomEnabled = true
            setMaxVisibleValueCount(100)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = Color.parseColor("#8B949E")
                gridColor = Color.parseColor("#21262D")
                axisLineColor = Color.parseColor("#30363D")
                labelCount = 6
                setDrawGridLines(true)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }
            }

            axisLeft.apply {
                textColor = Color.parseColor("#8B949E")
                gridColor = Color.parseColor("#21262D")
                axisLineColor = Color.parseColor("#30363D")
                setDrawGridLines(true)
            }

            axisRight.isEnabled = false
        }
    }

    private fun setupVolumeChart() {
        binding.volumeChart.apply {
            setBackgroundColor(Color.parseColor("#161B22"))
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setScaleEnabled(false)
            isDragEnabled = false

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = Color.parseColor("#8B949E")
                setDrawGridLines(false)
                labelCount = 0
                setDrawLabels(false)
            }

            axisLeft.apply {
                textColor = Color.parseColor("#8B949E")
                setDrawGridLines(false)
                labelCount = 3
            }

            axisRight.isEnabled = false
        }
    }

    private fun updateCharts(response: CandleResponse, signals: List<Signal> = emptyList()) {
        if (response.candles.isEmpty()) return

        val candles = response.candles
        val signal = signals.firstOrNull { it.symbol == response.symbol }

        // --- Candlestick data ---
        val candleEntries = candles.mapIndexed { idx, candle ->
            CandleEntry(
                idx.toFloat(),
                candle.high.toFloat(),
                candle.low.toFloat(),
                candle.open.toFloat(),
                candle.close.toFloat()
            )
        }

        val candleDataSet = CandleDataSet(candleEntries, "Price").apply {
            decreasingColor = Color.parseColor("#FF4444")
            decreasingPaintStyle = android.graphics.Paint.Style.FILL
            increasingColor = Color.parseColor("#00D964")
            increasingPaintStyle = android.graphics.Paint.Style.FILL
            shadowColor = Color.parseColor("#8B949E")
            shadowWidth = 0.7f
            setDrawValues(false)
        }

        // --- VWAP Line ---
        val indicators = viewModel.indicators.value
        val vwapEntries = if (indicators != null && indicators.vwap > 0) {
            candles.mapIndexed { idx, _ ->
                Entry(idx.toFloat(), indicators.vwap.toFloat())
            }
        } else emptyList()

        val vwapDataSet = LineDataSet(vwapEntries, "VWAP").apply {
            color = Color.parseColor("#E3B341")
            lineWidth = 1.5f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR
        }

        // --- EMA20 Line ---
        val ema20 = indicators?.ema20 ?: 0.0
        val ema20Entries = if (ema20 > 0) {
            candles.mapIndexed { idx, _ ->
                Entry(idx.toFloat(), ema20.toFloat())
            }
        } else emptyList()

        val ema20DataSet = LineDataSet(ema20Entries, "EMA20").apply {
            color = Color.parseColor("#1F6FEB")
            lineWidth = 1.5f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR
        }

        val combinedData = CombinedData()
        combinedData.setData(CandleData(candleDataSet))

        val lineSets = mutableListOf<ILineDataSet>()
        if (vwapEntries.isNotEmpty()) lineSets.add(vwapDataSet)
        if (ema20Entries.isNotEmpty()) lineSets.add(ema20DataSet)
        if (lineSets.isNotEmpty()) combinedData.setData(LineData(lineSets))

        binding.candleChart.apply {
            data = combinedData
            invalidate()
            // Scroll to latest
            moveViewToX((candles.size - 20).toFloat().coerceAtLeast(0f))
        }

        // --- Volume chart ---
        val volEntries = candles.mapIndexed { idx, candle ->
            val isUp = candle.close >= candle.open
            BarEntry(idx.toFloat(), candle.volume.toFloat())
        }

        val volColors = candles.map { candle ->
            if (candle.close >= candle.open) Color.parseColor("#00D96466")
            else Color.parseColor("#FF444466")
        }

        val volDataSet = BarDataSet(volEntries, "Volume").apply {
            colors = volColors
            setDrawValues(false)
        }

        binding.volumeChart.apply {
            data = BarData(volDataSet)
            invalidate()
        }
    }

    private fun updateIndicatorPanel(ind: Indicators) {
        binding.tvRsi.text = "RSI: ${String.format("%.1f", ind.rsi)}"
        binding.tvRsi.setTextColor(
            when {
                ind.rsi > 70 -> Color.parseColor("#FF4444")
                ind.rsi < 30 -> Color.parseColor("#00D964")
                else -> Color.parseColor("#8B949E")
            }
        )
        binding.tvAdx.text = "ADX: ${String.format("%.1f", ind.adx)}"
        binding.tvMacd.text = "MACD: ${String.format("%.2f", ind.macd)}"
        binding.tvVwap.text = "VWAP: ${String.format("%,.2f", ind.vwap)}"
        binding.tvEma20.text = "EMA20: ${String.format("%,.2f", ind.ema20)}"
        binding.tvAtr.text = "ATR: ${String.format("%.2f", ind.atr)}"
        binding.tvSignal.text = ind.overallSignal
        binding.tvSignal.setTextColor(
            when (ind.overallSignal.uppercase()) {
                "BULLISH", "STRONG_BULLISH" -> Color.parseColor("#00D964")
                "BEARISH", "STRONG_BEARISH" -> Color.parseColor("#FF4444")
                else -> Color.parseColor("#8B949E")
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
