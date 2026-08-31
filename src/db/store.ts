import {
  User,
  Campaign,
  CampaignVisit,
  CreditTransaction,
  Referral,
  Package,
  Payment,
  SupportTicket,
  TicketStatus,
  SystemSettings,
  AdminAuditLog,
  GlobalStats,
  TransactionType,
} from '../types.js';

import crypto from 'crypto';
import fs from 'fs';
import path from 'path';

// File persistence path for standard node environment
const DATA_FILE_PATH = path.join(process.cwd(), 'infinitehits_db.json');

export interface DatabaseState {
  users: Record<string, User>;
  campaigns: Record<string, Campaign>;
  visits: Record<string, CampaignVisit>;
  transactions: CreditTransaction[];
  referrals: Referral[];
  packages: Package[];
  payments: Record<string, Payment>;
  tickets: Record<string, SupportTicket>;
  admin_logs: AdminAuditLog[];
  settings: SystemSettings;
}

const defaultPackages: Package[] = [
  {
    package_id: 'pkg_starter',
    name: 'Starter',
    credits: 500,
    price: 50,
    currency: 'BDT (৳)',
    bonus_credits: 0,
    display_order: 1,
    is_active: true,
    tag: 'Popular for Testing',
  },
  {
    package_id: 'pkg_popular',
    name: 'Popular',
    credits: 1500,
    price: 120,
    currency: 'BDT (৳)',
    bonus_credits: 100,
    display_order: 2,
    is_active: true,
    tag: 'Best Value',
  },
  {
    package_id: 'pkg_pro',
    name: 'Pro',
    credits: 5000,
    price: 350,
    currency: 'BDT (৳)',
    bonus_credits: 500,
    display_order: 3,
    is_active: true,
    tag: 'Growth Boost',
  },
  {
    package_id: 'pkg_business',
    name: 'Business',
    credits: 10000,
    price: 600,
    currency: 'BDT (৳)',
    bonus_credits: 1500,
    display_order: 4,
    is_active: true,
    tag: 'Maximum Traffic',
  },
];

const defaultSettings: SystemSettings = {
  signup_bonus_credits: 50,
  referral_reward_credits: 10,
  min_visit_seconds_default: 20,
  cost_per_visit_default: 1,
  auto_approve_campaigns: false,
  bot_maintenance_mode: false,
  payment_methods: [
    {
      name: 'bKash Personal',
      number: '01700000000',
      instructions: 'Send Money to 01700000000 (Personal). Enter your Transaction ID (TrxID) below.',
      is_active: true,
    },
    {
      name: 'Nagad Personal',
      number: '01800000000',
      instructions: 'Send Money to 01800000000 (Personal). Enter your Transaction ID (TrxID) below.',
      is_active: true,
    },
    {
      name: 'USDT (TRC20)',
      number: 'TY34kLpQ99zM88...TRC20Address',
      instructions: 'Send USDT to TRC20 wallet address. Submit TxID.',
      is_active: true,
    },
  ],
  notification_templates: {
    signup_welcome: '🎉 Welcome to InfiniteHits!\n\nYou received:\n50 Free Credits\n\nUse your Credits to promote your website.\n1 Credit = 1 eligible promotional visit/action.',
    campaign_approved: '✅ Your campaign "{title}" has been approved by admin! Traffic is now live.',
    campaign_rejected: '❌ Your campaign "{title}" was rejected. Your credits have been refunded.',
    payment_approved: '✅ Payment Approved!\n\nYour purchase of {credits} credits has been added to your balance.',
    payment_rejected: '❌ Payment Verification Failed!\n\nReference: {ref}. Reason: Invalid Transaction ID.',
  },
};

const initialDemoUser: User = {
  id: '987654321',
  telegram_id: 987654321,
  first_name: 'Alex',
  last_name: 'Trader',
  username: 'alex_trader',
  credits: 50,
  referral_code: 'REF9876',
  referral_count: 0,
  referral_earnings: 0,
  traffic_delivered: 0,
  traffic_provided: 0,
  is_banned: false,
  is_admin: true,
  created_at: new Date().toISOString(),
  last_active_at: new Date().toISOString(),
};

