import { useState, useEffect, useCallback } from 'react'
import { fetchSignals, fetchHistory } from '../api/client'
import type { Signal, HistorySignal } from '../types'

function timeAgo(isoString: string): string {
  const diff = Date.now() - new Date(isoString).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins} min ago`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24) return `${hrs}h ${mins % 60}m ago`
  const days = Math.floor(hrs / 24)
  return `${days}d ago`
}

function formatPrice(p: number): string {
  return p.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatDateTime(isoString: string): string {
  const d = new Date(isoString)
  return d.toLocaleString('en-IN', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// Mock data for demonstration
function generateMockSignals(): Signal[] {
  const now = new Date()
  return [
    {
      id: '1',
      symbol: 'NIFTY50',
      direction: 'UP',
      entry: 22480.5,
      target: 22580.5,
      sl: 22430.5,
      confidence: 88,
      timestamp: new Date(now.getTime() - 12 * 60000).toISOString(),
      status: 'OPEN',
      currentPrice: 22510,
    },
    {
      id: '2',
      symbol: 'BANKNIFTY',
      direction: 'DOWN',
      entry: 48250,
      target: 48100,
      sl: 48350,
      confidence: 82,
      timestamp: new Date(now.getTime() - 28 * 60000).toISOString(),
      status: 'OPEN',
      currentPrice: 48200,
    },
  ]
}

function generateMockHistory(): HistorySignal[] {
  const results = [
    { status: 'WIN' as const, dir: 'UP' as const, sym: 'NIFTY50', entry: 22300, target: 22400, sl: 22250, conf: 89, exitPrice: 22395 },
    { status: 'LOSS' as const, dir: 'DOWN' as const, sym: 'SENSEX', entry: 73800, target: 73650, sl: 73900, conf: 81, exitPrice: 73910 },
    { status: 'WIN' as const, dir: 'UP' as const, sym: 'BANKNIFTY', entry: 47900, target: 48050, sl: 47820, conf: 86, exitPrice: 48040 },
    { status: 'WIN' as const, dir: 'DOWN' as const, sym: 'NIFTY50', entry: 22200, target: 22100, sl: 22260, conf: 91, exitPrice: 22110 },
    { status: 'LOSS' as const, dir: 'UP' as const, sym: 'BANKNIFTY', entry: 47500, target: 47680, sl: 47410, conf: 83, exitPrice: 47405 },
    { status: 'WIN' as const, dir: 'UP' as const, sym: 'SENSEX', entry: 73500, target: 73700, sl: 73400, conf: 87, exitPrice: 73695 },
  ]

  const now = Date.now()
  return results.map((r, i) => ({
    id: `h${i}`,
    symbol: r.sym,
    direction: r.dir,
    entry: r.entry,
    target: r.target,
    sl: r.sl,
    confidence: r.conf,
    timestamp: new Date(now - (i + 1) * 3.5 * 3600000).toISOString(),
    status: r.status,
    exitPrice: r.exitPrice,
    pnl: r.status === 'WIN'
      ? Math.abs(r.exitPrice - r.entry)
      : -Math.abs(r.exitPrice - r.entry),
  }))
}

interface ActiveSignalCardProps {
  signal: Signal
}

function ActiveSignalCard({ signal }: ActiveSignalCardProps) {
  const isUp = signal.direction === 'UP'
  const pnl = signal.currentPrice
    ? isUp
      ? signal.currentPrice - signal.entry
      : signal.entry - signal.currentPrice
    : null

  return (
    <div className={`signal-card ${isUp ? 'up' : 'down'}`}>
      <div className="signal-card-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span className="signal-symbol">{signal.symbol}</span>
          <span className={`badge ${isUp ? 'badge-up' : 'badge-down'}`}>
            {isUp ? '▲ LONG' : '▼ SHORT'}
          </span>
        </div>
        <span className="signal-time">{timeAgo(signal.timestamp)}</span>
      </div>

      <div className="signal-prices">
        <div className="price-col">
          <span className="price-label">Entry</span>
          <span className="price-val entry">{formatPrice(signal.entry)}</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', color: 'var(--muted)', fontSize: 18 }}>
          →
        </div>
        <div className="price-col">
          <span className="price-label">Target</span>
          <span className="price-val target">{formatPrice(signal.target)}</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', color: 'var(--muted)', fontSize: 18 }}>
          |
        </div>
        <div className="price-col">
          <span className="price-label">Stop Loss</span>
          <span className="price-val sl">{formatPrice(signal.sl)}</span>
        </div>
      </div>

      <div className="confidence-bar-wrap" style={{ marginBottom: 10 }}>
        <div className="confidence-bar-bg">
          <div
            className="confidence-bar-fill"
            style={{
              width: `${signal.confidence}%`,
              background: isUp ? 'var(--green)' : 'var(--red)',
            }}
          />
        </div>
        <span className="confidence-label" style={{
          color: isUp ? 'var(--green)' : 'var(--red)',
        }}>
          {signal.confidence}%
        </span>
      </div>

      <div className="signal-footer">
        <div className="monitoring-text">
          <div className="dot dot-green" />
          ⏳ Monitoring...
        </div>
        {signal.currentPrice && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <div style={{ fontSize: 11, color: 'var(--muted)' }}>
              LTP: <strong style={{ color: 'var(--text)' }}>
                {formatPrice(signal.currentPrice)}
              </strong>
            </div>
            {pnl !== null && (
              <div style={{
                fontSize: 12,
                fontWeight: 700,
                color: pnl >= 0 ? 'var(--green)' : 'var(--red)',
              }}>
                {pnl >= 0 ? '+' : ''}{pnl.toFixed(1)} pts
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

export default function Signals() {
  const [activeSignals, setActiveSignals] = useState<Signal[]>([])
  const [history, setHistory] = useState<HistorySignal[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null)

  const loadData = useCallback(async (showSpinner = false) => {
    if (showSpinner) setRefreshing(true)
    setError(null)
    try {
      const [signalsData, historyData] = await Promise.all([
        fetchSignals().catch(() => generateMockSignals()),
        fetchHistory().catch(() => generateMockHistory()),
      ])
      setActiveSignals(signalsData.filter(s => s.status === 'OPEN'))
      setHistory(historyData)
      setLastUpdated(new Date())
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load signals')
      setActiveSignals(generateMockSignals())
      setHistory(generateMockHistory())
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
        <span>Loading signals...</span>
      </div>
    )
  }

  return (
    <div className="page">
      {error && (
        <div className="alert alert-info" style={{ fontSize: 12 }}>
          ℹ️ Using demo data — {error}
        </div>
      )}

      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        marginBottom: 14,
      }}>
        <div style={{ fontSize: 12, color: 'var(--muted)' }}>
          {lastUpdated
            ? `Updated ${lastUpdated.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' })}`
            : ''}
        </div>
        <button
          className={`refresh-btn${refreshing ? ' spinning' : ''}`}
          onClick={() => loadData(true)}
          aria-label="Refresh"
        >
          <span className="refresh-icon">↻</span>
        </button>
      </div>

      {/* Active Signals */}
      <div className="section-title">
        🟢 Active Signals ({activeSignals.length})
      </div>

      {activeSignals.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state-icon">🔍</div>
          <div className="empty-state-text">No active signals right now</div>
          <div style={{ fontSize: 12 }}>Bot is scanning for opportunities...</div>
        </div>
      ) : (
        activeSignals.map(signal => (
          <ActiveSignalCard key={signal.id} signal={signal} />
        ))
      )}

      {/* Signal History */}
      <div className="section-title" style={{ marginTop: 20 }}>
        📋 Signal History ({history.length})
      </div>

      {history.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state-icon">📭</div>
          <div className="empty-state-text">No history yet</div>
        </div>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Time</th>
                <th>Symbol</th>
                <th>Dir</th>
                <th>Entry</th>
                <th>Target</th>
                <th>SL</th>
                <th>Conf</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {history.map(sig => (
                <tr key={sig.id}>
                  <td style={{ color: 'var(--muted)', fontSize: 11 }}>
                    {formatDateTime(sig.timestamp)}
                  </td>
                  <td style={{ fontWeight: 600 }}>{sig.symbol}</td>
                  <td>
                    <span className={`badge ${sig.direction === 'UP' ? 'badge-up' : 'badge-down'}`}
                      style={{ fontSize: 10, padding: '2px 6px' }}>
                      {sig.direction === 'UP' ? '▲' : '▼'}
                    </span>
                  </td>
                  <td>{formatPrice(sig.entry)}</td>
                  <td style={{ color: 'var(--green)' }}>{formatPrice(sig.target)}</td>
                  <td style={{ color: 'var(--red)' }}>{formatPrice(sig.sl)}</td>
                  <td>
                    <span style={{
                      color: sig.confidence >= 85 ? 'var(--green)' : 'var(--text)',
                      fontWeight: 700,
                    }}>
                      {sig.confidence}%
                    </span>
                  </td>
                  <td>
                    <span className={
                      sig.status === 'WIN' ? 'badge badge-win' :
                      sig.status === 'LOSS' ? 'badge badge-loss' :
                      'badge badge-open'
                    } style={{ fontSize: 10, padding: '2px 6px' }}>
                      {sig.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div style={{ height: 12 }} />
    </div>
  )
}
