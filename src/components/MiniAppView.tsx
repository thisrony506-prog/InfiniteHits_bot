import React, { useState, useEffect } from 'react';
import { User, Campaign, Package, Payment, CreditTransaction, SupportTicket, SystemSettings } from '../types';
import { TrafficViewer } from './TrafficViewer';
import {
  Home,
  Globe,
  PlusCircle,
  Wallet,
  User as UserIcon,
  Coins,
  Sparkles,
  ArrowUpRight,
  Share2,
  Copy,
  Clock,
  CheckCircle2,
  AlertCircle,
  ShieldCheck,
  Send,
  HelpCircle,
  History,
  TrendingUp,
  ExternalLink,
  ChevronRight,
  Filter,
} from 'lucide-react';

interface MiniAppViewProps {
  user: User | null;
  settings: SystemSettings | null;
  initialTab?: string;
  initialVisitCampaignId?: string;
  onRefreshUser: () => void;
}

export const MiniAppView: React.FC<MiniAppViewProps> = ({
  user,
  settings,
  initialTab = 'home',
  initialVisitCampaignId,
  onRefreshUser,
}) => {
  const [activeTab, setActiveTab] = useState<'home' | 'traffic' | 'campaigns' | 'wallet' | 'profile'>(
    (initialTab as any) || 'home'
  );

  // Active traffic viewing modal state
  const [activeVisitingCampaign, setActiveVisitingCampaign] = useState<Campaign | null>(null);

  // Data collections
  const [trafficCampaigns, setTrafficCampaigns] = useState<Campaign[]>([]);
  const [myCampaigns, setMyCampaigns] = useState<Campaign[]>([]);
  const [packages, setPackages] = useState<Package[]>([]);
  const [myPayments, setMyPayments] = useState<Payment[]>([]);
  const [historyTxs, setHistoryTxs] = useState<CreditTransaction[]>([]);
  const [myTickets, setMyTickets] = useState<SupportTicket[]>([]);

  // Create Campaign Form state
  const [promoUrl, setPromoUrl] = useState('');
  const [promoVisits, setPromoVisits] = useState<number>(1000);
  const [promoTitle, setPromoTitle] = useState('');
  const [urlValidationError, setUrlValidationError] = useState<string | null>(null);
  const [campaignCreationSuccess, setCampaignCreationSuccess] = useState<boolean>(false);
  const [campaignCreationError, setCampaignCreationError] = useState<string | null>(null);

  // Buy Credits Payment state
  const [selectedPackage, setSelectedPackage] = useState<Package | null>(null);
  const [paymentMethod, setPaymentMethod] = useState<string>('bKash Personal');
  const [trxRef, setTrxRef] = useState('');
  const [senderNumber, setSenderNumber] = useState('');
  const [paymentSubmitted, setPaymentSubmitted] = useState(false);
  const [paymentError, setPaymentError] = useState<string | null>(null);

  // Support Form state
  const [ticketCategory, setTicketCategory] = useState('Campaign Help');
  const [ticketMessage, setTicketMessage] = useState('');
  const [ticketSuccess, setTicketSuccess] = useState(false);

  // Copy status
  const [copied, setCopied] = useState(false);
  const [statsDateFilter, setStatsDateFilter] = useState<'today' | '7days' | '30days' | 'all'>('all');

  useEffect(() => {
    fetchTabData();
  }, [activeTab]);

  useEffect(() => {
    if (initialVisitCampaignId && trafficCampaigns.length > 0) {
      const cmp = trafficCampaigns.find((c) => c.campaign_id === initialVisitCampaignId);
      if (cmp) setActiveVisitingCampaign(cmp);
    }
  }, [initialVisitCampaignId, trafficCampaigns]);

  const fetchTabData = async () => {
    try {
      if (!user) return;
      const headers = { 'x-user-id': String(user.telegram_id) };

      if (activeTab === 'traffic' || activeTab === 'home') {
        const res = await fetch('/api/traffic/campaigns', { headers });
        const data = await res.json();
        if (data.status === 'SUCCESS') setTrafficCampaigns(data.campaigns);
      }

      if (activeTab === 'campaigns') {
        const res = await fetch('/api/campaigns/my', { headers });
        const data = await res.json();
        if (data.status === 'SUCCESS') setMyCampaigns(data.campaigns);
      }

      if (activeTab === 'wallet') {
        const pkgRes = await fetch('/api/packages');
        const pkgData = await pkgRes.json();
        if (pkgData.status === 'SUCCESS') setPackages(pkgData.packages);

        const payRes = await fetch('/api/payments/my', { headers });
        const payData = await payRes.json();
        if (payData.status === 'SUCCESS') setMyPayments(payData.payments);
      }

      if (activeTab === 'profile') {
        const txRes = await fetch('/api/user/history', { headers });
        const txData = await txRes.json();
        if (txData.status === 'SUCCESS') setHistoryTxs(txData.transactions);

        const tktRes = await fetch('/api/support/my', { headers });
        const tktData = await tktRes.json();
        if (tktData.status === 'SUCCESS') setMyTickets(tktData.tickets);
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleValidateUrlInput = (urlInput: string) => {
    setPromoUrl(urlInput);
    if (!urlInput.trim()) {
      setUrlValidationError(null);
      return;
    }
    try {
      const parsed = new URL(urlInput);
      if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
        setUrlValidationError('URL must begin with http:// or https://');
        return;
      }
      const host = parsed.hostname.toLowerCase();
      if (
        host === 'localhost' ||
        host === '127.0.0.1' ||
        host.startsWith('192.168.') ||
        host.startsWith('10.') ||
        host.endsWith('.local')
      ) {
        setUrlValidationError('Forbidden: Private IP or localhost addresses are not allowed.');
        return;
      }
      setUrlValidationError(null);
    } catch (e) {
      setUrlValidationError('Invalid URL format. Please include full https:// domain.');
    }
  };

  const handleCreateCampaign = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || urlValidationError || !promoUrl) return;

    setCampaignCreationError(null);
    setCampaignCreationSuccess(false);

    try {
      const res = await fetch('/api/campaigns/create', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-user-id': String(user.telegram_id),
        },
        body: JSON.stringify({
          website_url: promoUrl,
          title: promoTitle || undefined,
          required_visits: promoVisits,
        }),
      });

      const data = await res.json();
      if (data.status === 'SUCCESS') {
        setCampaignCreationSuccess(true);
        setPromoUrl('');
        setPromoTitle('');
        onRefreshUser();
        fetchTabData();
      } else {
        setCampaignCreationError(data.error || 'Failed to launch campaign');
      }
    } catch (err: any) {
      setCampaignCreationError('Server error while launching campaign');
    }
  };

  const handleSubmitPayment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !selectedPackage || !trxRef.trim()) return;

    setPaymentError(null);
    try {
      const res = await fetch('/api/payments/create', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-user-id': String(user.telegram_id),
        },
        body: JSON.stringify({
          package_id: selectedPackage.package_id,
          payment_method: paymentMethod,
          transaction_reference: trxRef,
          sender_number: senderNumber,
        }),
      });

      const data = await res.json();
      if (data.status === 'SUCCESS') {
        setPaymentSubmitted(true);
        setTrxRef('');
        setSenderNumber('');
        fetchTabData();
      } else {
        setPaymentError(data.error || 'Payment submission failed');
      }
    } catch (e) {
      setPaymentError('Network error submitting payment');
    }
  };

  const handleCreateSupportTicket = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !ticketMessage.trim()) return;

    try {
      const res = await fetch('/api/support/create', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-user-id': String(user.telegram_id),
        },
        body: JSON.stringify({
          category: ticketCategory,
          message: ticketMessage,
        }),
      });
      const data = await res.json();
      if (data.status === 'SUCCESS') {
        setTicketSuccess(true);
        setTicketMessage('');
        fetchTabData();
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleCopyReferralLink = () => {
    if (!user) return;
    const botUsername = process.env.TELEGRAM_BOT_USERNAME || 'InfiniteHits_bot';
    const link = `https://t.me/${botUsername}?start=${user.referral_code}`;
    navigator.clipboard.writeText(link);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  if (!user) {
    return (
      <div className="flex flex-col items-center justify-center p-12 text-center text-slate-400">
        <Sparkles className="w-10 h-10 text-indigo-400 animate-spin mb-3" />
        <p>Loading Telegram Mini App...</p>
      </div>
    );
  }

  const costPerVisit = settings?.cost_per_visit_default || 1;
  const totalCampaignCost = promoVisits * costPerVisit;
  const isBalanceSufficient = user.credits >= totalCampaignCost;

  return (
    <div className="max-w-md mx-auto bg-slate-950 border border-slate-800 rounded-3xl min-h-[720px] flex flex-col justify-between overflow-hidden shadow-2xl relative text-slate-100 font-sans">
      
      {/* Mini App Top Header Card */}
      <div className="bg-gradient-to-b from-slate-900 to-slate-950 p-5 border-b border-slate-800/80 space-y-4">
        
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <div className="w-8 h-8 rounded-xl bg-gradient-to-tr from-indigo-500 to-purple-600 flex items-center justify-center shadow-md">
              <Sparkles className="w-4 h-4 text-white" />
            </div>
            <div>
              <h2 className="font-extrabold text-white text-base leading-none">InfiniteHits</h2>
              <p className="text-[10px] text-slate-400 mt-0.5">Telegram Mini App</p>
            </div>
          </div>

          <div className="flex items-center space-x-1.5 bg-indigo-950/80 border border-indigo-500/30 px-3 py-1 rounded-full">
            <Coins className="w-4 h-4 text-amber-400" />
            <span className="font-bold text-amber-300 text-sm">{user.credits.toLocaleString()}</span>
            <span className="text-[10px] text-indigo-300 uppercase font-semibold">Credits</span>
          </div>
        </div>

        {/* 4 Dashboard Quick Stat Cards */}
        <div className="grid grid-cols-2 gap-2.5">
          <div className="bg-slate-900/90 border border-slate-800 p-3 rounded-2xl flex items-center space-x-3">
            <div className="p-2 rounded-xl bg-amber-500/10 text-amber-400 border border-amber-500/20">
              <Coins className="w-4 h-4" />
            </div>
            <div>
              <div className="text-[10px] text-slate-400 uppercase font-semibold">Credits</div>
              <div className="font-extrabold text-sm text-white">{user.credits}</div>
            </div>
          </div>

          <div className="bg-slate-900/90 border border-slate-800 p-3 rounded-2xl flex items-center space-x-3">
            <div className="p-2 rounded-xl bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
              <Globe className="w-4 h-4" />
            </div>
            <div>
              <div className="text-[10px] text-slate-400 uppercase font-semibold">Active Ads</div>
              <div className="font-extrabold text-sm text-white">{myCampaigns.filter((c) => c.status === 'ACTIVE').length}</div>
            </div>
          </div>

          <div className="bg-slate-900/90 border border-slate-800 p-3 rounded-2xl flex items-center space-x-3">
            <div className="p-2 rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
              <TrendingUp className="w-4 h-4" />
            </div>
            <div>
              <div className="text-[10px] text-slate-400 uppercase font-semibold">Delivered</div>
              <div className="font-extrabold text-sm text-white">{user.traffic_delivered}</div>
            </div>
          </div>

          <div className="bg-slate-900/90 border border-slate-800 p-3 rounded-2xl flex items-center space-x-3">
            <div className="p-2 rounded-xl bg-purple-500/10 text-purple-400 border border-purple-500/20">
              <UserIcon className="w-4 h-4" />
            </div>
            <div>
              <div className="text-[10px] text-slate-400 uppercase font-semibold">Referrals</div>
              <div className="font-extrabold text-sm text-white">{user.referral_count}</div>
            </div>
          </div>
        </div>

      </div>

      {/* Main Tab Content Body */}
      <div className="flex-1 p-4 overflow-y-auto space-y-4">
        
        {/* TAB 1: HOME */}
        {activeTab === 'home' && (
          <div className="space-y-4">
            
            <div className="bg-gradient-to-r from-indigo-900/40 via-purple-900/30 to-slate-900 border border-indigo-500/30 p-5 rounded-3xl relative overflow-hidden space-y-3">
              <div className="flex justify-between items-start">
                <div>
                  <span className="bg-indigo-500/20 text-indigo-300 text-[10px] font-bold px-2.5 py-0.5 rounded-full border border-indigo-500/30">
                    VOLUNTARY REAL USER TRAFFIC
                  </span>
                  <h3 className="text-xl font-extrabold text-white mt-1">Accelerate Website Hits</h3>
                </div>
                <Sparkles className="w-6 h-6 text-indigo-400" />
              </div>
              <p className="text-xs text-slate-300 leading-relaxed">
                Exchange credits for verified visitor stays. 100% compliant, real voluntary user browsing.
              </p>
              <div className="grid grid-cols-2 gap-2 pt-1">
                <button
                  onClick={() => setActiveTab('traffic')}
                  className="py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white font-bold text-xs rounded-xl shadow-md flex items-center justify-center space-x-1"
                >
                  <Globe className="w-3.5 h-3.5" />
                  <span>Get Traffic</span>
                </button>
                <button
                  onClick={() => setActiveTab('campaigns')}
                  className="py-2.5 bg-purple-600 hover:bg-purple-500 text-white font-bold text-xs rounded-xl shadow-md flex items-center justify-center space-x-1"
                >
                  <PlusCircle className="w-3.5 h-3.5" />
                  <span>Promote Site</span>
                </button>
              </div>
            </div>

            {/* Quick Actions Grid */}
            <div className="grid grid-cols-2 gap-3">
              <button
                onClick={() => setActiveTab('wallet')}
                className="bg-slate-900 p-4 rounded-2xl border border-slate-800 hover:border-indigo-500/50 text-left transition-all space-y-2 group"
              >
                <div className="w-9 h-9 rounded-xl bg-amber-500/10 text-amber-400 flex items-center justify-center border border-amber-500/20 group-hover:scale-105 transition-transform">
                  <Wallet className="w-5 h-5" />
                </div>
                <div>
                  <div className="font-bold text-sm text-white">Buy Credits</div>
                  <div className="text-[11px] text-slate-400">Discounted packages</div>
                </div>
              </button>

              <button
                onClick={() => setActiveTab('profile')}
                className="bg-slate-900 p-4 rounded-2xl border border-slate-800 hover:border-indigo-500/50 text-left transition-all space-y-2 group"
              >
                <div className="w-9 h-9 rounded-xl bg-purple-500/10 text-purple-400 flex items-center justify-center border border-purple-500/20 group-hover:scale-105 transition-transform">
                  <Share2 className="w-5 h-5" />
                </div>
                <div>
                  <div className="font-bold text-sm text-white">Invite & Earn</div>
                  <div className="text-[11px] text-slate-400">+{settings?.referral_reward_credits || 10} Credits / invite</div>
                </div>
              </button>
            </div>

            {/* Active Available Campaigns Preview */}
            <div className="space-y-2">
              <div className="flex justify-between items-center px-1">
                <h4 className="font-bold text-sm text-white">Featured Traffic Campaigns</h4>
                <button
                  onClick={() => setActiveTab('traffic')}
                  className="text-xs text-indigo-400 hover:text-indigo-300 font-semibold"
                >
                  View All
                </button>
              </div>

              {trafficCampaigns.length === 0 ? (
                <div className="bg-slate-900/60 border border-slate-800 p-6 rounded-2xl text-center text-xs text-slate-400">
                  No campaigns available to visit right now. Check back soon!
                </div>
              ) : (
                trafficCampaigns.slice(0, 2).map((cmp) => (
                  <div key={cmp.campaign_id} className="bg-slate-900 border border-slate-800 p-4 rounded-2xl flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <div className="font-bold text-sm text-white truncate">{cmp.title}</div>
                      <div className="text-xs text-slate-400 flex items-center gap-2 mt-0.5">
                        <span className="flex items-center gap-1">
                          <Clock className="w-3 h-3 text-indigo-400" />
                          {cmp.minimum_visit_seconds}s stay
                        </span>
                        <span className="text-emerald-400 font-semibold">+{cmp.cost_per_visit} Credit</span>
                      </div>
                    </div>
                    <button
                      onClick={() => setActiveVisitingCampaign(cmp)}
                      className="px-3.5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-bold text-xs shrink-0 shadow-md"
                    >
                      Visit
                    </button>
                  </div>
                ))
              )}
            </div>

          </div>
        )}

        {/* TAB 2: TRAFFIC (GET TRAFFIC) */}
        {activeTab === 'traffic' && (
          <div className="space-y-4">
            <div className="bg-slate-900 p-4 rounded-2xl border border-slate-800 flex items-center justify-between">
              <div>
                <h3 className="font-bold text-white text-sm">Earn Traffic Credits</h3>
                <p className="text-xs text-slate-400">Visit promotional sites, complete minimum stay, earn credits.</p>
              </div>
              <ShieldCheck className="w-6 h-6 text-emerald-400 shrink-0" />
            </div>

            {trafficCampaigns.length === 0 ? (
              <div className="bg-slate-900 border border-slate-800 p-8 rounded-3xl text-center space-y-3">
                <Globe className="w-10 h-10 text-slate-600 mx-auto" />
                <h4 className="font-bold text-white text-base">All Campaigns Completed!</h4>
                <p className="text-xs text-slate-400 max-w-xs mx-auto">
                  You have completed all active promotional visit campaigns. Create your own site promotion to receive traffic!
                </p>
                <button
                  onClick={() => setActiveTab('campaigns')}
                  className="px-4 py-2.5 rounded-xl bg-indigo-600 font-bold text-xs text-white"
                >
                  Promote Your Website
                </button>
              </div>
            ) : (
              trafficCampaigns.map((cmp) => (
                <div key={cmp.campaign_id} className="bg-slate-900 border border-slate-800 p-4 rounded-2xl space-y-3 shadow-lg">
                  <div className="flex justify-between items-start">
                    <div>
                      <h4 className="font-bold text-white text-base">{cmp.title}</h4>
                      <p className="text-xs text-slate-400 truncate max-w-[220px]">{cmp.website_url}</p>
                    </div>
                    <span className="bg-emerald-950 border border-emerald-500/30 text-emerald-300 font-bold text-xs px-2.5 py-1 rounded-full">
                      +{cmp.cost_per_visit} Credit
                    </span>
                  </div>

                  <div className="flex items-center justify-between text-xs text-slate-400 border-t border-slate-800/80 pt-2.5">
                    <div className="flex items-center space-x-1">
                      <Clock className="w-3.5 h-3.5 text-indigo-400" />
                      <span>Stay: <b>{cmp.minimum_visit_seconds}s</b></span>
                    </div>
                    <div>
                      Remaining: <b>{cmp.remaining_visits}</b> visits
                    </div>
                  </div>

                  <button
                    onClick={() => setActiveVisitingCampaign(cmp)}
                    className="w-full py-2.5 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 font-bold text-xs text-white shadow-md flex items-center justify-center space-x-1.5"
                  >
                    <span>🚀 Visit Website & Verify</span>
                  </button>
                </div>
              ))
            )}
          </div>
        )}

        {/* TAB 3: CAMPAIGNS (PROMOTE WEBSITE) */}
        {activeTab === 'campaigns' && (
          <div className="space-y-5">
            
            {/* Create Campaign Form Card */}
            <div className="bg-slate-900 border border-slate-800 p-5 rounded-3xl space-y-4 shadow-xl">
              <div className="flex items-center space-x-2">
                <PlusCircle className="w-5 h-5 text-indigo-400" />
                <h3 className="font-bold text-white text-base">Promote Your Website</h3>
              </div>

              {campaignCreationSuccess && (
                <div className="p-3 bg-emerald-950/60 border border-emerald-500/40 text-emerald-300 text-xs rounded-xl flex items-center gap-2 font-medium">
                  <CheckCircle2 className="w-4 h-4 shrink-0" />
                  <span>Campaign submitted successfully! It is now active or pending admin approval.</span>
                </div>
              )}

              {campaignCreationError && (
                <div className="p-3 bg-rose-950/60 border border-rose-500/40 text-rose-300 text-xs rounded-xl flex items-center gap-2 font-medium">
                  <AlertCircle className="w-4 h-4 shrink-0" />
                  <span>{campaignCreationError}</span>
                </div>
              )}

              <form onSubmit={handleCreateCampaign} className="space-y-4">
                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1">Website URL</label>
                  <input
                    type="url"
                    placeholder="https://example.com"
                    value={promoUrl}
                    onChange={(e) => handleValidateUrlInput(e.target.value)}
                    required
                    className="w-full bg-slate-950 border border-slate-800 text-slate-100 placeholder-slate-500 text-xs px-3.5 py-2.5 rounded-xl focus:outline-none focus:border-indigo-500"
                  />
                  {urlValidationError && (
                    <p className="text-[11px] text-rose-400 mt-1">{urlValidationError}</p>
                  )}
                </div>

                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1">Campaign Title (Optional)</label>
                  <input
                    type="text"
                    placeholder="e.g. My Crypto Blog / Tech Portal"
                    value={promoTitle}
                    onChange={(e) => setPromoTitle(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 text-slate-100 placeholder-slate-500 text-xs px-3.5 py-2.5 rounded-xl focus:outline-none"
                  />
                </div>

                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1.5">Select Requested Visits</label>
                  <div className="grid grid-cols-5 gap-1.5">
                    {[100, 500, 1000, 5000, 10000].map((v) => (
                      <button
                        type="button"
                        key={v}
                        onClick={() => setPromoVisits(v)}
                        className={`py-2 rounded-xl text-xs font-bold border transition-all ${
                          promoVisits === v
                            ? 'bg-indigo-600 border-indigo-500 text-white shadow-md'
                            : 'bg-slate-950 border-slate-800 text-slate-300 hover:border-slate-700'
                        }`}
                      >
                        {v.toLocaleString()}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Calculation Summary Card */}
                <div className="bg-slate-950 p-3.5 rounded-2xl border border-slate-800/90 text-xs space-y-1.5">
                  <div className="flex justify-between text-slate-400">
                    <span>Requested Visits:</span>
                    <span className="font-mono font-bold text-white">{promoVisits.toLocaleString()}</span>
                  </div>
                  <div className="flex justify-between text-slate-400">
                    <span>Cost Per Visit:</span>
                    <span className="font-mono text-white">{costPerVisit} Credit</span>
                  </div>
                  <div className="flex justify-between border-t border-slate-800 pt-1.5 font-bold">
                    <span className="text-slate-200">Total Campaign Cost:</span>
                    <span className="text-amber-300">{totalCampaignCost.toLocaleString()} Credits</span>
                  </div>
                </div>

                {!isBalanceSufficient ? (
                  <div className="space-y-2">
                    <div className="p-3 bg-rose-950/60 border border-rose-500/30 text-rose-300 text-xs rounded-xl font-medium flex items-center gap-2">
                      <AlertCircle className="w-4 h-4 shrink-0" />
                      <span>Insufficient Credits (Required: {totalCampaignCost}, Available: {user.credits})</span>
                    </div>
                    <button
                      type="button"
                      onClick={() => setActiveTab('wallet')}
                      className="w-full py-2.5 bg-amber-600 hover:bg-amber-500 font-bold text-xs text-white rounded-xl shadow-md"
                    >
                      💳 Buy Credits Now
                    </button>
                  </div>
                ) : (
                  <button
                    type="submit"
                    disabled={!!urlValidationError || !promoUrl}
                    className="w-full py-3 bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 disabled:opacity-50 font-bold text-xs text-white rounded-xl shadow-lg transition-all"
                  >
                    🚀 Start Campaign ({totalCampaignCost} Credits)
                  </button>
                )}
              </form>
            </div>

            {/* My Created Campaigns */}
            <div className="space-y-3">
              <h4 className="font-bold text-sm text-white px-1">My Created Campaigns</h4>
              {myCampaigns.length === 0 ? (
                <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl text-center text-xs text-slate-400">
                  You have not launched any campaigns yet.
                </div>
              ) : (
                myCampaigns.map((c) => (
                  <div key={c.campaign_id} className="bg-slate-900 border border-slate-800 p-4 rounded-2xl space-y-2 text-xs">
                    <div className="flex justify-between items-start">
                      <div>
                        <div className="font-bold text-white">{c.title}</div>
                        <div className="text-slate-400 truncate max-w-[200px]">{c.website_url}</div>
                      </div>
                      <span
                        className={`text-[10px] font-bold px-2 py-0.5 rounded-full border ${
                          c.status === 'ACTIVE'
                            ? 'bg-emerald-950 text-emerald-300 border-emerald-800'
                            : c.status === 'PENDING'
                            ? 'bg-amber-950 text-amber-300 border-amber-800'
                            : 'bg-slate-800 text-slate-400 border-slate-700'
                        }`}
                      >
                        {c.status}
                      </span>
                    </div>

                    <div className="flex justify-between text-slate-400 border-t border-slate-800/80 pt-2 font-mono">
                      <span>Completed: {c.completed_visits} / {c.required_visits}</span>
                      <span className="text-amber-300 font-bold">{c.total_cost} Credits</span>
                    </div>
                  </div>
                ))
              )}
            </div>

          </div>
        )}

        {/* TAB 4: WALLET (BUY CREDITS & PAYMENTS) */}
        {activeTab === 'wallet' && (
          <div className="space-y-5">
            
            {/* Credit Packages Grid */}
            <div className="space-y-3">
              <h3 className="font-bold text-sm text-white px-1">Select Credit Package</h3>

              <div className="grid grid-cols-2 gap-3">
                {packages.map((pkg) => (
                  <div
                    key={pkg.package_id}
                    onClick={() => setSelectedPackage(pkg)}
                    className={`p-4 rounded-2xl border cursor-pointer transition-all space-y-2 relative ${
                      selectedPackage?.package_id === pkg.package_id
                        ? 'bg-indigo-950/60 border-indigo-500 shadow-xl scale-[1.02]'
                        : 'bg-slate-900 border-slate-800 hover:border-slate-700'
                    }`}
                  >
                    {pkg.tag && (
                      <span className="absolute -top-2 right-2 bg-gradient-to-r from-amber-500 to-rose-500 text-slate-950 font-extrabold text-[9px] px-2 py-0.5 rounded-full shadow-md">
                        {pkg.tag}
                      </span>
                    )}

                    <div className="font-bold text-white text-sm">{pkg.name}</div>
                    <div className="text-lg font-black text-amber-300">{pkg.credits.toLocaleString()} Credits</div>
                    {pkg.bonus_credits > 0 && (
                      <div className="text-[10px] text-emerald-400 font-semibold">+{pkg.bonus_credits} Bonus</div>
                    )}
                    <div className="text-xs text-indigo-300 font-bold border-t border-slate-800 pt-1">
                      {pkg.price} {pkg.currency}
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Selected Package Checkout & Payment Form */}
            {selectedPackage && (
              <div className="bg-slate-900 border border-slate-800 p-5 rounded-3xl space-y-4 shadow-xl">
                <div className="flex justify-between items-center border-b border-slate-800 pb-3">
                  <div>
                    <h4 className="font-bold text-white text-sm">Checkout: {selectedPackage.name}</h4>
                    <p className="text-xs text-amber-300 font-bold">
                      {selectedPackage.credits + selectedPackage.bonus_credits} Total Credits for {selectedPackage.price} {selectedPackage.currency}
                    </p>
                  </div>
                  <button onClick={() => setSelectedPackage(null)} className="text-xs text-slate-400 hover:text-white">
                    Change
                  </button>
                </div>

                {paymentSubmitted ? (
                  <div className="p-4 bg-emerald-950/60 border border-emerald-500/30 text-emerald-300 text-xs rounded-2xl space-y-2">
                    <CheckCircle2 className="w-6 h-6 text-emerald-400" />
                    <div className="font-bold text-sm text-white">Payment Submitted for Admin Verification</div>
                    <p>Your payment transaction reference has been recorded. Admin will credit your balance shortly.</p>
                  </div>
                ) : (
                  <form onSubmit={handleSubmitPayment} className="space-y-3">
                    <div>
                      <label className="text-xs font-semibold text-slate-300 block mb-1">Payment Method</label>
                      <select
                        value={paymentMethod}
                        onChange={(e) => setPaymentMethod(e.target.value)}
                        className="w-full bg-slate-950 border border-slate-800 text-slate-100 text-xs px-3.5 py-2.5 rounded-xl focus:outline-none"
                      >
                        {settings?.payment_methods.map((m) => (
                          <option key={m.name} value={m.name}>
                            {m.name} ({m.number})
                          </option>
                        ))}
                      </select>
                    </div>

                    <div className="bg-slate-950 p-3 rounded-xl border border-slate-800/80 text-xs text-slate-300">
                      <p className="font-semibold text-indigo-300 mb-1">Instructions:</p>
                      <p>
                        {settings?.payment_methods.find((m) => m.name === paymentMethod)?.instructions ||
                          'Send money to our payment account and submit transaction reference ID.'}
                      </p>
                    </div>

                    <div>
                      <label className="text-xs font-semibold text-slate-300 block mb-1">
                        Transaction ID / Reference (TrxID)
                      </label>
                      <input
                        type="text"
                        placeholder="e.g. 9K3L88ZM"
                        value={trxRef}
                        onChange={(e) => setTrxRef(e.target.value)}
                        required
                        className="w-full bg-slate-950 border border-slate-800 text-slate-100 placeholder-slate-500 text-xs px-3.5 py-2.5 rounded-xl focus:outline-none font-mono"
                      />
                    </div>

                    <div>
                      <label className="text-xs font-semibold text-slate-300 block mb-1">
                        Sender Mobile / Wallet Number (Optional)
                      </label>
                      <input
                        type="text"
                        placeholder="017XXXXXXXX"
                        value={senderNumber}
                        onChange={(e) => setSenderNumber(e.target.value)}
                        className="w-full bg-slate-950 border border-slate-800 text-slate-100 placeholder-slate-500 text-xs px-3.5 py-2.5 rounded-xl focus:outline-none font-mono"
                      />
                    </div>

                    {paymentError && <div className="text-xs text-rose-400">{paymentError}</div>}

                    <button
                      type="submit"
                      disabled={!trxRef.trim()}
                      className="w-full py-3 bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 disabled:opacity-50 font-bold text-xs text-white rounded-xl shadow-lg transition-all"
                    >
                      Submit Payment Verification
                    </button>
                  </form>
                )}
              </div>
            )}

            {/* Payment History List */}
            <div className="space-y-3">
              <h4 className="font-bold text-sm text-white px-1">Payment Orders History</h4>
              {myPayments.length === 0 ? (
                <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl text-center text-xs text-slate-400">
                  No payment records found.
                </div>
              ) : (
                myPayments.map((p) => (
                  <div key={p.payment_id} className="bg-slate-900 border border-slate-800 p-4 rounded-2xl space-y-1 text-xs">
                    <div className="flex justify-between items-center">
                      <span className="font-bold text-white">{p.package_name}</span>
                      <span
                        className={`text-[10px] font-bold px-2 py-0.5 rounded-full border ${
                          p.status === 'APPROVED'
                            ? 'bg-emerald-950 text-emerald-300 border-emerald-800'
                            : p.status === 'PENDING'
                            ? 'bg-amber-950 text-amber-300 border-amber-800'
                            : 'bg-rose-950 text-rose-300 border-rose-800'
                        }`}
                      >
                        {p.status}
                      </span>
                    </div>
                    <div className="text-slate-400 font-mono">
                      Ref: {p.transaction_reference} • {p.amount} {p.currency}
                    </div>
                  </div>
                ))
              )}
            </div>

          </div>
        )}

        {/* TAB 5: PROFILE / REFERRAL / STATS / SUPPORT */}
        {activeTab === 'profile' && (
          <div className="space-y-5">
            
            {/* Referral Card */}
            <div className="bg-gradient-to-r from-purple-900/40 via-indigo-900/40 to-slate-900 border border-purple-500/30 p-5 rounded-3xl space-y-3 shadow-xl">
              <div className="flex justify-between items-start">
                <div>
                  <h3 className="font-bold text-white text-base">Referral Program</h3>
                  <p className="text-xs text-slate-300">Invite friends & earn free promotional traffic credits!</p>
                </div>
                <Share2 className="w-5 h-5 text-purple-400" />
              </div>

              <div className="grid grid-cols-2 gap-2 text-center py-2 bg-slate-950/60 rounded-2xl border border-slate-800">
                <div>
                  <div className="text-[10px] text-slate-400 uppercase font-semibold">Your Referrals</div>
                  <div className="font-extrabold text-sm text-white">{user.referral_count}</div>
                </div>
                <div>
                  <div className="text-[10px] text-slate-400 uppercase font-semibold">Earned Credits</div>
                  <div className="font-extrabold text-sm text-amber-300">{user.referral_earnings}</div>
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-[11px] text-slate-400 font-semibold">Your Unique Link:</label>
                <div className="flex items-center space-x-2">
                  <input
                    type="text"
                    readOnly
                    value={`https://t.me/${process.env.TELEGRAM_BOT_USERNAME || 'InfiniteHits_bot'}?start=${user.referral_code}`}
                    className="flex-1 bg-slate-950 border border-slate-800 text-slate-200 text-xs px-3 py-2 rounded-xl font-mono focus:outline-none"
                  />
                  <button
                    onClick={handleCopyReferralLink}
                    className="px-3 py-2 bg-indigo-600 hover:bg-indigo-500 font-bold text-xs text-white rounded-xl shadow-md"
                  >
                    {copied ? 'Copied!' : 'Copy'}
                  </button>
                </div>
              </div>
            </div>

            {/* Support Ticket Section */}
            <div className="bg-slate-900 border border-slate-800 p-5 rounded-3xl space-y-4 shadow-xl">
              <h3 className="font-bold text-white text-base flex items-center gap-2">
                <HelpCircle className="w-5 h-5 text-indigo-400" />
                <span>Support Center</span>
              </h3>

              {ticketSuccess && (
                <div className="p-3 bg-emerald-950/60 border border-emerald-500/30 text-emerald-300 text-xs rounded-xl font-medium">
                  Support ticket created! Admin will respond to your account.
                </div>
              )}

              <form onSubmit={handleCreateSupportTicket} className="space-y-3">
                <select
                  value={ticketCategory}
                  onChange={(e) => setTicketCategory(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 text-slate-100 text-xs px-3 py-2.5 rounded-xl focus:outline-none"
                >
                  <option value="Campaign Help">Campaign Help</option>
                  <option value="Payment Inquiry">Payment Inquiry</option>
                  <option value="Credit Balance Issue">Credit Balance Issue</option>
                  <option value="General Bug / Feedback">General Bug / Feedback</option>
                </select>

                <textarea
                  rows={3}
                  placeholder="Describe your question or issue..."
                  value={ticketMessage}
                  onChange={(e) => setTicketMessage(e.target.value)}
                  required
                  className="w-full bg-slate-950 border border-slate-800 text-slate-100 placeholder-slate-500 text-xs p-3 rounded-xl focus:outline-none"
                />

                <button
                  type="submit"
                  className="w-full py-2.5 bg-indigo-600 hover:bg-indigo-500 font-bold text-xs text-white rounded-xl shadow-md"
                >
                  Submit Support Ticket
                </button>
              </form>
            </div>

            {/* Transaction Audit History */}
            <div className="space-y-3">
              <h4 className="font-bold text-sm text-white px-1">Credit History</h4>
              {historyTxs.length === 0 ? (
                <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl text-center text-xs text-slate-400">
                  No transaction history recorded yet.
                </div>
              ) : (
                historyTxs.slice(0, 10).map((tx) => (
                  <div key={tx.transaction_id} className="bg-slate-900 border border-slate-800 p-3.5 rounded-2xl flex items-center justify-between text-xs">
                    <div>
                      <div className="font-bold text-white">{tx.description}</div>
                      <div className="text-[10px] text-slate-400">{new Date(tx.created_at).toLocaleString()}</div>
                    </div>
                    <span className={`font-mono font-bold ${tx.amount > 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                      {tx.amount > 0 ? `+${tx.amount}` : tx.amount}
                    </span>
                  </div>
                ))
              )}
            </div>

          </div>
        )}

      </div>

      {/* Telegram Mini App Bottom Navigation Bar */}
      <div className="bg-slate-950 border-t border-slate-800/90 px-4 py-2 flex items-center justify-around z-30">
        {[
          { id: 'home', label: 'Home', icon: Home },
          { id: 'traffic', label: 'Traffic', icon: Globe },
          { id: 'campaigns', label: 'Campaigns', icon: PlusCircle },
          { id: 'wallet', label: 'Wallet', icon: Wallet },
          { id: 'profile', label: 'Profile', icon: UserIcon },
        ].map((item) => {
          const Icon = item.icon;
          const isActive = activeTab === item.id;
          return (
            <button
              key={item.id}
              onClick={() => setActiveTab(item.id as any)}
              className={`flex flex-col items-center space-y-1 py-1 px-3 rounded-xl transition-all ${
                isActive ? 'text-indigo-400 scale-105 font-bold' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <Icon className="w-5 h-5" />
              <span className="text-[10px] tracking-tight">{item.label}</span>
            </button>
          );
        })}
      </div>

      {/* Traffic Verification Modal Viewer */}
      {activeVisitingCampaign && (
        <TrafficViewer
          campaign={activeVisitingCampaign}
          onClose={() => setActiveVisitingCampaign(null)}
          onVerifiedSuccess={(reward, newBalance) => {
            onRefreshUser();
            fetchTabData();
          }}
        />
      )}

    </div>
  );
};
