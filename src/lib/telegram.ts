/**
 * Helper utilities for Telegram WebApp SDK & Telegram User Authentication
 */

declare global {
  interface Window {
    Telegram?: {
      WebApp?: any;
    };
  }
}

export interface TelegramUser {
  id: number;
  first_name: string;
  last_name?: string;
  username?: string;
  language_code?: string;
  is_premium?: boolean;
}

export interface TelegramWebAppInitData {
  query_id?: string;
  user?: TelegramUser;
  auth_date?: number;
  hash?: string;
  start_param?: string;
}

export function getTelegramWebApp() {
  if (typeof window !== 'undefined' && window.Telegram && window.Telegram.WebApp) {
    return window.Telegram.WebApp;
  }
  return null;
}

export function getTelegramInitData(): string {
  const tg = getTelegramWebApp();
  if (tg && tg.initData) {
    return tg.initData;
  }
  return '';
}

export function getTelegramUser(): TelegramUser | null {
  const tg = getTelegramWebApp();
  if (tg && tg.initDataUnsafe && tg.initDataUnsafe.user) {
    return tg.initDataUnsafe.user as TelegramUser;
  }

  // Fallback demo user for browser preview when not launched directly inside Telegram
  const stored = localStorage.getItem('infinitehits_demo_user');
  if (stored) {
    try {
      return JSON.parse(stored);
    } catch (e) {
      console.error(e);
    }
  }

  const defaultDemoUser: TelegramUser = {
    id: 987654321,
    first_name: 'Alex',
    last_name: 'Developer',
    username: 'alex_trader',
    language_code: 'en',
  };
  localStorage.setItem('infinitehits_demo_user', JSON.stringify(defaultDemoUser));
  return defaultDemoUser;
}

export function initializeTelegramTheme() {
  const tg = getTelegramWebApp();
  if (tg) {
    tg.ready();
    tg.expand();
    try {
      if (tg.setHeaderColor) tg.setHeaderColor('#0f172a');
      if (tg.setBackgroundColor) tg.setBackgroundColor('#020617');
    } catch (e) {
      // Ignore if webapp theme API isn't available
    }
  }
}
