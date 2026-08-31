import React, { useState, useEffect } from 'react';
import { Campaign } from '../types';
import { ShieldCheck, AlertCircle, ExternalLink, Clock, CheckCircle2, ArrowLeft, Eye, EyeOff } from 'lucide-react';

interface TrafficViewerProps {
  campaign: Campaign;
  onClose: () => void;
  onVerifiedSuccess: (reward: number, newBalance: number) => void;
}

export const TrafficViewer: React.FC<TrafficViewerProps> = ({ campaign, onClose, onVerifiedSuccess }) => {
  const [timeLeft, setTimeLeft] = useState(campaign.minimum_visit_seconds);
  const [isVisible, setIsVisible] = useState(true);
  const [isVerifying, setIsVerifying] = useState(false);
  const [verificationResult, setVerificationResult] = useState<{
    success: boolean;
    reward?: number;
    newBalance?: number;
    error?: string;
  } | null>(null);

  // Monitor visibility state
  useEffect(() => {
    const handleVisibilityChange = () => {
      const visible = !document.hidden;
      setIsVisible(visible);
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, []);

  // Countdown timer
  useEffect(() => {
    if (verificationResult || isVerifying) return;

    if (!isVisible) {
      // Pause countdown if user switches tabs
      return;
    }

    if (timeLeft <= 0) {
      handleCompleteVisit();
      return;
    }

    const timer = setInterval(() => {
      setTimeLeft((prev) => Math.max(0, prev - 1));
    }, 1000);

    return () => clearInterval(timer);
  }, [timeLeft, isVisible, verificationResult, isVerifying]);

  const handleCompleteVisit = async () => {
    setIsVerifying(true);
    try {
      const res = await fetch('/api/traffic/verify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          campaign_id: campaign.campaign_id,
          duration_seconds: campaign.minimum_visit_seconds,
        }),
      });
      const data = await res.json();
      if (data.status === 'SUCCESS') {
        setVerificationResult({
          success: true,
          reward: data.reward,
          newBalance: data.new_balance,
        });
        onVerifiedSuccess(data.reward, data.new_balance);
      } else {
        setVerificationResult({
          success: false,
          error: data.error || 'Verification failed. Please try again.',
        });
      }
    } catch (e) {
      setVerificationResult({
        success: false,
        error: 'Network error during verification',
      });
    } finally {
      setIsVerifying(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-slate-950/95 backdrop-blur-xl flex flex-col justify-between p-4 sm:p-6 text-slate-100 animate-in fade-in duration-200">
      
      {/* Top Header Verification Bar */}
      <div className="bg-slate-900/90 border border-slate-800 rounded-2xl p-4 shadow-2xl flex flex-wrap items-center justify-between gap-3">
        
        <div className="flex items-center space-x-3">
          <button
            onClick={onClose}
            className="p-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 transition-colors"
            title="Cancel Visit"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div>
            <h3 className="font-bold text-sm sm:text-base text-white flex items-center gap-2">
              <span>{campaign.title}</span>
              <a
                href={campaign.website_url}
                target="_blank"
                rel="noreferrer"
                className="text-indigo-400 hover:text-indigo-300 text-xs flex items-center gap-1 font-normal underline"
              >
                <span>Open Direct</span>
                <ExternalLink className="w-3 h-3" />
              </a>
            </h3>
            <p className="text-xs text-slate-400 truncate max-w-xs">{campaign.website_url}</p>
          </div>
        </div>

        {/* Status / Countdown */}
        <div className="flex items-center space-x-4">
          
          {/* Tab Visibility Status */}
          <div className={`flex items-center space-x-1.5 px-3 py-1 rounded-full text-xs font-medium border ${
            isVisible ? 'bg-emerald-950/60 border-emerald-500/40 text-emerald-300' : 'bg-rose-950/60 border-rose-500/40 text-rose-300 animate-pulse'
          }`}>
            {isVisible ? <Eye className="w-3.5 h-3.5" /> : <EyeOff className="w-3.5 h-3.5" />}
            <span>{isVisible ? 'Page Active' : 'Tab Hidden - Timer Paused'}</span>
          </div>

          {/* Countdown Clock */}
          {!verificationResult && (
            <div className="flex items-center space-x-2 bg-indigo-950/80 border border-indigo-500/40 px-4 py-1.5 rounded-xl">
              <Clock className="w-4 h-4 text-indigo-400 animate-spin" style={{ animationDuration: '4s' }} />
              <span className="font-mono text-lg font-bold text-indigo-200">{timeLeft}s</span>
            </div>
          )}

        </div>
      </div>

      {/* Main Content Area - Web Frame / Verification Status */}
      <div className="flex-1 my-4 bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden relative shadow-inner flex flex-col">
        {verificationResult ? (
          <div className="flex-1 flex flex-col items-center justify-center p-6 text-center">
            {verificationResult.success ? (
              <div className="max-w-md w-full bg-slate-950 border border-emerald-500/30 rounded-3xl p-8 shadow-2xl space-y-4">
                <div className="w-16 h-16 rounded-full bg-emerald-500/20 text-emerald-400 mx-auto flex items-center justify-center border border-emerald-500/40">
                  <CheckCircle2 className="w-10 h-10" />
                </div>
                <h2 className="text-2xl font-extrabold text-white">✅ Visit Verified</h2>
                <p className="text-emerald-400 font-semibold text-lg">
                  🎁 +{verificationResult.reward} Credit Earned!
                </p>
                <div className="bg-slate-900 p-3 rounded-xl border border-slate-800 text-sm text-slate-300">
                  💰 New Credit Balance: <span className="font-bold text-amber-300">{verificationResult.newBalance} Credits</span>
                </div>
                <button
                  onClick={onClose}
                  className="w-full py-3 rounded-xl bg-gradient-to-r from-emerald-600 to-teal-600 font-bold text-white shadow-lg hover:from-emerald-500 hover:to-teal-500 transition-all"
                >
                  Continue Browsing Traffic
                </button>
              </div>
            ) : (
              <div className="max-w-md w-full bg-slate-950 border border-rose-500/30 rounded-3xl p-8 shadow-2xl space-y-4">
                <div className="w-16 h-16 rounded-full bg-rose-500/20 text-rose-400 mx-auto flex items-center justify-center border border-rose-500/40">
                  <AlertCircle className="w-10 h-10" />
                </div>
                <h2 className="text-2xl font-extrabold text-white">Verification Failed</h2>
                <p className="text-rose-400 text-sm">{verificationResult.error}</p>
                <button
                  onClick={onClose}
                  className="w-full py-3 rounded-xl bg-slate-800 hover:bg-slate-700 font-bold text-white transition-all"
                >
                  Close & Return
                </button>
              </div>
            )}
          </div>
        ) : (
          <div className="flex-1 flex flex-col">
            {/* Live iframe embed of campaign website */}
            <div className="bg-slate-950 px-4 py-2 border-b border-slate-800 flex items-center justify-between text-xs text-slate-400">
              <span className="flex items-center gap-1.5">
                <ShieldCheck className="w-4 h-4 text-emerald-400" />
                Real Voluntary Promotional Visit • Anti-Bot Protected
              </span>
              <span>Stay visible for full duration</span>
            </div>
            <iframe
              src={campaign.website_url}
              title={campaign.title}
              className="w-full flex-1 border-0"
              sandbox="allow-scripts allow-same-origin allow-forms"
            />
          </div>
        )}
      </div>

      {/* Footer Banner */}
      <div className="text-center text-xs text-slate-500">
        InfiniteHits Traffic Policy: Voluntary Real User Traffic Only • Zero Bot Activity
      </div>

    </div>
  );
};
