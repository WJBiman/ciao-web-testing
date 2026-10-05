"use client";

import { SearchableSelect } from "@/components/ui/searchable-select";
import { DeleteRecordDialog } from "@/components/ui/delete-record-dialog";

import { useState, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";
import { API_BASE_URL, apiRequest } from "@/lib/api";
import {
  RotateCw,
  AlertCircle,
  Loader2,
  Phone,
  Bus as BusIcon,
  ShieldCheck,
  Trash2
} from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";

interface AdminParcel {
  id: number;
  trackingId: string;
  senderName: string;
  senderPhone: string;
  receiverName: string;
  receiverPhone: string;
  weight: number;
  totalFee: number;
  busId?: number;
  status: string;
  createdAt: string;
}

export default function AdminParcels() {
  const router = useRouter();
  const [parcels, setParcels] = useState<AdminParcel[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [updating, setUpdating] = useState<number | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<AdminParcel | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [canDelete, setCanDelete] = useState(false);
  const [notice, setNotice] = useState("");

  const fetchParcels = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/api/parcels`, {
        credentials: "include",
      });
      if (res.status === 401 || res.status === 403) {
        router.push("/login");
        return;
      }
      if (!res.ok) throw new Error("Failed to fetch parcels");
      const data = await res.json();
      setParcels(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to load parcels");
    } finally {
      setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    const timer = setTimeout(() => {
      void fetchParcels();
    }, 0);
    return () => clearTimeout(timer);
  }, [fetchParcels]);

  useEffect(() => {
    void apiRequest<{ staffType: string }>("/api/eer/access")
      .then((access) => setCanDelete(["SYSTEM_ADMINISTRATOR", "BRANCH_MANAGER"].includes(access.staffType)))
      .catch(() => setCanDelete(false));
  }, []);

  const deleteRecord = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    setError("");
    try {
      await apiRequest(`/api/eer/parcels/${deleteTarget.id}`, { method: "DELETE" });
      setDeleteTarget(null);
      setNotice("Parcel permanently deleted from the database.");
      await fetchParcels();
    } catch (e) {
      setDeleteTarget(null);
      setError(e instanceof Error ? e.message : "Could not delete parcel.");
    } finally {
      setDeleting(false);
    }
  };

  const handleUpdateStatus = async (id: number, newStatus: string) => {
    setUpdating(id);
    try {
      const res = await fetch(`${API_BASE_URL}/api/parcels/${id}/status`, {
        method: "PATCH",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ status: newStatus }),
      });

      if (!res.ok) throw new Error("Failed to update status");

      setParcels(parcels.map((p) => (p.id === id ? { ...p, status: newStatus } : p)));
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : "Error updating parcel status");
    } finally {
      setUpdating(null);
    }
  };

  const getStatusBadgeVariant = (status: string) => {
    switch (status) {
      case "PENDING":
        return "warning";
      case "IN_TRANSIT":
        return "gold";
      case "DELIVERED":
        return "success";
      case "RETURNED":
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
            <ShieldCheck className="w-4 h-4" /> Parcel Booking & Tracking Management
          </div>
          <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-[#193542]">
            Parcel Booking & Tracking
          </h1>
          <p className="text-xs sm:text-sm text-[var(--ciao-muted)] mt-1">
            Review parcel bookings, update tracking status, and verify branch handovers.
          </p>
        </div>
        <Button
          onClick={fetchParcels}
          variant="outline"
          className="self-start sm:self-auto text-xs h-9 font-semibold flex items-center gap-1.5"
        >
          <RotateCw className="w-3.5 h-3.5" /> Refresh Parcels
        </Button>
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
          <p className="text-xs">Loading parcels...</p>
        </div>
      ) : (
        <Card className="bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] shadow-xl overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-[rgba(25,53,66,0.12)] bg-[#EAF3F0] text-[#516A74] uppercase tracking-wider font-semibold text-xs">
                  <th className="p-4">Parcel Tracking Number</th>
                  <th className="p-4">Sender</th>
                  <th className="p-4">Receiver</th>
                  <th className="p-4">Assigned Bus</th>
                  <th className="p-4">Weight & Fee</th>
                  <th className="p-4">Parcel Status</th>
                  <th className="p-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[rgba(203,213,225,0.10)] bg-[#FFFFFF]">
                {parcels.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="p-8 text-center text-sm text-[#5E7480]">
                      No parcel bookings found.
                    </td>
                  </tr>
                ) : (
                  parcels.map((parcel) => (
                    <tr key={parcel.id} className="hover:bg-[#E2EFEB] transition-colors">
                      <td className="p-4">
                        <span className="font-mono text-sm font-bold text-[#087478]">
                          {parcel.trackingId}
                        </span>
                        <div className="text-xs text-[#5E7480] mt-0.5">
                          ID #{parcel.id}
                        </div>
                      </td>
                      <td className="p-4">
                        <div className="font-semibold text-[#193542]">{parcel.senderName}</div>
                        <div className="text-xs text-[#516A74] flex items-center gap-1 mt-0.5">
                          <Phone className="w-3.5 h-3.5 text-[#087478]" /> {parcel.senderPhone}
                        </div>
                      </td>
                      <td className="p-4">
                        <div className="font-semibold text-[#193542]">{parcel.receiverName}</div>
                        <div className="text-xs text-[#516A74] flex items-center gap-1 mt-0.5">
                          <Phone className="w-3.5 h-3.5 text-[#087478]" /> {parcel.receiverPhone}
                        </div>
                      </td>
                      <td className="p-4 font-mono text-[#193542]">
                        {parcel.busId ? (
                          <span className="flex items-center gap-1.5 text-[#087478] font-semibold">
                            <BusIcon className="w-3.5 h-3.5" /> #{parcel.busId}
                          </span>
                        ) : (
                          <span className="text-[#5E7480] italic font-sans text-xs">Terminal Depot</span>
                        )}
                      </td>
                      <td className="p-4">
                        <div className="font-bold text-[#193542]">
                          LKR {parcel.totalFee.toLocaleString()}
                        </div>
                        <div className="text-xs text-[#516A74]">
                          {parcel.weight} kg parcel
                        </div>
                      </td>
                      <td className="p-4">
                        <Badge
                          variant={getStatusBadgeVariant(parcel.status)}
                          className="text-xs uppercase tracking-wider font-semibold"
                        >
                          {parcel.status.replaceAll("_", " ")}
                        </Badge>
                      </td>
                      <td className="p-4 text-right">
                        <div className="inline-flex items-center gap-2">
                          <SearchableSelect
                            className="rounded-lg bg-[#F7FAF9] text-[#193542] border border-[rgba(25,53,66,0.16)] px-3 py-1.5 text-xs focus:border-[#087478] focus:outline-none disabled:opacity-50"
                            value={parcel.status}
                            onChange={(e) => handleUpdateStatus(parcel.id, e.target.value)}
                            disabled={updating === parcel.id}
                          >
                            <option value="PENDING">PENDING</option>
                            <option value="IN_TRANSIT">IN TRANSIT</option>
                            <option value="DELIVERED">DELIVERED</option>
                            <option value="RETURNED">RETURNED</option>
                          </SearchableSelect>
                          {updating === parcel.id && (
                            <Loader2 className="w-4 h-4 animate-spin text-[#087478]" />
                          )}
                          {canDelete && <button type="button" onClick={() => setDeleteTarget(parcel)} aria-label={`Permanently delete parcel ${parcel.trackingId}`} title="Permanently delete parcel" className="inline-flex min-h-9 items-center gap-1 rounded-lg border border-rose-200 bg-rose-50 px-2.5 text-xs font-semibold text-rose-700 hover:bg-rose-100 focus-visible:outline-2 focus-visible:outline-rose-700"><Trash2 className="size-3.5" /> Delete</button>}
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
      <DeleteRecordDialog open={deleteTarget !== null} record={deleteTarget ? `Parcel ${deleteTarget.trackingId}` : ""} busy={deleting} onCancel={() => setDeleteTarget(null)} onConfirm={() => void deleteRecord()} />
    </div>
  );
}
