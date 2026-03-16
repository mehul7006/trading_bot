package com.trading.bot.android.api.models

import com.google.gson.annotations.SerializedName

data class Indicators(
    @SerializedName("symbol") val symbol: String = "",
    @SerializedName("rsi") val rsi: Double = 50.0,
    @SerializedName("adx") val adx: Double = 25.0,
    @SerializedName("macd") val macd: Double = 0.0,
    @SerializedName("macdSignal") val macdSignal: Double = 0.0,
    @SerializedName("macdHist") val macdHist: Double = 0.0,
    @SerializedName("vwap") val vwap: Double = 0.0,
    @SerializedName("ema20") val ema20: Double = 0.0,
    @SerializedName("atr") val atr: Double = 0.0,
    @SerializedName("stochK") val stochK: Double = 50.0,
    @SerializedName("stochD") val stochD: Double = 50.0,
    @SerializedName("williamsR") val williamsR: Double = -50.0,
    @SerializedName("overallSignal") val overallSignal: String = "NEUTRAL",
    @SerializedName("confluenceScore") val confluenceScore: Double = 0.0,
    @SerializedName("reasoning") val reasoning: String = ""
)
