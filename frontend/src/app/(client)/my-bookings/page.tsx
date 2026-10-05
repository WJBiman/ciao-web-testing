"use client";

import { apiRequest, ApiError } from "@/lib/api";
import { useState, useEffect } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Ticket, Calendar, Clock, Bus, ArrowRight, AlertCircle } from "lucide-react";
import { Badge } from "@/components/ui/badge";

interface UserReservation {
  id: number;
  bookingReference: string;
  status: string;
  passengerName: string;
  passengerPhone: string;
  scheduleId: number;
  origin: string;
  destination: string;
  departureTime: string;
  arrivalTime: string;
  busPlateNumber: string | null;
  seatNumbers: string[];
  totalFare: number;
  createdAt: string;
}

export default function MyBookingsPage() {
  const router = useRouter();
  const [reservations, setReservations] = useState<UserReservation[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selectedCancelRes, setSelectedCancelRes] = useState<UserReservation | null>(null);
  const [cancelReason, setCancelReason] = useState("");
  const [cancelError, setCancelError] = useState("");
  const [cancelSuccess, setCancelSuccess] = useState("");
  const [submittingCancel, setSubmittingCancel] = useState(false);

  useEffect(() => {
    const fetchMyReservations = async () => {
      try {
        const data = await apiRequest<UserReservation[]>("/api/reservations/my");
        setReservations(Array.isArray(data) ? data : []);
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          router.replace("/login?redirect=/my-bookings");
          return;
        }
        setError(error instanceof Error ? error.message : "Unable to load bookings. Please retry.");
      } finally {
        setLoading(false);
      }
    };

    fetchMyReservations();
  }, [router]);

  const getStatusBadge = (status: string) => {
    switch (status) {
      case "CONFIRMED":
        return <Badge variant="success">CONFIRMED</Badge>;
      case "PENDING":
        return <Badge variant="warning">PENDING</Badge>;
      case "CANCELLED":
        return <Badge variant="danger">CANCELLED</Badge>;
      default:
        return <Badge variant="secondary">{status}</Badge>;
    }
  };

  return (
    <div className="min-h-screen pt-28 pb-20 px-5 bg-[#F6F8F6]">
      {/* Top Banner */}
      <div className="max-w-[1180px] mx-auto mb-8 flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div>
          <Badge variant="gold" className="mb-2">Passenger Archive</Badge>
          <h1 className="text-2xl md:text-3xl font-extrabold text-[#193542]">My Bookings & E-Tickets</h1>
          <p className="text-sm text-[#516A74] mt-1">
            View your bus bookings, reserved seats, and downloadable e-tickets
          </p>
        </div>

        <Link href="/" className="ciao-btn-secondary text-xs md:text-sm self-start md:self-auto">
          + Book Another Journey
        </Link>
      </div>

      <main className="max-w-[1180px] mx-auto w-full">
        {error && (
          <div role="alert" className="mb-6 p-4 rounded-xl bg-red-50 border border-red-500/40 text-red-700 text-sm flex items-center gap-3">
            <AlertCircle className="w-5 h-5 shrink-0 text-red-400" />
            <span>{error}</span>
          </div>
        )}

        {loading ? (
          <div className="bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] p-16 rounded-3xl text-center text-sm text-[#516A74]">
            <span className="w-6 h-6 border-2 border-[#087478] border-t-transparent rounded-full inline-block animate-spin mr-2" />
            Retrieving your confirmed reservations...
          </div>
        ) : reservations.length === 0 ? (
          <div className="bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] p-12 text-center rounded-3xl max-w-lg mx-auto shadow-xl">
            <div className="w-16 h-16 rounded-2xl bg-[#087478]/10 border border-[#087478]/20 flex items-center justify-center mx-auto mb-4 text-[#087478]">
              <Ticket className="w-8 h-8" />
            </div>
            <h3 className="text-xl font-bold text-[#193542] mb-2">No Bookings Yet</h3>
            <p className="text-sm text-[#5E7480] mb-6 leading-relaxed">
              You haven&apos;t booked any bus tickets yet. Search bus schedules and reserve your seats.
            </p>
            <Link href="/" className="ciao-btn-primary mx-auto">
              Browse Active Routes <ArrowRight className="w-4 h-4 ml-2" />
            </Link>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="flex justify-between items-center px-1">
              <span className="text-xs md:text-sm font-semibold text-[#5E7480] uppercase tracking-wider">
                {reservations.length} Journey{reservations.length > 1 ? "s" : ""} Recorded
              </span>
            </div>

            {reservations.map((res) => (
              <div
                key={res.id}
                className="bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] p-6 rounded-2xl flex flex-col lg:grid lg:grid-cols-[1.5fr_1.5fr_1fr_auto] gap-6 items-center transition-all hover:border-[#087478]/40 shadow-lg"
              >
                {/* Route & Booking Code */}
                <div className="w-full">
                  <div className="flex items-center gap-2.5 mb-2">
                    <span className="font-mono font-bold text-xs text-[#087478] bg-[#087478]/15 border border-[#087478]/30 px-2.5 py-0.5 rounded-full">
                      #{res.bookingReference}
                    </span>
                    {getStatusBadge(res.status)}
                  </div>
                  <h3 className="text-lg md:text-xl font-bold text-[#193542] flex items-center gap-2">
                    {res.origin || "Origin"} <ArrowRight className="w-4 h-4 text-[#087478]" /> {res.destination || "Destination"}
                  </h3>
                  <div className="text-xs md:text-sm text-[#5E7480] mt-1 flex items-center gap-2">
                    <Bus className="w-3.5 h-3.5 text-[#516A74]" /> 
                    <span>Bus: <strong className="text-[#193542]">{res.busPlateNumber || "Bus pending"}</strong></span>
                    <span>•</span>
                    <span>Passenger: <strong className="text-[#193542]">{res.passengerName}</strong></span>
                  </div>
                </div>

                {/* Departure & Arrival Schedule */}
                <div className="w-full text-left lg:text-center">
                  <div className="flex items-center gap-1.5 text-sm md:text-base font-semibold text-[#193542] lg:justify-center">
                    <Calendar className="w-4 h-4 text-[#087478]" />
                    {res.departureTime ? new Date(res.departureTime).toLocaleDateString([], { weekday: "short", year: "numeric", month: "short", day: "numeric" }) : "Scheduled Date"}
                  </div>
                  <div className="flex items-center gap-1 text-xs md:text-sm text-[#5E7480] mt-1 lg:justify-center">
                    <Clock className="w-3.5 h-3.5" />
                    <span>Departs: <strong className="text-[#516A74]">{res.departureTime ? new Date(res.departureTime).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "TBD"}</strong></span>
                  </div>
                </div>

                {/* Seat Numbers & Fare */}
                <div className="w-full text-left lg:text-right">
                  <div className="text-xs md:text-sm text-[#5E7480]">
                    Reserved Seat(s): <span className="font-bold text-[#087478]">{res.seatNumbers?.join(", ") || "-"}</span>
                  </div>
                  <div className="text-xl font-extrabold text-[#087478] mt-0.5">
                    LKR {Number(res.totalFare).toFixed(2)}
                  </div>
                </div>

                {/* Ticket View & Cancellation Actions */}
                <div className="w-full lg:w-auto flex flex-col sm:flex-row lg:flex-col gap-2">
                  <Link
                    href={`/view-ticket?reservationId=${res.id}`}
                    className="ciao-btn-primary w-full lg:w-auto justify-center text-xs md:text-sm !py-2.5 !px-4"
                  >
                    <Ticket className="w-4 h-4 mr-1.5" /> View E-Ticket
                  </Link>
                  {res.status === "CONFIRMED" && (
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedCancelRes(res);
                        setCancelReason("");
                        setCancelError("");
                        setCancelSuccess("");
                      }}
                      className="inline-flex items-center justify-center rounded-xl border border-red-200 bg-red-50 text-red-700 hover:bg-red-100 text-xs font-semibold py-2 px-3 transition"
                    >
                      Request Cancellation
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Cancellation Request Modal */}
        {selectedCancelRes && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
            <div className="w-full max-w-md rounded-3xl bg-white p-6 md:p-8 shadow-2xl">
              <h3 className="text-xl font-extrabold text-[#193542]">Request Booking Cancellation</h3>
              <p className="mt-2 text-sm text-[#5E7480]">
                Booking #{selectedCancelRes.bookingReference} ({selectedCancelRes.origin} → {selectedCancelRes.destination}).
                Total refundable fare: <strong className="text-[#087478]">LKR {Number(selectedCancelRes.totalFare).toFixed(2)}</strong>.
              </p>

              {cancelError && (
                <div className="mt-4 rounded-xl border border-red-200 bg-red-50 p-3 text-xs text-red-700">
                  {cancelError}
                </div>
              )}
              {cancelSuccess && (
                <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-xs text-emerald-800">
                  {cancelSuccess}
                </div>
              )}

              {!cancelSuccess && (
                <div className="mt-4">
                  <label className="block text-xs font-bold uppercase tracking-wider text-[#5E7480] mb-1">
                    Reason for Cancellation
                  </label>
                  <textarea
                    rows={3}
                    value={cancelReason}
                    onChange={(e) => setCancelReason(e.target.value)}
                    placeholder="Please specify why you are requesting cancellation..."
                    className="w-full rounded-xl border border-[#DCE7E4] p-3 text-sm focus:border-[#087478] focus:outline-none"
                  />
                  <p className="mt-1 text-xs text-[#5E7480]">
                    * Approval and simulated refund reversal are subject to review by a Customer Service Supervisor prior to scheduled departure.
                  </p>
                </div>
              )}

              <div className="mt-6 flex justify-end gap-3">
                <button
                  type="button"
                  disabled={submittingCancel}
                  onClick={() => setSelectedCancelRes(null)}
                  className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50"
                >
                  Close
                </button>
                {!cancelSuccess && (
                  <button
                    type="button"
                    disabled={submittingCancel || !cancelReason.trim()}
                    onClick={async () => {
                      setSubmittingCancel(true);
                      setCancelError("");
                      try {
                        await apiRequest(`/api/eer/reservations/${selectedCancelRes.id}/cancel-request`, {
                          method: "POST",
                          body: JSON.stringify({ reason: cancelReason.trim() }),
                        });
                        setCancelSuccess("Cancellation request submitted successfully. A Customer Service Supervisor will review your request.");
                        // Refresh bookings after 1.5s
                        setTimeout(() => {
                          setSelectedCancelRes(null);
                          window.location.reload();
                        }, 1500);
                      } catch (err: unknown) {
                        setCancelError(err instanceof Error ? err.message : "Failed to submit cancellation request.");
                      } finally {
                        setSubmittingCancel(false);
                      }
                    }}
                    className="rounded-xl bg-red-600 px-4 py-2 text-sm font-bold text-white hover:bg-red-700 disabled:opacity-50"
                  >
                    {submittingCancel ? "Submitting..." : "Submit Request"}
                  </button>
                )}
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
