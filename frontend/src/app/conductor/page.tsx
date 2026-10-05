"use client";

import { useState } from "react";
import Link from "next/link";
import Image from "next/image";
import {
  QrCode,
  CheckCircle2,
  AlertTriangle,
  RotateCw,
  ArrowLeft,
  ShieldCheck,
  Loader2,
  Ticket,
  User,
  Phone,
  Bus as BusIcon,
  MapPin,
  Clock
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";

interface CheckInDetails {
  ticketId: number;
  passenger: string;
  phone: string;
  seats: string;
  route: string;
  departureTime: string;
  bus: string;
  checkedInAt: string;
}

export default function ConductorScanner() {
  const [qrCode, setQrCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState<CheckInDetails | null>(null);

  const performCheckIn = async (codeToVerify?: string) => {
    const code = (codeToVerify || qrCode).trim();
    if (!code) {
      setError("Please enter or scan a valid QR Code or Ticket Reference.");
      return;
    }

    setLoading(true);
    setError("");
    setResult(null);

    try {
      const res = await fetch("/api/eer/tickets/check-in", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ qrCode: code }),
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || "Ticket check-in rejected. Verification failed.");
      }

      setResult(data as CheckInDetails);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Network error during check-in.";
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const resetScanner = () => {
    setQrCode("");
    setResult(null);
    setError("");
  };

  return (
    <div className="min-h-screen bg-[var(--ciao-bg)] text-[var(--ciao-text)] flex flex-col">
      {/* Top Header Navigation */}
      <header className="p-4 sm:px-8 bg-[var(--ciao-surface)] border-b border-[var(--ciao-border)] flex justify-between items-center sticky top-0 z-40">
        <div className="flex items-center gap-3">
          <Image
            src="/images/ciao_logo.png"
            alt="Ciao Bus Logo"
            width={120}
            height={40}
            unoptimized
            className="h-8 w-auto object-contain"
          />
          <Badge variant="gold" className="text-xs uppercase tracking-widest px-2 py-0.5">
            Boarding Check-In
          </Badge>
        </div>
        <Link
          href="/view-ticket"
          className="text-xs font-semibold text-[var(--ciao-muted)] hover:text-[#193542] flex items-center gap-1.5 transition-colors"
        >
          <ArrowLeft className="w-3.5 h-3.5" /> Return to Passenger Portal
        </Link>
      </header>

      <main className="flex-1 flex items-center justify-center p-4 sm:p-6">
        <div className="w-full max-w-md">
          {/* Viewfinder Mobile Card */}
          <Card className="p-6 bg-[var(--ciao-surface)] border-[var(--ciao-border)] shadow-2xl rounded-3xl relative overflow-hidden">
            <div className="flex items-center justify-between pb-4 border-b border-[var(--ciao-border)] mb-4">
              <div>
                <span className="text-xs font-bold text-[var(--ciao-gold)] uppercase tracking-wider">
                  Live Scanner Gateway
                </span>
                <h2 className="text-base font-bold text-[#193542] tracking-tight">On-Board Passenger Validation</h2>
              </div>
              <div className="w-8 h-8 rounded-full bg-emerald-500/10 border border-emerald-500/30 flex items-center justify-center text-emerald-400">
                <ShieldCheck className="w-4 h-4" />
              </div>
            </div>

            {/* Viewfinder Camera Frame */}
            <div className="w-full h-44 rounded-2xl bg-[var(--ciao-bg)] border-2 border-dashed border-[var(--ciao-gold)]/40 relative flex flex-col items-center justify-center overflow-hidden my-4 group">
              <div className="absolute top-0 left-0 right-0 h-[2px] bg-[var(--ciao-gold)] shadow-[0_0_12px_var(--ciao-gold)] animate-[scan-laser_2.2s_infinite_ease-in-out]"></div>
              <QrCode className="w-12 h-12 text-[var(--ciao-gold)] mb-2 animate-pulse" />
              <div className="text-center text-xs text-[var(--ciao-muted)] px-4">
                Position passenger QR code or ticket reference in view
              </div>
            </div>

            {/* Manual or Scanned Token Input */}
            <div className="space-y-3 mb-4">
              <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)]">
                Ticket Reference / Decoded QR Token
              </label>
              <div className="flex gap-2">
                <Input
                  type="text"
                  value={qrCode}
                  onChange={(e) => setQrCode(e.target.value)}
                  placeholder="e.g. CIAO-TICKET:... or TKT-1, RES-1"
                  onKeyDown={(e) => {
                    if (e.key === "Enter") performCheckIn();
                  }}
                  className="text-xs"
                />
                <Button
                  type="button"
                  onClick={() => performCheckIn()}
                  disabled={loading}
                  variant="gold"
                  className="text-xs h-10 px-5 font-semibold shrink-0"
                >
                  {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : "Verify"}
                </Button>
              </div>
            </div>

            {/* Error Announcement */}
            {error && (
              <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 text-sm text-rose-700 flex items-start gap-2.5 mb-4 animate-fade-in">
                <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
                <div>
                  <strong className="block font-bold text-rose-800">Boarding Check-In Rejected:</strong>
                  {error}
                </div>
              </div>
            )}

            {/* Verification Result Sheet */}
            {result && (
              <div className="p-5 rounded-2xl bg-[var(--ciao-bg)] border border-emerald-500/40 space-y-4 animate-fade-in">
                <div className="flex items-center gap-3 pb-3 border-b border-[var(--ciao-border)]">
                  <div className="w-10 h-10 rounded-full bg-emerald-500/20 border border-emerald-500/40 flex items-center justify-center text-emerald-400">
                    <CheckCircle2 className="w-6 h-6" />
                  </div>
                  <div>
                    <div className="text-xs uppercase font-bold text-emerald-700 tracking-wider">
                      Verified On-Board
                    </div>
                    <div className="text-base font-extrabold text-[#193542]">
                      Pass #{result.ticketId}
                    </div>
                  </div>
                </div>

                <div className="text-xs space-y-2">
                  <div className="flex justify-between items-center">
                    <span className="text-[var(--ciao-muted)] flex items-center gap-1.5">
                      <User className="w-3.5 h-3.5" /> Passenger:
                    </span>
                    <strong className="text-[#193542] font-semibold">{result.passenger}</strong>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-[var(--ciao-muted)] flex items-center gap-1.5">
                      <Phone className="w-3.5 h-3.5" /> Contact:
                    </span>
                    <span className="text-[#193542]">{result.phone}</span>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-[var(--ciao-muted)] flex items-center gap-1.5">
                      <Ticket className="w-3.5 h-3.5" /> Allocated Seats:
                    </span>
                    <strong className="text-[var(--ciao-gold)] font-bold text-sm">
                      {result.seats}
                    </strong>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-[var(--ciao-muted)] flex items-center gap-1.5">
                      <MapPin className="w-3.5 h-3.5" /> Route:
                    </span>
                    <span className="text-[#193542] font-medium">{result.route}</span>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-[var(--ciao-muted)] flex items-center gap-1.5">
                      <BusIcon className="w-3.5 h-3.5" /> Assigned Bus:
                    </span>
                    <span className="text-[#193542] font-mono">{result.bus}</span>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-[var(--ciao-muted)] flex items-center gap-1.5">
                      <Clock className="w-3.5 h-3.5" /> Check-in Timestamp:
                    </span>
                    <span className="font-mono text-xs text-[var(--ciao-muted)]">
                      {new Date(result.checkedInAt).toLocaleTimeString()}
                    </span>
                  </div>
                </div>

                <Button
                  onClick={resetScanner}
                  variant="outline"
                  className="w-full text-xs h-9 font-semibold flex items-center justify-center gap-2 mt-2"
                >
                  <RotateCw className="w-3.5 h-3.5" /> Scan Next Passenger Pass
                </Button>
              </div>
            )}
          </Card>
        </div>
      </main>

      <style
        dangerouslySetInnerHTML={{
          __html: `
        @keyframes scan-laser {
            0% { top: 12%; }
            50% { top: 88%; }
            100% { top: 12%; }
        }
      `,
        }}
      />
    </div>
  );
}
