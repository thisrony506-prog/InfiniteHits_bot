import express from 'express';
import path from 'path';
import { createServer as createViteServer } from 'vite';
import dotenv from 'dotenv';
import { db } from './src/db/store.js';
import { handleTelegramWebhookUpdate, sendTelegramMessage, verifyTelegramWebAppData } from './src/server/bot.js';

dotenv.config();

async function startServer() {
  const app = express();
  const PORT = 3000;

  app.use(express.json());

  // Helper middleware to extract user from Telegram initData header or query
  const authenticateUser = (req: express.Request, res: express.Response, next: express.NextFunction) => {
    const initDataRaw = (req.headers['x-telegram-init-data'] as string) || (req.query.initData as string) || '';
    const authHeaderUser = (req.headers['x-user-id'] as string) || '';

    let telegramId: number | null = null;
    let firstName = 'User';
    let lastName = '';
    let username = '';

    if (initDataRaw) {
      const verified = verifyTelegramWebAppData(initDataRaw);
      if (verified.valid && verified.user) {
        telegramId = verified.user.id;
        firstName = verified.user.first_name || 'User';
        lastName = verified.user.last_name || '';
        username = verified.user.username || '';
      }
    }

    if (!telegramId && authHeaderUser) {
      telegramId = parseInt(authHeaderUser, 10);
    }

    // Default fallback demo user for web browser preview
    if (!telegramId || isNaN(telegramId)) {
      telegramId = 987654321;
      firstName = 'Alex';
      lastName = 'Trader';
      username = 'alex_trader';
    }

    const { user } = db.getOrCreateUser(telegramId, firstName, lastName, username);
    (req as any).user = user;
    next();
  };

  const requireAdmin = (req: express.Request, res: express.Response, next: express.NextFunction) => {
    const user = (req as any).user;
    if (!user || !user.is_admin) {
      res.status(403).json({ status: 'UNAUTHORIZED', error: 'Admin privileges required' });
      return;
    }
    next();
  };

  // --- 1. TELEGRAM WEBHOOK ---
  app.post('/api/telegram/webhook', async (req, res) => {
    try {
      const appUrl = (process.env.APP_URL || `${req.protocol}://${req.get('host')}`).replace(/\/$/, '');
      await handleTelegramWebhookUpdate(req.body, appUrl);
      res.json({ ok: true });
    } catch (e) {
      console.error('Webhook error:', e);
      res.status(500).json({ status: 'SERVER_ERROR', error: 'Webhook processing error' });
    }
  });

  // Simulator helper endpoint for interactive Bot Simulator UI
  app.post('/api/telegram/simulate', async (req, res) => {
    try {
      const appUrl = (process.env.APP_URL || `${req.protocol}://${req.get('host')}`).replace(/\/$/, '');
      const { text, from } = req.body;

      // Mock Telegram update payload
      const mockUpdate = {
        message: {
          chat: { id: from?.id || 987654321 },
          from: from || { id: 987654321, first_name: 'Alex', username: 'alex_trader' },
          text: text || '/start',
        },
      };

      await handleTelegramWebhookUpdate(mockUpdate, appUrl);
      res.json({ ok: true });
    } catch (e) {
      res.status(500).json({ error: 'Simulation failed' });
    }
  });

  // --- 2. AUTH & USER ENDPOINTS ---
  app.get('/api/user/me', authenticateUser, (req, res) => {
    const user = (req as any).user;
    res.json({ status: 'SUCCESS', user, settings: db.getSettings() });
  });

  app.get('/api/user/stats', authenticateUser, (req, res) => {
    const user = (req as any).user;
    const txs = db.getUserTransactions(user.id);
    const totalEarned = txs.filter((t) => t.amount > 0).reduce((acc, t) => acc + t.amount, 0);
    const totalSpent = Math.abs(txs.filter((t) => t.amount < 0).reduce((acc, t) => acc + t.amount, 0));

    res.json({
      status: 'SUCCESS',
      stats: {
        credits: user.credits,
        total_earned: totalEarned,
        total_spent: totalSpent,
        traffic_delivered: user.traffic_delivered,
        traffic_provided: user.traffic_provided,
        referral_count: user.referral_count,
        referral_earnings: user.referral_earnings,
      },
    });
  });

  app.get('/api/user/history', authenticateUser, (req, res) => {
    const user = (req as any).user;
    res.json({ status: 'SUCCESS', transactions: db.getUserTransactions(user.id) });
  });

  // --- 3. TRAFFIC CAMPAIGNS ---
  app.get('/api/traffic/campaigns', authenticateUser, (req, res) => {
    const user = (req as any).user;
    const campaigns = db.getActiveCampaignsForUser(user.id);
    res.json({ status: 'SUCCESS', campaigns });
  });

  app.post('/api/traffic/verify', authenticateUser, (req, res) => {
    try {
      const user = (req as any).user;
      const { campaign_id, duration_seconds } = req.body;

      if (!campaign_id || typeof duration_seconds !== 'number') {
        res.status(400).json({ status: 'INVALID_INPUT', error: 'campaign_id and duration_seconds are required' });
        return;
      }

      const result = db.verifyVisit(user.id, campaign_id, duration_seconds);
      if (!result.success) {
        res.status(400).json({ status: 'VERIFICATION_FAILED', error: result.error });
        return;
      }

      res.json({ status: 'SUCCESS', reward: result.reward, new_balance: result.newBalance });
    } catch (e: any) {
      res.status(500).json({ status: 'SERVER_ERROR', error: e.message });
    }
  });

  // --- 4. CAMPAIGN CREATION ---
  app.post('/api/campaigns/create', authenticateUser, (req, res) => {
    try {
      const user = (req as any).user;
      const { website_url, title, required_visits, minimum_visit_seconds } = req.body;

      if (!website_url || !required_visits) {
        res.status(400).json({ status: 'INVALID_INPUT', error: 'Website URL and required visits count are mandatory.' });
        return;
      }

      const campaign = db.createCampaign({
        user_id: user.id,
        website_url,
        title,
        required_visits: Number(required_visits),
        minimum_visit_seconds: minimum_visit_seconds ? Number(minimum_visit_seconds) : undefined,
      });

      res.json({ status: 'SUCCESS', campaign });
    } catch (e: any) {
      res.status(400).json({ status: 'INSUFFICIENT_CREDITS_OR_INVALID', error: e.message });
    }
  });

  app.get('/api/campaigns/my', authenticateUser, (req, res) => {
    const user = (req as any).user;
    res.json({ status: 'SUCCESS', campaigns: db.getUserCampaigns(user.id) });
  });

  app.post('/api/campaigns/update-status', authenticateUser, (req, res) => {
    try {
      const user = (req as any).user;
      const { campaign_id, status } = req.body;

      const campaign = db.getUserCampaigns(user.id).find((c) => c.campaign_id === campaign_id);
      if (!campaign && !user.is_admin) {
        res.status(403).json({ status: 'UNAUTHORIZED', error: 'Campaign does not belong to you' });
        return;
      }

      const updated = db.updateCampaignStatus(campaign_id, status);
      res.json({ status: 'SUCCESS', campaign: updated });
    } catch (e: any) {
      res.status(400).json({ status: 'ERROR', error: e.message });
    }
  });

  // --- 5. PACKAGES & PAYMENTS ---
  app.get('/api/packages', (req, res) => {
    res.json({ status: 'SUCCESS', packages: db.getPackages() });
  });

  app.post('/api/payments/create', authenticateUser, (req, res) => {
    try {
      const user = (req as any).user;
      const { package_id, payment_method, transaction_reference, sender_number } = req.body;

      if (!package_id || !payment_method || !transaction_reference) {
        res.status(400).json({ status: 'INVALID_INPUT', error: 'Package, payment method, and transaction reference are required.' });
        return;
      }

      const payment = db.createPayment({
        user_id: user.id,
        package_id,
        payment_method,
        transaction_reference,
        sender_number,
      });

      res.json({ status: 'SUCCESS', payment });
    } catch (e: any) {
      res.status(400).json({ status: 'ERROR', error: e.message });
    }
  });

  app.get('/api/payments/my', authenticateUser, (req, res) => {
    const user = (req as any).user;
    res.json({ status: 'SUCCESS', payments: db.getUserPayments(user.id) });
  });

  // --- 6. SUPPORT TICKETS ---
  app.post('/api/support/create', authenticateUser, (req, res) => {
    try {
      const user = (req as any).user;
      const { category, message, attachment_url } = req.body;

      if (!category || !message) {
        res.status(400).json({ status: 'INVALID_INPUT', error: 'Category and message content are required.' });
        return;
      }

      const ticket = db.createSupportTicket({
        user_id: user.id,
        category,
        message,
        attachment_url,
      });

      res.json({ status: 'SUCCESS', ticket });
    } catch (e: any) {
      res.status(400).json({ status: 'ERROR', error: e.message });
    }
  });

  app.get('/api/support/my', authenticateUser, (req, res) => {
    const user = (req as any).user;
    res.json({ status: 'SUCCESS', tickets: db.getUserTickets(user.id) });
  });

  // --- 7. ADMIN PANEL API ---
  app.get('/api/admin/stats', authenticateUser, requireAdmin, (req, res) => {
    res.json({ status: 'SUCCESS', stats: db.getGlobalStats() });
  });

  app.get('/api/admin/users', authenticateUser, requireAdmin, (req, res) => {
    res.json({ status: 'SUCCESS', users: db.getAllUsers() });
  });

  app.post('/api/admin/users/adjust-credits', authenticateUser, requireAdmin, (req, res) => {
    try {
      const admin = (req as any).user;
      const { user_id, amount, reason } = req.body;

      if (!user_id || typeof amount !== 'number' || !reason) {
        res.status(400).json({ status: 'INVALID_INPUT', error: 'User ID, amount number, and reason are required' });
        return;
      }

      const result = db.adjustUserCredits(user_id, amount, reason, admin.id);
      res.json({ status: 'SUCCESS', result });
    } catch (e: any) {
      res.status(400).json({ status: 'ERROR', error: e.message });
    }
  });

  app.post('/api/admin/users/ban', authenticateUser, requireAdmin, (req, res) => {
    try {
      const admin = (req as any).user;
      const { user_id, action, reason } = req.body; // action: BAN | UNBAN

      if (action === 'BAN') {
        const user = db.banUser(user_id, reason || 'Admin ban', admin.id);
        res.json({ status: 'SUCCESS', user });
      } else {
        const user = db.unbanUser(user_id, admin.id);
        res.json({ status: 'SUCCESS', user });
      }
    } catch (e: any) {
      res.status(400).json({ status: 'ERROR', error: e.message });
    }
  });

  app.get('/api/admin/campaigns', authenticateUser, requireAdmin, (req, res) => {
    res.json({ status: 'SUCCESS', campaigns: db.getAllCampaigns() });
  });

  app.post('/api/admin/campaigns/review', authenticateUser, requireAdmin, (req, res) => {
    try {
      const admin = (req as any).user;
      const { campaign_id, status, reason } = req.body;

      const campaign = db.updateCampaignStatus(campaign_id, status, admin.id, reason);

      // Telegram notification to owner
      if (status === 'ACTIVE') {
        sendTelegramMessage(campaign.telegram_id, `✅ Your campaign "<b>${campaign.title}</b>" has been approved! Promotional traffic is now live.`);
      } else if (status === 'REJECTED') {
        sendTelegramMessage(campaign.telegram_id, `❌ Your campaign "<b>${campaign.title}</b>" was rejected.\nReason: ${reason || 'Violation of policies.'}\nYour credits have been refunded.`);
      }

      res.json({ status: 'SUCCESS', campaign });
    } catch (e: any) {
      res.status(400).json({ status: 'ERROR', error: e.message });
    }
  });

  app.get('/api/admin/payments', authenticateUser, requireAdmin, (req, res) => {
    res.json({ status: 'SUCCESS', payments: db.getAllPayments() });
  });

  app.post('/api/admin/payments/verify', authenticateUser, requireAdmin, (req, res) => {
    try {
      const admin = (req as any).user;
      const { payment_id, action, notes } = req.body; // action: APPROVE | REJECT

      const payment = db.verifyPayment(payment_id, action, admin.id, notes);

      if (action === 'APPROVE') {
        sendTelegramMessage(payment.telegram_id, `✅ <b>Payment Approved!</b>\n\nYour purchase of <b>${payment.credits} Credits</b> for package <b>${payment.package_name}</b> has been credited to your account!`);
      } else {
        sendTelegramMessage(payment.telegram_id, `❌ <b>Payment Rejected</b>\n\nTransaction Ref: <code>${payment.transaction_reference}</code>\nNotes: ${notes || 'Invalid transaction ID'}`);
      }

      res.json({ status: 'SUCCESS', payment });
    } catch (e: any) {
      res.status(400).json({ status: 'ERROR', error: e.message });
    }
  });

  app.get('/api/admin/packages', authenticateUser, requireAdmin, (req, res) => {
    res.json({ status: 'SUCCESS', packages: db.getAllPackages() });
  });

  app.post('/api/admin/packages/save', authenticateUser, requireAdmin, (req, res) => {
    try {
      const admin = (req as any).user;
      const pkg = req.body;
      const saved = db.savePackage(pkg, admin.id);
      res.json({ status: 'SUCCESS', package: saved });
    } catch (e: any) {
      res.status(400).json({ status: 'ERROR', error: e.message });
    }
  });

  app.delete('/api/admin/packages/:id', authenticateUser, requireAdmin, (req, res) => {
    const admin = (req as any).user;
    db.deletePackage(req.params.id, admin.id);
    res.json({ status: 'SUCCESS' });
  });

  app.get('/api/admin/settings', authenticateUser, requireAdmin, (req, res) => {
    res.json({ status: 'SUCCESS', settings: db.getSettings() });
  });

  app.post('/api/admin/settings', authenticateUser, requireAdmin, (req, res) => {
    const admin = (req as any).user;
    const settings = db.updateSettings(req.body, admin.id);
    res.json({ status: 'SUCCESS', settings });
  });

  app.get('/api/admin/logs', authenticateUser, requireAdmin, (req, res) => {
    res.json({ status: 'SUCCESS', logs: db.getAdminLogs() });
  });

  app.get('/api/admin/tickets', authenticateUser, requireAdmin, (req, res) => {
    res.json({ status: 'SUCCESS', tickets: db.getAllTickets() });
  });

  app.post('/api/admin/tickets/reply', authenticateUser, requireAdmin, (req, res) => {
    try {
      const admin = (req as any).user;
      const { ticket_id, reply, status } = req.body;
      const updated = db.replySupportTicket(ticket_id, reply, status || 'RESOLVED', admin.id);

      sendTelegramMessage(updated.telegram_id, `🎧 <b>Support Response</b>\n\nCategory: ${updated.category}\nReply: ${reply}\nStatus: ${updated.status}`);
      res.json({ status: 'SUCCESS', ticket: updated });
    } catch (e: any) {
      res.status(400).json({ status: 'ERROR', error: e.message });
    }
  });

  app.post('/api/admin/broadcast', authenticateUser, requireAdmin, async (req, res) => {
    try {
      const { message } = req.body;
      if (!message) {
        res.status(400).json({ status: 'INVALID_INPUT', error: 'Broadcast message text is required' });
        return;
      }

      const users = db.getAllUsers();
      let sentCount = 0;

      for (const u of users) {
        if (!u.is_banned) {
          await sendTelegramMessage(u.telegram_id, `📢 <b>Announcement from InfiniteHits</b>\n\n${message}`);
          sentCount++;
        }
      }

      res.json({ status: 'SUCCESS', total_recipients: sentCount });
    } catch (e: any) {
      res.status(500).json({ status: 'SERVER_ERROR', error: e.message });
    }
  });

  // --- VITE MIDDLEWARE SETUP FOR DEV AND PRODUCTION ---
  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`🚀 InfiniteHits Server running on http://0.0.0.0:${PORT}`);
  });
}

startServer();
