import React, { useState, useEffect } from 'react';
import { User, Campaign, Payment, Package, GlobalStats, SystemSettings, AdminAuditLog, SupportTicket } from '../types';
import {
  ShieldCheck,
  Users,
  Globe,
  CreditCard,
  Package as PackageIcon,
  Settings as SettingsIcon,
  Send,
  FileText,
  CheckCircle,
  XCircle,
  Plus,
  Search,
  Lock,
  MessageSquare,
  Sparkles,
  TrendingUp,
} from 'lucide-react';

interface AdminPanelViewProps {
  currentAdmin: User;
  onRefreshGlobalData: () => void;
}

export const AdminPanelView: React.FC<AdminPanelViewProps> = ({ currentAdmin, onRefreshGlobalData }) => {
  const [activeTab, setActiveTab] = useState<
    'dashboard' | 'users' | 'campaigns' | 'payments' | 'packages' | 'broadcast' | 'settings' | 'tickets' | 'logs'
  >('dashboard');

  const [stats, setStats] = useState<GlobalStats | null>(null);
  const [usersList, setUsersList] = useState<User[]>([]);
  const [campaignsList, setCampaignsList] = useState<Campaign[]>([]);
  const [paymentsList, setPaymentsList] = useState<Payment[]>([]);
  const [packagesList, setPackagesList] = useState<Package[]>([]);
  const [settings, setSettings] = useState<SystemSettings | null>(null);
  const [logsList, setLogsList] = useState<AdminAuditLog[]>([]);
  const [ticketsList, setTicketsList] = useState<SupportTicket[]>([]);

  // Search & Filters
  const [userSearchQuery, setUserSearchQuery] = useState('');
  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [creditAdjustAmount, setCreditAdjustAmount] = useState<number>(100);
  const [creditAdjustReason, setCreditAdjustReason] = useState('Admin Bonus');
  const [banReason, setBanReason] = useState('Policy violation');

  // Broadcast state
  const [broadcastText, setBroadcastText] = useState('');
  const [broadcastStatus, setBroadcastStatus] = useState<string | null>(null);

  // Package Form state
  const [editingPackage, setEditingPackage] = useState<Partial<Package> | null>(null);

  // Ticket reply state
  const [replyTicketId, setReplyTicketId] = useState<string | null>(null);
  const [replyText, setReplyText] = useState('');

  useEffect(() => {
    fetchAdminData();
  }, [activeTab]);

  const fetchAdminData = async () => {
    try {
      const headers = {
        'Content-Type': 'application/json',
        'x-user-id': String(currentAdmin.telegram_id),
      };

      if (activeTab === 'dashboard') {
        const res = await fetch('/api/admin/stats', { headers });
        const data = await res.json();
        if (data.status === 'SUCCESS') setStats(data.stats);
      } else if (activeTab === 'users') {
        const res = await fetch('/api/admin/users', { headers });
        const data = await res.json();
        if (data.status === 'SUCCESS') setUsersList(data.users);
      } else if (activeTab === 'campaigns') {
        const res = await fetch('/api/admin/campaigns', { headers });
        const data = await res.json();
        if (data.status === 'SUCCESS') setCampaignsList(data.campaigns);
      } else if (activeTab === 'payments') {
        const res = await fetch('/api/admin/payments', { headers });
        const data = await res.json();
        if (data.status === 'SUCCESS') setPaymentsList(data.payments);
      } else if (activeTab === 'packages') {
        const res = await fetch('/api/admin/packages', { headers });
        const data = await res.json();
        if (data.status === 'SUCCESS') setPackagesList(data.packages);
      } else if (activeTab === 'settings') {
        const res = await fetch('/api/admin/settings', { headers });
        const data = await res.json();
        if (data.status === 'SUCCESS') setSettings(data.settings);
      } else if (activeTab === 'tickets') {
        const res = await fetch('/api/admin/tickets', { headers });
        const data = await res.json();
        if (data.status === 'SUCCESS') setTicketsList(data.tickets);
      } else if (activeTab === 'logs') {
        const res = await fetch('/api/admin/logs', { headers });
        const data = await res.json();
        if (data.status === 'SUCCESS') setLogsList(data.logs);
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleAdjustCredits = async (userId: string) => {
    try {
      const res = await fetch('/api/admin/users/adjust-credits', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-user-id': String(currentAdmin.telegram_id),
        },
        body: JSON.stringify({
          user_id: userId,
          amount: creditAdjustAmount,
          reason: creditAdjustReason,
        }),
      });
      const data = await res.json();
      if (data.status === 'SUCCESS') {
        alert('Credits adjusted successfully!');
        setSelectedUser(null);
        fetchAdminData();
        onRefreshGlobalData();
      } else {
        alert(data.error || 'Failed to adjust credits');
      }
    } catch (e) {
      alert('Error adjusting credits');
    }
  };

  const handleToggleBan = async (userId: string, currentBanned: boolean) => {
    try {
      const res = await fetch('/api/admin/users/ban', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-user-id': String(currentAdmin.telegram_id),
        },
        body: JSON.stringify({
          user_id: userId,
          action: currentBanned ? 'UNBAN' : 'BAN',
          reason: banReason,
        }),
      });
      const data = await res.json();
      if (data.status === 'SUCCESS') {
        fetchAdminData();
      }
    } catch (e) {
      alert('Failed to update ban status');
    }
  };

  const handleReviewCampaign = async (campaignId: string, status: 'ACTIVE' | 'REJECTED') => {
    try {
      const res = await fetch('/api/admin/campaigns/review', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-user-id': String(currentAdmin.telegram_id),
        },
        body: JSON.stringify({
          campaign_id: campaignId,
          status,
          reason: status === 'REJECTED' ? 'Violates campaign guidelines' : undefined,
        }),
      });
      const data = await res.json();
      if (data.status === 'SUCCESS') {
        fetchAdminData();
      }
    } catch (e) {
      alert('Error reviewing campaign');
    }
  };

  const handleVerifyPayment = async (paymentId: string, action: 'APPROVE' | 'REJECT') => {
    try {
      const res = await fetch('/api/admin/payments/verify', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-user-id': String(currentAdmin.telegram_id),
        },
        body: JSON.stringify({
          payment_id: paymentId,
          action,
        }),
      });
      const data = await res.json();
      if (data.status === 'SUCCESS') {
        fetchAdminData();
        onRefreshGlobalData();
      }
    } catch (e) {
      alert('Error verifying payment');
    }
  };

  const handleSavePackage = async () => {
    if (!editingPackage?.name || !editingPackage?.credits || !editingPackage?.price) return;
    try {
      const pkgToSave = {
        package_id: editingPackage.package_id || `pkg_${Date.now()}`,
        name: editingPackage.name,
        credits: Number(editingPackage.credits),
        price: Number(editingPackage.price),
        currency: editingPackage.currency || 'BDT (৳)',
        bonus_credits: Number(editingPackage.bonus_credits || 0),
        display_order: Number(editingPackage.display_order || 1),
        is_active: editingPackage.is_active !== false,
        tag: editingPackage.tag || '',
      };

      const res = await fetch('/api/admin/packages/save', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-user-id': String(currentAdmin.telegram_id),
        },
        body: JSON.stringify(pkgToSave),
      });
      const data = await res.json();
      if (data.status === 'SUCCESS') {
        setEditingPackage(null);
        fetchAdminData();
      }
    } catch (e) {
      alert('Error saving package');
    }
  };

  const handleSaveSettings = async () => {
    if (!settings) return;
    try {
      const res = await fetch('/api/admin/settings', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-user-id': String(currentAdmin.telegram_id),
        },
        body: JSON.stringify(settings),
      });
      const data = await res.json();
      if (data.status === 'SUCCESS') {
        alert('System settings updated successfully!');
      }
    } catch (e) {
      alert('Failed to update settings');
    }
  };

  const handleSendBroadcast = async () => {
    if (!broadcastText.trim()) return;
    setBroadcastStatus('Sending broadcast messages...');
    try {
      const res = await fetch('/api/admin/broadcast', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-user-id': String(currentAdmin.telegram_id),
        },
        body: JSON.stringify({ message: broadcastText }),
      });
      const data = await res.json();
      if (data.status === 'SUCCESS') {
        setBroadcastStatus(`Broadcast completed! Total Telegram recipients: ${data.total_recipients}`);
        setBroadcastText('');
      } else {
        setBroadcastStatus(`Broadcast failed: ${data.error}`);
      }
    } catch (e) {
      setBroadcastStatus('Error initiating broadcast');
    }
  };

  const handleReplyTicket = async (ticketId: string) => {
    if (!replyText.trim()) return;
    try {
      const res = await fetch('/api/admin/tickets/reply', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-user-id': String(currentAdmin.telegram_id),
        },
        body: JSON.stringify({
          ticket_id: ticketId,
          reply: replyText,
          status: 'RESOLVED',
        }),
      });
      const data = await res.json();
      if (data.status === 'SUCCESS') {
        setReplyTicketId(null);
        setReplyText('');
        fetchAdminData();
      }
    } catch (e) {
      alert('Error replying to ticket');
    }
  };

  const filteredUsers = usersList.filter(
    (u) =>
      u.first_name.toLowerCase().includes(userSearchQuery.toLowerCase()) ||
      (u.username && u.username.toLowerCase().includes(userSearchQuery.toLowerCase())) ||
      String(u.telegram_id).includes(userSearchQuery)
  );

  return (
    <div className="max-w-7xl mx-auto p-4 sm:p-6 space-y-6">
      
      {/* Admin Header */}
      <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 shadow-2xl flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center space-x-4">
          <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-amber-500 to-rose-600 flex items-center justify-center text-white shadow-lg">
            <ShieldCheck className="w-7 h-7" />
          </div>
          <div>
            <h1 className="text-2xl font-extrabold text-white flex items-center gap-2">
              <span>Admin Control Center</span>
              <span className="bg-amber-500/20 text-amber-300 text-xs px-2.5 py-0.5 rounded-full border border-amber-500/30">
                Super Admin
              </span>
            </h1>
            <p className="text-sm text-slate-400">
              Manage platform users, traffic campaigns, payments verification, package pricing & settings.
            </p>
          </div>
        </div>
      </div>

      {/* Admin Navigation Tabs */}
      <div className="flex items-center space-x-2 overflow-x-auto no-scrollbar border-b border-slate-800 pb-2">
        {[
          { id: 'dashboard', label: 'Dashboard', icon: TrendingUp },
          { id: 'users', label: 'Users', icon: Users },
          { id: 'campaigns', label: 'Campaigns', icon: Globe },
          { id: 'payments', label: 'Payments', icon: CreditCard },
          { id: 'packages', label: 'Packages', icon: PackageIcon },
          { id: 'broadcast', label: 'Broadcast', icon: Send },
          { id: 'settings', label: 'Settings', icon: SettingsIcon },
          { id: 'tickets', label: 'Support Tickets', icon: MessageSquare },
          { id: 'logs', label: 'Audit Logs', icon: FileText },
        ].map((tab) => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as any)}
              className={`flex items-center space-x-2 px-4 py-2.5 rounded-xl text-xs font-bold transition-all shrink-0 ${
                isActive
                  ? 'bg-gradient-to-r from-amber-600 to-rose-600 text-white shadow-lg'
                  : 'bg-slate-900 border border-slate-800 text-slate-400 hover:text-slate-200 hover:bg-slate-850'
              }`}
            >
              <Icon className="w-4 h-4" />
              <span>{tab.label}</span>
            </button>
          );
        })}
      </div>

      {/* TAB CONTENT: DASHBOARD */}
      {activeTab === 'dashboard' && stats && (
        <div className="space-y-6">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="bg-slate-900 border border-slate-800 p-5 rounded-2xl shadow-lg">
              <div className="text-xs text-slate-400 font-medium">Total Registered Users</div>
              <div className="text-2xl font-black text-white mt-1">{stats.total_users}</div>
              <div className="text-[11px] text-emerald-400 mt-1">{stats.active_users} active this week</div>
            </div>

            <div className="bg-slate-900 border border-slate-800 p-5 rounded-2xl shadow-lg">
              <div className="text-xs text-slate-400 font-medium">Active Traffic Campaigns</div>
              <div className="text-2xl font-black text-indigo-400 mt-1">{stats.active_campaigns}</div>
              <div className="text-[11px] text-slate-400 mt-1">{stats.completed_campaigns} completed</div>
            </div>

            <div className="bg-slate-900 border border-slate-800 p-5 rounded-2xl shadow-lg">
              <div className="text-xs text-slate-400 font-medium">Pending Payments</div>
              <div className="text-2xl font-black text-amber-400 mt-1">{stats.pending_payments}</div>
              <div className="text-[11px] text-slate-400 mt-1">{stats.total_payments} total orders</div>
            </div>

            <div className="bg-slate-900 border border-slate-800 p-5 rounded-2xl shadow-lg">
              <div className="text-xs text-slate-400 font-medium">Total Traffic Delivered</div>
              <div className="text-2xl font-black text-emerald-400 mt-1">{stats.total_traffic_delivered}</div>
              <div className="text-[11px] text-slate-400 mt-1">Verified user visits</div>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl space-y-4">
              <h3 className="font-bold text-white text-base">Credit Supply Metrics</h3>
              <div className="space-y-3">
                <div className="flex justify-between text-sm py-2 border-b border-slate-800">
                  <span className="text-slate-400">Circulating User Credits:</span>
                  <span className="font-bold text-amber-300">{stats.total_credits_circulating.toLocaleString()}</span>
                </div>
                <div className="flex justify-between text-sm py-2 border-b border-slate-800">
                  <span className="text-slate-400">Purchased Package Credits:</span>
                  <span className="font-bold text-emerald-300">{stats.total_credits_purchased.toLocaleString()}</span>
                </div>
                <div className="flex justify-between text-sm py-2">
                  <span className="text-slate-400">Credits Spent on Traffic:</span>
                  <span className="font-bold text-indigo-300">{stats.total_credits_spent.toLocaleString()}</span>
                </div>
              </div>
            </div>

            <div className="bg-slate-900 border border-slate-800 p-6 rounded-2xl space-y-3">
              <h3 className="font-bold text-white text-base">Platform Quick Actions</h3>
              <div className="grid grid-cols-2 gap-3">
                <button
                  onClick={() => setActiveTab('payments')}
                  className="p-3 bg-slate-950 hover:bg-slate-800 border border-slate-800 rounded-xl text-left transition-colors"
                >
                  <CreditCard className="w-5 h-5 text-amber-400 mb-1" />
                  <div className="font-bold text-sm text-white">Review Payments</div>
                  <div className="text-xs text-slate-400">{stats.pending_payments} pending</div>
                </button>

                <button
                  onClick={() => setActiveTab('campaigns')}
                  className="p-3 bg-slate-950 hover:bg-slate-800 border border-slate-800 rounded-xl text-left transition-colors"
                >
                  <Globe className="w-5 h-5 text-indigo-400 mb-1" />
                  <div className="font-bold text-sm text-white">Approve Websites</div>
                  <div className="text-xs text-slate-400">Review campaigns</div>
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* TAB CONTENT: USERS MANAGEMENT */}
      {activeTab === 'users' && (
        <div className="space-y-4">
          <div className="flex items-center space-x-3 bg-slate-900 border border-slate-800 p-3 rounded-2xl">
            <Search className="w-5 h-5 text-slate-400 ml-2" />
            <input
              type="text"
              placeholder="Search by user name, username, or Telegram ID..."
              value={userSearchQuery}
              onChange={(e) => setUserSearchQuery(e.target.value)}
              className="w-full bg-transparent text-slate-100 placeholder-slate-500 text-sm focus:outline-none"
            />
          </div>

          <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
            <table className="w-full text-left text-sm text-slate-300">
              <thead className="bg-slate-950 text-xs text-slate-400 uppercase border-b border-slate-800">
                <tr>
                  <th className="p-4">User</th>
                  <th className="p-4">Telegram ID</th>
                  <th className="p-4">Balance</th>
                  <th className="p-4">Traffic (Sent / Recv)</th>
                  <th className="p-4">Status</th>
                  <th className="p-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {filteredUsers.map((u) => (
                  <tr key={u.id} className="hover:bg-slate-850">
                    <td className="p-4 font-semibold text-white">
                      {u.first_name} {u.last_name}
                      {u.username && <span className="text-xs text-indigo-400 block font-normal">@{u.username}</span>}
                    </td>
                    <td className="p-4 font-mono text-xs">{u.telegram_id}</td>
                    <td className="p-4 font-bold text-amber-300">{u.credits} Credits</td>
                    <td className="p-4 text-xs text-slate-300">{u.traffic_provided} / {u.traffic_delivered}</td>
                    <td className="p-4">
                      {u.is_banned ? (
                        <span className="bg-rose-950 text-rose-300 border border-rose-800 text-[10px] font-bold px-2 py-0.5 rounded-full">
                          Banned
                        </span>
                      ) : (
                        <span className="bg-emerald-950 text-emerald-300 border border-emerald-800 text-[10px] font-bold px-2 py-0.5 rounded-full">
                          Active
                        </span>
                      )}
                    </td>
                    <td className="p-4 text-right space-x-2">
                      <button
                        onClick={() => setSelectedUser(u)}
                        className="px-2.5 py-1 rounded-lg bg-indigo-900/60 hover:bg-indigo-800 text-indigo-200 text-xs font-semibold"
                      >
                        Adjust Credits
                      </button>
                      <button
                        onClick={() => handleToggleBan(u.id, u.is_banned)}
                        className={`px-2.5 py-1 rounded-lg text-xs font-semibold ${
                          u.is_banned ? 'bg-emerald-900/60 text-emerald-200' : 'bg-rose-900/60 text-rose-200'
                        }`}
                      >
                        {u.is_banned ? 'Unban' : 'Ban'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Credit Adjustment Modal */}
          {selectedUser && (
            <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4">
              <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 max-w-md w-full space-y-4 shadow-2xl">
                <h3 className="font-bold text-lg text-white">
                  Adjust Credits for {selectedUser.first_name}
                </h3>
                <p className="text-xs text-slate-400">Current Balance: {selectedUser.credits} Credits</p>

                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1">
                    Credit Delta (Use negative to deduct)
                  </label>
                  <input
                    type="number"
                    value={creditAdjustAmount}
                    onChange={(e) => setCreditAdjustAmount(Number(e.target.value))}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2 text-slate-100 text-sm focus:outline-none"
                  />
                </div>

                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1">Reason (Audit Log)</label>
                  <input
                    type="text"
                    value={creditAdjustReason}
                    onChange={(e) => setCreditAdjustReason(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2 text-slate-100 text-sm focus:outline-none"
                  />
                </div>

                <div className="flex space-x-3 pt-2">
                  <button
                    onClick={() => setSelectedUser(null)}
                    className="flex-1 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 font-bold text-white text-xs"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={() => handleAdjustCredits(selectedUser.id)}
                    className="flex-1 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 font-bold text-white text-xs"
                  >
                    Apply Adjustment
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* TAB CONTENT: CAMPAIGNS */}
      {activeTab === 'campaigns' && (
        <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
          <table className="w-full text-left text-sm text-slate-300">
            <thead className="bg-slate-950 text-xs text-slate-400 uppercase border-b border-slate-800">
              <tr>
                <th className="p-4">Title & URL</th>
                <th className="p-4">Visits (Done / Target)</th>
                <th className="p-4">Cost</th>
                <th className="p-4">Status</th>
                <th className="p-4 text-right">Review Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {campaignsList.map((c) => (
                <tr key={c.campaign_id} className="hover:bg-slate-850">
                  <td className="p-4">
                    <div className="font-semibold text-white">{c.title}</div>
                    <a href={c.website_url} target="_blank" rel="noreferrer" className="text-xs text-indigo-400 hover:underline">
                      {c.website_url}
                    </a>
                  </td>
                  <td className="p-4 text-xs font-mono">{c.completed_visits} / {c.required_visits}</td>
                  <td className="p-4 font-bold text-amber-300">{c.total_cost} Credits</td>
                  <td className="p-4">
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
                  </td>
                  <td className="p-4 text-right space-x-2">
                    {c.status === 'PENDING' && (
                      <>
                        <button
                          onClick={() => handleReviewCampaign(c.campaign_id, 'ACTIVE')}
                          className="px-2 py-1 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold rounded-lg"
                        >
                          Approve
                        </button>
                        <button
                          onClick={() => handleReviewCampaign(c.campaign_id, 'REJECTED')}
                          className="px-2 py-1 bg-rose-600 hover:bg-rose-500 text-white text-xs font-bold rounded-lg"
                        >
                          Reject
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* TAB CONTENT: PAYMENTS */}
      {activeTab === 'payments' && (
        <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
          <table className="w-full text-left text-sm text-slate-300">
            <thead className="bg-slate-950 text-xs text-slate-400 uppercase border-b border-slate-800">
              <tr>
                <th className="p-4">Package & Amount</th>
                <th className="p-4">Method & Ref (TrxID)</th>
                <th className="p-4">Credits to Grant</th>
                <th className="p-4">Status</th>
                <th className="p-4 text-right">Verification</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {paymentsList.map((p) => (
                <tr key={p.payment_id} className="hover:bg-slate-850">
                  <td className="p-4">
                    <div className="font-semibold text-white">{p.package_name}</div>
                    <div className="text-xs text-slate-400">{p.amount} {p.currency}</div>
                  </td>
                  <td className="p-4 text-xs font-mono">
                    <span className="text-indigo-300 font-bold">{p.payment_method}</span>
                    <div className="text-slate-400 font-bold">Ref: {p.transaction_reference}</div>
                  </td>
                  <td className="p-4 font-bold text-emerald-400">+{p.credits} Credits</td>
                  <td className="p-4">
                    <span
                      className={`text-[10px] font-bold px-2 py-0.5 rounded-full border ${
                        p.status === 'APPROVED'
                          ? 'bg-emerald-950 text-emerald-300 border-emerald-800'
                          : p.status === 'PENDING'
                          ? 'bg-amber-950 text-amber-300 border-amber-800 animate-pulse'
                          : 'bg-rose-950 text-rose-300 border-rose-800'
                      }`}
                    >
                      {p.status}
                    </span>
                  </td>
                  <td className="p-4 text-right space-x-2">
                    {p.status === 'PENDING' && (
                      <>
                        <button
                          onClick={() => handleVerifyPayment(p.payment_id, 'APPROVE')}
                          className="px-3 py-1 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold rounded-lg shadow-md"
                        >
                          Approve & Add Credits
                        </button>
                        <button
                          onClick={() => handleVerifyPayment(p.payment_id, 'REJECT')}
                          className="px-3 py-1 bg-rose-600 hover:bg-rose-500 text-white text-xs font-bold rounded-lg"
                        >
                          Reject
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* TAB CONTENT: PACKAGES */}
      {activeTab === 'packages' && (
        <div className="space-y-4">
          <button
            onClick={() =>
              setEditingPackage({
                name: 'New Package',
                credits: 1000,
                price: 100,
                currency: 'BDT (৳)',
                bonus_credits: 0,
                display_order: packagesList.length + 1,
                is_active: true,
              })
            }
            className="flex items-center space-x-2 px-4 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 font-bold text-white text-xs"
          >
            <Plus className="w-4 h-4" />
            <span>Add New Package</span>
          </button>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {packagesList.map((pkg) => (
              <div key={pkg.package_id} className="bg-slate-900 border border-slate-800 p-5 rounded-2xl space-y-3">
                <div className="flex justify-between items-center">
                  <h4 className="font-bold text-white text-base">{pkg.name}</h4>
                  <span className="text-xs font-bold text-amber-300">{pkg.price} {pkg.currency}</span>
                </div>
                <div className="text-2xl font-black text-indigo-400">{pkg.credits} Credits</div>
                {pkg.bonus_credits > 0 && <div className="text-xs text-emerald-400 font-semibold">+{pkg.bonus_credits} Bonus Credits</div>}
                <button
                  onClick={() => setEditingPackage(pkg)}
                  className="w-full py-2 bg-slate-800 hover:bg-slate-700 font-bold text-white text-xs rounded-xl"
                >
                  Edit Package
                </button>
              </div>
            ))}
          </div>

          {editingPackage && (
            <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4">
              <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 max-w-md w-full space-y-4 shadow-2xl">
                <h3 className="font-bold text-lg text-white">Edit Package</h3>
                <input
                  type="text"
                  placeholder="Package Name"
                  value={editingPackage.name || ''}
                  onChange={(e) => setEditingPackage({ ...editingPackage, name: e.target.value })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2 text-slate-100 text-sm focus:outline-none"
                />
                <input
                  type="number"
                  placeholder="Credits"
                  value={editingPackage.credits || ''}
                  onChange={(e) => setEditingPackage({ ...editingPackage, credits: Number(e.target.value) })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2 text-slate-100 text-sm focus:outline-none"
                />
                <input
                  type="number"
                  placeholder="Price"
                  value={editingPackage.price || ''}
                  onChange={(e) => setEditingPackage({ ...editingPackage, price: Number(e.target.value) })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2 text-slate-100 text-sm focus:outline-none"
                />
                <input
                  type="number"
                  placeholder="Bonus Credits"
                  value={editingPackage.bonus_credits || 0}
                  onChange={(e) => setEditingPackage({ ...editingPackage, bonus_credits: Number(e.target.value) })}
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2 text-slate-100 text-sm focus:outline-none"
                />
                <div className="flex space-x-3 pt-2">
                  <button
                    onClick={() => setEditingPackage(null)}
                    className="flex-1 py-2.5 rounded-xl bg-slate-800 font-bold text-white text-xs"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={handleSavePackage}
                    className="flex-1 py-2.5 rounded-xl bg-indigo-600 font-bold text-white text-xs"
                  >
                    Save Package
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* TAB CONTENT: BROADCAST */}
      {activeTab === 'broadcast' && (
        <div className="bg-slate-900 border border-slate-800 p-6 rounded-3xl space-y-4 max-w-2xl mx-auto shadow-2xl">
          <h3 className="font-bold text-lg text-white flex items-center gap-2">
            <Send className="w-5 h-5 text-indigo-400" />
            <span>Broadcast Message to All Telegram Users</span>
          </h3>
          <p className="text-xs text-slate-400">
            This message will be dispatched directly to all registered bot user accounts via the Telegram Bot API.
          </p>

          <textarea
            rows={5}
            value={broadcastText}
            onChange={(e) => setBroadcastText(e.target.value)}
            placeholder="Type your announcement or update message here..."
            className="w-full bg-slate-950 border border-slate-800 rounded-2xl p-4 text-slate-100 text-sm focus:outline-none focus:border-indigo-500"
          />

          {broadcastStatus && (
            <div className="p-3 bg-indigo-950/60 border border-indigo-500/30 text-indigo-300 text-xs rounded-xl font-medium">
              {broadcastStatus}
            </div>
          )}

          <button
            onClick={handleSendBroadcast}
            className="w-full py-3 bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 font-bold text-white rounded-xl shadow-lg transition-all"
          >
            Send Telegram Broadcast
          </button>
        </div>
      )}

      {/* TAB CONTENT: SETTINGS */}
      {activeTab === 'settings' && settings && (
        <div className="bg-slate-900 border border-slate-800 p-6 rounded-3xl space-y-5 max-w-2xl mx-auto shadow-2xl">
          <h3 className="font-bold text-lg text-white flex items-center gap-2">
            <SettingsIcon className="w-5 h-5 text-amber-400" />
            <span>System Parameters</span>
          </h3>

          <div className="space-y-4">
            <div>
              <label className="text-xs font-semibold text-slate-300 block mb-1">
                Signup Free Bonus Credits
              </label>
              <input
                type="number"
                value={settings.signup_bonus_credits}
                onChange={(e) => setSettings({ ...settings, signup_bonus_credits: Number(e.target.value) })}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2 text-slate-100 text-sm focus:outline-none"
              />
            </div>

            <div>
              <label className="text-xs font-semibold text-slate-300 block mb-1">
                Referral Reward Credits
              </label>
              <input
                type="number"
                value={settings.referral_reward_credits}
                onChange={(e) => setSettings({ ...settings, referral_reward_credits: Number(e.target.value) })}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2 text-slate-100 text-sm focus:outline-none"
              />
            </div>

            <div>
              <label className="text-xs font-semibold text-slate-300 block mb-1">
                Default Minimum Visit Stay (Seconds)
              </label>
              <input
                type="number"
                value={settings.min_visit_seconds_default}
                onChange={(e) => setSettings({ ...settings, min_visit_seconds_default: Number(e.target.value) })}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2 text-slate-100 text-sm focus:outline-none"
              />
            </div>

            <div className="flex items-center space-x-3 pt-2">
              <input
                type="checkbox"
                id="autoApprove"
                checked={settings.auto_approve_campaigns}
                onChange={(e) => setSettings({ ...settings, auto_approve_campaigns: e.target.checked })}
                className="w-4 h-4 rounded text-indigo-600 focus:ring-0 bg-slate-950 border-slate-800"
              />
              <label htmlFor="autoApprove" className="text-sm font-semibold text-slate-200">
                Auto Approve Campaigns (Skip manual review)
              </label>
            </div>
          </div>

          <button
            onClick={handleSaveSettings}
            className="w-full py-3 bg-gradient-to-r from-amber-600 to-rose-600 hover:from-amber-500 hover:to-rose-500 font-bold text-white rounded-xl shadow-lg transition-all"
          >
            Save System Settings
          </button>
        </div>
      )}

      {/* TAB CONTENT: AUDIT LOGS */}
      {activeTab === 'logs' && (
        <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
          <table className="w-full text-left text-sm text-slate-300">
            <thead className="bg-slate-950 text-xs text-slate-400 uppercase border-b border-slate-800">
              <tr>
                <th className="p-4">Timestamp</th>
                <th className="p-4">Action</th>
                <th className="p-4">Details</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {logsList.map((log) => (
                <tr key={log.log_id} className="hover:bg-slate-850">
                  <td className="p-4 text-xs font-mono text-slate-400">
                    {new Date(log.created_at).toLocaleString()}
                  </td>
                  <td className="p-4 font-bold text-amber-300 text-xs">{log.action}</td>
                  <td className="p-4 text-xs text-slate-200">{log.details}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

    </div>
  );
};
