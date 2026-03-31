import { useState, useEffect, useCallback } from 'react'
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  LineChart,
  Line,
  Legend,
} from 'recharts'
import { fetchStatus, fetchHistory } from '../api/client'
import type { BotStatus, HistorySignal, SymbolType, DailyPnL, TimeSlabStat } from '../types'

interface StatCardProps {
  label: string
  value: string
  sub?: string
  color?: string
}

function StatCard({ label, value, sub, color }: StatCardProps) {
  return (
    <div className="stat-card">
      <div className="stat-card-label">{label}</div>
      <div className="stat-card-value" style={{ color: color ?? 'var(--text)' }}>
        {value}
      </div>
      {sub && <div className="stat-card-sub">{sub}</div>}
    </div>
  )
}

// Generate mock daily data
function generateMockDailyData(): DailyPnL[] {
  const days = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Mon', 'Tue']
  return days.map(day => {
    const wins = Math.floor(2 + Math.random() * 4)
    const losses = Math.floor(Math.random() * 2)
    const netPoints = wins * (80 + Math.random() * 60) - losses * (40 + Math.random() * 30)
    return { date: day, wins, losses, netPoints: Math.round(netPoints) }
  })
}

// Generate mock time slab data
function generateMockSlabData(): TimeSlabStat[] {
  return [
    { slab: '9:15–11:00', trades: 8, wins: 6, losses: 2, winRate: 75 },
    { slab: '11:00–13:00', trades: 12, wins: 9, losses: 3, winRate: 75 },
    { slab: '13:00–15:30', trades: 10, wins: 7, losses: 3, winRate: 70 },
  ]
}

function computeStats(history: HistorySignal[], status: BotStatus | null) {
  const wins = history.filter(h => h.status === 'WIN').length
  const losses = history.filter(h => h.status === 'LOSS').length
  const open = history.filter(h => h.status === 'OPEN').length
  const total = wins + losses
  const winRate = total > 0 ? (wins / total) * 100 : status?.winRate ?? 0
  const netPoints = history.reduce((acc, h) => acc + (h.pnl ?? 0), 0)
  const todayCalls = status?.todayCalls ?? total

  return { wins, losses, open, total, winRate, netPoints, todayCalls }
}

function computeSymbolStats(history: HistorySignal[]) {
  const symbols: SymbolType[] = ['NIFTY50', 'SENSEX', 'BANKNIFTY']
  return symbols.map(sym => {
    const sigs = history.filter(h => h.symbol === sym)
    const wins = sigs.filter(h => h.status === 'WIN').length
    const total = sigs.filter(h => h.status !== 'OPEN').length
    const wr = total > 0 ? Math.round((wins / total) * 100) : 0
    const netPts = Math.round(sigs.reduce((a, h) => a + (h.pnl ?? 0), 0))
    return { symbol: sym, calls: sigs.length, winRate: wr, netPoints: netPts }
  })
}

interface CustomTooltipProps {
  active?: boolean
  payload?: Array<{ value: number; name: string; color: string }>
  label?: string
}

function CustomBarTooltip({ active, payload, label }: CustomTooltipProps) {
  if (!active || !payload?.length) return null
  return (
    <div style={{
      background: 'var(--card)',
      border: '1px solid var(--border)',
      borderRadius: 8,
      padding: '8px 12px',
      fontSize: 12,
    }}>
      <div style={{ fontWeight: 600, marginBottom: 4, color: 'var(--text)' }}>{label}</div>
      {payload.map(p => (
        <div key={p.name} style={{ color: p.color }}>
          {p.name}: {p.value}
        </div>
      ))}
    </div>
  )
}

function CustomLineTooltip({ active, payload, label }: CustomTooltipProps) {
  if (!active || !payload?.length) return null
  const val = payload[0]?.value ?? 0
  return (
    <div style={{
      background: 'var(--card)',
      border: '1px solid var(--border)',
      borderRadius: 8,
      padding: '8px 12px',
      fontSize: 12,
    }}>
      <div style={{ fontWeight: 600, color: 'var(--text)' }}>{label}</div>
      <div style={{ color: val >= 0 ? 'var(--green)' : 'var(--red)', fontWeight: 700 }}>
        {val >= 0 ? '+' : ''}{val} pts
      </div>
    </div>
  )
}

