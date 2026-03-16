import { useState, useEffect, useCallback } from 'react'
import { fetchStatus, fetchSnapshot, fetchSignals } from '../api/client'
import type { BotStatus, Signal, Snapshot, SymbolType } from '../types'

const SYMBOLS: SymbolType[] = ['NIFTY50', 'SENSEX', 'BANKNIFTY']

const SYMBOL_META: Record<SymbolType, { emoji: string; label: string }> = {
  NIFTY50: { emoji: '📉', label: 'NIFTY 50' },
  SENSEX: { emoji: '📊', label: 'SENSEX' },
  BANKNIFTY: { emoji: '🏦', label: 'BANK NIFTY' },
}

function formatPrice(price: number): string {
  return price.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatTime(date: Date): string {
  return date.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function timeAgo(isoString: string): string {
  const diff = Date.now() - new Date(isoString).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins}m ago`
  const hrs = Math.floor(mins / 60)
  return `${hrs}h ${mins % 60}m ago`
}

interface SymbolCardProps {
  symbol: SymbolType
  price: number | null
  signal: Signal | undefined
  status: BotStatus | null
}

function SymbolCard({ symbol, price, signal, status }: SymbolCardProps) {
  const meta = SYMBOL_META[symbol]
  const hasSignal = !!signal
  const cardClass = `symbol-card${signal?.direction === 'UP' ? ' signal-up' : signal?.direction === 'DOWN' ? ' signal-down' : ''}`

  return (
    <div className={cardClass}>
      <div className="symbol-header">
        <div className="symbol-name">
          <span>{meta.emoji}</span>
          <span>{meta.label}</span>
        </div>
        {hasSignal ? (
          <span className={`badge ${signal.direction === 'UP' ? 'badge-up' : 'badge-down'}`}>
            {signal.direction === 'UP' ? '▲ UP' : '▼ DOWN'}
          </span>
        ) : (
          <span className="badge badge-neutral">— IDLE</span>
        )}
      </div>

      <div className="symbol-price">
        {price !== null ? formatPrice(price) : '—'}
      </div>

      {hasSignal && (
        <>
          <div className="confidence-bar-wrap">
            <div className="confidence-bar-bg">
              <div
                className="confidence-bar-fill"
                style={{
                  width: `${signal.confidence}%`,
                  background: signal.direction === 'UP' ? 'var(--green)' : 'var(--red)',
                }}
              />
            </div>
            <span className="confidence-label" style={{
              color: signal.direction === 'UP' ? 'var(--green)' : 'var(--red)'
            }}>
              {signal.confidence}%
            </span>
          </div>

          <div className="signal-info" style={{
            background: signal.direction === 'UP'
              ? 'rgba(0,217,100,0.06)'
              : 'rgba(255,68,68,0.06)',
            borderColor: signal.direction === 'UP'
              ? 'rgba(0,217,100,0.2)'
              : 'rgba(255,68,68,0.2)',
          }}>
            <div className="signal-row">
              <div className="signal-field">
                <span className="signal-field-label">Entry</span>
                <span className="signal-field-value entry">{formatPrice(signal.entry)}</span>
              </div>
              <div style={{ width: 1, background: 'var(--border)', alignSelf: 'stretch' }} />
              <div className="signal-field">
                <span className="signal-field-label">Target</span>
                <span className="signal-field-value target">{formatPrice(signal.target)}</span>
              </div>
              <div style={{ width: 1, background: 'var(--border)', alignSelf: 'stretch' }} />
              <div className="signal-field">
                <span className="signal-field-label">Stop Loss</span>
                <span className="signal-field-value sl">{formatPrice(signal.sl)}</span>
              </div>
            </div>
            <div style={{ marginTop: 8, fontSize: 11, color: 'var(--muted)', textAlign: 'center' }}>
              Signal {timeAgo(signal.timestamp)}
            </div>
          </div>
        </>
      )}

      <div className="status-row">
        <div className="status-item">
          <span className="status-label">Bot</span>
          <div className="bot-status">
            <div className={`dot ${status?.scanning ? 'dot-green' : 'dot-gray'}`} />
            <span style={{ fontSize: 12 }}>{status?.scanning ? 'Scanning' : 'Idle'}</span>
          </div>
        </div>
        <div className="status-item">
          <span className="status-label">Today</span>
          <span className="status-value">{status?.todayCalls ?? '—'} Calls</span>
        </div>
        <div className="status-item">
          <span className="status-label">Win Rate</span>
          <span className="status-value" style={{
            color: (status?.winRate ?? 0) >= 60 ? 'var(--green)' : 'var(--text)'
          }}>
            {status ? `${status.winRate.toFixed(1)}%` : '—'}
          </span>
        </div>
      </div>
    </div>
  )
}

export default function Dashboard() {
  const [status, setStatus] = useState<BotStatus | null>(null)
  const [signals, setSignals] = useState<Signal[]>([])
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null)
  const [refreshing, setRefreshing] = useState(false)

  const loadData = useCallback(async (showLoading = false) => {
    if (showLoading) setRefreshing(true)
    setError(null)
    try {
      const [statusData, snapshotData, signalsData] = await Promise.all([
        fetchStatus(),
        fetchSnapshot(),
        fetchSignals(),
      ])
      setStatus(statusData)
      setSnapshot(snapshotData)
      setSignals(signalsData)
      setLastUpdated(new Date())
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Connection failed')
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [])

  useEffect(() => {
    loadData()
    const interval = setInterval(() => loadData(), 30000)
    return () => clearInterval(interval)
  }, [loadData])

  if (loading) {
    return (
      <div className="loading-overlay">
        <div className="spinner" />
        <span>Connecting to trading server...</span>
      </div>
    )
  }

  const activeSignals = signals.filter(s => s.status === 'OPEN')
  const getSignalForSymbol = (sym: SymbolType) =>
    activeSignals.find(s => s.symbol === sym)

  return (
    <div className="page">
      {error && (
        <div className="alert alert-error">
          ⚠️ {error} — showing last known data
        </div>
      )}

      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        marginBottom: 14,
      }}>
        <div>
          <div style={{ fontSize: 12, color: 'var(--muted)' }}>
            {lastUpdated ? `Updated ${formatTime(lastUpdated)}` : 'Loading...'}
          </div>
        </div>
        <button
          className={`refresh-btn${refreshing ? ' spinning' : ''}`}
          onClick={() => loadData(true)}
          aria-label="Refresh"
        >
          <span className="refresh-icon">↻</span>
        </button>
      </div>

      {/* Summary strip */}
      <div className="card" style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ textAlign: 'center', flex: 1 }}>
            <div style={{ fontSize: 11, color: 'var(--muted)', marginBottom: 4, textTransform: 'uppercase', letterSpacing: '0.5px' }}>
              Active
            </div>
            <div style={{ fontSize: 22, fontWeight: 800 }}>{activeSignals.length}</div>
          </div>
          <div style={{ width: 1, background: 'var(--border)', height: 40 }} />
          <div style={{ textAlign: 'center', flex: 1 }}>
            <div style={{ fontSize: 11, color: 'var(--muted)', marginBottom: 4, textTransform: 'uppercase', letterSpacing: '0.5px' }}>
              Today
            </div>
            <div style={{ fontSize: 22, fontWeight: 800 }}>{status?.todayCalls ?? 0}</div>
          </div>
          <div style={{ width: 1, background: 'var(--border)', height: 40 }} />
          <div style={{ textAlign: 'center', flex: 1 }}>
            <div style={{ fontSize: 11, color: 'var(--muted)', marginBottom: 4, textTransform: 'uppercase', letterSpacing: '0.5px' }}>
              Win Rate
            </div>
            <div style={{
              fontSize: 22,
              fontWeight: 800,
              color: (status?.winRate ?? 0) >= 60 ? 'var(--green)' : 'var(--text)',
            }}>
              {status ? `${status.winRate.toFixed(0)}%` : '—'}
            </div>
          </div>
          <div style={{ width: 1, background: 'var(--border)', height: 40 }} />
          <div style={{ textAlign: 'center', flex: 1 }}>
            <div style={{ fontSize: 11, color: 'var(--muted)', marginBottom: 4, textTransform: 'uppercase', letterSpacing: '0.5px' }}>
              Status
            </div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 5 }}>
              <div className={`dot ${status?.scanning ? 'dot-green' : 'dot-gray'}`} />
              <span style={{ fontSize: 12, fontWeight: 700 }}>
                {status?.scanning ? 'ON' : 'OFF'}
              </span>
            </div>
          </div>
        </div>
      </div>

      {SYMBOLS.map(sym => (
        <SymbolCard
          key={sym}
          symbol={sym}
          price={snapshot ? snapshot[sym] : null}
          signal={getSignalForSymbol(sym)}
          status={status}
        />
      ))}

      <div style={{ height: 8 }} />
    </div>
  )
}
