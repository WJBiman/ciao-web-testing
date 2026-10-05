"use client";

import { SearchableSelect } from "@/components/ui/searchable-select";
import { DeleteRecordDialog } from "@/components/ui/delete-record-dialog";

import { useState, useEffect, useCallback } from "react";
import { API_BASE_URL, apiRequest } from "@/lib/api";
import {
  Search,
  Plus,
  Bus as BusIcon,
  Phone,
  X,
  AlertCircle,
  Loader2,
  ShieldCheck,
  CheckCircle2,
  Clock,
  MapPin,
  Send,
  Trash2
} from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";

type LostItemStatus = "LOST" | "FOUND" | "CLAIMED";

interface LostItem {
  id: number;
  itemDescription: string;
  reportedByName: string;
  reportedByPhone: string;
  status: LostItemStatus;
  createdAt: string | null;
  busId: number | null;
  busPlateNumber: string | null;
  routeId: number | null;
  routeName: string | null;
}

const NEXT_STATUS: Partial<Record<LostItemStatus, LostItemStatus>> = {
  LOST: "FOUND",
  FOUND: "CLAIMED",
};

const NEXT_LABEL: Partial<Record<LostItemStatus, string>> = {
  LOST: "Mark as Secured (Found)",
  FOUND: "Discharge to Claimant",
};