export default function Stats() {
  const [status, setStatus] = useState<BotStatus | null>(null)
  const [history, setHistory] = useState<HistorySignal[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadData = useCallback(async () => {
    setError(null)
    try {
      const [statusData, historyData] = await Promise.all([
        fetchStatus().catch(() => null),
        fetchHistory().catch(() => []),
      ])
      setStatus(statusData)
      setHistory(historyData.length > 0 ? historyData : generateMockHistory())
    } catch {
      setError('Using demo data')
      setHistory(generateMockHistory())
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadData()
    const interval = setInterval(loadData, 30000)
    return () => clearInterval(interval)
  }, [loadData])

  function generateMockHistory(): HistorySignal[] {
    const results = [
      { s: 'WIN', dir: 'UP', sym: 'NIFTY50', pnl: 95 },
      { s: 'WIN', dir: 'DOWN', sym: 'BANKNIFTY', pnl: 140 },
      { s: 'LOSS', dir: 'UP', sym: 'SENSEX', pnl: -45 },
      { s: 'WIN', dir: 'UP', sym: 'NIFTY50', pnl: 110 },
      { s: 'WIN', dir: 'DOWN', sym: 'BANKNIFTY', pnl: 120 },
      { s: 'LOSS', dir: 'UP', sym: 'NIFTY50', pnl: -38 },
      { s: 'WIN', dir: 'UP', sym: 'SENSEX', pnl: 180 },
      { s: 'WIN', dir: 'DOWN', sym: 'BANKNIFTY', pnl: 95 },
      { s: 'WIN', dir: 'UP', sym: 'NIFTY50', pnl: 78 },
      { s: 'LOSS', dir: 'DOWN', sym: 'SENSEX', pnl: -52 },
    ]
    const now = Date.now()
    return results.map((r, i) => ({
      id: `h${i}`,
      symbol: r.sym as SymbolType,
      direction: r.dir as 'UP' | 'DOWN',
      entry: 22000 + i * 100,
      target: 22100 + i * 100,
      sl: 21950 + i * 100,
      confidence: 82 + Math.floor(Math.random() * 10),
      timestamp: new Date(now - (i + 1) * 2.5 * 3600000).toISOString(),
      status: r.s as 'WIN' | 'LOSS' | 'OPEN',
      pnl: r.pnl,
    }))
  }

  if (loading) {
    return (
      <div className="loading-overlay">
        <div className="spinner" />
        <span>Loading statistics...</span>
      </div>
    )
  }

  const stats = computeStats(history, status)
  const symbolStats = computeSymbolStats(history)
  const dailyData = generateMockDailyData()
  const slabData = generateMockSlabData()

  // Cumulative P&L
  let cumPnl = 0
  const cumulativePnL = dailyData.map(d => {
    cumPnl += d.netPoints
    return { date: d.date, pnl: Math.round(cumPnl) }
  })

  return (
    <div className="page">
      {error && (
        <div className="alert alert-info" style={{ fontSize: 12 }}>
          ℹ️ {error}
        </div>
      )}

      {/* Top stat cards */}
      <div className="stat-grid">
        <StatCard
          label="Total Calls"
          value={stats.todayCalls.toString()}
          sub="Today's signals"
        />
        <StatCard
          label="Win Rate"
          value={`${stats.winRate.toFixed(1)}%`}
          sub={`${stats.wins}W / ${stats.losses}L`}
          color={stats.winRate >= 60 ? 'var(--green)' : 'var(--red)'}
        />
        <StatCard
          label="Net Points"
          value={`${stats.netPoints >= 0 ? '+' : ''}${stats.netPoints.toFixed(0)}`}
          sub="Est. pts gained"
          color={stats.netPoints >= 0 ? 'var(--green)' : 'var(--red)'}
        />
        <StatCard
          label="Active"
          value={stats.open.toString()}
          sub="Open signals"
          color="var(--blue)"
        />
      </div>

      {/* Time Slab Breakdown */}
      <div className="section-title">⏰ Time Slab Breakdown</div>
      <div className="slab-table-wrap">
        <table style={{ minWidth: 'unset' }}>
          <thead>
            <tr>
              <th>Time Slab</th>
              <th style={{ textAlign: 'center' }}>Trades</th>
              <th style={{ textAlign: 'center' }}>W/L</th>
              <th style={{ textAlign: 'center' }}>Win %</th>
            </tr>
          </thead>
          <tbody>
            {slabData.map(row => (
              <tr key={row.slab}>
                <td style={{ fontWeight: 600, fontSize: 12 }}>{row.slab}</td>
                <td style={{ textAlign: 'center' }}>{row.trades}</td>
                <td style={{ textAlign: 'center' }}>
                  <span style={{ color: 'var(--green)', fontWeight: 600 }}>{row.wins}</span>
                  <span style={{ color: 'var(--muted)' }}>/</span>
                  <span style={{ color: 'var(--red)', fontWeight: 600 }}>{row.losses}</span>
                </td>
                <td style={{ textAlign: 'center' }}>
                  <span style={{
                    fontWeight: 700,
                    color: row.winRate >= 70 ? 'var(--green)' : row.winRate >= 55 ? 'var(--text)' : 'var(--red)',
                  }}>
                    {row.winRate}%
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Daily Win/Loss Bar Chart */}
      <div className="section-title">📊 Daily Performance (Last 7 Days)</div>
      <div className="card">
        <ResponsiveContainer width="100%" height={180}>
          <BarChart data={dailyData} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
            <XAxis dataKey="date" tick={{ fill: 'var(--muted)', fontSize: 11 }} axisLine={false} tickLine={false} />
            <YAxis tick={{ fill: 'var(--muted)', fontSize: 11 }} axisLine={false} tickLine={false} />
            <Tooltip content={<CustomBarTooltip />} />
            <Legend
              wrapperStyle={{ fontSize: 11, paddingTop: 8 }}
              formatter={(value) => (
                <span style={{ color: 'var(--muted)' }}>{value}</span>
              )}
            />
            <Bar dataKey="wins" name="Wins" fill="var(--green)" radius={[3, 3, 0, 0]} />
            <Bar dataKey="losses" name="Losses" fill="var(--red)" radius={[3, 3, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>

      {/* Cumulative P&L Chart */}
      <div className="section-title">📈 Cumulative P&L (Points)</div>
      <div className="card">
        <ResponsiveContainer width="100%" height={160}>
          <LineChart data={cumulativePnL} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
            <XAxis dataKey="date" tick={{ fill: 'var(--muted)', fontSize: 11 }} axisLine={false} tickLine={false} />
            <YAxis tick={{ fill: 'var(--muted)', fontSize: 11 }} axisLine={false} tickLine={false} />
            <Tooltip content={<CustomLineTooltip />} />
            <Line
              type="monotone"
              dataKey="pnl"
              name="P&L"
              stroke="var(--blue)"
              strokeWidth={2}
              dot={{ fill: 'var(--blue)', r: 3 }}
              activeDot={{ r: 5 }}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>

      {/* Per-Symbol Breakdown */}
      <div className="section-title">🔍 Symbol Breakdown</div>
      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <div style={{
          display: 'flex',
          padding: '8px 14px',
          fontSize: 10,
          color: 'var(--muted)',
          fontWeight: 600,
          textTransform: 'uppercase',
          letterSpacing: '0.5px',
          background: 'rgba(48,54,61,0.3)',
          borderBottom: '1px solid var(--border)',
        }}>
          <span style={{ flex: 2 }}>Symbol</span>
          <span style={{ flex: 1, textAlign: 'center' }}>Calls</span>
          <span style={{ flex: 1, textAlign: 'center' }}>Win %</span>
          <span style={{ flex: 1, textAlign: 'right' }}>Net Pts</span>
        </div>
        {symbolStats.map(row => (
          <div key={row.symbol} style={{
            display: 'flex',
            alignItems: 'center',
            padding: '12px 14px',
            borderBottom: '1px solid var(--border)',
          }}>
            <div style={{ flex: 2, fontWeight: 700, fontSize: 13 }}>{row.symbol}</div>
            <div style={{ flex: 1, textAlign: 'center', fontSize: 13, fontWeight: 600 }}>
              {row.calls}
            </div>
            <div style={{ flex: 1, textAlign: 'center' }}>
              <span style={{
                fontWeight: 700,
                fontSize: 13,
                color: row.winRate >= 65 ? 'var(--green)' : row.winRate >= 50 ? 'var(--text)' : 'var(--red)',
              }}>
                {row.winRate}%
              </span>
            </div>
            <div style={{
              flex: 1,
              textAlign: 'right',
              fontWeight: 700,
              fontSize: 13,
              color: row.netPoints >= 0 ? 'var(--green)' : 'var(--red)',
            }}>
              {row.netPoints >= 0 ? '+' : ''}{row.netPoints}
            </div>
          </div>
        ))}
      </div>

      <div style={{ height: 12 }} />
    </div>
  )
}
