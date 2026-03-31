package com.trading.bot.android.api.models

import com.google.gson.annotations.SerializedName

data class Signal(
    @SerializedName("symbol") val symbol: String = "",
    @SerializedName("direction") val direction: String = "NEUTRAL",
    @SerializedName("entryPrice") val entryPrice: Double = 0.0,
    @SerializedName("targetPoints") val targetPoints: Double = 0.0,
    @SerializedName("stopLossPoints") val stopLossPoints: Double = 0.0,
    @SerializedName("confidence") val confidence: Double = 0.0,
    @SerializedName("createdAt") val createdAt: Long = 0L,
    @SerializedName("status") val status: String = "OPEN",
    @SerializedName("elapsedMinutes") val elapsedMinutes: Long = 0L,
    @SerializedName("targetPrice") val targetPrice: Double = 0.0,
    @SerializedName("stopLossPrice") val stopLossPrice: Double = 0.0,
    @SerializedName("pcr") val pcr: Double = 1.0
)
