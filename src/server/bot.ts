import crypto from 'crypto';
import { db } from '../db/store.js';

const BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN || '8875964066:AAEzo1ILFvWc3n4841Dj0bjJGjDxaBBApbA';
const BOT_USERNAME = process.env.TELEGRAM_BOT_USERNAME || 'InfiniteHits_bot';
const TELEGRAM_API_BASE = `https://api.telegram.org/bot${BOT_TOKEN}`;

/**
 * Validate Telegram Mini App initData server-side using HMAC-SHA256
 */
export function verifyTelegramWebAppData(initDataRaw: string): { valid: boolean; user?: any } {
  if (!initDataRaw) return { valid: false };

  try {
    const urlParams = new URLSearchParams(initDataRaw);
    const hash = urlParams.get('hash');
    if (!hash) return { valid: false };

    urlParams.delete('hash');
    const params: string[] = [];
    urlParams.forEach((val, key) => {
      params.push(`${key}=${val}`);
    });
    params.sort();

    const dataCheckString = params.join('\n');
    const secretKey = crypto.createHmac('sha256', 'WebAppData').update(BOT_TOKEN).digest();
    const calculatedHash = crypto.createHmac('sha256', secretKey).update(dataCheckString).digest('hex');

    const isValid = calculatedHash === hash;
    const userJson = urlParams.get('user');
    const user = userJson ? JSON.parse(userJson) : undefined;

    return { valid: isValid, user };
  } catch (e) {
    console.error('Error verifying Telegram WebApp data:', e);
    return { valid: false };
  }
}

/**
 * Send message via Telegram Bot API
 */
export async function sendTelegramMessage(chatId: number | string, text: string, replyMarkup?: any) {
  try {
    const response = await fetch(`${TELEGRAM_API_BASE}/sendMessage`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        chat_id: chatId,
        text,
        parse_mode: 'HTML',
        disable_web_page_preview: true,
        reply_markup: replyMarkup,
      }),
    });
    return await response.json();
  } catch (e) {
    console.error('Failed to send Telegram message:', e);
    return null;
  }
}

/**
 * Main Webhook and Interactive Command Handler
 */
