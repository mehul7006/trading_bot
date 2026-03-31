package com.trading.bot.android.api.models

import com.google.gson.annotations.SerializedName

data class BotStatus(
    @SerializedName("botRunning") val botRunning: Boolean = false,
    @SerializedName("isScanning") val isScanning: Boolean = false,
    @SerializedName("todayCallsGenerated") val todayCallsGenerated: Int = 0,
    @SerializedName("activeSignalsCount") val activeSignalsCount: Int = 0,
    @SerializedName("serverTime") val serverTime: String = "",
    @SerializedName("version") val version: String = "",
    @SerializedName("winRate") val winRate: Double = 0.0,
    @SerializedName("todayWins") val todayWins: Int = 0,
    @SerializedName("todayLosses") val todayLosses: Int = 0
)
