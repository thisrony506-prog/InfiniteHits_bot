import React, { useState, useEffect } from 'react';
import { User, SystemSettings } from './types';
import { getTelegramInitData, initializeTelegramTheme } from './lib/telegram';
import { Navbar } from './components/Navbar';
import { MiniAppView } from './components/MiniAppView';
import { BotSimulatorView } from './components/BotSimulatorView';
import { AdminPanelView } from './components/AdminPanelView';
import { Sparkles, ShieldAlert } from 'lucide-react';

export default function App() {
  const [user, setUser] = useState<User | null>(null);
  const [settings, setSettings] = useState<SystemSettings | null>(null);
  const [activeView, setActiveView] = useState<'miniapp' | 'bot' | 'admin'>('miniapp');
  const [initialTab, setInitialTab] = useState<string>('home');
  const [initialVisitCampaignId, setInitialVisitCampaignId] = useState<string | undefined>(undefined);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    initializeTelegramTheme();

    // Parse URL query parameters
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get('admin') === 'true') {
      setActiveView('admin');
    }
    if (urlParams.get('bot') === 'true') {
      setActiveView('bot');
    }
    const tabParam = urlParams.get('tab');
    if (tabParam) {
      setInitialTab(tabParam);
    }
    const visitParam = urlParams.get('visit');
    if (visitParam) {
      setInitialVisitCampaignId(visitParam);
      setInitialTab('traffic');
    }

    fetchUserData();
  }, []);

  const fetchUserData = async () => {
    try {
      const initDataRaw = getTelegramInitData();
      const headers: Record<string, string> = {};
      if (initDataRaw) {
        headers['x-telegram-init-data'] = initDataRaw;
      }

      const res = await fetch('/api/user/me', { headers });
      const data = await res.json();
      if (data.status === 'SUCCESS') {
        setUser(data.user);
        setSettings(data.settings);
      }
    } catch (e) {
      console.error('Failed to load user session', e);
    } finally {
      setIsLoading(false);
    }
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col items-center justify-center p-6 space-y-4">
        <div className="w-14 h-14 rounded-2xl bg-gradient-to-tr from-indigo-600 via-purple-600 to-pink-500 flex items-center justify-center shadow-xl shadow-indigo-500/20">
          <Sparkles className="w-8 h-8 text-white animate-pulse" />
        </div>
        <div className="text-center">
          <h1 className="text-xl font-bold text-white">InfiniteHits</h1>
          <p className="text-xs text-slate-400 mt-1">Connecting Telegram WebApp & Traffic Engine...</p>
        </div>
      </div>
    );
  }

  if (user && user.is_banned) {
    return (
      <div className="min-h-screen bg-slate-950 text-slate-100 flex items-center justify-center p-6">
        <div className="max-w-md w-full bg-slate-900 border border-rose-500/30 rounded-3xl p-8 text-center space-y-4 shadow-2xl">
          <ShieldAlert className="w-14 h-14 text-rose-500 mx-auto" />
          <h2 className="text-2xl font-black text-white">Account Suspended</h2>
          <p className="text-xs text-slate-300">
            Your InfiniteHits account has been restricted due to policy violations.
          </p>
          <div className="bg-slate-950 p-3 rounded-xl text-xs text-rose-400 font-medium">
            Reason: {user.ban_reason || 'Anti-abuse policy enforcement.'}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col">
      <Navbar
        user={user}
        activeView={activeView}
        setActiveView={setActiveView}
        onRefreshUser={fetchUserData}
      />

      <main className="flex-1 py-6 px-4">
        {activeView === 'miniapp' && (
          <MiniAppView
            user={user}
            settings={settings}
            initialTab={initialTab}
            initialVisitCampaignId={initialVisitCampaignId}
            onRefreshUser={fetchUserData}
          />
        )}

        {activeView === 'bot' && (
          <BotSimulatorView
            user={user}
            onRefreshData={fetchUserData}
            onOpenMiniAppTab={(tab) => {
              setInitialTab(tab);
              setActiveView('miniapp');
            }}
          />
        )}

        {activeView === 'admin' && user?.is_admin && (
          <AdminPanelView currentAdmin={user} onRefreshGlobalData={fetchUserData} />
        )}
      </main>
    </div>
  );
}