export async function handleTelegramWebhookUpdate(update: any, appUrl: string) {
  if (!update) return;

  const miniAppUrl = process.env.WEB_APP_URL || appUrl || 'https://ais-dev-7iikegl5iksyqo2q4epmaf-696349996958.asia-east1.run.app';

  // 1. Handle Callback Queries (Inline Button Clicks)
  if (update.callback_query) {
    const cb = update.callback_query;
    const chatId = cb.message.chat.id;
    const data = cb.data;
    const fromUser = cb.from;

    const { user } = db.getOrCreateUser(fromUser.id, fromUser.first_name, fromUser.last_name, fromUser.username);

    if (data === 'btn_home') {
      await sendHomeMessage(chatId, user, miniAppUrl);
    } else if (data === 'btn_traffic') {
      await sendTrafficMessage(chatId, user, miniAppUrl);
    } else if (data === 'btn_promote') {
      await sendPromoteMessage(chatId, user, miniAppUrl);
    } else if (data === 'btn_buy') {
      await sendBuyMessage(chatId, user, miniAppUrl);
    } else if (data === 'btn_earn') {
      await sendEarnMessage(chatId, user, miniAppUrl);
    } else if (data === 'btn_referral') {
      await sendReferralMessage(chatId, user, miniAppUrl);
    } else if (data === 'btn_stats') {
      await sendStatsMessage(chatId, user);
    } else if (data === 'btn_history') {
      await sendHistoryMessage(chatId, user);
    } else if (data === 'btn_support') {
      await sendSupportMessage(chatId, user, miniAppUrl);
    } else if (data === 'btn_settings') {
      await sendSettingsMessage(chatId, user);
    }
    return;
  }

  // 2. Handle Direct Messages / Commands
  if (update.message && update.message.text) {
    const msg = update.message;
    const chatId = msg.chat.id;
    const text = msg.text.trim();
    const fromUser = msg.from;

    // Check for referral code in start param: /start REF123
    let startRefCode: string | undefined;
    if (text.startsWith('/start')) {
      const parts = text.split(' ');
      if (parts.length > 1) {
        startRefCode = parts[1].trim();
      }
    }

    const { user, isNew } = db.getOrCreateUser(fromUser.id, fromUser.first_name, fromUser.last_name, fromUser.username, startRefCode);

    if (user.is_banned) {
      await sendTelegramMessage(chatId, `🚫 <b>Account Suspended</b>\n\nYour account has been banned for violating policies.\nReason: ${user.ban_reason || 'N/A'}`);
      return;
    }

    // Command Dispatcher
    if (text.startsWith('/start')) {
      const settings = db.getSettings();
      let welcomeTxt = `🎉 <b>Welcome to InfiniteHits!</b>\n\n`;
      if (isNew) {
        welcomeTxt += `You received:\n<b>${settings.signup_bonus_credits} Free Credits</b>\n\n`;
      }
      welcomeTxt += `Use your Credits to promote your website.\n<b>1 Credit = 1 eligible promotional visit/action.</b>\n\n`;
      welcomeTxt += `🚀 <b>InfiniteHits</b>\nWelcome, ${user.first_name}\n\n`;
      welcomeTxt += `💰 Credits: <b>${user.credits}</b>\n`;
      welcomeTxt += `🌐 Active Campaigns: <b>${db.getActiveCampaignsForUser(user.id).length}</b>\n`;
      welcomeTxt += `👁️ Traffic Delivered: <b>${user.traffic_delivered}</b>\n`;
      welcomeTxt += `👥 Referrals: <b>${user.referral_count}</b>`;

      await sendTelegramMessage(chatId, welcomeTxt, getMainMenuKeyboard(miniAppUrl, user.is_admin));
    } else if (text === '/menu' || text === '🏠 Home' || text === '/home') {
      await sendHomeMessage(chatId, user, miniAppUrl);
    } else if (text === '/traffic' || text === '🌐 Get Traffic') {
      await sendTrafficMessage(chatId, user, miniAppUrl);
    } else if (text === '/campaign' || text === '➕ Promote Website') {
      await sendPromoteMessage(chatId, user, miniAppUrl);
    } else if (text === '/packages' || text === '💳 Buy Credits') {
      await sendBuyMessage(chatId, user, miniAppUrl);
    } else if (text === '🎁 Earn Credits') {
      await sendEarnMessage(chatId, user, miniAppUrl);
    } else if (text === '/referral' || text === '👥 Referral') {
      await sendReferralMessage(chatId, user, miniAppUrl);
    } else if (text === '/stats' || text === '📊 Statistics') {
      await sendStatsMessage(chatId, user);
    } else if (text === '/history' || text === '📜 History') {
      await sendHistoryMessage(chatId, user);
    } else if (text === '/support' || text === '🎧 Support') {
      await sendSupportMessage(chatId, user, miniAppUrl);
    } else if (text === '⚙️ Settings') {
      await sendSettingsMessage(chatId, user);
    } else if (text === '/help') {
      await sendHelpMessage(chatId);
    } else if (text.startsWith('/admin') && user.is_admin) {
      await sendAdminMenuMessage(chatId, miniAppUrl);
    } else {
      // Default response for unhandled input
      await sendHomeMessage(chatId, user, miniAppUrl);
    }
  }
}

