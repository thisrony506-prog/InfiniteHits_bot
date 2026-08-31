import React from 'react';
import { User } from '../types';
import { Sparkles, Bot, ShieldCheck, Smartphone, Coins } from 'lucide-react';

interface NavbarProps {
  user: User | null;
  activeView: 'miniapp' | 'bot' | 'admin';
  setActiveView: (view: 'miniapp' | 'bot' | 'admin') => void;
  onRefreshUser: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({ user, activeView, setActiveView, onRefreshUser }) => {
  return (
    <header className="sticky top-0 z-40 bg-slate-900/90 backdrop-blur-md border-b border-slate-800 text-slate-100 shadow-xl">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
        
        {/* Brand identity */}
        <div className="flex items-center space-x-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-600 via-purple-600 to-pink-500 flex items-center justify-center shadow-lg shadow-indigo-500/20">
            <Sparkles className="w-5 h-5 text-white animate-pulse" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <span className="font-extrabold text-lg tracking-tight bg-clip-text text-transparent bg-gradient-to-r from-white via-slate-100 to-indigo-200">
                InfiniteHits
              </span>
              <span className="bg-indigo-500/20 text-indigo-300 text-[10px] font-semibold px-2 py-0.5 rounded-full border border-indigo-500/30">
                Telegram Platform
              </span>
            </div>
            <p className="text-[11px] text-slate-400 hidden sm:block font-medium">
              More Hits. More Growth. Infinite Possibilities.
            </p>
          </div>
        </div>

        {/* View mode buttons */}
        <div className="flex items-center bg-slate-950 p-1 rounded-xl border border-slate-800/80">
          <button
            onClick={() => setActiveView('miniapp')}
            className={`flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
              activeView === 'miniapp'
                ? 'bg-gradient-to-r from-indigo-600 to-purple-600 text-white shadow-md'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
            }`}
          >
            <Smartphone className="w-3.5 h-3.5" />
            <span>Mini App</span>
          </button>

          <button
            onClick={() => setActiveView('bot')}
            className={`flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
              activeView === 'bot'
                ? 'bg-gradient-to-r from-indigo-600 to-purple-600 text-white shadow-md'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
            }`}
          >
            <Bot className="w-3.5 h-3.5" />
            <span>Bot Simulator</span>
          </button>

          {user?.is_admin && (
            <button
              onClick={() => setActiveView('admin')}
              className={`flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                activeView === 'admin'
                  ? 'bg-gradient-to-r from-amber-600 to-rose-600 text-white shadow-md'
                  : 'text-amber-400 hover:text-amber-200 hover:bg-slate-900'
              }`}
            >
              <ShieldCheck className="w-3.5 h-3.5" />
              <span>Admin Panel</span>
            </button>
          )}
        </div>

        {/* User profile & credits */}
        {user && (
          <div className="flex items-center space-x-3">
            <div className="flex items-center space-x-1.5 bg-indigo-950/80 border border-indigo-500/30 px-3 py-1.5 rounded-xl shadow-inner">
              <Coins className="w-4 h-4 text-amber-400 animate-spin" style={{ animationDuration: '6s' }} />
              <span className="text-sm font-bold text-amber-300">{user.credits.toLocaleString()}</span>
              <span className="text-[10px] text-indigo-300 font-semibold uppercase tracking-wider">Credits</span>
            </div>
            
            <div className="hidden md:flex items-center space-x-2 bg-slate-800/60 px-2.5 py-1 rounded-xl border border-slate-700/50">
              <div className="w-7 h-7 rounded-lg bg-indigo-600 text-white flex items-center justify-center font-bold text-xs">
                {user.first_name.charAt(0)}
              </div>
              <div className="text-left text-xs">
                <div className="font-semibold text-slate-200 leading-none">{user.first_name}</div>
                <div className="text-[10px] text-slate-400 leading-none mt-0.5">@{user.username || user.telegram_id}</div>
              </div>
            </div>
          </div>
        )}

      </div>
    </header>
  );
};
