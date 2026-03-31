export interface BotStatus {
  scanning: boolean;
  todayCalls: number;
  winRate: number;
}

export interface Signal {
  id: string;
  symbol: string;
  direction: 'UP' | 'DOWN';
  entry: number;
  target: number;
  sl: number;
  confidence: number;
  timestamp: string;
  status: 'OPEN' | 'WIN' | 'LOSS';
  currentPrice?: number;
}

export interface Candle {
  timestamp: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface MarketData {
  candles: Candle[];
}

export interface Snapshot {
  NIFTY50: number;
  SENSEX: number;
  BANKNIFTY: number;
}

export interface Indicators {
  rsi: number;
  adx: number;
  macd: number;
  vwap: number;
  ema20: number;
  atr: number;
}

export interface HistorySignal extends Signal {
  exitPrice?: number;
  pnl?: number;
}

export type SymbolType = 'NIFTY50' | 'SENSEX' | 'BANKNIFTY';

export interface TimeSlabStat {
  slab: string;
  trades: number;
  wins: number;
  losses: number;
  winRate: number;
}

export interface DailyPnL {
  date: string;
  wins: number;
  losses: number;
  netPoints: number;
}

export interface SymbolStat {
  symbol: SymbolType;
  calls: number;
  winRate: number;
  netPoints: number;
}

export interface Settings {
  serverUrl: string;
  upstoxToken: string;
  paperTrading: boolean;
  minConfidence: number;
}
