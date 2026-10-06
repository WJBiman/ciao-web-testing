"use client";

import { API_BASE_URL } from "@/lib/api";
import { Suspense, useEffect, useState, useCallback } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { 
  Users, 
  Search, 
  CheckCircle2, 
  CreditCard, 
  Lock,
  XCircle,
  AlertTriangle,
  X
} from "lucide-react";
import { Badge } from "@/components/ui/badge";

type BookingStatus = {
  id: number;
  bookingReference: string;
  status: string;
  customerName: string;
  eventType?: string | null;
  preferredBusType?: string | null;
  journeyDetails?: string | null;
  startDate: string;
  endDate: string;
  passengerCount: number;
  totalCost: number;
  depositAmount: number;
  assignedBusId?: number | null;
  guestAccessToken?: string | null;
  cancellationReason?: string | null;
};

function GroupBookingStatusContent() {
  const params = useSearchParams();
  const [reference, setReference] = useState(params.get("reference") || "");
  const [phone, setPhone] = useState(() => (typeof window !== "undefined" ? sessionStorage.getItem("groupBookingPhone") || "" : ""));
  const [booking, setBooking] = useState<BookingStatus | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  // Payment states
  const [showPayment, setShowPayment] = useState(false);
  const [paymentMode, setPaymentMode] = useState<"deposit" | "balance">("deposit");
  const [guestToken, setGuestToken] = useState(() => (typeof window !== "undefined" ? sessionStorage.getItem("groupBookingGuestToken") || "" : ""));
  const [cardNumber, setCardNumber] = useState("4111111111111111");
  const [cardHolder, setCardHolder] = useState("CIAO TEST");
  const [expiry, setExpiry] = useState("12/30");
  const [cvv, setCvv] = useState("123");
  const [paying, setPaying] = useState(false);
  const [paymentMsg, setPaymentMsg] = useState("");
  const [paymentError, setPaymentError] = useState("");

  // Customer Cancellation states
  const [showCancelModal, setShowCancelModal] = useState(false);
  const [cancelReason, setCancelReason] = useState("");
  const [cancelling, setCancelling] = useState(false);
  const [cancelNotice, setCancelNotice] = useState("");
  const [cancelError, setCancelError] = useState("");

  const lookup = useCallback(async (event?: React.FormEvent) => {
    event?.preventDefault();
    setLoading(true); 
    setError(""); 
    setBooking(null); 
    setPaymentMsg(""); 
    setPaymentError("");
    try {
      const query = new URLSearchParams({ reference: reference.trim(), phone: phone.trim() });
      const response = await fetch(`${API_BASE_URL}/api/group-bookings/status?${query}`);
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || "Booking reference not found.");
      setBooking(data);
    } catch (e) { 
      setError(e instanceof Error ? e.message : "Unable to check booking status."); 
    } finally { 
      setLoading(false); 
    }
  }, [reference, phone]);

  const handlePaymentSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!booking) return;
    setPaying(true);
    setPaymentMsg("");
    setPaymentError("");

    try {
      const tokenToSend = guestToken.trim() ||
        (typeof window !== "undefined" ? sessionStorage.getItem("groupBookingGuestToken") || "" : "");

      const endpoint = paymentMode === "balance" 
        ? "/api/eer/groups/pay-balance" 
        : "/api/eer/groups/pay-deposit";

      const res = await fetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({
          groupId: booking.id,
          guestToken: tokenToSend || undefined,
          cardNumber: cardNumber.trim(),
          cardholderName: cardHolder.trim(),
          expiry: expiry.trim(),
          cvv: cvv.trim(),
        }),
      });

      const data = await res.json();
      if (!res.ok || data.status === "FAILED") {
        throw new Error(data.message || data.error || "Simulated payment failed.");
      }

      if (paymentMode === "balance") {
        setPaymentMsg(`Remaining Balance Paid Successfully! Transaction ID: ${data.transactionId}. Full group booking settlement of LKR ${Number(data.settledAmount || (booking.totalCost - booking.depositAmount)).toLocaleString()} completed.`);
      } else {
        setPaymentMsg(`Deposit Paid Successfully! Transaction ID: ${data.transactionId}. Group Booking Deposit of LKR ${Number(data.depositAmount || booking.depositAmount).toLocaleString()} confirmed.`);
      }

      setShowPayment(false);
      void lookup();
    } catch (err: unknown) {
      setPaymentError(err instanceof Error ? err.message : "Payment processing error.");
    } finally {
      setPaying(false);
    }
  };

  const handleCancelSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!booking) return;
    setCancelling(true);
    setCancelError("");
    setCancelNotice("");
    try {
      const res = await fetch(`${API_BASE_URL}/api/group-bookings/cancel`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          reference: booking.bookingReference,
          phone: phone.trim(),
          reason: cancelReason.trim() || "Customer requested cancellation prior to payment / verification",
        }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message || "Failed to cancel booking request.");
      setBooking(data);
      setShowCancelModal(false);
      setCancelNotice("Your group booking request has been successfully cancelled.");
      setShowPayment(false);
    } catch (err: unknown) {
      setCancelError(err instanceof Error ? err.message : "Failed to cancel booking request.");
    } finally {
      setCancelling(false);
    }
  };

  useEffect(() => {
    const ref = params.get("reference");
    const savedPhone = sessionStorage.getItem("groupBookingPhone") || "";
    if (ref && savedPhone) {
      const timer = setTimeout(() => {
        void lookup();
      }, 0);
      return () => clearTimeout(timer);
    }
  }, [lookup, params]);

  const getStatusBadge = (status: string) => {
    switch (status) {
      case "DEPOSIT_PAID":
        return <Badge variant="success">DEPOSIT PAID</Badge>;
      case "APPROVED":
        return <Badge variant="info">APPROVED</Badge>;
      case "PENDING_REVIEW":
        return <Badge variant="warning">PENDING REVIEW</Badge>;
      case "COMPLETED":
        return <Badge variant="gold">COMPLETED</Badge>;
      case "CANCELLED":
        return <Badge variant="danger">CANCELLED</Badge>;
      default:
        return <Badge variant="secondary">{status.replaceAll("_", " ")}</Badge>;
    }
  };

  return (
    <main className="max-w-2xl mx-auto px-5 pt-12 md:pt-16 pb-20 w-full">
      {/* Title */}
      <div className="text-center mb-8">
        <Badge variant="gold" className="mb-2">Group Booking Management</Badge>
        <h1 className="text-2xl font-extrabold tracking-tight text-[#193542] md:text-3xl">Group Booking Status & Deposit</h1>
        <p className="text-xs md:text-sm text-[#5E7480] mt-1 max-w-md mx-auto">
          Track staff review, bus assignment, and authorize simulated advance deposits
        </p>
      </div>

      <div className="ciao-card p-6 md:p-8 rounded-3xl shadow-sm">
        {/* Lookup Form */}
        <form onSubmit={lookup} className="space-y-4 mb-6">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="flex flex-col">
              <label className="ciao-label">Booking Reference *</label>
              <input 
                className="ciao-input font-mono uppercase text-sm" 
                placeholder="e.g. GRP-10" 
                value={reference} 
                onChange={e => setReference(e.target.value)} 
                required 
              />
            </div>

            <div className="flex flex-col">
              <label className="ciao-label">Organizer Mobile No *</label>
              <input 
                className="ciao-input text-sm" 
                placeholder="077XXXXXXX" 
                value={phone} 
                onChange={e => setPhone(e.target.value)} 
                required 
              />
            </div>
          </div>

          <button 
            type="submit" 
            className="ciao-btn-primary w-full justify-center h-11 text-sm disabled:opacity-50" 
            disabled={loading}
          >
            <Search className="w-4 h-4" /> {loading ? "Checking Reference..." : "Check Group Booking Status"}
          </button>
        </form>

        {error && (
          <div role="alert" className="p-3 bg-red-50 border border-red-500/30 text-red-700 text-xs rounded-xl mb-4 text-center">
            {error}
          </div>
        )}

        {paymentMsg && (
          <div role="status" className="p-4 bg-emerald-50 border border-emerald-500/30 text-emerald-700 text-xs rounded-2xl mb-4 flex items-center gap-2">
            <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0" />
            <span>{paymentMsg}</span>
          </div>
        )}

        {cancelNotice && (
          <div role="status" className="p-4 bg-rose-50 border border-rose-300 text-rose-800 text-xs rounded-2xl mb-4 flex items-center gap-2">
            <XCircle className="w-5 h-5 text-rose-500 shrink-0" />
            <span>{cancelNotice}</span>
          </div>
        )}

        {booking && (
          <div className="ciao-card rounded-2xl overflow-hidden">
            {/* Header info */}
            <div className="bg-[#F6F8F6] p-6 border-b border-[var(--ciao-border)] flex justify-between items-center">
              <div>
                <span className="text-xs text-[#5E7480] font-bold uppercase tracking-wider block">Reference Code</span>
                <span className="text-xl font-mono font-bold text-[#087478]">{booking.bookingReference}</span>
              </div>
              {getStatusBadge(booking.status)}
            </div>

            {/* Details Grid */}
            <div className="p-6 space-y-3 text-xs md:text-sm">
              <div className="flex justify-between py-1.5 border-b border-[var(--ciao-border)]">
                <span className="text-[#5E7480]">Lead Organizer:</span>
                <span className="font-semibold text-[#193542]">{booking.customerName}</span>
              </div>

              <div className="flex justify-between py-1.5 border-b border-[var(--ciao-border)]">
                <span className="text-[#5E7480]">Passenger Capacity:</span>
                <span className="font-semibold text-[#193542]">{booking.passengerCount} Passengers</span>
              </div>

              {booking.eventType && (
                <div className="flex justify-between py-1.5 border-b border-[var(--ciao-border)]">
                  <span className="text-[#5E7480]">Group Trip Purpose:</span>
                  <span className="font-semibold text-[#193542]">{booking.eventType}</span>
                </div>
              )}

              {booking.preferredBusType && (
                <div className="flex justify-between py-1.5 border-b border-[var(--ciao-border)]">
                  <span className="text-[#5E7480]">Bus Type:</span>
                  <span className="font-semibold text-[#193542]">{booking.preferredBusType}</span>
                </div>
              )}

              {booking.journeyDetails && (
                <div className="py-1.5 border-b border-[var(--ciao-border)]">
                  <span className="text-[#5E7480] block mb-1">Itinerary Route Notes:</span>
                  <p className="p-2.5 rounded-xl bg-[#F6F8F6] text-[#516A74] text-xs leading-relaxed">
                    {booking.journeyDetails}
                  </p>
                </div>
              )}

              <div className="flex justify-between py-1.5 border-b border-[var(--ciao-border)]">
                <span className="text-[#5E7480]">Schedule Dates:</span>
                <span className="font-semibold text-[#193542]">
                  {new Date(booking.startDate).toLocaleDateString()} – {new Date(booking.endDate).toLocaleDateString()}
                </span>
              </div>

              <div className="flex justify-between py-1.5 border-b border-[var(--ciao-border)]">
                <span className="text-[#5E7480]">Total Estimated Cost:</span>
                <span className="font-bold text-[#193542]">
                  LKR {Number(booking.totalCost || 0).toLocaleString(undefined, { minimumFractionDigits: 2 })}
                </span>
              </div>

              <div className="flex justify-between py-2 border-b border-[var(--ciao-border)] bg-[#087478]/5 px-3 rounded-xl">
                <div className="flex items-center gap-1.5">
                  <span className="text-[#087478] font-semibold">30% Advance Deposit:</span>
                  {booking.status === "DEPOSIT_PAID" || booking.status === "COMPLETED" ? (
                    <Badge variant="success" className="text-[10px] py-0 px-1.5">PAID</Badge>
                  ) : null}
                </div>
                <span className="text-base font-extrabold text-[#087478]">
                  LKR {Number(booking.depositAmount || 0).toLocaleString(undefined, { minimumFractionDigits: 2 })}
                </span>
              </div>

              {/* Remaining Balance Indicator */}
              <div className="flex justify-between py-2 border-b border-[var(--ciao-border)] bg-amber-50/60 px-3 rounded-xl">
                <div className="flex items-center gap-1.5">
                  <span className="text-amber-800 font-semibold">70% Remaining Balance:</span>
                  {booking.status === "COMPLETED" ? (
                    <Badge variant="success" className="text-[10px] py-0 px-1.5">SETTLED</Badge>
                  ) : (
                    <Badge variant="secondary" className="text-[10px] py-0 px-1.5">DUE</Badge>
                  )}
                </div>
                <span className="text-base font-extrabold text-amber-900">
                  LKR {Math.max(0, Number(booking.totalCost || 0) - Number(booking.depositAmount || 0)).toLocaleString(undefined, { minimumFractionDigits: 2 })}
                </span>
              </div>

              <div className="flex justify-between py-1.5">
                <span className="text-[#5E7480]">Assigned Bus:</span>
                <span className="font-semibold text-[#087478]">
                  {booking.assignedBusId ? `Bus #${booking.assignedBusId}` : "Pending Staff Allocation"}
                </span>
              </div>

              {/* Status Note: CANCELLED */}
              {booking.status === "CANCELLED" && (
                <div className="pt-4 border-t border-[var(--ciao-border)]">
                  <div className="p-4 bg-rose-50/90 border border-rose-200 rounded-2xl flex items-start gap-3">
                    <XCircle className="w-5 h-5 text-rose-600 shrink-0 mt-0.5" />
                    <div>
                      <p className="text-xs font-bold text-rose-900">This Group Booking Has Been Cancelled</p>
                      <p className="text-[11px] text-rose-700 mt-1 leading-relaxed">
                        {booking.cancellationReason || "This booking request was cancelled prior to payment and confirmation."}
                      </p>
                    </div>
                  </div>
                </div>
              )}

              {/* Status Note: COMPLETED */}
              {booking.status === "COMPLETED" && (
                <div className="pt-4 border-t border-[var(--ciao-border)]">
                  <div className="p-4 bg-emerald-50/80 border border-emerald-500/30 rounded-2xl flex items-center gap-3">
                    <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0" />
                    <div>
                      <p className="text-xs font-bold text-emerald-900">Full Payment Settled &amp; Trip Confirmed</p>
                      <p className="text-[11px] text-emerald-700 mt-0.5">
                        Both advance deposit and the remaining balance have been fully authorized. Your charter bus is booked.
                      </p>
                    </div>
                  </div>
                </div>
              )}

              {/* Payment Actions: Deposit OR Balance */}
              {(booking.status === "APPROVED" || booking.status === "PENDING_REVIEW" || booking.status === "DEPOSIT_PAID") && (
                <div className="pt-4 border-t border-[var(--ciao-border)]">
                  {!showPayment ? (
                    <div className="space-y-2.5">
                      {booking.status === "DEPOSIT_PAID" ? (
                        <div className="space-y-2">
                          <div className="p-3 bg-blue-50/60 border border-blue-200 rounded-xl text-xs text-blue-900 flex items-center justify-between">
                            <span>Advance Deposit received. You can now settle the remaining 70% balance directly online.</span>
                          </div>
                          <button
                            type="button"
                            onClick={() => {
                              setPaymentMode("balance");
                              setShowPayment(true);
                            }}
                            className="ciao-btn-primary w-full justify-center h-11 text-xs"
                          >
                            <CreditCard className="w-4 h-4" /> Pay Remaining Balance (LKR {Math.max(0, Number(booking.totalCost || 0) - Number(booking.depositAmount || 0)).toLocaleString()})
                          </button>
                        </div>
                      ) : (
                        <div className="space-y-2">
                          <button
                            type="button"
                            onClick={() => {
                              setPaymentMode("deposit");
                              setShowPayment(true);
                            }}
                            className="ciao-btn-primary w-full justify-center h-11 text-xs"
                          >
                            <CreditCard className="w-4 h-4" /> Pay Advance Deposit (University Simulation)
                          </button>
                          <button
                            type="button"
                            onClick={() => {
                              setCancelReason("");
                              setCancelError("");
                              setShowCancelModal(true);
                            }}
                            className="w-full inline-flex items-center justify-center gap-1.5 rounded-xl border border-rose-200 bg-rose-50/70 hover:bg-rose-100 text-rose-700 font-semibold py-2.5 px-4 text-xs transition-colors"
                          >
                            <XCircle className="w-4 h-4" /> Cancel Booking Request
                          </button>
                        </div>
                      )}
                    </div>
                  ) : (
                    <form onSubmit={handlePaymentSubmit} className="p-5 rounded-2xl bg-[#F6F8F6] border border-[#087478]/30 space-y-4 mt-2">
                      <div className="flex justify-between items-center">
                        <span className="text-xs font-bold text-[#087478] uppercase tracking-wider flex items-center gap-1.5">
                          <Lock className="w-3.5 h-3.5" /> {paymentMode === "balance" ? "Balance Settlement" : "University Simulated Payment"}
                        </span>
                        <Badge variant="warning">SIMULATION</Badge>
                      </div>

                      {paymentError && (
                        <div role="alert" className="p-2.5 text-xs bg-red-50 border border-red-500/30 text-red-700 rounded-xl">
                          {paymentError}
                        </div>
                      )}

                      <div className="text-xs text-[#5E7480]">
                        Accepted simulated card: <code className="text-[#516A74]">4111 1111 1111 1111 / CIAO TEST / 12/30 / 123</code>
                      </div>

                      {/* Verified Guest Security Badge */}
                      <input type="hidden" value={guestToken} />
                      {guestToken && (
                        <div className="flex items-center gap-2 px-3 py-2 bg-[#EAF4F2] border border-[#C9DBD6] rounded-xl text-xs text-[#087478] font-semibold">
                          <Lock className="w-3.5 h-3.5 shrink-0" />
                          <span>Verified Secure Guest Session</span>
                          <span className="ml-auto text-[10px] text-[#516A74] font-mono">
                            ID: {guestToken.slice(0, 8)}...
                          </span>
                        </div>
                      )}

                      <div className="p-3 bg-white border border-[var(--ciao-border)] rounded-xl flex justify-between items-center">
                        <span className="text-xs text-[#5E7480]">
                          {paymentMode === "balance" ? "Settling Balance Amount:" : "Authorizing Deposit:"}
                        </span>
                        <span className="text-sm font-bold text-[#087478]">
                          LKR {paymentMode === "balance"
                            ? Math.max(0, Number(booking.totalCost || 0) - Number(booking.depositAmount || 0)).toLocaleString()
                            : Number(booking.depositAmount || 0).toLocaleString()}
                        </span>
                      </div>

                      <div className="flex flex-col">
                        <label className="ciao-label">Card Number</label>
                        <input 
                          className="ciao-input font-mono text-sm" 
                          value={cardNumber} 
                          onChange={e => setCardNumber(e.target.value)} 
                          required 
                        />
                      </div>

                      <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                        <div className="col-span-2 sm:col-span-1 flex flex-col">
                          <label className="ciao-label">Cardholder</label>
                          <input 
                            className="ciao-input text-base sm:text-sm" 
                            value={cardHolder} 
                            onChange={e => setCardHolder(e.target.value)} 
                            required 
                          />
                        </div>
                        <div className="flex flex-col">
                          <label className="ciao-label">Expiry</label>
                          <input 
                            className="ciao-input font-mono text-base sm:text-sm" 
                            placeholder="MM/YY" 
                            value={expiry} 
                            onChange={e => setExpiry(e.target.value)} 
                            required 
                          />
                        </div>
                        <div className="flex flex-col">
                          <label className="ciao-label">CVV</label>
                          <input 
                            className="ciao-input font-mono text-base sm:text-sm" 
                            placeholder="123" 
                            value={cvv} 
                            onChange={e => setCvv(e.target.value)} 
                            required 
                          />
                        </div>
                      </div>

                      <div className="flex gap-2 pt-2">
                        <button 
                          type="button" 
                          onClick={() => setShowPayment(false)} 
                          className="ciao-btn-secondary w-1/3 justify-center text-xs"
                        >
                          Cancel
                        </button>
                        <button 
                          type="submit" 
                          disabled={paying} 
                          className="ciao-btn-primary w-2/3 justify-center text-xs disabled:opacity-50"
                        >
                          {paying 
                            ? "Processing..." 
                            : paymentMode === "balance"
                              ? `Pay Balance (LKR ${Math.max(0, Number(booking.totalCost || 0) - Number(booking.depositAmount || 0)).toLocaleString()})`
                              : `Authorize Deposit (LKR ${Number(booking.depositAmount).toLocaleString()})`
                          }
                        </button>
                      </div>
                    </form>
                  )}
                </div>
              )}
            </div>
          </div>
        )}

        <div className="mt-6 text-center">
          <Link href="/group-booking" className="text-xs text-[#087478] hover:underline font-semibold flex items-center justify-center gap-1">
            <Users className="w-3.5 h-3.5" /> Submit a New Group Booking Request
          </Link>
        </div>
      </div>

      {/* Customer Cancellation Dialog */}
      {showCancelModal && booking && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm animate-in fade-in duration-200">
          <div role="dialog" aria-modal="true" className="w-full max-w-md rounded-2xl border border-rose-200 bg-white p-6 shadow-2xl space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-[var(--ciao-border)]">
              <div className="flex items-center gap-2 text-rose-700">
                <AlertTriangle className="w-5 h-5 shrink-0" />
                <h2 className="text-base font-bold text-[#193542]">Cancel Group Booking Request</h2>
              </div>
              <button
                type="button"
                onClick={() => setShowCancelModal(false)}
                disabled={cancelling}
                className="rounded-lg p-1.5 text-[#516a74] hover:bg-rose-50"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <p className="text-xs text-[#516A74] leading-relaxed">
              Are you sure you want to cancel charter booking <strong className="text-[#193542]">{booking.bookingReference}</strong> for <strong className="text-[#193542]">{booking.customerName}</strong>? Once cancelled, staff will be notified and this request cannot be reactivated.
            </p>

            {cancelError && (
              <div role="alert" className="p-3 text-xs bg-rose-50 border border-rose-200 text-rose-700 rounded-xl">
                {cancelError}
              </div>
            )}

            <form onSubmit={handleCancelSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-[#193542] mb-1">
                  Reason for Cancellation (Optional)
                </label>
                <textarea
                  rows={3}
                  value={cancelReason}
                  onChange={(e) => setCancelReason(e.target.value)}
                  placeholder="e.g. Schedule dates rescheduled, event venue changed, etc."
                  className="ciao-input text-xs w-full resize-none"
                />
              </div>

              <div className="flex items-center justify-end gap-2 pt-2 border-t border-[var(--ciao-border)]">
                <button
                  type="button"
                  disabled={cancelling}
                  onClick={() => setShowCancelModal(false)}
                  className="ciao-btn-secondary text-xs h-9 px-4"
                >
                  Keep Booking
                </button>
                <button
                  type="submit"
                  disabled={cancelling}
                  className="inline-flex h-9 items-center justify-center gap-1.5 rounded-xl border border-rose-300 bg-rose-600 px-4 text-xs font-semibold text-white hover:bg-rose-700 focus-visible:outline-2 focus-visible:outline-rose-600 disabled:opacity-50"
                >
                  {cancelling ? "Cancelling..." : "Confirm Cancellation"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </main>
  );
}

export default function GroupBookingStatusPage() {
  return (
    <Suspense fallback={<main className="pt-32 text-center text-sm text-[#5E7480]">Loading group booking status...</main>}>
      <GroupBookingStatusContent />
    </Suspense>
  );
}
