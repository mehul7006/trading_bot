import { useState, useEffect, useCallback } from 'react'
import { testConnection, saveSettings } from '../api/client'
import type { Settings } from '../types'

const DEFAULT_SETTINGS: Settings = {
  serverUrl: '',
  upstoxToken: '',
  paperTrading: true,
  minConfidence: 87,
}

function loadSettings(): Settings {
  try {
    return {
      serverUrl: localStorage.getItem('serverUrl') ?? '',
      upstoxToken: localStorage.getItem('upstoxToken') ?? '',
      paperTrading: localStorage.getItem('paperTrading') !== 'false',
      minConfidence: parseInt(localStorage.getItem('minConfidence') ?? '87', 10),
    }
  } catch {
    return DEFAULT_SETTINGS
  }
}

function saveToLocalStorage(settings: Settings): void {
  localStorage.setItem('serverUrl', settings.serverUrl)
  localStorage.setItem('upstoxToken', settings.upstoxToken)
  localStorage.setItem('paperTrading', settings.paperTrading.toString())
  localStorage.setItem('minConfidence', settings.minConfidence.toString())
}

type ConnectionStatus = 'idle' | 'checking' | 'connected' | 'error'

interface ConnectionResultProps {
  status: ConnectionStatus
  message: string
}

function ConnectionResult({ status, message }: ConnectionResultProps) {
  if (status === 'idle') return null

  if (status === 'checking') {
    return (
      <div className="alert alert-info" style={{ marginTop: 8 }}>
        <div className="spinner spinner-sm" />
        Testing connection...
      </div>
    )
  }

  if (status === 'connected') {
    return (
      <div className="alert alert-success" style={{ marginTop: 8 }}>
        ✅ {message}
      </div>
    )
  }

  return (
    <div className="alert alert-error" style={{ marginTop: 8 }}>
      ❌ {message}
    </div>
  )
}

