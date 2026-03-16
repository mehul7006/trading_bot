package com.trading.bot.android.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.trading.bot.android.api.ApiClient
import com.trading.bot.android.databinding.FragmentSettingsBinding
import com.trading.bot.android.viewmodel.TradingViewModel

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TradingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load saved values
        val ctx = requireContext()
        binding.etServerUrl.setText(ApiClient.getServerUrl(ctx))
        binding.etUpstoxToken.setText(ApiClient.getUpstoxToken(ctx))
        binding.switchPaperTrading.isChecked = ApiClient.isPaperTrading(ctx)
        val minConf = ApiClient.getMinConfidence(ctx)
        binding.seekBarConfidence.progress = minConf - 80
        binding.tvConfidenceValue.text = "${minConf}%"

        // Seek bar listener
        binding.seekBarConfidence.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                binding.tvConfidenceValue.text = "${progress + 80}%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        // Save button
        binding.btnSave.setOnClickListener {
            val serverUrl = binding.etServerUrl.text.toString().trim()
            val upstoxToken = binding.etUpstoxToken.text.toString().trim()
            val paperTrading = binding.switchPaperTrading.isChecked
            val minConfidence = binding.seekBarConfidence.progress + 80

            if (serverUrl.isEmpty()) {
                binding.etServerUrl.error = "Server URL is required"
                return@setOnClickListener
            }

            viewModel.saveSettings(serverUrl, upstoxToken, paperTrading, minConfidence)
        }

        // Test connection button
        binding.btnTestConnection.setOnClickListener {
            binding.tvConnectionStatus.text = "Testing..."
            viewModel.refreshStatus()
        }

        viewModel.settingsSaved.observe(viewLifecycleOwner) { saved ->
            if (saved) {
                Toast.makeText(requireContext(), "Settings saved!", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.botStatus.observe(viewLifecycleOwner) { status ->
            if (status != null) {
                binding.tvConnectionStatus.text = "Connected — Bot ${if (status.isScanning) "Scanning" else "Idle"} (${status.version})"
                binding.tvConnectionStatus.setTextColor(android.graphics.Color.parseColor("#00D964"))
            } else {
                binding.tvConnectionStatus.text = "Connection Failed"
                binding.tvConnectionStatus.setTextColor(android.graphics.Color.parseColor("#FF4444"))
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            err?.let {
                binding.tvConnectionStatus.text = "Error: $it"
                binding.tvConnectionStatus.setTextColor(android.graphics.Color.parseColor("#FF4444"))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
