"use client";

import { apiRequest } from "@/lib/api";
import { useState, useEffect, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { 
  CreditCard, 
  Ticket, 
  AlertCircle, 
  User, 
  Lock, 
  ArrowLeft, 
  CheckCircle2, 
  Copy, 
  Check,
  XCircle
} from "lucide-react";
import { Badge } from "@/components/ui/badge";

type Outcome = "SUCCESS" | "FAILED" | "CANCELLED";
interface Reservation { 
  id: number; 
  passengerName: string; 
  totalFare: number; 
  seatNumbers: string[]; 
  status: string; 
}
interface PaymentResult { 
  status: Outcome; 
  message: string; 
  transactionId: string | null; 
}

function guestHeaders(): Record<string, string> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  const token = sessionStorage.getItem("guestReservationToken");
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
}

function CheckoutContent() {
  const router = useRouter();
  const id = useSearchParams().get("reservationId");
  const [reservation, setReservation] = useState<Reservation | null>(null);
  const [card, setCard] = useState({ cardNumber: "", cardholderName: "", expiry: "", cvv: "" });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [loadError, setLoadError] = useState("");
  const [retry, setRetry] = useState(0);
  const [copiedCard, setCopiedCard] = useState(false);

  useEffect(() => {
    if (!id || !/^[1-9][0-9]*$/.test(id)) return;
    let active = true;
    (async () => {
      try {
        const result = await apiRequest<Reservation>(`/api/reservations/ticket/${id}`, { headers: guestHeaders() });
        if (active) setReservation(result);
      } catch (error) { 
        if (active) setLoadError(error instanceof Error ? error.message : "Unable to load booking."); 
      }
    })();
    return () => { active = false; };
  }, [id, retry]);

  async function pay(cancel = false) {
    if (busy || !reservation || reservation.status !== "PENDING") return;
    setBusy(true); 
    setMessage("");
    try {
      const result = await apiRequest<PaymentResult>("/api/payments/checkout", {
        method: "POST", 
        headers: guestHeaders(),
        body: JSON.stringify(cancel ? { reservationId: Number(id), cancel: true } : { reservationId: Number(id), ...card }),
      });
      if (result.status === "SUCCESS") {
        setCard({ cardNumber: "", cardholderName: "", expiry: "", cvv: "" });
        setReservation({ ...reservation, status: "CONFIRMED" });
        router.push(`/view-ticket?reservationId=${id}`);
      } else {
        setMessage(result.message);
        const updated = await apiRequest<Reservation>(`/api/reservations/ticket/${id}`, { headers: guestHeaders() });
        setReservation(updated);
      }
    } catch (error) { 
      setMessage(error instanceof Error ? error.message : "Unable to process demo payment."); 
    } finally { 
      setBusy(false); 
    }
  }

  const fillTestCard = () => {
    setCard({
      cardNumber: "4111 1111 1111 1111",
      cardholderName: "CIAO TEST",
      expiry: "12/30",
      cvv: "123"
    });
    setCopiedCard(true);
    setTimeout(() => setCopiedCard(false), 2000);
  };

  if (!id || !/^[1-9][0-9]*$/.test(id)) {
    return (
      <main className="max-w-md mx-auto mt-36 p-8 bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] rounded-3xl text-center">
        <AlertCircle className="w-10 h-10 text-[#087478] mx-auto mb-3" />
        <h2 className="text-lg font-bold text-[#193542]">Missing Reservation</h2>
        <p className="text-sm text-[#5E7480] mt-1 mb-4">Please select a schedule and choose seats first.</p>
        <Link href="/" className="ciao-btn-primary">Find a Journey</Link>
      </main>
    );
  }

  return (
    <main className="max-w-3xl mx-auto mt-28 md:mt-32 mb-20 px-5 w-full">
      {/* Page Title */}
      <div className="mb-6 flex items-center justify-between">
        <div>
          <Badge variant="gold" className="mb-2">Simulated Checkout</Badge>
          <h1 className="text-2xl md:text-3xl font-extrabold text-[#193542]">Secure Card Payment</h1>
          <p className="text-sm text-[#516A74] mt-1">
            University test environment • No real financial transactions are executed
          </p>
        </div>
        <Link href="/" className="ciao-btn-ghost text-xs md:text-sm text-[#516A74] hover:text-[#193542] hidden sm:flex items-center gap-1.5">
          <ArrowLeft className="w-4 h-4" /> Cancel & Exit
        </Link>
      </div>

      {loadError ? (
        <div role="alert" className="bg-[#FFFFFF] border border-red-500/30 p-6 rounded-3xl text-center">
          <p className="text-red-400 text-sm mb-4">{loadError}</p>
          <button 
            onClick={() => { setLoadError(""); setRetry(value => value + 1); }} 
            className="ciao-btn-secondary"
          >
            Retry Loading Booking
          </button>
        </div>
      ) : !reservation ? (
        <div className="bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] p-12 rounded-3xl text-center text-sm text-[#516A74]">
          <span className="w-5 h-5 border-2 border-[#087478] border-t-transparent rounded-full inline-block animate-spin mr-2" />
          Loading reservation details...
        </div>
      ) : (
        <div className="space-y-6">
          {/* Reservation Summary Card */}
          <div className="bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] p-6 md:p-8 rounded-3xl shadow-lg">
            <div className="flex items-center justify-between pb-4 mb-4 border-b border-[rgba(25,53,66,0.12)]">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-[#087478]/10 border border-[#087478]/20 flex items-center justify-center text-[#087478]">
                  <Ticket className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-base md:text-lg font-bold text-[#193542]">Booking Ref #{reservation.id}</h3>
                  <div className="text-xs md:text-sm text-[#5E7480] flex items-center gap-1.5 mt-0.5">
                    <User className="w-3.5 h-3.5 text-[#516A74]" /> Passenger: <strong className="text-[#193542]">{reservation.passengerName}</strong>
                  </div>
                </div>
              </div>

              <Badge variant={reservation.status === "CONFIRMED" ? "success" : reservation.status === "PENDING" ? "warning" : "danger"}>
                {reservation.status}
              </Badge>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 py-2 text-xs md:text-sm">
              <div className="p-4 bg-[#F7FAF9] rounded-xl border border-[rgba(25,53,66,0.12)]">
                <span className="text-[#5E7480] block mb-1">Reserved Seat Numbers:</span>
                <span className="text-base font-bold text-[#087478]">
                  {reservation.seatNumbers.join(", ")} ({reservation.seatNumbers.length} Seat{reservation.seatNumbers.length > 1 ? "s" : ""})
                </span>
              </div>

              <div className="p-4 bg-[#F7FAF9] rounded-xl border border-[rgba(25,53,66,0.12)] flex justify-between items-center">
                <div>
                  <span className="text-[#5E7480] block mb-1">Authoritative Total:</span>
                  <span className="text-xl font-extrabold text-[#087478]">
                    LKR {Number(reservation.totalFare).toFixed(2)}
                  </span>
                </div>
                <div className="text-xs text-[#5E7480] text-right">Standard Fare</div>
              </div>
            </div>

            {message && (
              <div role="status" className="mt-4 p-3 bg-amber-950/70 border border-amber-500/40 text-amber-200 text-xs md:text-sm rounded-xl flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0 text-amber-400" />
                <span>{message}</span>
              </div>
            )}
          </div>

          {/* Payment Card / Status Action */}
          {reservation.status === "PENDING" ? (
            <div className="bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] p-6 md:p-8 rounded-3xl shadow-xl">
              {/* Accepted Test Card Notice */}
              <div className="p-4 mb-6 rounded-2xl bg-[#F7FAF9] border border-[#087478]/30 flex flex-col sm:flex-row justify-between sm:items-center gap-4">
                <div>
                  <div className="flex items-center gap-2 text-xs font-bold text-[#087478] uppercase tracking-wide">
                    <CreditCard className="w-4 h-4 text-[#087478]" /> Accepted Test Card
                  </div>
                  <div className="font-mono text-base font-bold text-[#193542] mt-1 tracking-wider">
                    4111 1111 1111 1111
                  </div>
                  <div className="text-xs md:text-sm text-[#5E7480] mt-0.5">
                    Name: <strong className="text-[#516A74]">CIAO TEST</strong> • Exp: <strong className="text-[#516A74]">12/30</strong> • CVV: <strong className="text-[#516A74]">123</strong>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={fillTestCard}
                  className="ciao-btn-secondary text-xs !py-2 !px-3 shrink-0 flex items-center gap-1.5"
                >
                  {copiedCard ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5 text-[#087478]" />}
                  {copiedCard ? "Filled in Form" : "Auto-Fill Card"}
                </button>
              </div>

              <form autoComplete="off" onSubmit={(e) => { e.preventDefault(); void pay(); }} className="space-y-4">
                <fieldset disabled={busy} className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div className="sm:col-span-2 flex flex-col">
                    <label htmlFor="cardholderName" className="ciao-label">Cardholder Name</label>
                    <input 
                      id="cardholderName" 
                      required 
                      maxLength={80} 
                      value={card.cardholderName} 
                      onChange={e => setCard({ ...card, cardholderName: e.target.value })} 
                      className="ciao-input" 
                      placeholder="Name exactly as on card" 
                    />
                  </div>

                  <div className="sm:col-span-2 flex flex-col">
                    <label htmlFor="cardNumber" className="ciao-label">Card Number</label>
                    <input 
                      id="cardNumber" 
                      required 
                      inputMode="numeric" 
                      maxLength={23} 
                      value={card.cardNumber} 
                      onChange={e => setCard({ ...card, cardNumber: e.target.value })} 
                      className="ciao-input font-mono" 
                      placeholder="4111 1111 1111 1111" 
                    />
                  </div>

                  <div className="flex flex-col">
                    <label htmlFor="expiry" className="ciao-label">Expiry Date</label>
                    <input 
                      id="expiry" 
                      required 
                      maxLength={5} 
                      placeholder="MM/YY" 
                      value={card.expiry} 
                      onChange={e => setCard({ ...card, expiry: e.target.value })} 
                      className="ciao-input font-mono" 
                    />
                  </div>

                  <div className="flex flex-col">
                    <label htmlFor="cvv" className="ciao-label">Security Code (CVV)</label>
                    <input 
                      id="cvv" 
                      required 
                      type="password" 
                      inputMode="numeric" 
                      maxLength={4} 
                      value={card.cvv} 
                      onChange={e => setCard({ ...card, cvv: e.target.value })} 
                      className="ciao-input font-mono" 
                      placeholder="123" 
                    />
                  </div>
                </fieldset>

                <div className="pt-3 flex flex-col gap-3">
                  <button 
                    type="submit"
                    disabled={busy} 
                    className="ciao-btn-primary w-full justify-center h-12 text-sm md:text-base font-bold disabled:opacity-50"
                  >
                    <Lock className="w-4 h-4 mr-2" /> 
                    {busy ? "Processing Verification..." : `Complete Payment (LKR ${Number(reservation.totalFare).toFixed(2)})`}
                  </button>

                  <button 
                    type="button" 
                    disabled={busy} 
                    onClick={() => void pay(true)} 
                    className="text-xs md:text-sm text-[#5E7480] hover:text-red-400 text-center transition-colors py-2 flex items-center justify-center gap-1 cursor-pointer"
                  >
                    <XCircle className="w-3.5 h-3.5" /> Cancel Reservation & Release Seats
                  </button>
                </div>
              </form>
            </div>
          ) : reservation.status === "CONFIRMED" ? (
            <div className="bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] p-8 rounded-3xl text-center shadow-lg">
              <CheckCircle2 className="w-12 h-12 text-emerald-400 mx-auto mb-3" />
              <h3 className="text-xl font-bold text-[#193542]">Payment Confirmed</h3>
              <p className="text-sm text-[#516A74] mt-1 mb-6">Your e-ticket is generated with unique booking reference.</p>
              <Link href={`/view-ticket?reservationId=${id}`} className="ciao-btn-primary mx-auto">
                <Ticket className="w-4 h-4 ml-1" /> View Confirmed E-Ticket
              </Link>
            </div>
          ) : (
            <div className="bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] p-8 rounded-3xl text-center shadow-lg">
              <XCircle className="w-12 h-12 text-red-400 mx-auto mb-3" />
              <h3 className="text-xl font-bold text-[#193542]">Reservation Cancelled or Expired</h3>
              <p className="text-sm text-[#516A74] mt-1 mb-6">The temporary hold has been released. You may start a fresh booking.</p>
              <Link href="/" className="ciao-btn-primary mx-auto">
                Browse Schedules
              </Link>
            </div>
          )}
        </div>
      )}
    </main>
  );
}

export default function Checkout() {
  return (
    <Suspense fallback={<div className="min-h-screen flex items-center justify-center text-sm text-[#5E7480]">Loading checkout gateway...</div>}>
      <CheckoutContent />
    </Suspense>
  );
}
