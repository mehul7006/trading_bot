package com.trading.bot.android.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.trading.bot.android.api.ApiClient
import com.trading.bot.android.api.models.*
import kotlinx.coroutines.*

class TradingViewModel(application: Application) : AndroidViewModel(application) {

    private val api get() = ApiClient.getApiService(getApplication())

    // -----------------------------------------------------------------------
    // LiveData
    // -----------------------------------------------------------------------

    private val _botStatus = MutableLiveData<BotStatus?>()
    val botStatus: LiveData<BotStatus?> = _botStatus

    private val _activeSignals = MutableLiveData<List<Signal>>(emptyList())
    val activeSignals: LiveData<List<Signal>> = _activeSignals

    private val _signalHistory = MutableLiveData<List<Map<String, Any>>>(emptyList())
    val signalHistory: LiveData<List<Map<String, Any>>> = _signalHistory

    private val _snapshot = MutableLiveData<List<MarketSnapshot>>(emptyList())
    val snapshot: LiveData<List<MarketSnapshot>> = _snapshot

    private val _chartData = MutableLiveData<CandleResponse?>()
    val chartData: LiveData<CandleResponse?> = _chartData

    private val _indicators = MutableLiveData<Indicators?>()
    val indicators: LiveData<Indicators?> = _indicators

    private val _selectedSymbol = MutableLiveData("NIFTY50")
    val selectedSymbol: LiveData<String> = _selectedSymbol

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _settingsSaved = MutableLiveData<Boolean>()
    val settingsSaved: LiveData<Boolean> = _settingsSaved

    // -----------------------------------------------------------------------
    // Polling
    // -----------------------------------------------------------------------

    private var pollingJob: Job? = null

    fun startPolling() {
        stopPolling()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                refreshAll()
                delay(30_000L)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun refresh() {
        viewModelScope.launch {
            refreshAll()
        }
    }

    private suspend fun refreshAll() {
        refreshStatus()
        refreshSignals()
        refreshSnapshot()
        val sym = _selectedSymbol.value ?: "NIFTY50"
        refreshChartData(sym)
    }

    fun refreshStatus() {
        viewModelScope.launch {
            try {
                val resp = api.getStatus()
                if (resp.isSuccessful) _botStatus.postValue(resp.body())
            } catch (e: Exception) {
                _error.postValue("Status: ${e.message}")
            }
        }
    }

    fun refreshSignals() {
        viewModelScope.launch {
            try {
                val resp = api.getSignals()
                if (resp.isSuccessful) _activeSignals.postValue(resp.body() ?: emptyList())

                val histResp = api.getHistory()
                if (histResp.isSuccessful) _signalHistory.postValue(histResp.body() ?: emptyList())
            } catch (e: Exception) {
                _error.postValue("Signals: ${e.message}")
            }
        }
    }

    fun refreshSnapshot() {
        viewModelScope.launch {
            try {
                val resp = api.getSnapshot()
                if (resp.isSuccessful) _snapshot.postValue(resp.body() ?: emptyList())
            } catch (e: Exception) {
                _error.postValue("Snapshot: ${e.message}")
            }
        }
    }

    fun refreshChartData(symbol: String) {
        _selectedSymbol.postValue(symbol)
        viewModelScope.launch {
            try {
                _isLoading.postValue(true)
                val candleResp = api.getMarketData(symbol)
                if (candleResp.isSuccessful) _chartData.postValue(candleResp.body())

                val indResp = api.getIndicators(symbol)
                if (indResp.isSuccessful) _indicators.postValue(indResp.body())
            } catch (e: Exception) {
                _error.postValue("Chart: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun saveSettings(serverUrl: String, upstoxToken: String, paperTrading: Boolean, minConfidence: Int) {
        viewModelScope.launch {
            try {
                // Save to local preferences
                ApiClient.saveServerUrl(getApplication(), serverUrl)
                ApiClient.saveUpstoxToken(getApplication(), upstoxToken)
                ApiClient.savePaperTrading(getApplication(), paperTrading)
                ApiClient.saveMinConfidence(getApplication(), minConfidence)

                // Push token to server
                val settings: Map<String, Any> = mapOf(
                    "upstoxToken" to upstoxToken,
                    "paperTrading" to paperTrading,
                    "minConfidence" to minConfidence
                )
                try {
                    api.saveSettings(settings)
                } catch (ignore: Exception) { /* server may not be reachable */ }

                _settingsSaved.postValue(true)
            } catch (e: Exception) {
                _error.postValue("Settings: ${e.message}")
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
