export type CampaignStatus = 'PENDING' | 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'REJECTED' | 'CANCELLED';

export type TransactionType =
  | 'SIGNUP_BONUS'
  | 'REFERRAL_REWARD'
  | 'TRAFFIC_REWARD'
  | 'CAMPAIGN_PURCHASE'
  | 'ADMIN_ADJUSTMENT'
  | 'PACKAGE_PURCHASE'
  | 'REFUND';

export type PaymentStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'REFUNDED';

export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';

export interface User {
  id: string; // Internal or Telegram ID string
  telegram_id: number;
  first_name: string;
  last_name?: string;
  username?: string;
  credits: number;
  referral_code: string;
  referred_by?: string;
  referral_count: number;
  referral_earnings: number;
  traffic_delivered: number;
  traffic_provided: number;
  is_banned: boolean;
  ban_reason?: string;
  is_admin: boolean;
  created_at: string;
  last_active_at: string;
}

export interface Campaign {
  campaign_id: string;
  user_id: string;
  telegram_id: number;
  website_url: string;
  title: string;
  required_visits: number;
  completed_visits: number;
  remaining_visits: number;
  cost_per_visit: number;
  total_cost: number;
  status: CampaignStatus;
  minimum_visit_seconds: number;
  created_at: string;
  updated_at: string;
}

export interface CampaignVisit {
  visit_id: string;
  campaign_id: string;
  visitor_user_id: string;
  visitor_telegram_id: number;
  duration_seconds: number;
  reward_credits: number;
  status: 'VERIFIED' | 'FAILED' | 'REJECTED';
  created_at: string;
}

export interface CreditTransaction {
  transaction_id: string;
  user_id: string;
  telegram_id: number;
  type: TransactionType;
  amount: number; // positive for addition, negative for deduction
  balance_after: number;
  reference?: string;
  description: string;
  created_at: string;
}

export interface Referral {
  referral_id: string;
  referrer_user_id: string;
  referred_user_id: string;
  referrer_telegram_id: number;
  referred_telegram_id: number;
  reward_credits: number;
  created_at: string;
}

export interface Package {
  package_id: string;
  name: string;
  credits: number;
  price: number;
  currency: string;
  bonus_credits: number;
  display_order: number;
  is_active: boolean;
  tag?: string;
}

export interface Payment {
  payment_id: string;
  user_id: string;
  telegram_id: number;
  package_id: string;
  package_name: string;
  amount: number;
  currency: string;
  credits: number;
  payment_method: string;
  transaction_reference: string;
  sender_number?: string;
  status: PaymentStatus;
  created_at: string;
  verified_at?: string;
  verified_by?: string;
  notes?: string;
}

export interface SupportTicket {
  ticket_id: string;
  user_id: string;
  telegram_id: number;
  user_name: string;
  category: string;
  message: string;
  attachment_url?: string;
  status: TicketStatus;
  admin_reply?: string;
  created_at: string;
  updated_at: string;
}

export interface SystemSettings {
  signup_bonus_credits: number;
  referral_reward_credits: number;
  min_visit_seconds_default: number;
  cost_per_visit_default: number;
  auto_approve_campaigns: boolean;
  bot_maintenance_mode: boolean;
  payment_methods: {
    name: string;
    number: string;
    instructions: string;
    is_active: boolean;
  }[];
  notification_templates: {
    signup_welcome: string;
    campaign_approved: string;
    campaign_rejected: string;
    payment_approved: string;
    payment_rejected: string;
  };
}

export interface AdminAuditLog {
  log_id: string;
  admin_id: string;
  action: string;
  target_user_id?: string;
  details: string;
  created_at: string;
}

export interface GlobalStats {
  total_users: number;
  active_users: number;
  total_campaigns: number;
  active_campaigns: number;
  completed_campaigns: number;
  total_credits_circulating: number;
  total_credits_purchased: number;
  total_credits_spent: number;
  total_payments: number;
  pending_payments: number;
  total_traffic_delivered: number;
}
