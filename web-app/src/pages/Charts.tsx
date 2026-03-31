import { useEffect, useRef, useState, useCallback } from 'react'
import {
  createChart,
  IChartApi,
  ISeriesApi,
  CandlestickData,
  LineData,
  HistogramData,
  UTCTimestamp,
  ColorType,
  CrosshairMode,
} from 'lightweight-charts'
import { fetchMarketData, fetchIndicators } from '../api/client'
import type { SymbolType, Candle, Indicators } from '../types'

const SYMBOLS: SymbolType[] = ['NIFTY50', 'BANKNIFTY', 'SENSEX']

function candleToChartData(c: Candle): CandlestickData {
  return {
    time: Math.floor(c.timestamp / 1000) as UTCTimestamp,
    open: c.open,
    high: c.high,
    low: c.low,
    close: c.close,
  }
}

function candleToLine(c: Candle, value: number): LineData {
  return {
    time: Math.floor(c.timestamp / 1000) as UTCTimestamp,
    value,
  }
}

function candleToVolume(c: Candle): HistogramData {
  return {
    time: Math.floor(c.timestamp / 1000) as UTCTimestamp,
    value: c.volume,
    color: c.close >= c.open ? 'rgba(0,217,100,0.4)' : 'rgba(255,68,68,0.4)',
  }
}

// Generate mock candles for demo when API not available
function generateMockCandles(symbol: SymbolType): Candle[] {
  const basePrice: Record<SymbolType, number> = {
    NIFTY50: 22500,
    SENSEX: 74000,
    BANKNIFTY: 48000,
  }
  const base = basePrice[symbol]
  const candles: Candle[] = []
  const now = Date.now()
  const fiveMins = 5 * 60 * 1000
  let price = base

  // 3 days of 5-min candles (trading hours 9:15-15:30 = ~75 candles/day)
  for (let d = 2; d >= 0; d--) {
    const dayStart = new Date(now - d * 86400000)
    dayStart.setHours(9, 15, 0, 0)
    for (let i = 0; i < 75; i++) {
      const ts = dayStart.getTime() + i * fiveMins
      if (ts > now) break
      const change = (Math.random() - 0.495) * base * 0.003
      const open = price
      const close = price + change
      const high = Math.max(open, close) + Math.random() * base * 0.001
      const low = Math.min(open, close) - Math.random() * base * 0.001
      const volume = Math.floor(50000 + Math.random() * 200000)
      candles.push({ timestamp: ts, open, high, low, close, volume })
      price = close
    }
  }
  return candles
}

function generateMockIndicators(): Indicators {
  return {
    rsi: 45 + Math.random() * 30,
    adx: 20 + Math.random() * 30,
    macd: (Math.random() - 0.5) * 50,
    vwap: 22400 + Math.random() * 200,
    ema20: 22450 + Math.random() * 100,
    atr: 80 + Math.random() * 40,
  }
}

interface ChartState {
  chart: IChartApi | null
  candleSeries: ISeriesApi<'Candlestick'> | null
  vwapSeries: ISeriesApi<'Line'> | null
  emaSeries: ISeriesApi<'Line'> | null
}

interface VolumeChartState {
  chart: IChartApi | null
  volSeries: ISeriesApi<'Histogram'> | null
}