const initialDemoCampaign: Campaign = {
  campaign_id: 'cmp_demo_1',
  user_id: '987654321',
  telegram_id: 987654321,
  website_url: 'https://news.google.com',
  title: 'Google News Portal Demo',
  required_visits: 500,
  completed_visits: 42,
  remaining_visits: 458,
  cost_per_visit: 1,
  total_cost: 500,
  status: 'ACTIVE',
  minimum_visit_seconds: 20,
  created_at: new Date(Date.now() - 3600000 * 24).toISOString(),
  updated_at: new Date().toISOString(),
};

class DataStore {
  private state: DatabaseState;

  constructor() {
    this.state = this.loadFromFile() || {
      users: { [initialDemoUser.id]: initialDemoUser },
      campaigns: { [initialDemoCampaign.campaign_id]: initialDemoCampaign },
      visits: {},
      transactions: [
        {
          transaction_id: 'tx_init_1',
          user_id: '987654321',
          telegram_id: 987654321,
          type: 'SIGNUP_BONUS',
          amount: 50,
          balance_after: 50,
          reference: 'WELCOME_BONUS',
          description: 'Initial Signup Free Credits',
          created_at: new Date().toISOString(),
        },
      ],
      referrals: [],
      packages: defaultPackages,
      payments: {},
      tickets: {},
      admin_logs: [],
      settings: defaultSettings,
    };
    this.saveToFile();
  }

  private loadFromFile(): DatabaseState | null {
    try {
      if (fs.existsSync(DATA_FILE_PATH)) {
        const raw = fs.readFileSync(DATA_FILE_PATH, 'utf-8');
        return JSON.parse(raw);
      }
    } catch (e) {
      console.error('Error loading DB file:', e);
    }
    return null;
  }

  private saveToFile() {
    try {
      fs.writeFileSync(DATA_FILE_PATH, JSON.stringify(this.state, null, 2), 'utf-8');
    } catch (e) {
      console.error('Error saving DB file:', e);
    }
  }

  // --- SETTINGS ---
  getSettings(): SystemSettings {
    return { ...this.state.settings };
  }

  updateSettings(newSettings: Partial<SystemSettings>, adminId: string): SystemSettings {
    this.state.settings = { ...this.state.settings, ...newSettings };
    this.logAdminAction(adminId, 'UPDATE_SETTINGS', undefined, JSON.stringify(newSettings));
    this.saveToFile();
    return this.getSettings();
  }

  // --- USERS & AUTHENTICATION ---
  getUserByTelegramId(telegramId: number): User | null {
    const userId = String(telegramId);
    return this.state.users[userId] || null;
  }