export default function Settings() {
  const [settings, setSettings] = useState<Settings>(DEFAULT_SETTINGS)
  const [saving, setSaving] = useState(false)
  const [saveMessage, setSaveMessage] = useState<string | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [connStatus, setConnStatus] = useState<ConnectionStatus>('idle')
  const [connMessage, setConnMessage] = useState('')
  const [showToken, setShowToken] = useState(false)

  useEffect(() => {
    setSettings(loadSettings())
  }, [])

  const handleSave = useCallback(async () => {
    setSaving(true)
    setSaveMessage(null)
    setSaveError(null)
    try {
      saveToLocalStorage(settings)
      try {
        await saveSettings(settings)
      } catch {
        // API might not be available — local save still worked
      }
      setSaveMessage('✅ Settings saved successfully')
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : 'Failed to save')
    } finally {
      setSaving(false)
    }
  }, [settings])

  const handleTestConnection = useCallback(async () => {
    setConnStatus('checking')
    setConnMessage('')
    try {
      const result = await testConnection()
      setConnStatus('connected')
      setConnMessage(
        `Connected! Bot is ${result.scanning ? 'scanning' : 'idle'}. Today: ${result.todayCalls} calls, ${result.winRate.toFixed(1)}% win rate.`
      )
    } catch (err) {
      setConnStatus('error')
      setConnMessage(err instanceof Error ? err.message : 'Connection refused')
    }
  }, [])

  const handleReset = useCallback(() => {
    if (confirm('Reset all settings to defaults?')) {
      setSettings(DEFAULT_SETTINGS)
      localStorage.clear()
      setSaveMessage('✅ Settings reset to defaults')
    }
  }, [])

  return (
    <div className="page">
      {/* Connection */}
      <div className="settings-section">
        <div className="settings-section-title">🌐 Server Connection</div>

        <div className="form-group">
          <label className="form-label" htmlFor="serverUrl">
            Backend Server URL
          </label>
          <input
            id="serverUrl"
            type="url"
            className="form-input"
            placeholder="http://localhost:8080 or https://your-render-url.com"
            value={settings.serverUrl}
            onChange={e => setSettings(prev => ({ ...prev, serverUrl: e.target.value }))}
            autoComplete="off"
            autoCapitalize="off"
          />
          <div style={{ fontSize: 11, color: 'var(--muted)', marginTop: 4 }}>
            Leave empty to use /api proxy (local development)
          </div>
        </div>

        <button
          className="btn btn-secondary"
          onClick={handleTestConnection}
          disabled={connStatus === 'checking'}
          style={{ marginBottom: 4 }}
        >
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: 6,
          }}>
            <div className={`connection-dot ${
              connStatus === 'connected' ? 'connected' :
              connStatus === 'error' ? 'disconnected' :
              connStatus === 'checking' ? 'checking' : 'disconnected'
            }`} />
            {connStatus === 'checking' ? 'Testing...' : 'Test Connection'}
          </div>
        </button>

        <ConnectionResult status={connStatus} message={connMessage} />
      </div>

      {/* API Keys */}
      <div className="settings-section">
        <div className="settings-section-title">🔑 API Configuration</div>

        <div className="form-group">
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
            <label className="form-label" htmlFor="upstoxToken" style={{ marginBottom: 0 }}>
              Upstox Access Token
            </label>
            <button
              style={{
                background: 'transparent',
                border: 'none',
                color: 'var(--blue)',
                fontSize: 12,
                cursor: 'pointer',
                padding: '2px 4px',
              }}
              onClick={() => setShowToken(prev => !prev)}
              type="button"
            >
              {showToken ? '🙈 Hide' : '👁️ Show'}
            </button>
          </div>
          <textarea
            id="upstoxToken"
            className="form-input form-textarea"
            placeholder="Paste your Upstox access token here..."
            value={settings.upstoxToken}
            onChange={e => setSettings(prev => ({ ...prev, upstoxToken: e.target.value }))}
            style={{
              filter: showToken ? 'none' : 'blur(4px)',
              transition: 'filter 0.2s',
              WebkitTextSecurity: showToken ? 'none' : 'disc',
            } as React.CSSProperties}
          />
          <div style={{ fontSize: 11, color: 'var(--muted)', marginTop: 4 }}>
            Generate from Upstox developer portal. Expires daily.
          </div>
        </div>
      </div>

      {/* Trading Settings */}
      <div className="settings-section">
        <div className="settings-section-title">⚙️ Trading Configuration</div>

        <div className="form-group">
          <div className="toggle-row">
            <div className="toggle-info">
              <span className="toggle-label">Paper Trading Mode</span>
              <span className="toggle-desc">Simulate trades without real money</span>
            </div>
            <label className="toggle-switch">
              <input
                type="checkbox"
                checked={settings.paperTrading}
                onChange={e => setSettings(prev => ({ ...prev, paperTrading: e.target.checked }))}
              />
              <span className="toggle-slider" />
            </label>
          </div>
        </div>

        {!settings.paperTrading && (
          <div className="alert alert-error" style={{ marginBottom: 14 }}>
            ⚠️ Live trading mode — real money at risk!
          </div>
        )}

        <div className="form-group">
          <label className="form-label" htmlFor="minConf">
            Minimum Signal Confidence
          </label>
          <div className="slider-row">
            <span style={{ fontSize: 12, color: 'var(--muted)', whiteSpace: 'nowrap' }}>80%</span>
            <input
              id="minConf"
              type="range"
              className="form-slider"
              min={80}
              max={95}
              step={1}
              value={settings.minConfidence}
              onChange={e => setSettings(prev => ({
                ...prev,
                minConfidence: parseInt(e.target.value, 10),
              }))}
            />
            <span style={{ fontSize: 12, color: 'var(--muted)', whiteSpace: 'nowrap' }}>95%</span>
            <span className="slider-value">{settings.minConfidence}%</span>
          </div>
          <div style={{ fontSize: 11, color: 'var(--muted)', marginTop: 4 }}>
            Only execute signals with confidence ≥ {settings.minConfidence}%
          </div>
        </div>
      </div>

      {/* Save / messages */}
      {saveMessage && (
        <div className="alert alert-success">
          {saveMessage}
        </div>
      )}
      {saveError && (
        <div className="alert alert-error">
          ❌ {saveError}
        </div>
      )}

      <button
        className="btn btn-primary btn-full"
        onClick={handleSave}
        disabled={saving}
        style={{ marginBottom: 10 }}
      >
        {saving ? (
          <>
            <div className="spinner spinner-sm" />
            Saving...
          </>
        ) : (
          '💾 Save Settings'
        )}
      </button>

      <button
        className="btn btn-secondary btn-full"
        onClick={handleReset}
        style={{ marginBottom: 16 }}
      >
        🔄 Reset to Defaults
      </button>

      {/* App Info */}
      <div className="settings-section">
        <div className="settings-section-title">ℹ️ App Information</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {[
            ['Version', 'V24.0 Institutional'],
            ['Strategy', 'SMC + Institutional Grade'],
            ['Refresh', 'Every 30 seconds'],
            ['Mode', settings.paperTrading ? 'Paper Trading' : 'Live Trading'],
            ['Min Confidence', `${settings.minConfidence}%`],
          ].map(([key, val]) => (
            <div key={key} style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              fontSize: 13,
            }}>
              <span style={{ color: 'var(--muted)' }}>{key}</span>
              <span style={{ fontWeight: 600 }}>{val}</span>
            </div>
          ))}
        </div>
      </div>

      <div style={{ height: 12 }} />
    </div>
  )
}
