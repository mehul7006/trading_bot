import { useState, useEffect, useCallback, useRef } from 'react';
import type {
  BotStatus,
  Signal,
  Snapshot,
  HistorySignal,
  Indicators,
  MarketData,
  SymbolType,
} from '../types';
import {
  fetchStatus,
  fetchSignals,
  fetchSnapshot,
  fetchHistory,
  fetchIndicators,
  fetchMarketData,
} from '../api/client';

interface TradingDataState {
  status: BotStatus | null;
  signals: Signal[];
  snapshot: Snapshot | null;
  history: HistorySignal[];
  indicators: Indicators | null;
  marketData: MarketData | null;
  loading: boolean;
  error: string | null;
  lastUpdated: Date | null;
  selectedSymbol: SymbolType;
}

interface TradingDataActions {
  refresh: () => void;
  setSymbol: (symbol: SymbolType) => void;
}

export function useTradingData(): TradingDataState & TradingDataActions {
  const [status, setStatus] = useState<BotStatus | null>(null);
  const [signals, setSignals] = useState<Signal[]>([]);
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [history, setHistory] = useState<HistorySignal[]>([]);
  const [indicators, setIndicators] = useState<Indicators | null>(null);
  const [marketData, setMarketData] = useState<MarketData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [selectedSymbol, setSelectedSymbol] = useState<SymbolType>('NIFTY50');

  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const selectedSymbolRef = useRef<SymbolType>(selectedSymbol);

  useEffect(() => {
    selectedSymbolRef.current = selectedSymbol;
  }, [selectedSymbol]);

  const fetchCoreData = useCallback(async () => {
    try {
      setError(null);
      const [statusData, snapshotData, signalsData] = await Promise.all([
        fetchStatus(),
        fetchSnapshot(),
        fetchSignals(),
      ]);
      setStatus(statusData);
      setSnapshot(snapshotData);
      setSignals(signalsData);
      setLastUpdated(new Date());
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to fetch data';
      setError(msg);
    }
  }, []);

  const fetchSymbolData = useCallback(async (symbol: SymbolType) => {
    try {
      const [indicatorsData, marketDataResult] = await Promise.all([
        fetchIndicators(symbol),
        fetchMarketData(symbol),
      ]);
      setIndicators(indicatorsData);
      setMarketData(marketDataResult);
    } catch (err) {
      // Non-critical, don't set global error
      console.warn('Failed to fetch symbol data:', err);
    }
  }, []);

  const fetchHistoryData = useCallback(async () => {
    try {
      const historyData = await fetchHistory();
      setHistory(historyData);
    } catch (err) {
      console.warn('Failed to fetch history:', err);
    }
  }, []);

  const refresh = useCallback(async () => {
    setLoading(true);
    await Promise.all([
      fetchCoreData(),
      fetchHistoryData(),
      fetchSymbolData(selectedSymbolRef.current),
    ]);
    setLoading(false);
  }, [fetchCoreData, fetchHistoryData, fetchSymbolData]);

  // Initial load
  useEffect(() => {
    let mounted = true;

    const init = async () => {
      setLoading(true);
      await Promise.all([
        fetchCoreData(),
        fetchHistoryData(),
      ]);
      if (mounted) setLoading(false);
    };

    init();

    // Auto-refresh every 30 seconds
    intervalRef.current = setInterval(() => {
      fetchCoreData();
    }, 30000);

    return () => {
      mounted = false;
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, [fetchCoreData, fetchHistoryData]);

  // Fetch symbol-specific data when symbol changes
  useEffect(() => {
    fetchSymbolData(selectedSymbol);
  }, [selectedSymbol, fetchSymbolData]);

  const setSymbol = useCallback((symbol: SymbolType) => {
    setSelectedSymbol(symbol);
  }, []);

  return {
    status,
    signals,
    snapshot,
    history,
    indicators,
    marketData,
    loading,
    error,
    lastUpdated,
    selectedSymbol,
    refresh,
    setSymbol,
  };
}
