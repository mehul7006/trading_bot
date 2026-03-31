package com.trading.bot.android.api.models

import com.google.gson.annotations.SerializedName

data class MarketSnapshot(
    @SerializedName("symbol") val symbol: String = "",
    @SerializedName("price") val price: Double = 0.0,
    @SerializedName("change") val change: Double = 0.0,
    @SerializedName("changePercent") val changePercent: Double = 0.0,
    @SerializedName("direction") val direction: String = "NEUTRAL"
)
