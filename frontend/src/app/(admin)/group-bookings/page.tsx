"use client";

import { SearchableSelect } from "@/components/ui/searchable-select";
import { DeleteRecordDialog } from "@/components/ui/delete-record-dialog";

import { useState, useEffect, useCallback } from "react";
import { API_BASE_URL, apiRequest } from "@/lib/api";
import {
  Users2,
  Calendar,
  Phone,
  Bus as BusIcon,
  ShieldCheck,
  Edit2,
  X,
  AlertCircle,
  Loader2,
  Trash2
} from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";

interface GroupBooking {
  id: number;
  customerName: string;
  customerPhone: string;
  eventType?: string | null;
  preferredBusType?: string | null;
  journeyDetails?: string | null;
  startDate: string;
  endDate: string;
  passengerCount: number;
  totalCost: number;
  depositAmount: number;
  status: string;
  assignedBusId?: number | null;
  cancellationReason?: string | null;
  createdAt: string;
}

interface Bus {
  id: number;
  plateNumber: string;
  capacity: number;
  status: string;
  busType?: string;
}

export default function GroupBookingsAdmin() {
  const [bookings, setBookings] = useState<GroupBooking[]>([]);
  const [buses, setBuses] = useState<Bus[]>([]);
  const [availableBuses, setAvailableBuses] = useState<Bus[]>([]);
  const [loadingAvailableBuses, setLoadingAvailableBuses] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [selectedBooking, setSelectedBooking] = useState<GroupBooking | null>(null);
  const [showStatusModal, setShowStatusModal] = useState(false);
  const [newStatus, setNewStatus] = useState("");
  const [assignedBusId, setAssignedBusId] = useState("");
  const [updateError, setUpdateError] = useState("");
  const [deleteTarget, setDeleteTarget] = useState<GroupBooking | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [canDelete, setCanDelete] = useState(false);
  const [notice, setNotice] = useState("");

  const fetchData = useCallback(async () => {
    try {
      const [bookingsRes, busesRes] = await Promise.all([
        fetch(`${API_BASE_URL}/api/group-bookings`, { credentials: "include" }),
        fetch(`${API_BASE_URL}/api/fleet/buses/active`, { credentials: "include" }),
      ]);

      if (!bookingsRes.ok) throw new Error("Failed to fetch bookings");
      if (!busesRes.ok) throw new Error("Failed to fetch buses");

      const bookingsData = await bookingsRes.json();
      const busesData = await busesRes.json();

      setBookings(bookingsData);
      setBuses(busesData.filter((b: Bus) => b.status === "ACTIVE"));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to load bookings");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => {
      void fetchData();
    }, 0);
    return () => clearTimeout(timer);
  }, [fetchData]);

  useEffect(() => {
    void apiRequest<{ staffType: string }>("/api/eer/access")
      .then((access) => setCanDelete(["SYSTEM_ADMINISTRATOR", "OPERATIONS_MANAGER", "FINANCE_MANAGER"].includes(access.staffType)))
      .catch(() => setCanDelete(false));
  }, []);

  const deleteRecord = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    setError("");
    try {
      await apiRequest(`/api/eer/groups/${deleteTarget.id}`, { method: "DELETE" });
      setDeleteTarget(null);
      setNotice("Group booking permanently deleted from the database.");
      await fetchData();
    } catch (e) {
      setDeleteTarget(null);
      setError(e instanceof Error ? e.message : "Could not delete group booking.");
    } finally {
      setDeleting(false);
    }
  };

  const handleStatusUpdate = async () => {
    setUpdateError("");
    try {
      const payload: { status: string; assignedBusId?: number } = { status: newStatus };
      if (newStatus === "APPROVED" || newStatus === "DEPOSIT_PAID") {
        if (!assignedBusId) {
          setUpdateError("You must assign an active bus to approve the booking.");
          return;
        }
        payload.assignedBusId = Number(assignedBusId);
      }

      const res = await fetch(`${API_BASE_URL}/api/group-bookings/${selectedBooking?.id}/status`, {
        method: "PATCH",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(payload),
      });

      if (!res.ok) {
        let errorMsg = "Failed to update status";
        try {
          const errorData = await res.json();
          errorMsg = errorData.message || errorData.error || errorMsg;
        } catch {
          errorMsg = (await res.text()) || errorMsg;
        }
        throw new Error(errorMsg);
      }

      setShowStatusModal(false);
      setSelectedBooking(null);
      setNewStatus("");
      setAssignedBusId("");
      fetchData();
    } catch (err: unknown) {
      setUpdateError(err instanceof Error ? err.message : "Update failed");
    }
  };

  const openModal = async (booking: GroupBooking) => {
    setSelectedBooking(booking);
    setNewStatus(booking.status);
    setAssignedBusId(booking.assignedBusId ? booking.assignedBusId.toString() : "");
    setUpdateError("");
    setShowStatusModal(true);
    setLoadingAvailableBuses(true);
    setAvailableBuses([]);

    try {
      const res = await fetch(`${API_BASE_URL}/api/group-bookings/${booking.id}/available-buses`, {
        credentials: "include",
      });
      if (res.ok) {
        const data = await res.json();
        setAvailableBuses(Array.isArray(data) ? data : []);
      } else {
        setAvailableBuses(buses);
      }
    } catch {
      setAvailableBuses(buses);
    } finally {
      setLoadingAvailableBuses(false);
    }
  };

  const getStatusBadgeVariant = (status: string) => {
    switch (status) {
      case "PENDING_REVIEW":
        return "warning";
      case "APPROVED":
        return "gold";
      case "DEPOSIT_PAID":
      case "COMPLETED":
        return "success";
      case "CANCELLED":
        return "destructive";
      default:
        return "secondary";
    }
  };

  return (
    <div className="max-w-7xl mx-auto space-y-6">
      {/* Top Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-[var(--ciao-border)]">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-[var(--ciao-gold)] mb-1">
            <ShieldCheck className="w-4 h-4" /> Group Booking Management
          </div>
          <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-[#193542]">
            Group Booking Management
          </h1>
          <p className="text-xs sm:text-sm text-[var(--ciao-muted)] mt-1">
            Review group booking requests, verify trip details, and assign buses.
          </p>
        </div>
        <Badge variant="outline" className="self-start sm:self-auto text-xs px-3 py-1">
          {bookings.length} Group Booking Requests
        </Badge>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 flex items-start gap-3">
          <AlertCircle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />
          <p className="text-rose-700 text-sm">{error}</p>
        </div>
      )}
      {notice && <div role="status" className="rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">{notice}</div>}

      {loading ? (
        <div className="py-20 flex flex-col items-center justify-center gap-3 text-[var(--ciao-muted)]">
          <Loader2 className="w-8 h-8 text-[var(--ciao-gold)] animate-spin" />
          <p className="text-xs">Loading group bookings...</p>
        </div>
      ) : (
        <Card className="bg-[var(--ciao-surface)] border-[var(--ciao-border)] shadow-xl overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[1120px] text-left text-xs">
              <thead>
                <tr className="border-b border-[var(--ciao-border)] bg-[var(--ciao-surface-elevated)] text-[var(--ciao-text-secondary)] uppercase tracking-wider font-semibold">
                  <th className="p-4 whitespace-nowrap">Ref #</th>
                  <th className="p-4 whitespace-nowrap">Client & Itinerary Specs</th>
                  <th className="p-4 whitespace-nowrap">Travel Window</th>
                  <th className="p-4 whitespace-nowrap">Party Size</th>
                  <th className="p-4 whitespace-nowrap">Cost & Deposit</th>
                  <th className="p-4 whitespace-nowrap">Workflow Status</th>
                  <th className="p-4 whitespace-nowrap">Bus ID</th>
                  <th className="p-4 text-right whitespace-nowrap">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--ciao-border)]">
                {bookings.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="p-8 text-center text-xs text-[var(--ciao-text-muted)]">
                      No group bookings recorded.
                    </td>
                  </tr>
                ) : (
                  bookings.map((booking) => (
                    <tr key={booking.id} className="hover:bg-[var(--ciao-surface-soft)] transition-colors">
                      <td className="p-4 font-mono font-bold text-[var(--ciao-gold)]">
                        GRP-{booking.id}
                      </td>
                      <td className="p-4">
                        <div className="font-semibold text-[var(--ciao-text-primary)] text-sm">{booking.customerName}</div>
                        <div className="text-xs text-[var(--ciao-text-secondary)] flex items-center gap-1 mt-0.5">
                          <Phone className="w-3 h-3 text-[var(--ciao-gold)]" /> {booking.customerPhone}
                        </div>
                        <div className="text-xs text-[var(--ciao-gold)] mt-1.5 font-medium">
                          Purpose: {booking.eventType || "Group Trip"}
                        </div>
                        <div className="text-xs text-[var(--ciao-muted)]">
                          Tier: {booking.preferredBusType || "Standard Luxury"}
                        </div>
                        {booking.journeyDetails && (
                          <div className="mt-1.5 p-2 rounded-lg bg-[var(--ciao-bg)] border border-[var(--ciao-border)] text-xs text-[var(--ciao-muted)] max-w-xs whitespace-pre-wrap">
                            {booking.journeyDetails}
                          </div>
                        )}
                      </td>
                      <td className="p-4 text-[var(--ciao-muted)] whitespace-nowrap">
                        <div className="flex items-center gap-1.5 text-[#193542]">
                          <Calendar className="w-3.5 h-3.5 text-[var(--ciao-gold)]" />
                          {new Date(booking.startDate).toLocaleDateString("en-GB", {
                            day: "2-digit",
                            month: "short",
                            year: "numeric",
                          })}
                        </div>
                        <div className="text-xs text-[var(--ciao-muted)] pl-5 mt-0.5">
                          thru{" "}
                          {new Date(booking.endDate).toLocaleDateString("en-GB", {
                            day: "2-digit",
                            month: "short",
                            year: "numeric",
                          })}
                        </div>
                      </td>
                      <td className="p-4 font-bold text-[#193542]">
                        {booking.passengerCount} Pax
                      </td>
                      <td className="p-4">
                        <div className="font-bold text-[#193542]">
                          LKR {booking.totalCost.toLocaleString()}
                        </div>
                        <div className="text-xs text-emerald-700 font-semibold mt-0.5">
                          Dep: LKR {booking.depositAmount.toLocaleString()}
                        </div>
                      </td>
                      <td className="p-4">
                        <Badge
                          variant={booking.status === "CANCELLED" ? "danger" : getStatusBadgeVariant(booking.status)}
                          className={`text-xs uppercase tracking-wider whitespace-nowrap ${booking.status === "CANCELLED" ? "bg-rose-100 text-rose-800 border-rose-300 font-bold" : ""}`}
                        >
                          {booking.status === "CANCELLED" && booking.cancellationReason?.toLowerCase().includes("customer")
                            ? "Cancelled by Customer"
                            : booking.status.replace("_", " ")}
                        </Badge>
                        {booking.status === "CANCELLED" && booking.cancellationReason && (
                          <div className="text-[11px] text-rose-700 font-medium mt-1 max-w-[220px] leading-tight">
                            Note: {booking.cancellationReason}
                          </div>
                        )}
                      </td>
                      <td className="p-4 font-mono text-[#193542]">
                        {booking.assignedBusId ? (
                          <span className="flex items-center gap-1 text-[var(--ciao-gold)]">
                            <BusIcon className="w-3.5 h-3.5" /> #{booking.assignedBusId}
                          </span>
                        ) : (
                          <span className="text-[var(--ciao-muted)] italic font-sans">Unassigned</span>
                        )}
                      </td>
                      <td className="p-4 text-right whitespace-nowrap">
                        <div className={canDelete ? "grid grid-cols-2 gap-2 min-w-[168px]" : "flex justify-end"}>
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => openModal(booking)}
                          aria-label={`Review group booking ${booking.id}`}
                          className="text-xs h-9 px-2 text-[var(--ciao-gold)] border-[var(--ciao-gold)]/40 hover:bg-[var(--ciao-gold)]/10"
                        >
                          <Edit2 className="w-3 h-3 mr-1" /> Review
                        </Button>
                        {canDelete && <button type="button" onClick={() => setDeleteTarget(booking)} aria-label={`Permanently delete group booking ${booking.id}`} title="Permanently delete group booking" className="inline-flex h-9 items-center justify-center gap-1 rounded-lg border border-rose-200 bg-rose-50 px-2 text-xs font-semibold text-rose-700 hover:bg-rose-100 focus-visible:outline-2 focus-visible:outline-rose-700"><Trash2 className="size-3.5" /> Delete</button>}
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {/* Status & Bus Allocation Modal */}
      {showStatusModal && selectedBooking && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <Card className="w-full max-w-md bg-[var(--ciao-surface)] border-[var(--ciao-border)] p-6 shadow-2xl relative">
            <button
              onClick={() => setShowStatusModal(false)}
              className="absolute right-4 top-4 text-[var(--ciao-muted)] hover:text-[#193542]"
            >
              <X className="w-5 h-5" />
            </button>
            <h3 className="text-base font-bold text-[#193542] mb-2 flex items-center gap-2">
              <Users2 className="w-4 h-4 text-[var(--ciao-gold)]" />
              Review Group Booking #{selectedBooking.id}
            </h3>
            <p className="text-xs text-[var(--ciao-muted)] mb-4">
              Update workflow progress and assign dedicated vehicle assets.
            </p>

            {updateError && (
              <div className="p-3 mb-4 text-sm bg-rose-500/10 border border-rose-500/30 text-rose-700 rounded-lg">
                {updateError}
              </div>
            )}

            <div className="space-y-4">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                  Workflow Status Decision
                </label>
                <SearchableSelect
                  className="w-full rounded-lg bg-[var(--ciao-bg)] text-[#193542] border border-[var(--ciao-border)] px-3 py-2.5 text-xs focus:border-[var(--ciao-gold)] focus:outline-none"
                  value={newStatus}
                  onChange={(e) => setNewStatus(e.target.value)}
                >
                  <option value="PENDING_REVIEW">Pending Review</option>
                  <option value="APPROVED">Approved (Requires Bus Assignment)</option>
                  <option value="DEPOSIT_PAID">Deposit Paid</option>
                  <option value="COMPLETED">Completed</option>
                  <option value="CANCELLED">Cancelled</option>
                </SearchableSelect>
              </div>

              {(newStatus === "APPROVED" || newStatus === "DEPOSIT_PAID") && (
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                    Assign Bus (Mandatory for Approval) <span className="text-rose-400">*</span>
                  </label>
                  <SearchableSelect
                    className="w-full rounded-lg bg-[var(--ciao-bg)] text-[#193542] border border-[var(--ciao-border)] px-3 py-2.5 text-xs focus:border-[var(--ciao-gold)] focus:outline-none"
                    value={assignedBusId}
                    onChange={(e) => setAssignedBusId(e.target.value)}
                  >
                    <option value="">
                      {loadingAvailableBuses
                        ? "-- Checking available fleet... --"
                        : availableBuses.length === 0
                        ? "-- No available buses found for this period --"
                        : "-- Select Available Bus --"}
                    </option>
                    {(availableBuses.length > 0 ? availableBuses : buses).map((b) => (
                      <option key={b.id} value={b.id}>
                        Bus #{b.id} · {b.plateNumber} ({b.capacity} Seats{b.busType ? ` · ${b.busType}` : ""})
                      </option>
                    ))}
                  </SearchableSelect>
                  <p className="text-xs text-[var(--ciao-muted)] mt-1.5 flex items-center gap-1.5">
                    <span className="inline-block w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
                    <span>
                      Showing only non-busy buses available for {selectedBooking.passengerCount} passengers.
                    </span>
                  </p>
                </div>
              )}
            </div>

            <div className="flex gap-3 justify-end pt-6">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setShowStatusModal(false)}
              >
                Cancel
              </Button>
              <Button type="button" onClick={handleStatusUpdate} variant="gold" size="sm">
                Commit Decision
              </Button>
            </div>
          </Card>
        </div>
      )}
      <DeleteRecordDialog open={deleteTarget !== null} record={deleteTarget ? `Group booking GRP-${deleteTarget.id} (${deleteTarget.customerName})` : ""} busy={deleting} onCancel={() => setDeleteTarget(null)} onConfirm={() => void deleteRecord()} />
    </div>
  );
}
