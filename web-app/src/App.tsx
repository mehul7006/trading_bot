import { BrowserRouter, Routes, Route, NavLink, useLocation } from 'react-router-dom'
import Dashboard from './pages/Dashboard'
import Charts from './pages/Charts'
import Signals from './pages/Signals'
import Stats from './pages/Stats'
import Settings from './pages/Settings'

const NAV_ITEMS = [
  { path: '/', icon: '📊', label: 'Dashboard' },
  { path: '/charts', icon: '📈', label: 'Charts' },
  { path: '/signals', icon: '🔔', label: 'Signals' },
  { path: '/stats', icon: '📉', label: 'Stats' },
  { path: '/settings', icon: '⚙️', label: 'Settings' },
]

const PAGE_TITLES: Record<string, string> = {
  '/': '📊 Dashboard',
  '/charts': '📈 Charts',
  '/signals': '🔔 Signals',
  '/stats': '📉 Statistics',
  '/settings': '⚙️ Settings',
}

function AppHeader() {
  const location = useLocation()
  const title = PAGE_TITLES[location.pathname] ?? '📈 Trading'

  return (
    <header className="app-header">
      <h1>{title}</h1>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <div className="dot dot-green" style={{ width: 8, height: 8 }} />
        <span style={{ fontSize: 11, color: 'var(--muted)' }}>LIVE</span>
      </div>
    </header>
  )
}

function BottomNav() {
  return (
    <nav className="bottom-nav">
      {NAV_ITEMS.map((item) => (
        <NavLink
          key={item.path}
          to={item.path}
          className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
          end={item.path === '/'}
        >
          <span className="nav-icon">{item.icon}</span>
          <span className="nav-label">{item.label}</span>
        </NavLink>
      ))}
    </nav>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <div className="app-container">
        <AppHeader />
        <main className="app-content">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/charts" element={<Charts />} />
            <Route path="/signals" element={<Signals />} />
            <Route path="/stats" element={<Stats />} />
            <Route path="/settings" element={<Settings />} />
          </Routes>
        </main>
        <BottomNav />
      </div>
    </BrowserRouter>
  )
}
