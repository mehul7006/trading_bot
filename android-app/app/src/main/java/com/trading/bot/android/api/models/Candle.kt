package com.trading.bot.android.api.models

import com.google.gson.annotations.SerializedName

data class Candle(
    @SerializedName("timestamp") val timestamp: String = "",
    @SerializedName("open") val open: Double = 0.0,
    @SerializedName("high") val high: Double = 0.0,
    @SerializedName("low") val low: Double = 0.0,
    @SerializedName("close") val close: Double = 0.0,
    @SerializedName("volume") val volume: Long = 0L
)

data class CandleResponse(
    @SerializedName("symbol") val symbol: String = "",
    @SerializedName("timeframe") val timeframe: String = "",
    @SerializedName("count") val count: Int = 0,
    @SerializedName("candles") val candles: List<Candle> = emptyList()
)
