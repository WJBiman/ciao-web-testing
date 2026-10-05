"use client";

import { API_BASE_URL } from "@/lib/api";
import { useState, useEffect, Suspense } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import QRCode from "react-qr-code";
import { 
  Ticket, 
  Printer, 
  CheckCircle2, 
  Bus, 
  ArrowLeft, 
  ShieldCheck,
  AlertCircle
} from "lucide-react";
import { Badge } from "@/components/ui/badge";

interface TicketData {
  status: string;
  passengerName: string;
  totalFare: number;
  scheduleId: number;
  seatNumbers: string[];
  qrCode?: string;
}

function ViewTicketContent() {
  const searchParams = useSearchParams();
  const reservationId = searchParams.get("reservationId");

  const [ticket, setTicket] = useState<TicketData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [isLoggedIn] = useState(() => typeof window !== "undefined" ? !!localStorage.getItem("ciao_user") : false);

  useEffect(() => {
    if (!reservationId) return;

    const fetchRes = async () => {
      try {
        const guestToken = sessionStorage.getItem("guestReservationToken");
        
        const headers: Record<string, string> = {};
        if (guestToken) headers["Authorization"] = `Bearer ${guestToken}`;

        const res = await fetch(`${API_BASE_URL}/api/reservations/ticket/${reservationId}`, {
          headers,
          credentials: "include"
        });
        if (res.ok) {
          setTicket(await res.json());
        } else {
          const errData = await res.json().catch(() => ({}));
          setError(errData.message || "Failed to load ticket details.");
        }
      } catch (err) {
        console.error(err);
        setError("Network error loading ticket.");
      } finally {
        setLoading(false);
      }
    };
    fetchRes();
  }, [reservationId]);

  if (!reservationId) {
    return (
      <main className="max-w-md mx-auto mt-36 px-5 text-center">
        <div className="bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] p-8 rounded-3xl shadow-xl">
          <AlertCircle className="w-12 h-12 text-[#087478] mx-auto mb-3" />
          <h2 className="text-xl font-bold text-[#193542] mb-2">No Ticket Specified</h2>
          <p className="text-sm text-[#5E7480] mb-6">
            A valid reservation reference is required to view an e-ticket.
          </p>
          {isLoggedIn ? (
            <Link href="/my-bookings" className="ciao-btn-primary mx-auto">
              Go to My Bookings
            </Link>
          ) : (
            <Link href="/" className="ciao-btn-primary mx-auto">
              Return to Search
            </Link>
          )}
        </div>
      </main>
    );
  }

  return (
    <main className="max-w-2xl mx-auto mt-28 md:mt-32 mb-20 px-5 w-full">
      {/* Top Header / Back */}
      <div className="flex items-center justify-between mb-6 no-print">
        <Link href={isLoggedIn ? "/my-bookings" : "/"} className="ciao-btn-ghost text-xs md:text-sm text-[#516A74] hover:text-[#193542] flex items-center gap-1.5">
          <ArrowLeft className="w-4 h-4" /> {isLoggedIn ? "Back to Bookings" : "Back to Home"}
        </Link>
        <Badge variant={ticket?.status === "CONFIRMED" ? "success" : "warning"}>
          {ticket?.status || "Processing"}
        </Badge>
      </div>

      {loading ? (
        <div className="bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] p-16 rounded-3xl text-center text-sm text-[#516A74]">
          <span className="w-5 h-5 border-2 border-[#087478] border-t-transparent rounded-full inline-block animate-spin mr-2" />
          Generating official e-ticket pass...
        </div>
      ) : error ? (
        <div role="alert" className="bg-[#FFFFFF] border border-red-500/30 p-8 rounded-3xl text-center shadow-xl">
          <AlertCircle className="w-10 h-10 text-red-400 mx-auto mb-3" />
          <h3 className="text-lg font-bold text-[#193542]">Ticket Unavailable</h3>
          <p className="text-sm text-red-700 mt-1 mb-4">{error}</p>
          <Link href="/" className="ciao-btn-secondary">Browse Schedules</Link>
        </div>
      ) : ticket?.status === "CONFIRMED" ? (
        <div className="print-ticket-area">
          {/* Boarding Pass Card */}
          <div className="bg-[#FFFFFF] border border-[rgba(8,127,129,0.25)] rounded-3xl overflow-hidden shadow-2xl relative">
            {/* Ticket Brand Banner */}
            <div className="bg-[#EDF3F1] p-6 md:p-8 border-b border-[rgba(25,53,66,0.12)] flex justify-between items-start gap-4">
              <div>
                <span className="text-xs font-bold uppercase tracking-widest text-[#087478] flex items-center gap-1.5 mb-1">
                  <Bus className="w-3.5 h-3.5" /> Intercity Bus
                </span>
                <h1 className="text-2xl md:text-3xl font-extrabold text-[#193542]">E-Ticket</h1>
                <p className="text-sm text-[#516A74] mt-0.5">Booking Reference #{reservationId}</p>
              </div>

              <div className="text-right">
                <span className="text-xs text-[#5E7480] uppercase tracking-wider block">Total Fare</span>
                <span className="text-xl md:text-2xl font-extrabold text-[#087478]">
                  LKR {Number(ticket.totalFare).toFixed(2)}
                </span>
              </div>
            </div>

            {/* Passenger and Journey Grid */}
            <div className="p-6 md:p-8 grid grid-cols-2 sm:grid-cols-4 gap-5 border-b border-dashed border-[rgba(25,53,66,0.16)] relative bg-[#FFFFFF]">
              {/* Notches for boarding pass look */}
              <div className="absolute -left-3.5 -bottom-3.5 w-7 h-7 bg-[#F6F8F6] rounded-full border-r border-[rgba(25,53,66,0.16)] no-print" />
              <div className="absolute -right-3.5 -bottom-3.5 w-7 h-7 bg-[#F6F8F6] rounded-full border-l border-[rgba(25,53,66,0.16)] no-print" />

              <div>
                <span className="text-xs text-[#5E7480] uppercase tracking-wider block font-semibold mb-1">Passenger</span>
                <span className="text-sm md:text-base font-bold text-[#193542] block truncate">{ticket.passengerName}</span>
              </div>

              <div>
                <span className="text-xs text-[#5E7480] uppercase tracking-wider block font-semibold mb-1">Schedule</span>
                <span className="text-sm md:text-base font-bold text-[#193542] block">#{ticket.scheduleId}</span>
              </div>

              <div>
                <span className="text-xs text-[#5E7480] uppercase tracking-wider block font-semibold mb-1">Assigned Seats</span>
                <span className="text-sm md:text-base font-bold text-[#087478] block">{ticket.seatNumbers.join(", ")}</span>
              </div>

              <div>
                <span className="text-xs text-[#5E7480] uppercase tracking-wider block font-semibold mb-1">Reservation</span>
                <span className="text-xs md:text-sm font-bold text-emerald-700 block flex items-center gap-1">
                  <CheckCircle2 className="w-3.5 h-3.5" /> CONFIRMED
                </span>
              </div>
            </div>

            {/* QR Code Presentation */}
            <div className="p-8 bg-[#F7FAF9] flex flex-col items-center justify-center text-center">
              <div className="p-4 bg-white rounded-2xl shadow-xl border border-white/20 mb-4 inline-block">
                {ticket.qrCode ? (
                  <QRCode value={ticket.qrCode} size={150} level="M" />
                ) : (
                  <div className="w-[150px] h-[150px] bg-slate-100 flex items-center justify-center text-xs text-slate-500">
                    QR Ready
                  </div>
                )}
              </div>

              <div className="flex items-center gap-1.5 text-sm text-[#516A74] font-medium">
                <ShieldCheck className="w-4 h-4 text-[#087478]" />
                <span>Present this e-ticket to staff when boarding</span>
              </div>
              <p className="text-xs text-[#5E7480] mt-1">
                Demo ticket QR code • Valid for verified university schedule
              </p>
            </div>
          </div>

          {/* Print Action */}
          <div className="mt-6 flex flex-col sm:flex-row justify-center gap-3 no-print">
            <button
              onClick={() => window.print()}
              className="ciao-btn-primary flex items-center justify-center gap-2 !py-3 !px-6"
            >
              <Printer className="w-4 h-4" /> Print E-Ticket
            </button>
            <Link
              href={isLoggedIn ? "/my-bookings" : "/"}
              className="ciao-btn-secondary flex items-center justify-center gap-2 !py-3 !px-6"
            >
              <Ticket className="w-4 h-4" /> {isLoggedIn ? "View All Bookings" : "Book Another Journey"}
            </Link>
          </div>
        </div>
      ) : (
        <div className="bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] p-10 text-center rounded-3xl shadow-lg">
          <p className="text-sm text-[#516A74]">
            No confirmed e-ticket is available for this {ticket?.status?.toLowerCase()} booking.
          </p>
        </div>
      )}
    </main>
  );
}

export default function ViewTicket() {
  return (
    <Suspense fallback={<div className="min-h-screen flex items-center justify-center text-sm text-[#5E7480]">Loading e-ticket...</div>}>
      <ViewTicketContent />
    </Suspense>
  );
}
