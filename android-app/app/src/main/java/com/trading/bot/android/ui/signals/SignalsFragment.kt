package com.trading.bot.android.ui.signals

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trading.bot.android.R
import com.trading.bot.android.api.models.Signal
import com.trading.bot.android.databinding.FragmentSignalsBinding
import com.trading.bot.android.viewmodel.TradingViewModel
import java.text.SimpleDateFormat
import java.util.*

class SignalsFragment : Fragment() {

    private var _binding: FragmentSignalsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TradingViewModel by activityViewModels()

    private lateinit var activeAdapter: SignalAdapter
    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignalsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activeAdapter = SignalAdapter()
        historyAdapter = HistoryAdapter()

        binding.rvActiveSignals.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = activeAdapter
        }

        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = historyAdapter
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshSignals()
        }

        viewModel.activeSignals.observe(viewLifecycleOwner) { signals ->
            binding.swipeRefresh.isRefreshing = false
            activeAdapter.submitList(signals)
            binding.tvNoActiveSignals.visibility = if (signals.isEmpty()) View.VISIBLE else View.GONE
            binding.tvActiveCount.text = "Active Signals (${signals.size})"
        }

        viewModel.signalHistory.observe(viewLifecycleOwner) { history ->
            historyAdapter.submitList(history)
            binding.tvHistoryCount.text = "Signal History (${history.size})"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// -----------------------------------------------------------------------
// Active Signal Adapter
// -----------------------------------------------------------------------

class SignalAdapter : RecyclerView.Adapter<SignalAdapter.ViewHolder>() {

    private var items: List<Signal> = emptyList()

    fun submitList(list: List<Signal>) {
        items = list
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.cardSignal)
        val tvSymbol: TextView = view.findViewById(R.id.tvSymbol)
        val tvDirection: TextView = view.findViewById(R.id.tvDirection)
        val tvEntry: TextView = view.findViewById(R.id.tvEntry)
        val tvTarget: TextView = view.findViewById(R.id.tvTarget)
        val tvSl: TextView = view.findViewById(R.id.tvSl)
        val tvElapsed: TextView = view.findViewById(R.id.tvElapsed)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvConfidence: TextView = view.findViewById(R.id.tvConfidence)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_signal, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val signal = items[position]
        val isUp = signal.direction.uppercase() == "UP"

        holder.tvSymbol.text = signal.symbol
        holder.tvDirection.text = if (isUp) "▲ BUY" else "▼ SELL"
        holder.tvDirection.setTextColor(
            if (isUp) Color.parseColor("#00D964") else Color.parseColor("#FF4444")
        )
        holder.tvEntry.text = "Entry: ${String.format("%.2f", signal.entryPrice)}"
        holder.tvTarget.text = "Target: ${String.format("%.0f", signal.targetPoints)} pts → ${String.format("%.2f", signal.targetPrice)}"
        holder.tvSl.text = "SL: ${String.format("%.0f", signal.stopLossPoints)} pts → ${String.format("%.2f", signal.stopLossPrice)}"
        holder.tvElapsed.text = "${signal.elapsedMinutes} min ago"
        holder.tvStatus.text = signal.status
        holder.tvStatus.setTextColor(
            when (signal.status) {
                "WIN" -> Color.parseColor("#00D964")
                "LOSS" -> Color.parseColor("#FF4444")
                else -> Color.parseColor("#E3B341")
            }
        )
        holder.tvConfidence.text = "${String.format("%.0f", signal.confidence)}%"

        // Card border color by direction
        holder.card.setCardBackgroundColor(
            if (isUp) Color.parseColor("#0D2818") else Color.parseColor("#2D0A0A")
        )
    }

    override fun getItemCount() = items.size
}

// -----------------------------------------------------------------------
// History Adapter
// -----------------------------------------------------------------------

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private var items: List<Map<String, Any>> = emptyList()
    private val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun submitList(list: List<Map<String, Any>>) {
        items = list
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.cardSignal)
        val tvSymbol: TextView = view.findViewById(R.id.tvSymbol)
        val tvDirection: TextView = view.findViewById(R.id.tvDirection)
        val tvEntry: TextView = view.findViewById(R.id.tvEntry)
        val tvTarget: TextView = view.findViewById(R.id.tvTarget)
        val tvSl: TextView = view.findViewById(R.id.tvSl)
        val tvElapsed: TextView = view.findViewById(R.id.tvElapsed)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvConfidence: TextView = view.findViewById(R.id.tvConfidence)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_signal, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val direction = item["direction"] as? String ?: "UP"
        val isUp = direction.uppercase() == "UP"
        val symbol = item["symbol"] as? String ?: ""
        val entry = (item["entryPrice"] as? Double) ?: 0.0
        val target = (item["targetPoints"] as? Double) ?: 0.0
        val sl = (item["stopLossPoints"] as? Double) ?: 0.0
        val confidence = (item["confidence"] as? Double) ?: 0.0
        val status = item["status"] as? String ?: "OPEN"
        val createdAt = (item["createdAt"] as? Double)?.toLong() ?: 0L

        holder.tvSymbol.text = symbol
        holder.tvDirection.text = if (isUp) "▲ BUY" else "▼ SELL"
        holder.tvDirection.setTextColor(
            if (isUp) Color.parseColor("#00D964") else Color.parseColor("#FF4444")
        )
        holder.tvEntry.text = "Entry: ${String.format("%.2f", entry)}"
        holder.tvTarget.text = "Target: ${String.format("%.0f", target)} pts"
        holder.tvSl.text = "SL: ${String.format("%.0f", sl)} pts"
        holder.tvElapsed.text = if (createdAt > 0) sdf.format(Date(createdAt)) else ""
        holder.tvStatus.text = status
        holder.tvStatus.setTextColor(
            when (status) {
                "WIN" -> Color.parseColor("#00D964")
                "LOSS" -> Color.parseColor("#FF4444")
                else -> Color.parseColor("#8B949E")
            }
        )
        holder.tvConfidence.text = "${String.format("%.0f", confidence)}%"
        holder.card.setCardBackgroundColor(Color.parseColor("#161B22"))
    }

    override fun getItemCount() = items.size
}