export default function Charts() {
  const [selectedSymbol, setSelectedSymbol] = useState<SymbolType>('NIFTY50')
  const [timeRange, setTimeRange] = useState<'1D' | '3D'>('1D')
  const [candles, setCandles] = useState<Candle[]>([])
  const [indicators, setIndicators] = useState<Indicators | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const chartWrapRef = useRef<HTMLDivElement>(null)
  const volumeWrapRef = useRef<HTMLDivElement>(null)
  const mainChartRef = useRef<ChartState>({
    chart: null,
    candleSeries: null,
    vwapSeries: null,
    emaSeries: null,
  })
  const volChartRef = useRef<VolumeChartState>({
    chart: null,
    volSeries: null,
  })
  const chartsInitialized = useRef(false)

  const chartOptions = {
    layout: {
      background: { type: ColorType.Solid, color: '#0D1117' },
      textColor: '#8B949E',
    },
    grid: {
      vertLines: { color: 'rgba(48,54,61,0.5)' },
      horzLines: { color: 'rgba(48,54,61,0.5)' },
    },
    crosshair: {
      mode: CrosshairMode.Normal,
    },
    rightPriceScale: {
      borderColor: '#30363D',
    },
    timeScale: {
      borderColor: '#30363D',
      timeVisible: true,
      secondsVisible: false,
    },
  }

  const initCharts = useCallback(() => {
    if (!chartWrapRef.current || !volumeWrapRef.current) return

    // Destroy existing
    if (mainChartRef.current.chart) {
      mainChartRef.current.chart.remove()
    }
    if (volChartRef.current.chart) {
      volChartRef.current.chart.remove()
    }

    // Main chart
    const mainChart = createChart(chartWrapRef.current, {
      ...chartOptions,
      width: chartWrapRef.current.clientWidth,
      height: 360,
    })

    const candleSeries = mainChart.addCandlestickSeries({
      upColor: '#00D964',
      downColor: '#FF4444',
      borderUpColor: '#00D964',
      borderDownColor: '#FF4444',
      wickUpColor: '#00D964',
      wickDownColor: '#FF4444',
    })

    const vwapSeries = mainChart.addLineSeries({
      color: '#F0B429',
      lineWidth: 1,
      title: 'VWAP',
      priceLineVisible: false,
      lastValueVisible: true,
    })

    const emaSeries = mainChart.addLineSeries({
      color: '#1F6FEB',
      lineWidth: 1,
      title: 'EMA20',
      priceLineVisible: false,
      lastValueVisible: true,
    })

    mainChartRef.current = { chart: mainChart, candleSeries, vwapSeries, emaSeries }

    // Volume chart
    const volChart = createChart(volumeWrapRef.current, {
      ...chartOptions,
      width: volumeWrapRef.current.clientWidth,
      height: 100,
      rightPriceScale: {
        borderColor: '#30363D',
        scaleMargins: { top: 0.1, bottom: 0 },
      },
    })

    const volSeries = volChart.addHistogramSeries({
      color: 'rgba(0,217,100,0.4)',
      priceFormat: { type: 'volume' },
      priceScaleId: 'right',
    })

    volChartRef.current = { chart: volChart, volSeries }

    // Sync crosshairs
    mainChart.timeScale().subscribeVisibleLogicalRangeChange((range) => {
      if (range) {
        volChart.timeScale().setVisibleLogicalRange(range)
      }
    })

    volChart.timeScale().subscribeVisibleLogicalRangeChange((range) => {
      if (range) {
        mainChart.timeScale().setVisibleLogicalRange(range)
      }
    })

    // Resize observer
    const resizeObserver = new ResizeObserver(() => {
      if (chartWrapRef.current && mainChartRef.current.chart) {
        mainChartRef.current.chart.applyOptions({
          width: chartWrapRef.current.clientWidth,
        })
      }
      if (volumeWrapRef.current && volChartRef.current.chart) {
        volChartRef.current.chart.applyOptions({
          width: volumeWrapRef.current.clientWidth,
        })
      }
    })

    if (chartWrapRef.current) resizeObserver.observe(chartWrapRef.current)
    chartsInitialized.current = true

    return () => {
      resizeObserver.disconnect()
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    const cleanup = initCharts()
    return () => {
      cleanup?.()
      if (mainChartRef.current.chart) {
        mainChartRef.current.chart.remove()
        mainChartRef.current = { chart: null, candleSeries: null, vwapSeries: null, emaSeries: null }
      }
      if (volChartRef.current.chart) {
        volChartRef.current.chart.remove()
        volChartRef.current = { chart: null, volSeries: null }
      }
      chartsInitialized.current = false
    }
  }, [initCharts])

  const loadData = useCallback(async (symbol: SymbolType) => {
    setLoading(true)
    setError(null)
    try {
      const [marketData, indicatorsData] = await Promise.all([
        fetchMarketData(symbol).catch(() => ({ candles: generateMockCandles(symbol) })),
        fetchIndicators(symbol).catch(() => generateMockIndicators()),
      ])
      setCandles(marketData.candles)
      setIndicators(indicatorsData)
    } catch (err) {
      setError('Failed to load chart data')
      const mockCandles = generateMockCandles(symbol)
      setCandles(mockCandles)
      setIndicators(generateMockIndicators())
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadData(selectedSymbol)
  }, [selectedSymbol, loadData])

  // Update chart data when candles/indicators change
  useEffect(() => {
    if (!chartsInitialized.current) return
    if (!candles.length) return

    const { candleSeries, vwapSeries, emaSeries } = mainChartRef.current
    const { volSeries } = volChartRef.current

    if (!candleSeries || !vwapSeries || !emaSeries || !volSeries) return

    // Filter by time range
    const cutoff = timeRange === '1D'
      ? Date.now() - 1 * 24 * 60 * 60 * 1000
      : Date.now() - 3 * 24 * 60 * 60 * 1000

    const filtered = candles.filter(c => c.timestamp >= cutoff)
    if (filtered.length === 0) return

    // Calculate VWAP and EMA20
    let cumVol = 0
    let cumVolPrice = 0
    const vwapData: LineData[] = []
    const ema20Data: LineData[] = []
    let ema = filtered[0].close
    const k = 2 / (20 + 1)

    filtered.forEach(c => {
      const typicalPrice = (c.high + c.low + c.close) / 3
      cumVolPrice += typicalPrice * c.volume
      cumVol += c.volume
      const vwap = cumVol > 0 ? cumVolPrice / cumVol : typicalPrice
      vwapData.push(candleToLine(c, vwap))

      ema = c.close * k + ema * (1 - k)
      ema20Data.push(candleToLine(c, ema))
    })

    candleSeries.setData(filtered.map(candleToChartData))
    vwapSeries.setData(vwapData)
    emaSeries.setData(ema20Data)
    volSeries.setData(filtered.map(candleToVolume))

    // Scroll to right
    mainChartRef.current.chart?.timeScale().scrollToRealTime()

  }, [candles, timeRange])

  function getIndicatorClass(name: string, value: number): string {
    if (name === 'RSI') {
      if (value > 70) return 'bad'
      if (value < 30) return 'bad'
      if (value > 55) return 'good'
      return 'neutral'
    }
    if (name === 'ADX') {
      return value > 25 ? 'good' : 'neutral'
    }
    if (name === 'MACD') {
      return value > 0 ? 'good' : 'bad'
    }
    return 'neutral'
  }

  return (
    <div className="chart-page">
      {/* Symbol selector */}
      <div className="symbol-tabs">
        {SYMBOLS.map(sym => (
          <button
            key={sym}
            className={`symbol-tab${selectedSymbol === sym ? ' active' : ''}`}
            onClick={() => setSelectedSymbol(sym)}
          >
            {sym}
          </button>
        ))}
      </div>

      {/* Time range */}
      <div className="time-range-bar">
        {(['1D', '3D'] as const).map(r => (
          <button
            key={r}
            className={`time-btn${timeRange === r ? ' active' : ''}`}
            onClick={() => setTimeRange(r)}
          >
            {r}
          </button>
        ))}
        {loading && <div className="spinner spinner-sm" style={{ marginLeft: 'auto' }} />}
      </div>

      {error && (
        <div className="alert alert-info" style={{ margin: '8px 12px', fontSize: 12 }}>
          ℹ️ {error} — using simulated data
        </div>
      )}

      {/* Main candlestick chart */}
      <div className="chart-container">
        <div ref={chartWrapRef} className="chart-wrap" />
      </div>

      {/* Volume chart */}
      <div ref={volumeWrapRef} className="volume-wrap" />

      {/* Indicator strip */}
      {indicators && (
        <div className="indicator-strip">
          <div className="indicator-item">
            <span className="indicator-name">RSI</span>
            <span className={`indicator-value ${getIndicatorClass('RSI', indicators.rsi)}`}>
              {indicators.rsi.toFixed(1)}
            </span>
          </div>
          <div style={{ width: 1, background: 'var(--border)' }} />
          <div className="indicator-item">
            <span className="indicator-name">ADX</span>
            <span className={`indicator-value ${getIndicatorClass('ADX', indicators.adx)}`}>
              {indicators.adx.toFixed(1)}
            </span>
          </div>
          <div style={{ width: 1, background: 'var(--border)' }} />
          <div className="indicator-item">
            <span className="indicator-name">MACD</span>
            <span className={`indicator-value ${getIndicatorClass('MACD', indicators.macd)}`}>
              {indicators.macd > 0 ? '▲' : '▼'} {Math.abs(indicators.macd).toFixed(1)}
            </span>
          </div>
          <div style={{ width: 1, background: 'var(--border)' }} />
          <div className="indicator-item">
            <span className="indicator-name">VWAP</span>
            <span className="indicator-value neutral">
              {indicators.vwap.toFixed(0)}
            </span>
          </div>
          <div style={{ width: 1, background: 'var(--border)' }} />
          <div className="indicator-item">
            <span className="indicator-name">EMA20</span>
            <span className="indicator-value neutral">
              {indicators.ema20.toFixed(0)}
            </span>
          </div>
          <div style={{ width: 1, background: 'var(--border)' }} />
          <div className="indicator-item">
            <span className="indicator-name">ATR</span>
            <span className="indicator-value neutral">
              {indicators.atr.toFixed(1)}
            </span>
          </div>
        </div>
      )}

      {/* Legend */}
      <div style={{
        display: 'flex',
        gap: 16,
        padding: '8px 12px',
        fontSize: 11,
        color: 'var(--muted)',
        background: 'var(--bg)',
        flexWrap: 'wrap',
      }}>
        <span>
          <span style={{ color: '#F0B429', marginRight: 4 }}>—</span> VWAP
        </span>
        <span>
          <span style={{ color: '#1F6FEB', marginRight: 4 }}>—</span> EMA 20
        </span>
        <span>
          <span style={{ color: '#00D964', marginRight: 4 }}>■</span> Bullish
        </span>
        <span>
          <span style={{ color: '#FF4444', marginRight: 4 }}>■</span> Bearish
        </span>
      </div>
    </div>
  )
}