  getOrCreateUser(telegramId: number, firstName: string, lastName?: string, username?: string, referralCodeInput?: string): { user: User; isNew: boolean } {
    const userId = String(telegramId);
    if (this.state.users[userId]) {
      // Update last active
      this.state.users[userId].last_active_at = new Date().toISOString();
      if (firstName) this.state.users[userId].first_name = firstName;
      if (lastName) this.state.users[userId].last_name = lastName;
      if (username) this.state.users[userId].username = username;
      this.saveToFile();
      return { user: this.state.users[userId], isNew: false };
    }

    // Determine admin status based on env or first user
    const adminIds = (process.env.ADMIN_TELEGRAM_IDS || '123456789,987654321').split(',').map((id) => id.trim());
    const isAdmin = adminIds.includes(String(telegramId)) || Object.keys(this.state.users).length === 0;

    const signupBonus = this.state.settings.signup_bonus_credits;
    const refCode = 'REF' + Math.random().toString(36).substring(2, 8).toUpperCase();

    const newUser: User = {
      id: userId,
      telegram_id: telegramId,
      first_name: firstName || 'User',
      last_name: lastName || '',
      username: username || '',
      credits: signupBonus,
      referral_code: refCode,
      referral_count: 0,
      referral_earnings: 0,
      traffic_delivered: 0,
      traffic_provided: 0,
      is_banned: false,
      is_admin: isAdmin,
      created_at: new Date().toISOString(),
      last_active_at: new Date().toISOString(),
    };

    // Check referral link
    if (referralCodeInput) {
      const referrer = Object.values(this.state.users).find((u) => u.referral_code.toLowerCase() === referralCodeInput.toLowerCase());
      if (referrer && referrer.id !== userId) {
        newUser.referred_by = referrer.id;
        // Grant referral reward to referrer
        const refReward = this.state.settings.referral_reward_credits;
        referrer.credits += refReward;
        referrer.referral_count += 1;
        referrer.referral_earnings += refReward;

        this.addTransaction({
          user_id: referrer.id,
          telegram_id: referrer.telegram_id,
          type: 'REFERRAL_REWARD',
          amount: refReward,
          balance_after: referrer.credits,
          reference: `REF_${newUser.id}`,
          description: `Referral reward for inviting ${newUser.first_name}`,
        });

        this.state.referrals.push({
          referral_id: `ref_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`,
          referrer_user_id: referrer.id,
          referred_user_id: newUser.id,
          referrer_telegram_id: referrer.telegram_id,
          referred_telegram_id: newUser.telegram_id,
          reward_credits: refReward,
          created_at: new Date().toISOString(),
        });
      }
    }

    this.state.users[userId] = newUser;

    // Transaction for signup bonus
    if (signupBonus > 0) {
      this.addTransaction({
        user_id: userId,
        telegram_id: telegramId,
        type: 'SIGNUP_BONUS',
        amount: signupBonus,
        balance_after: signupBonus,
        reference: 'WELCOME_FREE_CREDITS',
        description: `Signup Bonus Free Credits`,
      });
    }

    this.saveToFile();
    return { user: newUser, isNew: true };
  }

  getAllUsers(): User[] {
    return Object.values(this.state.users);
  }

  banUser(userId: string, reason: string, adminId: string): User | null {
    const user = this.state.users[userId];
    if (!user) return null;
    user.is_banned = true;
    user.ban_reason = reason;
    this.logAdminAction(adminId, 'BAN_USER', userId, `Reason: ${reason}`);
    this.saveToFile();
    return user;
  }

  unbanUser(userId: string, adminId: string): User | null {
    const user = this.state.users[userId];
    if (!user) return null;
    user.is_banned = false;
    delete user.ban_reason;
    this.logAdminAction(adminId, 'UNBAN_USER', userId, 'User unbanned');
    this.saveToFile();
    return user;
  }

  adjustUserCredits(userId: string, delta: number, reason: string, adminId: string): { user: User; tx: CreditTransaction } | null {
    const user = this.state.users[userId];
    if (!user) return null;

    if (user.credits + delta < 0) {
      throw new Error('Insufficient user credit balance for deduction');
    }

    user.credits += delta;
    const tx = this.addTransaction({
      user_id: userId,
      telegram_id: user.telegram_id,
      type: 'ADMIN_ADJUSTMENT',
      amount: delta,
      balance_after: user.credits,
      reference: `ADMIN_${adminId}`,
      description: `Admin manual adjustment: ${reason}`,
    });

    this.logAdminAction(adminId, 'CREDIT_ADJUSTMENT', userId, `Delta: ${delta}, New Balance: ${user.credits}, Reason: ${reason}`);
    this.saveToFile();
    return { user, tx };
  }

  // --- TRANSACTIONS ---
  private addTransaction(params: Omit<CreditTransaction, 'transaction_id' | 'created_at'>): CreditTransaction {
    const tx: CreditTransaction = {
      ...params,
      transaction_id: `tx_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`,
      created_at: new Date().toISOString(),
    };
    this.state.transactions.unshift(tx);
    return tx;
  }

  getUserTransactions(userId: string): CreditTransaction[] {
    return this.state.transactions.filter((t) => t.user_id === userId);
  }

  getAllTransactions(): CreditTransaction[] {
    return this.state.transactions;
  }

