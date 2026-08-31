import React, { useState, useEffect, useRef } from 'react';
import { User } from '../types';
import { Send, Bot, Sparkles, RefreshCw, CheckCheck, Smartphone } from 'lucide-react';

interface BotSimulatorViewProps {
  user: User | null;
  onRefreshData: () => void;
  onOpenMiniAppTab?: (tab: string) => void;
}

interface ChatMessage {
  id: string;
  sender: 'user' | 'bot';
  text: string;
  timestamp: string;
  reply_markup?: {
    inline_keyboard?: Array<Array<{ text: string; callback_data?: string; url?: string; web_app?: { url: string } }>>;
  };
}

export const BotSimulatorView: React.FC<BotSimulatorViewProps> = ({ user, onRefreshData, onOpenMiniAppTab }) => {
  const [inputText, setInputText] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isTyping, setIsTyping] = useState(false);
  const chatEndRef = useRef<HTMLDivElement>(null);

  // Auto-scroll chat to bottom
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isTyping]);

  // Initial trigger start message on load if chat empty
  useEffect(() => {
    if (messages.length === 0) {
      handleSendMessage('/start');
    }
  }, []);

  const handleSendMessage = async (textToSend?: string) => {
    const text = (textToSend || inputText).trim();
    if (!text) return;

    if (!textToSend) {
      setInputText('');
    }

    const userMsgId = `msg_${Date.now()}`;
    const userMsg: ChatMessage = {
      id: userMsgId,
      sender: 'user',
      text,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    };

    setMessages((prev) => [...prev, userMsg]);
    setIsTyping(true);

    try {
      const res = await fetch('/api/telegram/simulate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          text,
          from: user
            ? {
                id: user.telegram_id,
                first_name: user.first_name,
                last_name: user.last_name,
                username: user.username,
              }
            : { id: 987654321, first_name: 'Alex', username: 'alex_trader' },
        }),
      });

      // Poll or fetch updated bot output response
      await new Promise((resolve) => setTimeout(resolve, 400));
      onRefreshData();
      await fetchBotReply(text);
    } catch (e) {
      console.error(e);
    } finally {
      setIsTyping(false);
    }
  };

  const fetchBotReply = async (inputCommand: string) => {
    // Generate simulated bot response locally to mirror bot logic for instant UI rendering
    const userFirstName = user ? user.first_name : 'Alex';
    const credits = user ? user.credits : 50;
    const refCount = user ? user.referral_count : 0;
    const trafficDelivered = user ? user.traffic_delivered : 0;
    const refCode = user ? user.referral_code : 'REF9876';
    const isUserAdmin = user ? user.is_admin : true;

    let botText = '';
    let replyMarkup: ChatMessage['reply_markup'] = {
      inline_keyboard: [
        [
          { text: '🚀 Open Mini App', callback_data: 'btn_miniapp' },
        ],
        [
          { text: '🌐 Get Traffic', callback_data: 'btn_traffic' },
          { text: '➕ Promote Website', callback_data: 'btn_promote' },
        ],
        [
          { text: '💳 Buy Credits', callback_data: 'btn_buy' },
          { text: '🎁 Earn Credits', callback_data: 'btn_earn' },
        ],
        [
          { text: '👥 Referral', callback_data: 'btn_referral' },
          { text: '📊 Statistics', callback_data: 'btn_stats' },
        ],
        [
          { text: '📜 History', callback_data: 'btn_history' },
          { text: '🎧 Support', callback_data: 'btn_support' },
        ],
      ],
    };

    if (isUserAdmin) {
      replyMarkup.inline_keyboard?.push([{ text: '🛡️ Admin Dashboard', callback_data: 'btn_admin' }]);
    }

    if (inputCommand.startsWith('/start')) {
      botText = `🎉 <b>Welcome to InfiniteHits!</b>\n\nYou received:\n<b>50 Free Credits</b>\n\nUse your Credits to promote your website.\n<b>1 Credit = 1 eligible promotional visit/action.</b>\n\n🚀 <b>InfiniteHits</b>\nWelcome, ${userFirstName}\n\n💰 Credits: <b>${credits}</b>\n🌐 Active Campaigns: <b>1</b>\n👁️ Traffic Delivered: <b>${trafficDelivered}</b>\n👥 Referrals: <b>${refCount}</b>`;
    } else if (inputCommand === '/traffic' || inputCommand === 'btn_traffic') {
      botText = `🌐 <b>Available Campaign</b>\n\nWebsite:\n"https://news.google.com"\n\n⏱ <b>Required Stay:</b> 20 seconds\n🎁 <b>Reward:</b> +1 Credit\n📊 <b>Remaining:</b> 458 visits`;
      replyMarkup = {
        inline_keyboard: [
          [{ text: '🚀 Visit & Earn (+1 Credit)', callback_data: 'btn_visit_cmp_demo_1' }],
          [{ text: '🏠 Back to Home', callback_data: 'btn_home' }],
        ],
      };
    } else if (inputCommand === '/campaign' || inputCommand === 'btn_promote') {
      botText = `➕ <b>Website Promotion</b>\n\nSend your website URL or launch campaign in Mini App.\n\n💳 Your Balance: <b>${credits} Credits</b>`;
      replyMarkup = {
        inline_keyboard: [
          [{ text: '➕ Promote Website in Mini App', callback_data: 'btn_tab_campaigns' }],
          [{ text: '🏠 Back to Home', callback_data: 'btn_home' }],
        ],
      };
    } else if (inputCommand === '/packages' || inputCommand === 'btn_buy') {
      botText = `💳 <b>Buy Credits Packages</b>\n\nYour Current Balance: <b>${credits} Credits</b>\n\n📦 <b>Starter:</b> 500 Credits (৳50)\n📦 <b>Popular:</b> 1,500 Credits (৳120)\n📦 <b>Pro:</b> 5,000 Credits (৳350)\n📦 <b>Business:</b> 10,000 Credits (৳600)`;
      replyMarkup = {
        inline_keyboard: [
          [{ text: '💳 Purchase Packages in Mini App', callback_data: 'btn_tab_wallet' }],
          [{ text: '🏠 Back to Home', callback_data: 'btn_home' }],
        ],
      };
    } else if (inputCommand === 'btn_earn') {
      botText = `🎁 <b>Earn Free Credits</b>\n\n1️⃣ View Promotional Websites (+1 Credit each)\n2️⃣ Invite Friends (+10 Credits each)\n\nReferral Link:\n<code>https://t.me/InfiniteHits_bot?start=${refCode}</code>`;
      replyMarkup = {
        inline_keyboard: [
          [{ text: '🌐 Get Traffic', callback_data: 'btn_traffic' }],
          [{ text: '👥 Referral Program', callback_data: 'btn_referral' }],
          [{ text: '🏠 Back to Home', callback_data: 'btn_home' }],
        ],
      };
    } else if (inputCommand === '/referral' || inputCommand === 'btn_referral') {
      botText = `👥 <b>Referral Program</b>\n\nYour Referrals:\n<b>${refCount}</b>\n\nEarned:\n<b>${user?.referral_earnings || 0} Credits</b>\n\nYour Referral Link:\n<code>https://t.me/InfiniteHits_bot?start=${refCode}</code>`;
      replyMarkup = {
        inline_keyboard: [
          [{ text: '📤 Share Referral Link', callback_data: 'btn_share_ref' }],
          [{ text: '🏠 Back to Home', callback_data: 'btn_home' }],
        ],
      };
    } else if (inputCommand === '/stats' || inputCommand === 'btn_stats') {
      botText = `📊 <b>Statistics</b>\n\n💰 Current Balance: <b>${credits} Credits</b>\n👁️ Traffic Delivered: <b>${trafficDelivered}</b>\n👥 Referral Count: <b>${refCount}</b>`;
      replyMarkup = {
        inline_keyboard: [[{ text: '🏠 Back to Home', callback_data: 'btn_home' }]],
      };
    } else if (inputCommand === '/history' || inputCommand === 'btn_history') {
      botText = `📜 <b>Credit History</b>\n\n+50 — Signup Bonus (WELCOME_BONUS)\n+10 — Referral Reward`;
      replyMarkup = {
        inline_keyboard: [[{ text: '🏠 Back to Home', callback_data: 'btn_home' }]],
      };
    } else if (inputCommand === '/support' || inputCommand === 'btn_support') {
      botText = `🎧 <b>Support Center</b>\n\nCreate a support ticket or contact our admin directly.`;
      replyMarkup = {
        inline_keyboard: [
          [{ text: '🎧 Support Ticket', callback_data: 'btn_tab_support' }],
          [{ text: '🏠 Back to Home', callback_data: 'btn_home' }],
        ],
      };
    } else if (inputCommand === '/admin' || inputCommand === 'btn_admin') {
      botText = `🛡️ <b>InfiniteHits Admin Dashboard</b>\n\nFull administrative controls are accessible in the Admin Panel tab above!`;
      replyMarkup = {
        inline_keyboard: [[{ text: '🛡️ Open Admin Panel', callback_data: 'btn_tab_admin' }]],
      };
    } else {
      botText = `🚀 <b>InfiniteHits</b>\n\nWelcome back, <b>${userFirstName}</b>\n\n💰 Credits: <b>${credits}</b>`;
    }

    const botMsg: ChatMessage = {
      id: `bot_${Date.now()}`,
      sender: 'bot',
      text: botText,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      reply_markup: replyMarkup,
    };

    setMessages((prev) => [...prev, botMsg]);
  };

  const handleInlineButtonClick = (btn: { text: string; callback_data?: string; url?: string }) => {
    if (btn.callback_data) {
      if (btn.callback_data === 'btn_tab_campaigns') {
        onOpenMiniAppTab?.('campaigns');
      } else if (btn.callback_data === 'btn_tab_wallet') {
        onOpenMiniAppTab?.('wallet');
      } else if (btn.callback_data === 'btn_tab_admin') {
        onOpenMiniAppTab?.('admin');
      } else if (btn.callback_data === 'btn_tab_support') {
        onOpenMiniAppTab?.('profile');
      } else {
        handleSendMessage(btn.callback_data);
      }
    }
  };

  return (
    <div className="max-w-2xl mx-auto p-4 sm:p-6">
      
      {/* Phone Telegram Window Container */}
      <div className="bg-slate-900 border border-slate-800 rounded-3xl overflow-hidden shadow-2xl flex flex-col h-[750px] relative">
        
        {/* Telegram Chat Header */}
        <div className="bg-slate-950 px-4 py-3 border-b border-slate-800/80 flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <div className="relative">
              <div className="w-10 h-10 rounded-full bg-gradient-to-tr from-indigo-500 via-purple-500 to-pink-500 flex items-center justify-center font-bold text-white shadow-md">
                <Bot className="w-6 h-6" />
              </div>
              <span className="absolute bottom-0 right-0 w-3 h-3 bg-emerald-500 border-2 border-slate-950 rounded-full"></span>
            </div>
            <div>
              <div className="flex items-center space-x-1.5">
                <h3 className="font-bold text-white text-sm">InfiniteHits</h3>
                <Sparkles className="w-3.5 h-3.5 text-indigo-400" />
              </div>
              <p className="text-xs text-indigo-400 font-mono">@InfiniteHits_bot • bot</p>
            </div>
          </div>

          <button
            onClick={() => {
              setMessages([]);
              handleSendMessage('/start');
            }}
            className="p-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-slate-400 hover:text-slate-200 transition-colors"
            title="Reset Chat"
          >
            <RefreshCw className="w-4 h-4" />
          </button>
        </div>

        {/* Chat Messages Body */}
        <div className="flex-1 p-4 overflow-y-auto space-y-4 bg-slate-950/60 backdrop-blur-sm">
          {messages.map((msg) => (
            <div
              key={msg.id}
              className={`flex flex-col ${msg.sender === 'user' ? 'items-end' : 'items-start'} space-y-2`}
            >
              <div
                className={`max-w-[85%] rounded-2xl px-4 py-3 shadow-lg text-sm leading-relaxed ${
                  msg.sender === 'user'
                    ? 'bg-gradient-to-r from-indigo-600 to-purple-600 text-white rounded-br-none'
                    : 'bg-slate-900 border border-slate-800 text-slate-100 rounded-bl-none'
                }`}
              >
                <div
                  dangerouslySetInnerHTML={{ __html: msg.text.replace(/\n/g, '<br/>') }}
                  className="prose prose-invert prose-sm"
                />
                <div className={`text-[10px] mt-1 text-right flex items-center justify-end space-x-1 ${
                  msg.sender === 'user' ? 'text-indigo-200' : 'text-slate-500'
                }`}>
                  <span>{msg.timestamp}</span>
                  {msg.sender === 'user' && <CheckCheck className="w-3 h-3 text-indigo-200" />}
                </div>
              </div>

              {/* Telegram Inline Keyboards */}
              {msg.reply_markup && msg.reply_markup.inline_keyboard && (
                <div className="w-full max-w-[85%] space-y-1.5 mt-1">
                  {msg.reply_markup.inline_keyboard.map((row, rIdx) => (
                    <div key={rIdx} className="grid grid-cols-1 sm:grid-cols-2 gap-1.5">
                      {row.map((btn, bIdx) => (
                        <button
                          key={bIdx}
                          onClick={() => handleInlineButtonClick(btn)}
                          className="w-full bg-slate-900/90 hover:bg-indigo-900/40 border border-indigo-500/30 hover:border-indigo-500/60 text-indigo-300 font-semibold text-xs py-2 px-3 rounded-xl transition-all shadow-sm flex items-center justify-center space-x-1.5 active:scale-95"
                        >
                          {btn.web_app && <Smartphone className="w-3.5 h-3.5 text-purple-400" />}
                          <span>{btn.text}</span>
                        </button>
                      ))}
                    </div>
                  ))}
                </div>
              )}
            </div>
          ))}

          {isTyping && (
            <div className="flex items-center space-x-2 bg-slate-900 border border-slate-800 text-slate-400 text-xs px-4 py-2 rounded-2xl w-fit">
              <Bot className="w-4 h-4 text-indigo-400 animate-bounce" />
              <span>InfiniteHits bot is typing...</span>
            </div>
          )}

          <div ref={chatEndRef} />
        </div>

        {/* Preset Command Quick Pills */}
        <div className="px-3 py-2 bg-slate-900 border-t border-slate-800 flex items-center space-x-2 overflow-x-auto no-scrollbar text-xs">
          {['/start', '/traffic', '/campaign', '/packages', '/referral', '/stats', '/history', '/support', '/admin'].map(
            (cmd) => (
              <button
                key={cmd}
                onClick={() => handleSendMessage(cmd)}
                className="bg-slate-950 hover:bg-slate-800 border border-slate-800 text-slate-300 font-mono px-2.5 py-1 rounded-lg shrink-0 transition-colors"
              >
                {cmd}
              </button>
            )
          )}
        </div>

        {/* Input Bar */}
        <div className="p-3 bg-slate-950 border-t border-slate-800 flex items-center space-x-2">
          <input
            type="text"
            value={inputText}
            onChange={(e) => setInputText(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSendMessage()}
            placeholder="Send command or message..."
            className="flex-1 bg-slate-900 border border-slate-800 text-slate-100 placeholder-slate-500 text-sm px-4 py-2.5 rounded-xl focus:outline-none focus:border-indigo-500 transition-colors"
          />
          <button
            onClick={() => handleSendMessage()}
            className="p-2.5 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 text-white shadow-lg hover:from-indigo-500 hover:to-purple-500 transition-all active:scale-95"
          >
            <Send className="w-4 h-4" />
          </button>
        </div>

      </div>

    </div>
  );
};