// Keyboards and Message Generators
function getMainMenuKeyboard(webAppUrl: string, isAdmin = false) {
  const inline_keyboard: any[][] = [
    [
      { text: '🚀 Open Mini App', web_app: { url: webAppUrl } },
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
  ];

  if (isAdmin) {
    inline_keyboard.push([{ text: '🛡️ Admin Dashboard', web_app: { url: `${webAppUrl}?admin=true` } }]);
  }

  return { inline_keyboard };
}

async function sendHomeMessage(chatId: number, user: any, miniAppUrl: string) {
  const activeCampaigns = db.getActiveCampaignsForUser(user.id).length;
  const msg = `🚀 <b>InfiniteHits</b>\n\n` +
    `Welcome, <b>${user.first_name}</b>\n\n` +
    `💰 <b>Credits:</b> ${user.credits}\n` +
    `🌐 <b>Active Campaigns:</b> ${activeCampaigns}\n` +
    `👁️ <b>Traffic Delivered:</b> ${user.traffic_delivered}\n` +
    `👥 <b>Referrals:</b> ${user.referral_count}`;

  await sendTelegramMessage(chatId, msg, getMainMenuKeyboard(miniAppUrl, user.is_admin));
}

async function sendTrafficMessage(chatId: number, user: any, miniAppUrl: string) {
  const campaigns = db.getActiveCampaignsForUser(user.id);
  if (campaigns.length === 0) {
    await sendTelegramMessage(
      chatId,
      `🌐 <b>Get Traffic</b>\n\nCurrently, there are no active promotional campaigns available for your account right now.\n\nCheck back soon or create your own campaign to get real visitors!`,
      {
        inline_keyboard: [
          [{ text: '➕ Create Campaign', callback_data: 'btn_promote' }],
          [{ text: '🏠 Back to Home', callback_data: 'btn_home' }],
        ],
      }
    );
    return;
  }

  const cmp = campaigns[0];
  const msg = `🌐 <b>Available Campaign</b>\n\n` +
    `Website:\n"${cmp.website_url}"\n\n` +
    `⏱ <b>Required Stay:</b> ${cmp.minimum_visit_seconds} seconds\n` +
    `🎁 <b>Reward:</b> +${cmp.cost_per_visit} Credit\n` +
    `📊 <b>Remaining:</b> ${cmp.remaining_visits} visits`;

  await sendTelegramMessage(chatId, msg, {
    inline_keyboard: [
      [{ text: '🚀 Visit & Earn in Mini App', web_app: { url: `${miniAppUrl}?visit=${cmp.campaign_id}` } }],
      [{ text: '🏠 Back to Home', callback_data: 'btn_home' }],
    ],
  });
}

async function sendPromoteMessage(chatId: number, user: any, miniAppUrl: string) {
  const msg = `➕ <b>Website Promotion</b>\n\n` +
    `Launch high-converting traffic campaigns using your Credits balance.\n\n` +
    `💳 <b>Your Balance:</b> ${user.credits} Credits\n\n` +
    `Use the InfiniteHits Mini App to easily submit your website URL and select visit volume!`;

  await sendTelegramMessage(chatId, msg, {
    inline_keyboard: [
      [{ text: '➕ Promote Website in Mini App', web_app: { url: `${miniAppUrl}?tab=campaigns` } }],
      [{ text: '🏠 Back to Home', callback_data: 'btn_home' }],
    ],
  });
}

async function sendBuyMessage(chatId: number, user: any, miniAppUrl: string) {
  const packages = db.getPackages();
  let msg = `💳 <b>Buy Credits</b>\n\nYour Current Balance: <b>${user.credits} Credits</b>\n\nAvailable Credit Packages:\n\n`;

  packages.forEach((pkg) => {
    msg += `📦 <b>${pkg.name}</b>\n`;
    msg += `• Credits: ${pkg.credits} ${pkg.bonus_credits ? `(+${pkg.bonus_credits} Bonus)` : ''}\n`;
    msg += `• Price: ${pkg.price} ${pkg.currency}\n\n`;
  });

  await sendTelegramMessage(chatId, msg, {
    inline_keyboard: [
      [{ text: '💳 Purchase in Mini App', web_app: { url: `${miniAppUrl}?tab=wallet` } }],
      [{ text: '🏠 Back to Home', callback_data: 'btn_home' }],
    ],
  });
}

async function sendEarnMessage(chatId: number, user: any, miniAppUrl: string) {
  const settings = db.getSettings();
  const refLink = `https://t.me/${BOT_USERNAME}?start=${user.referral_code}`;
  const msg = `🎁 <b>Earn Free Credits</b>\n\n` +
    `You can get more Credits by:\n` +
    `1️⃣ <b>Viewing Promotional Websites:</b> Earn +1 Credit for every verified visit.\n` +
    `2️⃣ <b>Inviting Friends:</b> Earn +${settings.referral_reward_credits} Credits for every new active user!\n\n` +
    `Your Referral Link:\n<code>${refLink}</code>`;

  await sendTelegramMessage(chatId, msg, {
    inline_keyboard: [
      [{ text: '🌐 View Traffic Campaigns', callback_data: 'btn_traffic' }],
      [{ text: '📤 Share Referral Link', url: `https://t.me/share/url?url=${encodeURIComponent(refLink)}&text=${encodeURIComponent('Join InfiniteHits & get 50 Free Website Promotional Credits!')}` }],
      [{ text: '🏠 Back to Home', callback_data: 'btn_home' }],
    ],
  });
}

async function sendReferralMessage(chatId: number, user: any, miniAppUrl: string) {
  const refLink = `https://t.me/${BOT_USERNAME}?start=${user.referral_code}`;
  const msg = `👥 <b>Referral Program</b>\n\n` +
    `Your Referrals:\n<b>${user.referral_count}</b>\n\n` +
    `Earned:\n<b>${user.referral_earnings} Credits</b>\n\n` +
    `Your Referral Link:\n<code>${refLink}</code>`;

  await sendTelegramMessage(chatId, msg, {
    inline_keyboard: [
      [{ text: '📤 Share Referral Link', url: `https://t.me/share/url?url=${encodeURIComponent(refLink)}&text=${encodeURIComponent('Join InfiniteHits to get 50 Free Traffic Credits!')}` }],
      [{ text: '🏠 Back to Home', callback_data: 'btn_home' }],
    ],
  });
}

async function sendStatsMessage(chatId: number, user: any) {
  const txs = db.getUserTransactions(user.id);
  const totalEarned = txs.filter((t) => t.amount > 0).reduce((acc, t) => acc + t.amount, 0);
  const totalSpent = Math.abs(txs.filter((t) => t.amount < 0).reduce((acc, t) => acc + t.amount, 0));

  const msg = `📊 <b>Your Statistics</b>\n\n` +
    `💰 <b>Current Balance:</b> ${user.credits} Credits\n` +
    `📈 <b>Total Credits Earned:</b> ${totalEarned}\n` +
    `📉 <b>Total Credits Spent:</b> ${totalSpent}\n` +
    `👁️ <b>Traffic Received:</b> ${user.traffic_delivered}\n` +
    `🌐 <b>Traffic Provided:</b> ${user.traffic_provided}\n` +
    `👥 <b>Referral Count:</b> ${user.referral_count}\n` +
    `🎁 <b>Referral Earnings:</b> ${user.referral_earnings} Credits`;

  await sendTelegramMessage(chatId, msg, {
    inline_keyboard: [[{ text: '🏠 Back to Home', callback_data: 'btn_home' }]],
  });
}

async function sendHistoryMessage(chatId: number, user: any) {
  const txs = db.getUserTransactions(user.id).slice(0, 8);
  let msg = `📜 <b>Credit History</b>\n\n`;

  if (txs.length === 0) {
    msg += `No credit transactions recorded yet.`;
  } else {
    txs.forEach((t) => {
      const sign = t.amount > 0 ? '+' : '';
      const dateStr = new Date(t.created_at).toLocaleDateString();
      msg += `<b>${sign}${t.amount}</b> — ${t.type.replace('_', ' ')}\n`;
      msg += `<i>${t.description}</i> (${dateStr})\n\n`;
    });
  }

  await sendTelegramMessage(chatId, msg, {
    inline_keyboard: [[{ text: '🏠 Back to Home', callback_data: 'btn_home' }]],
  });
}

async function sendSupportMessage(chatId: number, user: any, miniAppUrl: string) {
  const msg = `🎧 <b>Support Center</b>\n\n` +
    `Need help with your campaign or payments?\n` +
    `Our team is available 24/7.\n\n` +
    `Submit a support ticket via the Mini App or contact our administrator.`;

  await sendTelegramMessage(chatId, msg, {
    inline_keyboard: [
      [{ text: '🎧 Open Support Ticket', web_app: { url: `${miniAppUrl}?tab=profile` } }],
      [{ text: '🏠 Back to Home', callback_data: 'btn_home' }],
    ],
  });
}

async function sendSettingsMessage(chatId: number, user: any) {
  const msg = `⚙️ <b>Account Settings</b>\n\n` +
    `ID: <code>${user.telegram_id}</code>\n` +
    `Name: ${user.first_name} ${user.last_name || ''}\n` +
    `Username: @${user.username || 'N/A'}\n` +
    `Role: ${user.is_admin ? 'Administrator 🛡️' : 'Standard Member'}\n` +
    `Joined: ${new Date(user.created_at).toLocaleDateString()}`;

  await sendTelegramMessage(chatId, msg, {
    inline_keyboard: [[{ text: '🏠 Back to Home', callback_data: 'btn_home' }]],
  });
}

async function sendHelpMessage(chatId: number) {
  const msg = `❓ <b>InfiniteHits Commands Help</b>\n\n` +
    `/start - Launch Bot & Show Main Dashboard\n` +
    `/menu - Main Keyboard Menu\n` +
    `/traffic - Get Traffic / Browse Campaigns\n` +
    `/campaign - Promote Your Website\n` +
    `/packages - Buy Credit Packages\n` +
    `/referral - View Referral Program & Link\n` +
    `/stats - View Account Analytics\n` +
    `/history - Check Credit Audit History\n` +
    `/support - Get Customer Support`;

  await sendTelegramMessage(chatId, msg);
}

async function sendAdminMenuMessage(chatId: number, miniAppUrl: string) {
  const stats = db.getGlobalStats();
  const msg = `🛡️ <b>InfiniteHits Admin Command Center</b>\n\n` +
    `👥 Total Users: <b>${stats.total_users}</b>\n` +
    `🌐 Active Campaigns: <b>${stats.active_campaigns}</b>\n` +
    `💳 Pending Payments: <b>${stats.pending_payments}</b>\n` +
    `👁️ Total Traffic Delivered: <b>${stats.total_traffic_delivered}</b>`;

  await sendTelegramMessage(chatId, msg, {
    inline_keyboard: [[{ text: '🛡️ Open Full Admin Panel', web_app: { url: `${miniAppUrl}?admin=true` } }]],
  });
}