export default function LostAndFoundPage() {
  const [items, setItems] = useState<LostItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<LostItemStatus | "">("");
  const [updatingId, setUpdatingId] = useState<number | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<LostItem | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [notice, setNotice] = useState("");

  // Report Modal state
  const [showModal, setShowModal] = useState(false);
  const [modalForm, setModalForm] = useState({
    itemDescription: "",
    reportedByName: "",
    reportedByPhone: "",
    busId: "",
    routeId: "",
  });
  const [modalLoading, setModalLoading] = useState(false);
  const [modalError, setModalError] = useState("");
  const [buses, setBuses] = useState<{ id: number; plateNumber: string }[]>([]);
  const [routes, setRoutes] = useState<{ id: number; origin: string; destination: string }[]>([]);

  const fetchItems = useCallback(async () => {
    try {
      setLoading(true);
      setError("");
      const params = new URLSearchParams();
      if (search.trim()) params.set("search", search.trim());
      if (statusFilter) params.set("status", statusFilter);

      const res = await fetch(`${API_BASE_URL}/api/lost-items?${params.toString()}`, {
        credentials: "include",
      });

      if (res.status === 401 || res.status === 403) {
        setError("Unauthorized. Please log in as Admin or Staff.");
        setItems([]);
        return;
      }

      const data = await res.json();
      setItems(Array.isArray(data) ? data : []);
    } catch {
      setError("Failed to load lost items. Check server connection.");
    } finally {
      setLoading(false);
    }
  }, [search, statusFilter]);

  useEffect(() => {
    const timer = setTimeout(() => {
      void fetchItems();
    }, 0);
    return () => clearTimeout(timer);
  }, [fetchItems]);

  useEffect(() => {
    fetch(`${API_BASE_URL}/api/fleet/buses`, { credentials: "include" })
      .then((r) => r.json())
      .then((data) =>
        setBuses(Array.isArray(data) ? data.filter((b: { status: string }) => b.status === "ACTIVE") : [])
      )
      .catch(() => setBuses([]));
    fetch(`${API_BASE_URL}/api/routes`)
      .then((r) => r.json())
      .then((data) => setRoutes(Array.isArray(data) ? data : []))
      .catch(() => setRoutes([]));
  }, []);

  const handleUpdateStatus = async (item: LostItem) => {
    const next = NEXT_STATUS[item.status];
    if (!next) return;

    setUpdatingId(item.id);
    try {
      const res = await fetch(`${API_BASE_URL}/api/lost-items/${item.id}/status`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ status: next }),
      });

      if (!res.ok) {
        const err = await res.json();
        alert(err.message || "Status update failed.");
      } else {
        await fetchItems();
      }
    } catch {
      alert("Network error. Please try again.");
    } finally {
      setUpdatingId(null);
    }
  };

  const deleteRecord = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    setError("");
    setNotice("");
    try {
      await apiRequest(`/api/eer/lost-items/${deleteTarget.id}`, { method: "DELETE" });
      setDeleteTarget(null);
      await fetchItems();
      setNotice("Lost item permanently deleted from the database.");
    } catch (e) {
      setDeleteTarget(null);
      setError(e instanceof Error ? e.message : "Could not delete this lost item.");
    } finally {
      setDeleting(false);
    }
  };

  const handleModalChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    setModalForm({ ...modalForm, [e.target.name]: e.target.value });
  };

  const handleModalSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setModalLoading(true);
    setModalError("");

    const payload: Record<string, string | number> = {
      itemDescription: modalForm.itemDescription.trim(),
      reportedByName: modalForm.reportedByName.trim(),
      reportedByPhone: modalForm.reportedByPhone.trim(),
    };
    if (modalForm.busId) payload.busId = parseInt(modalForm.busId);
    if (modalForm.routeId) payload.routeId = parseInt(modalForm.routeId);

    try {
      const res = await fetch(`${API_BASE_URL}/api/lost-items`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      const data = await res.json();
      if (!res.ok) {
        setModalError(data.message || "Submission failed.");
      } else {
        setShowModal(false);
        setModalForm({ itemDescription: "", reportedByName: "", reportedByPhone: "", busId: "", routeId: "" });
        await fetchItems();
      }
    } catch {
      setModalError("Network error. Please try again.");
    } finally {
      setModalLoading(false);
    }
  };

  const formatDate = (dt: string | null) => {
    if (!dt) return "—";
    return new Date(dt).toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric" });
  };

  const getStatusBadgeVariant = (status: LostItemStatus) => {
    switch (status) {
      case "LOST":
        return "destructive";
      case "FOUND":
        return "warning";
      case "CLAIMED":
        return "success";
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
            <ShieldCheck className="w-4 h-4" /> Baggage Assistance
          </div>
          <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-[#193542]">
            Lost & Found Management
          </h1>
          <p className="text-xs sm:text-sm text-[var(--ciao-muted)] mt-1">
            Track missing items from terminal reports through security inspection and verified handover.
          </p>
        </div>
        <Button
          onClick={() => setShowModal(true)}
          variant="gold"
          className="self-start sm:self-auto text-xs h-9 font-semibold flex items-center gap-1.5"
        >
          <Plus className="w-4 h-4" /> Log Secured Item
        </Button>
      </div>

      {/* KPI Stats Bar */}
      <div className="grid grid-cols-1 min-[420px]:grid-cols-3 gap-3 sm:gap-4">
        {(["LOST", "FOUND", "CLAIMED"] as LostItemStatus[]).map((s) => {
          const count = items.filter((i) => i.status === s).length;
          return (
            <Card key={s} className="p-4 bg-[var(--ciao-surface)] border-[var(--ciao-border)] text-center">
              <Badge variant={getStatusBadgeVariant(s)} className="text-xs uppercase tracking-wider px-2">
                {s}
              </Badge>
              <p className="text-2xl sm:text-3xl font-bold text-[#193542] mt-2">{count}</p>
            </Card>
          );
        })}
      </div>

      {/* Filter / Search Bar */}
      <div className="flex flex-col sm:flex-row gap-3">
        <div className="relative grow">
          <Input
            type="text"
            placeholder="Search items by keywords or passenger name..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && fetchItems()}
            className="text-xs pl-9"
          />
          <Search className="w-4 h-4 text-[var(--ciao-muted)] absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
        </div>
        <SearchableSelect
          className="rounded-lg bg-[var(--ciao-surface)] text-[#193542] border border-[var(--ciao-border)] px-3 py-2 text-xs focus:border-[var(--ciao-gold)] focus:outline-none sm:w-44"
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as LostItemStatus | "")}
        >
          <option value="">All Statuses</option>
          <option value="LOST">LOST (Pending Finding)</option>
          <option value="FOUND">FOUND (In Depot Vault)</option>
          <option value="CLAIMED">CLAIMED (Returned)</option>
        </SearchableSelect>
        <Button onClick={fetchItems} variant="outline" className="text-xs h-9 font-semibold">
          Search
        </Button>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 flex items-start gap-3">
          <AlertCircle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />
          <p className="text-rose-700 text-sm">{error}</p>
        </div>
      )}
      {notice && <div role="status" className="rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">{notice}</div>}

      {/* Table Card */}
      {loading ? (
        <div className="py-20 flex flex-col items-center justify-center gap-3 text-[var(--ciao-muted)]">
          <Loader2 className="w-8 h-8 text-[var(--ciao-gold)] animate-spin" />
          <p className="text-xs">Accessing baggage custody records...</p>
        </div>
      ) : (
        <Card className="bg-[var(--ciao-surface)] border-[var(--ciao-border)] shadow-xl overflow-hidden">
          <div className="overflow-x-auto" role="region" aria-label="Lost and found records, scroll horizontally for all columns" tabIndex={0}>
            <table className="min-w-[960px] w-full text-left text-xs">
              <thead>
                <tr className="border-b border-[var(--ciao-border)] bg-[var(--ciao-surface-elevated)] text-[var(--ciao-text-secondary)] uppercase tracking-wider font-semibold">
                  <th className="p-4">Case Docket #</th>
                  <th className="p-4">Article Description</th>
                  <th className="p-4">Transit Bus / Route</th>
                  <th className="p-4">Reporter Information</th>
                  <th className="p-4">Logged Date</th>
                  <th className="p-4">Custody Status</th>
                  <th className="p-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--ciao-border)]">
                {items.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="p-8 text-center text-xs text-[var(--ciao-text-muted)]">
                      No lost belongings found matching criteria.
                    </td>
                  </tr>
                ) : (
                  items.map((item) => (
                    <tr key={item.id} className="hover:bg-[var(--ciao-surface-soft)] transition-colors">
                      <td className="p-4 font-mono font-bold text-[var(--ciao-gold)]">
                        LF-#{String(item.id).padStart(4, "0")}
                      </td>
                      <td className="p-4">
                        <span className="font-medium text-[var(--ciao-text-primary)] max-w-xs block truncate" title={item.itemDescription}>
                          {item.itemDescription}
                        </span>
                      </td>
                      <td className="p-4 text-[var(--ciao-muted)]">
                        {item.busPlateNumber && (
                          <div className="flex items-center gap-1.5 text-[#193542]">
                            <BusIcon className="w-3.5 h-3.5 text-[var(--ciao-gold)]" />
                            {item.busPlateNumber}
                          </div>
                        )}
                        {item.routeName && (
                          <div className="text-xs text-[var(--ciao-muted)] flex items-center gap-1 mt-0.5">
                            <MapPin className="w-3 h-3" /> {item.routeName}
                          </div>
                        )}
                        {!item.busPlateNumber && !item.routeName && (
                          <span className="text-[var(--ciao-muted)] italic">—</span>
                        )}
                      </td>
                      <td className="p-4">
                        <div className="font-semibold text-[#193542]">{item.reportedByName}</div>
                        <div className="text-xs text-[var(--ciao-muted)] flex items-center gap-1 mt-0.5">
                          <Phone className="w-3 h-3 text-[var(--ciao-gold)]" /> {item.reportedByPhone}
                        </div>
                      </td>
                      <td className="p-4 text-[var(--ciao-muted)] whitespace-nowrap">
                        <div className="flex items-center gap-1.5">
                          <Clock className="w-3.5 h-3.5 text-[var(--ciao-muted)]" />
                          {formatDate(item.createdAt)}
                        </div>
                      </td>
                      <td className="p-4">
                        <Badge
                          variant={getStatusBadgeVariant(item.status)}
                          className="text-xs uppercase tracking-wider"
                        >
                          {item.status}
                        </Badge>
                      </td>
                      <td className="p-4 text-right">
                        <div className="flex flex-wrap items-center justify-end gap-2">
                        {NEXT_STATUS[item.status] ? (
                          <Button
                            size="sm"
                            variant="gold"
                            onClick={() => handleUpdateStatus(item)}
                            disabled={updatingId === item.id}
                            className="text-xs h-8 whitespace-nowrap font-semibold"
                          >
                            {updatingId === item.id ? (
                              <Loader2 className="w-3.5 h-3.5 animate-spin" />
                            ) : (
                              NEXT_LABEL[item.status]
                            )}
                          </Button>
                        ) : (
                          <span className="text-emerald-700 font-semibold text-xs flex items-center justify-end gap-1">
                            <CheckCircle2 className="w-3.5 h-3.5" /> Case Closed
                          </span>
                        )}
                          <button type="button" onClick={() => setDeleteTarget(item)} aria-label={`Permanently delete lost item ${item.id}`} title="Permanently delete item" className="inline-flex min-h-8 items-center gap-1 rounded-lg border border-rose-200 bg-rose-50 px-2.5 text-xs font-semibold text-rose-700 hover:bg-rose-100 focus-visible:outline-2 focus-visible:outline-rose-700"><Trash2 className="size-3.5" /> Delete</button>
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

      {/* Found Item Log Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <Card className="w-full max-w-lg bg-[var(--ciao-surface)] border-[var(--ciao-border)] p-6 shadow-2xl relative">
            <button
              onClick={() => {
                setShowModal(false);
                setModalError("");
              }}
              className="absolute right-4 top-4 text-[var(--ciao-muted)] hover:text-[#193542]"
            >
              <X className="w-5 h-5" />
            </button>
            <h3 className="text-base font-bold text-[#193542] mb-2 flex items-center gap-2">
              <Search className="w-4 h-4 text-[var(--ciao-gold)]" />
              Register Found Item
            </h3>
            <p className="text-xs text-[var(--ciao-muted)] mb-4">
              Record an item found on a bus or at a terminal.
            </p>

            {modalError && (
              <div className="p-3 mb-4 text-sm bg-rose-500/10 border border-rose-500/30 text-rose-700 rounded-lg">
                {modalError}
              </div>
            )}

            <form onSubmit={handleModalSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                  Article Description <span className="text-rose-400">*</span>
                </label>
                <textarea
                  name="itemDescription"
                  value={modalForm.itemDescription}
                  onChange={handleModalChange}
                  required
                  maxLength={255}
                  rows={3}
                  className="w-full rounded-lg bg-[var(--ciao-bg)] text-[#193542] border border-[var(--ciao-border)] p-3 text-xs focus:border-[var(--ciao-gold)] focus:outline-none transition-colors resize-none placeholder:text-[var(--ciao-muted)]/50"
                  placeholder="Describe color, brand, condition, identifying contents..."
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                    Finder / Officer Name <span className="text-rose-400">*</span>
                  </label>
                  <Input
                    name="reportedByName"
                    value={modalForm.reportedByName}
                    onChange={handleModalChange}
                    required
                    maxLength={100}
                    placeholder="Staff or passenger name"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                    Contact Phone <span className="text-rose-400">*</span>
                  </label>
                  <Input
                    name="reportedByPhone"
                    value={modalForm.reportedByPhone}
                    onChange={handleModalChange}
                    required
                    maxLength={15}
                    placeholder="07X XXXXXXX"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                    Bus Found On
                  </label>
                  <SearchableSelect
                    name="busId"
                    value={modalForm.busId}
                    onChange={handleModalChange}
                    className="w-full rounded-lg bg-[var(--ciao-bg)] text-[#193542] border border-[var(--ciao-border)] px-3 py-2 text-xs focus:border-[var(--ciao-gold)] focus:outline-none"
                  >
                    <option value="">— Unspecified / Terminal Depot —</option>
                    {buses.map((b) => (
                      <option key={b.id} value={b.id}>
                        {b.plateNumber}
                      </option>
                    ))}
                  </SearchableSelect>
                </div>
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                    Traveled Route
                  </label>
                  <SearchableSelect
                    name="routeId"
                    value={modalForm.routeId}
                    onChange={handleModalChange}
                    className="w-full rounded-lg bg-[var(--ciao-bg)] text-[#193542] border border-[var(--ciao-border)] px-3 py-2 text-xs focus:border-[var(--ciao-gold)] focus:outline-none"
                  >
                    <option value="">— Route Not Specified —</option>
                    {routes.map((r) => (
                      <option key={r.id} value={r.id}>
                        {r.origin} → {r.destination}
                      </option>
                    ))}
                  </SearchableSelect>
                </div>
              </div>

              <div className="flex gap-3 justify-end pt-4">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    setShowModal(false);
                    setModalError("");
                  }}
                >
                  Cancel
                </Button>
                <Button type="submit" disabled={modalLoading} variant="gold" size="sm">
                  {modalLoading ? (
                    <>
                      <Loader2 className="w-3.5 h-3.5 animate-spin mr-1.5" /> Committing...
                    </>
                  ) : (
                    <>
                      <Send className="w-3.5 h-3.5 mr-1.5" /> Register Item
                    </>
                  )}
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}
      <DeleteRecordDialog open={deleteTarget !== null} record={deleteTarget ? `Lost item #${deleteTarget.id}: ${deleteTarget.itemDescription}` : ""} busy={deleting} onCancel={() => setDeleteTarget(null)} onConfirm={() => void deleteRecord()} />
    </div>
  );
}