  // --- CAMPAIGNS ---
  validateUrl(urlStr: string): { valid: boolean; error?: string } {
    try {
      const parsed = new URL(urlStr);
      if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
        return { valid: false, error: 'URL must begin with http:// or https://' };
      }
      const host = parsed.hostname.toLowerCase();
      if (
        host === 'localhost' ||
        host === '127.0.0.1' ||
        host.startsWith('192.168.') ||
        host.startsWith('10.') ||
        host.endsWith('.local')
      ) {
        return { valid: false, error: 'Localhost and private network IP addresses are forbidden.' };
      }
      return { valid: true };
    } catch (e) {
      return { valid: false, error: 'Invalid URL format. Please provide full address including https://' };
    }
  }

  createCampaign(params: {
    user_id: string;
    website_url: string;
    title?: string;
    required_visits: number;
    cost_per_visit?: number;
    minimum_visit_seconds?: number;
  }): Campaign {
    const user = this.state.users[params.user_id];
    if (!user) throw new Error('User not found');
    if (user.is_banned) throw new Error('Account is banned');

    const urlCheck = this.validateUrl(params.website_url);
    if (!urlCheck.valid) {
      throw new Error(urlCheck.error || 'Invalid campaign website URL');
    }

    const costPerVisit = params.cost_per_visit || this.state.settings.cost_per_visit_default;
    const totalCost = params.required_visits * costPerVisit;

    if (user.credits < totalCost) {
      throw new Error(`Insufficient credits. Required: ${totalCost}, Available: ${user.credits}`);
    }

    // Atomic deduction
    user.credits -= totalCost;
    this.addTransaction({
      user_id: user.id,
      telegram_id: user.telegram_id,
      type: 'CAMPAIGN_PURCHASE',
      amount: -totalCost,
      balance_after: user.credits,
      reference: `CAMPAIGN_CREATE`,
      description: `Promote ${params.website_url} (${params.required_visits} visits)`,
    });

    const status: Campaign['status'] = this.state.settings.auto_approve_campaigns ? 'ACTIVE' : 'PENDING';
    const campaignId = `cmp_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`;

    let title = params.title;
    if (!title) {
      try {
        title = new URL(params.website_url).hostname;
      } catch (e) {
        title = 'Website Campaign';
      }
    }

    const campaign: Campaign = {
      campaign_id: campaignId,
      user_id: user.id,
      telegram_id: user.telegram_id,
      website_url: params.website_url,
      title: title || 'Promotional Site',
      required_visits: params.required_visits,
      completed_visits: 0,
      remaining_visits: params.required_visits,
      cost_per_visit: costPerVisit,
      total_cost: totalCost,
      status,
      minimum_visit_seconds: params.minimum_visit_seconds || this.state.settings.min_visit_seconds_default,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    };

    this.state.campaigns[campaignId] = campaign;
    this.saveToFile();
    return campaign;
  }

  getActiveCampaignsForUser(visitorUserId: string): Campaign[] {
    return Object.values(this.state.campaigns).filter((cmp) => {
      if (cmp.status !== 'ACTIVE') return false;
      if (cmp.remaining_visits <= 0) return false;
      if (cmp.user_id === visitorUserId) return false; // Prevent self-visiting

      // Prevent duplicate completed visit
      const existingVisit = Object.values(this.state.visits).find(
        (v) => v.campaign_id === cmp.campaign_id && v.visitor_user_id === visitorUserId && v.status === 'VERIFIED'
      );
      return !existingVisit;
    });
  }

  getUserCampaigns(userId: string): Campaign[] {
    return Object.values(this.state.campaigns).filter((c) => c.user_id === userId);
  }

  getAllCampaigns(): Campaign[] {
    return Object.values(this.state.campaigns);
  }

  updateCampaignStatus(campaignId: string, status: Campaign['status'], adminId?: string, reason?: string): Campaign {
    const campaign = this.state.campaigns[campaignId];
    if (!campaign) throw new Error('Campaign not found');

    const prevStatus = campaign.status;
    campaign.status = status;
    campaign.updated_at = new Date().toISOString();

    // If REJECTED or CANCELLED, refund unused visits to owner
    if ((status === 'REJECTED' || status === 'CANCELLED') && prevStatus !== 'REJECTED' && prevStatus !== 'CANCELLED') {
      const refundVisits = campaign.remaining_visits;
      const refundCredits = refundVisits * campaign.cost_per_visit;
      if (refundCredits > 0) {
        const owner = this.state.users[campaign.user_id];
        if (owner) {
          owner.credits += refundCredits;
          this.addTransaction({
            user_id: owner.id,
            telegram_id: owner.telegram_id,
            type: 'REFUND',
            amount: refundCredits,
            balance_after: owner.credits,
            reference: campaign.campaign_id,
            description: `Refund for ${refundVisits} unused campaign visits (${campaign.title})`,
          });
        }
      }
    }

    if (adminId) {
      this.logAdminAction(adminId, `UPDATE_CAMPAIGN_STATUS_${status}`, undefined, `Campaign ID: ${campaignId}, Reason: ${reason || 'N/A'}`);
    }

    this.saveToFile();
    return campaign;
  }

  // --- TRAFFIC VISIT VERIFICATION ---
  verifyVisit(visitorUserId: string, campaignId: string, durationSeconds: number): { success: boolean; reward: number; newBalance: number; error?: string } {
    const visitor = this.state.users[visitorUserId];
    if (!visitor) return { success: false, reward: 0, newBalance: 0, error: 'User not found' };
    if (visitor.is_banned) return { success: false, reward: 0, newBalance: visitor.credits, error: 'Account is banned' };

    const campaign = this.state.campaigns[campaignId];
    if (!campaign) return { success: false, reward: 0, newBalance: visitor.credits, error: 'Campaign not found' };

    if (campaign.status !== 'ACTIVE') return { success: false, reward: 0, newBalance: visitor.credits, error: 'Campaign is not active' };
    if (campaign.remaining_visits <= 0) return { success: false, reward: 0, newBalance: visitor.credits, error: 'Campaign has no remaining visits' };
    if (campaign.user_id === visitorUserId) return { success: false, reward: 0, newBalance: visitor.credits, error: 'Cannot visit your own campaign' };

    // Anti-Abuse: Prevent duplicate verification
    const existingVisitKey = `${campaignId}_${visitorUserId}`;
    if (this.state.visits[existingVisitKey] && this.state.visits[existingVisitKey].status === 'VERIFIED') {
      return { success: false, reward: 0, newBalance: visitor.credits, error: 'You have already completed this campaign' };
    }

    // Minimum stay duration verification
    if (durationSeconds < campaign.minimum_visit_seconds - 2) {
      // allow 2s margin for network jitter
      return {
        success: false,
        reward: 0,
        newBalance: visitor.credits,
        error: `Required stay duration was ${campaign.minimum_visit_seconds}s, but only ${durationSeconds}s recorded.`,
      };
    }

    const reward = campaign.cost_per_visit;
    visitor.credits += reward;
    visitor.traffic_provided += 1;

    // Update campaign stats
    campaign.completed_visits += 1;
    campaign.remaining_visits = Math.max(0, campaign.required_visits - campaign.completed_visits);
    if (campaign.remaining_visits === 0) {
      campaign.status = 'COMPLETED';
    }

    // Update campaign owner traffic delivered
    const owner = this.state.users[campaign.user_id];
    if (owner) {
      owner.traffic_delivered += 1;
    }

    // Log visit record
    const visit: CampaignVisit = {
      visit_id: `vst_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`,
      campaign_id: campaign.campaign_id,
      visitor_user_id: visitor.id,
      visitor_telegram_id: visitor.telegram_id,
      duration_seconds: durationSeconds,
      reward_credits: reward,
      status: 'VERIFIED',
      created_at: new Date().toISOString(),
    };
    this.state.visits[existingVisitKey] = visit;

    // Log transaction
    this.addTransaction({
      user_id: visitor.id,
      telegram_id: visitor.telegram_id,
      type: 'TRAFFIC_REWARD',
      amount: reward,
      balance_after: visitor.credits,
      reference: campaign.campaign_id,
      description: `Traffic reward for visiting ${campaign.title} (${durationSeconds}s)`,
    });

    this.saveToFile();
    return { success: true, reward, newBalance: visitor.credits };
  }

  // --- PACKAGES & PAYMENTS ---
  getPackages(): Package[] {
    return this.state.packages.filter((p) => p.is_active).sort((a, b) => a.display_order - b.display_order);
  }

  getAllPackages(): Package[] {
    return [...this.state.packages].sort((a, b) => a.display_order - b.display_order);
  }

  savePackage(pkg: Package, adminId: string): Package {
    const idx = this.state.packages.findIndex((p) => p.package_id === pkg.package_id);
    if (idx >= 0) {
      this.state.packages[idx] = pkg;
    } else {
      this.state.packages.push(pkg);
    }
    this.logAdminAction(adminId, 'SAVE_PACKAGE', undefined, `Package: ${pkg.name}`);
    this.saveToFile();
    return pkg;
  }

  deletePackage(packageId: string, adminId: string) {
    this.state.packages = this.state.packages.filter((p) => p.package_id !== packageId);
    this.logAdminAction(adminId, 'DELETE_PACKAGE', undefined, `Package ID: ${packageId}`);
    this.saveToFile();
  }

  createPayment(params: {
    user_id: string;
    package_id: string;
    payment_method: string;
    transaction_reference: string;
    sender_number?: string;
  }): Payment {
    const user = this.state.users[params.user_id];
    if (!user) throw new Error('User not found');

    const pkg = this.state.packages.find((p) => p.package_id === params.package_id);
    if (!pkg) throw new Error('Selected package not found');

    const paymentId = `pay_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`;
    const totalCredits = pkg.credits + (pkg.bonus_credits || 0);

    const payment: Payment = {
      payment_id: paymentId,
      user_id: user.id,
      telegram_id: user.telegram_id,
      package_id: pkg.package_id,
      package_name: pkg.name,
      amount: pkg.price,
      currency: pkg.currency,
      credits: totalCredits,
      payment_method: params.payment_method,
      transaction_reference: params.transaction_reference,
      sender_number: params.sender_number,
      status: 'PENDING',
      created_at: new Date().toISOString(),
    };

    this.state.payments[paymentId] = payment;
    this.saveToFile();
    return payment;
  }

  getUserPayments(userId: string): Payment[] {
    return Object.values(this.state.payments).filter((p) => p.user_id === userId);
  }

  getAllPayments(): Payment[] {
    return Object.values(this.state.payments);
  }

  verifyPayment(paymentId: string, action: 'APPROVE' | 'REJECT', adminId: string, notes?: string): Payment {
    const payment = this.state.payments[paymentId];
    if (!payment) throw new Error('Payment record not found');
    if (payment.status !== 'PENDING') throw new Error(`Payment is already ${payment.status}`);

    const user = this.state.users[payment.user_id];
    if (!user) throw new Error('Associated user not found');

    if (action === 'APPROVE') {
      payment.status = 'APPROVED';
      payment.verified_at = new Date().toISOString();
      payment.verified_by = adminId;
      payment.notes = notes || 'Verified by admin';

      // Grant credits
      user.credits += payment.credits;

      this.addTransaction({
        user_id: user.id,
        telegram_id: user.telegram_id,
        type: 'PACKAGE_PURCHASE',
        amount: payment.credits,
        balance_after: user.credits,
        reference: payment.payment_id,
        description: `Purchased ${payment.package_name} package (${payment.amount} ${payment.currency})`,
      });

      this.logAdminAction(adminId, 'APPROVE_PAYMENT', user.id, `Payment ID: ${paymentId}, Credits Added: ${payment.credits}`);
    } else {
      payment.status = 'REJECTED';
      payment.verified_at = new Date().toISOString();
      payment.verified_by = adminId;
      payment.notes = notes || 'Rejected by admin';

      this.logAdminAction(adminId, 'REJECT_PAYMENT', user.id, `Payment ID: ${paymentId}, Reason: ${notes}`);
    }

    this.saveToFile();
    return payment;
  }

  // --- SUPPORT TICKETS ---
  createSupportTicket(params: { user_id: string; category: string; message: string; attachment_url?: string }): SupportTicket {
    const user = this.state.users[params.user_id];
    if (!user) throw new Error('User not found');

    const ticketId = `tkt_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`;
    const ticket: SupportTicket = {
      ticket_id: ticketId,
      user_id: user.id,
      telegram_id: user.telegram_id,
      user_name: `${user.first_name} ${user.last_name || ''}`.trim(),
      category: params.category,
      message: params.message,
      attachment_url: params.attachment_url,
      status: 'OPEN',
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    };

    this.state.tickets[ticketId] = ticket;
    this.saveToFile();
    return ticket;
  }

  getUserTickets(userId: string): SupportTicket[] {
    return Object.values(this.state.tickets).filter((t) => t.user_id === userId);
  }

  getAllTickets(): SupportTicket[] {
    return Object.values(this.state.tickets);
  }

  replySupportTicket(ticketId: string, replyMessage: string, status: TicketStatus, adminId: string): SupportTicket {
    const ticket = this.state.tickets[ticketId];
    if (!ticket) throw new Error('Ticket not found');
    ticket.admin_reply = replyMessage;
    ticket.status = status;
    ticket.updated_at = new Date().toISOString();

    this.logAdminAction(adminId, 'REPLY_SUPPORT_TICKET', ticket.user_id, `Ticket: ${ticketId}`);
    this.saveToFile();
    return ticket;
  }

  // --- ADMIN LOGS & DASHBOARD STATS ---
  private logAdminAction(adminId: string, action: string, targetUserId?: string, details?: string) {
    const log: AdminAuditLog = {
      log_id: `log_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`,
      admin_id: adminId,
      action,
      target_user_id: targetUserId,
      details: details || '',
      created_at: new Date().toISOString(),
    };
    this.state.admin_logs.unshift(log);
  }

  getAdminLogs(): AdminAuditLog[] {
    return this.state.admin_logs;
  }

  getGlobalStats(): GlobalStats {
    const users = Object.values(this.state.users);
    const campaigns = Object.values(this.state.campaigns);
    const payments = Object.values(this.state.payments);

    const now = Date.now();
    const activeCutoff = now - 7 * 24 * 3600 * 1000; // 7 days active

    const activeUsers = users.filter((u) => new Date(u.last_active_at).getTime() >= activeCutoff).length;
    const activeCampaigns = campaigns.filter((c) => c.status === 'ACTIVE').length;
    const completedCampaigns = campaigns.filter((c) => c.status === 'COMPLETED').length;

    const totalCreditsCirculating = users.reduce((acc, u) => acc + u.credits, 0);
    const totalCreditsPurchased = payments.filter((p) => p.status === 'APPROVED').reduce((acc, p) => acc + p.credits, 0);
    const totalCreditsSpent = campaigns.reduce((acc, c) => acc + c.total_cost, 0);

    const totalPayments = payments.length;
    const pendingPayments = payments.filter((p) => p.status === 'PENDING').length;
    const totalTrafficDelivered = campaigns.reduce((acc, c) => acc + c.completed_visits, 0);

    return {
      total_users: users.length,
      active_users: activeUsers,
      total_campaigns: campaigns.length,
      active_campaigns: activeCampaigns,
      completed_campaigns: completedCampaigns,
      total_credits_circulating: totalCreditsCirculating,
      total_credits_purchased: totalCreditsPurchased,
      total_credits_spent: totalCreditsSpent,
      total_payments: totalPayments,
      pending_payments: pendingPayments,
      total_traffic_delivered: totalTrafficDelivered,
    };
  }
}

export const db = new DataStore();
