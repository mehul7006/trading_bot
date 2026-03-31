package com.trading.bot.android.api

import com.trading.bot.android.api.models.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @GET("api/status")
    suspend fun getStatus(): Response<BotStatus>

    @GET("api/signals")
    suspend fun getSignals(): Response<List<Signal>>

    @GET("api/marketdata")
    suspend fun getMarketData(@Query("symbol") symbol: String): Response<CandleResponse>

    @GET("api/snapshot")
    suspend fun getSnapshot(): Response<List<MarketSnapshot>>

    @GET("api/history")
    suspend fun getHistory(): Response<List<Map<String, Any>>>

    @GET("api/indicators")
    suspend fun getIndicators(@Query("symbol") symbol: String): Response<Indicators>

    @POST("api/settings")
    suspend fun saveSettings(@Body settings: Map<String, Any>): Response<Map<String, Any>>
}
