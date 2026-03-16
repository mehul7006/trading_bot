import axios, { AxiosInstance, AxiosError } from 'axios';
import type {
  BotStatus,
  Signal,
  MarketData,
  Snapshot,
  Indicators,
  HistorySignal,
  SymbolType,
  Settings,
} from '../types';

function getBaseUrl(): string {
  const stored = localStorage.getItem('serverUrl');
  if (stored && stored.trim() !== '') {
    return stored.trim();
  }
  return '/api';
}

function createAxiosInstance(): AxiosInstance {
  return axios.create({
    baseURL: getBaseUrl(),
    timeout: 10000,
    headers: {
      'Content-Type': 'application/json',
    },
  });
}

async function withRetry<T>(fn: () => Promise<T>): Promise<T> {
  try {
    return await fn();
  } catch (err) {
    const axiosErr = err as AxiosError;
    if (axiosErr.code === 'ECONNABORTED' || axiosErr.code === 'ERR_NETWORK') {
      // retry once
      return await fn();
    }
    throw err;
  }
}

export async function fetchStatus(): Promise<BotStatus> {
  return withRetry(async () => {
    const client = createAxiosInstance();
    const res = await client.get<BotStatus>('/status');
    return res.data;
  });
}

export async function fetchSignals(): Promise<Signal[]> {
  return withRetry(async () => {
    const client = createAxiosInstance();
    const res = await client.get<Signal[]>('/signals');
    return res.data;
  });
}

export async function fetchMarketData(symbol: SymbolType): Promise<MarketData> {
  return withRetry(async () => {
    const client = createAxiosInstance();
    const res = await client.get<MarketData>('/marketdata', {
      params: { symbol },
    });
    return res.data;
  });
}

export async function fetchSnapshot(): Promise<Snapshot> {
  return withRetry(async () => {
    const client = createAxiosInstance();
    const res = await client.get<Snapshot>('/snapshot');
    return res.data;
  });
}

export async function fetchHistory(): Promise<HistorySignal[]> {
  return withRetry(async () => {
    const client = createAxiosInstance();
    const res = await client.get<HistorySignal[]>('/history');
    return res.data;
  });
}

export async function fetchIndicators(symbol: SymbolType): Promise<Indicators> {
  return withRetry(async () => {
    const client = createAxiosInstance();
    const res = await client.get<Indicators>('/indicators', {
      params: { symbol },
    });
    return res.data;
  });
}

export async function saveSettings(settings: Partial<Settings>): Promise<void> {
  return withRetry(async () => {
    const client = createAxiosInstance();
    await client.post('/settings', settings);
  });
}

export async function testConnection(): Promise<BotStatus> {
  return fetchStatus();
}
