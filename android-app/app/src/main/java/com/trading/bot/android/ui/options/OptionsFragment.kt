package com.trading.bot.android.ui.options

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.trading.bot.android.api.models.BotStatus
import com.trading.bot.android.api.models.Signal
import com.trading.bot.android.databinding.FragmentOptionsBinding
import com.trading.bot.android.viewmodel.TradingViewModel
import kotlin.math.abs

class OptionsFragment : Fragment() {

    private var _binding: FragmentOptionsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TradingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPnlChart()

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
            binding.swipeRefresh.isRefreshing = false
        }

        viewModel.botStatus.observe(viewLifecycleOwner) { status ->
            status?.let { updateStatsSection(it) }
        }

        viewModel.activeSignals.observe(viewLifecycleOwner) { signals ->
            updateSignalStats(signals)
            updatePcrCards(signals)
        }

        viewModel.signalHistory.observe(viewLifecycleOwner) { history ->
            updatePnlChart(history)
        }
    }

    private fun updateStatsSection(status: BotStatus) {
        binding.tvTotalCalls.text = status.todayCallsGenerated.toString()
        binding.tvWinRate.text = "${status.winRate}%"
        binding.tvWins.text = status.todayWins.toString()
        binding.tvLosses.text = status.todayLosses.toString()
        binding.tvNetPoints.text = "${(status.todayWins - status.todayLosses) * 50} pts (est)"

        val winRateColor = when {
            status.winRate >= 70 -> Color.parseColor("#00D964")
            status.winRate >= 50 -> Color.parseColor("#E3B341")
            else -> Color.parseColor("#FF4444")
        }
        binding.tvWinRate.setTextColor(winRateColor)
    }

    private fun updateSignalStats(signals: List<Signal>) {
        // Time slot breakdown
        val now = System.currentTimeMillis()
        val slot1 = signals.count { s ->
            val hour = java.util.Calendar.getInstance().apply { timeInMillis = s.createdAt }.get(java.util.Calendar.HOUR_OF_DAY)
            hour in 9..10
        }
        val slot2 = signals.count { s ->
            val hour = java.util.Calendar.getInstance().apply { timeInMillis = s.createdAt }.get(java.util.Calendar.HOUR_OF_DAY)
            hour in 11..12
        }
        val slot3 = signals.count { s ->
            val hour = java.util.Calendar.getInstance().apply { timeInMillis = s.createdAt }.get(java.util.Calendar.HOUR_OF_DAY)
            hour in 13..15
        }

        binding.tvSlot1.text = "09-11: $slot1 calls"
        binding.tvSlot2.text = "11-13: $slot2 calls"
        binding.tvSlot3.text = "13-15: $slot3 calls"
    }

    private fun updatePcrCards(signals: List<Signal>) {
        val symbols = listOf("NIFTY50", "BANKNIFTY", "SENSEX")
        val pcrs = signals.associateBy({ it.symbol }, { it.pcr })

        binding.tvNiftyPcr.text = "NIFTY PCR: ${String.format("%.2f", pcrs["NIFTY50"] ?: 1.0)}"
        binding.tvBankniftyPcr.text = "BANKNIFTY PCR: ${String.format("%.2f", pcrs["BANKNIFTY"] ?: 1.0)}"
        binding.tvSensexPcr.text = "SENSEX PCR: ${String.format("%.2f", pcrs["SENSEX"] ?: 1.0)}"

        // Color PCR by value
        listOf(
            Triple(binding.tvNiftyPcr, pcrs["NIFTY50"] ?: 1.0, "NIFTY50"),
            Triple(binding.tvBankniftyPcr, pcrs["BANKNIFTY"] ?: 1.0, "BANKNIFTY"),
            Triple(binding.tvSensexPcr, pcrs["SENSEX"] ?: 1.0, "SENSEX")
        ).forEach { (tv, pcr, _) ->
            tv.setTextColor(
                when {
                    pcr > 1.2 -> Color.parseColor("#00D964") // Bullish
                    pcr < 0.8 -> Color.parseColor("#FF4444") // Bearish
                    else -> Color.parseColor("#E6EDF3")
                }
            )
        }
    }

    private fun setupPnlChart() {
        binding.barChartPnl.apply {
            setBackgroundColor(Color.parseColor("#161B22"))
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = Color.parseColor("#8B949E")
                setDrawGridLines(false)
                valueFormatter = object : ValueFormatter() {
                    private val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
                    override fun getFormattedValue(value: Float): String {
                        val idx = value.toInt()
                        return if (idx in days.indices) days[idx] else ""
                    }
                }
            }

            axisLeft.apply {
                textColor = Color.parseColor("#8B949E")
                gridColor = Color.parseColor("#21262D")
                axisLineColor = Color.parseColor("#30363D")
            }

            axisRight.isEnabled = false
        }
    }

    private fun updatePnlChart(history: List<Map<String, Any>>) {
        // Build a simple daily P&L from history
        val pnlByDay = Array(5) { 0f }
        history.forEach { item ->
            val createdAt = (item["createdAt"] as? Double)?.toLong() ?: 0L
            val status = item["status"] as? String ?: "OPEN"
            val target = (item["targetPoints"] as? Double) ?: 50.0
            val sl = (item["stopLossPoints"] as? Double) ?: 25.0

            if (createdAt > 0) {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = createdAt }
                val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK) - 2 // Mon=0
                val dayIdx = dayOfWeek.coerceIn(0, 4)

                when (status) {
                    "WIN" -> pnlByDay[dayIdx] += target.toFloat()
                    "LOSS" -> pnlByDay[dayIdx] -= sl.toFloat()
                }
            }
        }

        val entries = pnlByDay.mapIndexed { idx, pnl -> BarEntry(idx.toFloat(), pnl) }
        val colors = pnlByDay.map { pnl ->
            if (pnl >= 0) Color.parseColor("#00D964") else Color.parseColor("#FF4444")
        }

        val dataSet = BarDataSet(entries, "Daily P&L").apply {
            this.colors = colors
            setDrawValues(true)
            valueTextColor = Color.parseColor("#E6EDF3")
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value >= 0) "+${value.toInt()}" else "${value.toInt()}"
                }
            }
        }

        binding.barChartPnl.apply {
            data = BarData(dataSet)
            animateY(500)
            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
