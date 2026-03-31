package com.trading.bot.android.ui.dashboard

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.trading.bot.android.R
import com.trading.bot.android.api.models.BotStatus
import com.trading.bot.android.api.models.MarketSnapshot
import com.trading.bot.android.api.models.Signal
import com.trading.bot.android.databinding.FragmentDashboardBinding
import com.trading.bot.android.viewmodel.TradingViewModel
import java.text.DecimalFormat

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TradingViewModel by activityViewModels()

    private val df2 = DecimalFormat("#,##0.00")
    private val df1 = DecimalFormat("+#,##0.00;-#,##0.00")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }

        binding.btnRefresh.setOnClickListener {
            viewModel.refresh()
        }

        viewModel.snapshot.observe(viewLifecycleOwner) { snapshots ->
            binding.swipeRefresh.isRefreshing = false
            if (snapshots.isNullOrEmpty()) return@observe
            val signalMap = viewModel.activeSignals.value?.associateBy { it.symbol } ?: emptyMap()
            updateSymbolCards(snapshots, signalMap)
        }

        viewModel.botStatus.observe(viewLifecycleOwner) { status ->
            updateStatusBar(status)
        }

        viewModel.activeSignals.observe(viewLifecycleOwner) { signals ->
            val snapshots = viewModel.snapshot.value ?: emptyList()
            if (snapshots.isNotEmpty()) {
                updateSymbolCards(snapshots, signals.associateBy { it.symbol })
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.swipeRefresh.isRefreshing = loading
        }
    }

    private fun updateStatusBar(status: BotStatus?) {
        if (status == null) return
        binding.tvBotStatus.text = if (status.isScanning) "SCANNING" else "IDLE"
        binding.tvBotStatus.setTextColor(
            if (status.isScanning) Color.parseColor("#00D964") else Color.parseColor("#FF4444")
        )
        binding.tvTodayCalls.text = "Calls: ${status.todayCallsGenerated}"
        binding.tvWinRate.text = "Win Rate: ${status.winRate}%"
        binding.tvVersion.text = status.version
    }

    private fun updateSymbolCards(
        snapshots: List<MarketSnapshot>,
        signalMap: Map<String, Signal>
    ) {
        val cards = listOf(
            Triple(binding.cardNifty, binding.tvNiftyPrice, binding.tvNiftyChange),
            Triple(binding.cardBanknifty, binding.tvBankniftyPrice, binding.tvBankniftyChange),
            Triple(binding.cardSensex, binding.tvSensexPrice, binding.tvSensexChange)
        )
        val symbolOrder = listOf("NIFTY50", "BANKNIFTY", "SENSEX")
        val labels = listOf(binding.tvNiftyLabel, binding.tvBankniftyLabel, binding.tvSensexLabel)
        val arrows = listOf(binding.tvNiftyArrow, binding.tvBankniftyArrow, binding.tvSensexArrow)
        val badges = listOf(binding.tvNiftyBadge, binding.tvBankniftyBadge, binding.tvSensexBadge)

        val snapshotMap = snapshots.associateBy { it.symbol }

        symbolOrder.forEachIndexed { idx, sym ->
            val snap = snapshotMap[sym] ?: return@forEachIndexed
            val signal = signalMap[sym]
            val (card, tvPrice, tvChange) = cards[idx]

            labels[idx].text = sym
            tvPrice.text = df2.format(snap.price)

            val changeSign = if (snap.changePercent >= 0) "+" else ""
            tvChange.text = "${changeSign}${String.format("%.2f", snap.changePercent)}%"
            val changeColor = if (snap.changePercent >= 0) Color.parseColor("#00D964") else Color.parseColor("#FF4444")
            tvChange.setTextColor(changeColor)

            // Direction arrow
            val direction = signal?.direction ?: snap.direction
            when (direction.uppercase()) {
                "UP" -> {
                    arrows[idx].text = "▲"
                    arrows[idx].setTextColor(Color.parseColor("#00D964"))
                    arrows[idx].visibility = View.VISIBLE
                }
                "DOWN" -> {
                    arrows[idx].text = "▼"
                    arrows[idx].setTextColor(Color.parseColor("#FF4444"))
                    arrows[idx].visibility = View.VISIBLE
                }
                else -> arrows[idx].visibility = View.INVISIBLE
            }

            // Signal badge
            if (signal != null) {
                badges[idx].visibility = View.VISIBLE
                badges[idx].text = "SIGNAL ACTIVE"
                badges[idx].setTextColor(
                    if (signal.direction == "UP") Color.parseColor("#00D964") else Color.parseColor("#FF4444")
                )
            } else {
                badges[idx].visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
