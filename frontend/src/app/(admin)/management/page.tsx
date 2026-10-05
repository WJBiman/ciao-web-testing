"use client";

import { SearchableSelect } from "@/components/ui/searchable-select";

import { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import { apiRequest } from "@/lib/api";
import { 
  Users, 
  Building2, 
  MapPin, 
  Bus, 
  Package, 
  Users2, 
  CheckCircle2, 
  Search, 
  ShieldCheck, 
  KeyRound, 
  Copy, 
  Loader2, 
  AlertCircle,
  FileCheck2,
  PlusCircle,
  Edit3,
  Trash2,
  Ticket
} from "lucide-react";
import { DeleteRecordDialog } from "@/components/ui/delete-record-dialog";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";

type Row = { id: number; [key: string]: unknown };
type Data = Record<string, Row[]>;
type Field = { key: string; label: string; source?: string; type?: string; optional?: boolean; options?: string[] };

const roles = [
  "SYSTEM_ADMINISTRATOR",
  "BRANCH_MANAGER",
  "OPERATIONS_MANAGER",
  "FINANCE_MANAGER",
  "E_TICKETING_COORDINATOR",
  "CUSTOMER_SERVICE_SUPERVISOR",
];

const modules: Record<
  string,
  { title: string; description: string; source: string; icon: React.ComponentType<{ className?: string }>; fields: Field[] }
> = {
  staff: {
    title: "Staff Management",
    description: "Create employee profiles, assign administrative credentials, and define operational roles.",
    source: "staff",
    icon: Users,
    fields: [
      { key: "id", label: "Edit existing employee", source: "staff", optional: true },
      { key: "name", label: "Full Name" },
      { key: "email", label: "Corporate Email", type: "email" },
      { key: "phone", label: "Mobile Telephone" },
      { key: "password", label: "Password (required for new staff; leave blank to preserve existing)", type: "password", optional: true },
      { key: "employeeCode", label: "Employee Code" },
      { key: "hireDate", label: "Effective Hire Date", type: "date" },
      { key: "staffType", label: "Designated Role", options: roles },
    ],
  },
  branches: {
    title: "Branch Management",
    description: "Manage regional branches and their branch managers.",
    source: "branches",
    icon: Building2,
    fields: [
      { key: "id", label: "Edit existing branch", source: "branches", optional: true },
      { key: "location", label: "Location / Station Name" },
      { key: "contactNumber", label: "Station Contact Hotline" },
      { key: "managerId", label: "Assigned Branch Manager", source: "branchManagers" },
    ],
  },
  routes: {
    title: "Route Management",
    description: "Manage route distances, operations managers, and intermediate stops.",
    source: "routes",
    icon: MapPin,
    fields: [
      { key: "id", label: "Target Route", source: "routes" },
      { key: "distanceKm", label: "Route Distance (km)", type: "number" },
      { key: "managerId", label: "Overseeing Operations Manager", source: "operationsManagers" },
      { key: "stops", label: "Intermediate Stops in Sequence (one entry per line)", type: "textarea" },
    ],
  },
  buses: {
    title: "Bus & Driver Records Management",
    description: "Manage bus types, operations managers, and assigned drivers.",
    source: "buses",
    icon: Bus,
    fields: [
      { key: "id", label: "Bus Registration Number", source: "buses" },
      { key: "busType", label: "Bus Type (e.g., Luxury AC, Semi-Luxury)" },
      { key: "managerId", label: "Assigned Operations Manager", source: "operationsManagers" },
      { key: "driverId", label: "Primary Assigned Driver", source: "drivers", optional: true },
    ],
  },
  parcels: {
    title: "Parcel Booking & Tracking Management",
    description: "Assign parcels to origin branches, destination branches, and bus schedules.",
    source: "parcels",
    icon: Package,
    fields: [
      { key: "id", label: "Select Parcel", source: "parcels" },
      { key: "customerId", label: "Sender Account", source: "customers" },
      { key: "originBranchId", label: "Origin Branch", source: "branches" },
      { key: "destinationBranchId", label: "Destination Branch", source: "branches" },
      { key: "scheduleId", label: "Assigned Bus Schedule", source: "schedules" },
    ],
  },
  groups: {
    title: "Group Booking Management",
    description: "Review group booking requests and assign buses and schedules.",
    source: "groups",
    icon: Users2,
    fields: [
      { key: "id", label: "Group Booking Reference", source: "groups" },
      { key: "customerId", label: "Client Account", source: "customers" },
      { key: "scheduleId", label: "Assigned Dispatch Schedule", source: "schedules" },
      { key: "eventType", label: "Group Trip Purpose (e.g., Wedding, Corporate Event, Tour)" },
    ],
  },
  payments: {
    title: "Payment Verification",
    description: "Review payment records and confirm successful payments.",
    source: "payments",
    icon: ShieldCheck,
    fields: [
      { key: "id", label: "Pending Successful Payment", source: "successfulPayments" },
    ],
  },
  claims: {
    title: "Lost & Found Management",
    description: "Review ownership claims and verify returns of found items.",
    source: "claims",
    icon: Search,
    fields: [
      { key: "id", label: "Ownership Claim", source: "claims" },
      { key: "status", label: "Claim Decision", options: ["APPROVED", "REJECTED", "RETURNED"] },
    ],
  },
  cancellations: {
    title: "Cancellation Requests",
    description: "Review and adjudicate passenger booking cancellation and simulated refund requests.",
    source: "cancellations",
    icon: AlertCircle,
    fields: [
      { key: "id", label: "Pending Cancellation Request", source: "cancellations" },
      { key: "decision", label: "Decision", options: ["APPROVE", "REJECT"] },
      { key: "notes", label: "Adjudication Notes", type: "textarea", optional: true },
    ],
  },
  reservations: {
    title: "Ticket Reservations",
    description: "Review reservation records. Pending or cancelled entries with no payment, ticket, or cancellation history may be deleted.",
    source: "reservations",
    icon: Ticket,
    fields: [],
  },
};

function label(row: Row): string {
  const user = row.user as { fullName?: string } | undefined;
  const route = row.route as { origin?: string; destination?: string } | undefined;
  const reservation = row.reservation as { id?: number; passengerName?: string; totalFare?: number } | undefined;
  return (
    "#" +
    row.id +
    " · " +
    ((reservation ? `Booking #${reservation.id} (${reservation.passengerName}) - LKR ${Number(reservation.totalFare || 0).toLocaleString()} - ${row.reason}` : "") ||
      user?.fullName ||
      row.location ||
      row.plateNumber ||
      row.driverName ||
      row.trackingId ||
      row.customerName ||
      row.passengerName ||
      (row.origin ? row.origin + " → " + row.destination : undefined) ||
      (route ? route.origin + " → " + route.destination + " · " + row.departureTime : undefined) ||
      row.proofOfOwnership ||
      "Amount LKR " + Number(row.amount || 0).toLocaleString())
  );
}

export default function Management() {
  const [data, setData] = useState<Data>({});
  const [permissions, setPermissions] = useState<string[]>([]);
  const [staffType, setStaffType] = useState("");
  const [tab, setTab] = useState("staff");
  const [form, setForm] = useState<Record<string, string>>({});
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [deleteTarget, setDeleteTarget] = useState<Row | null>(null);
  const [deleting, setDeleting] = useState(false);

  // Staff-assisted guest token recovery states
  const [recoverRef, setRecoverRef] = useState("");
  const [recoverPhone, setRecoverPhone] = useState("");
  const [recoverName, setRecoverName] = useState("");
  const [recoveredToken, setRecoveredToken] = useState("");
  const [recoverError, setRecoverError] = useState("");
  const [recovering, setRecovering] = useState(false);

  const load = useCallback(async () => {
    try {
      const result = await apiRequest<Data & { permissions?: Array<{ id: string }> }>("/api/eer/management");
      setData(result);
      setStaffType((result as unknown as { staffType?: string }).staffType || "");
      const allowed = (result.permissions || []).map((p) => p.id);
      setPermissions(allowed);
      if (allowed.length > 0 && !allowed.includes(tab)) {
        setTab(allowed[0]);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to load service data.");
    } finally {
      setLoading(false);
    }
  }, [tab]);

  useEffect(() => {
    const timer = setTimeout(() => {
      void load();
    }, 0);
    return () => clearTimeout(timer);
  }, [load]);

  const sources: Record<string, Row[]> = {
    ...data,
    branchManagers: (data.staff || []).filter((s) => s.staffType === "BRANCH_MANAGER"),
    operationsManagers: (data.staff || []).filter((s) => s.staffType === "OPERATIONS_MANAGER"),
    successfulPayments: (data.payments || []).filter((p) => p.status === "SUCCESS" && !p.verifiedAt),
    cancellations: (data.cancellationRequests || []).filter((cr) => cr.status === "PENDING"),
  };

  const activeModule = modules[tab] || modules.staff;
  const canCreate = tab === "staff" || tab === "branches";
  const creationLinks: Record<string, { href: string; label: string }> = {
    routes: { href: "/routes", label: "New Route" },
    buses: { href: "/fleet", label: "New Bus" },
    parcels: { href: "/book-parcel", label: "Book Parcel" },
    groups: { href: "/group-booking", label: "Request Group Booking" },
  };
  const createDestination = creationLinks[tab];
  const rosterRows = tab === "payments" ? (data.payments || [])
    : tab === "claims" ? (data.claims || [])
    : tab === "cancellations" ? (data.cancellationRequests || [])
    : (data[activeModule.source] || []);
  const canDelete = staffType === "SYSTEM_ADMINISTRATOR" ||
    (["routes", "buses", "groups"].includes(tab) && staffType === "OPERATIONS_MANAGER") ||
    (tab === "parcels" && staffType === "BRANCH_MANAGER") ||
    (tab === "payments" && staffType === "FINANCE_MANAGER") ||
    (tab === "reservations" && staffType === "E_TICKETING_COORDINATOR") ||
    (["claims", "cancellations"].includes(tab) && staffType === "CUSTOMER_SERVICE_SUPERVISOR");
  const selectedClaim = tab === "claims" ? (data.claims || []).find((claim) => claim.id === Number(form.id)) : undefined;

  const handleRecordSelect = (fieldKey: string, value: string) => {
    if (fieldKey !== "id") {
      setForm((current) => ({ ...current, [fieldKey]: value }));
      return;
    }
    if (!value) {
      setForm({});
      return;
    }

    const selectedId = Number(value);
    const updated: Record<string, string> = { id: value };
    const relatedId = (row: Row, key: string) => String((row[key] as { id?: number } | undefined)?.id ?? "");
      if (tab === "staff") {
        const s = (data.staff || []).find((x) => x.id === selectedId);
        if (s) {
          const user = s.user as { fullName?: string; email?: string; phone?: string } | undefined;
          updated.name = user?.fullName || "";
          updated.email = user?.email || "";
          updated.phone = user?.phone || "";
          updated.employeeCode = String(s.employeeCode || "");
          updated.hireDate = String(s.hireDate || "");
          updated.staffType = String(s.staffType || "");
        }
      } else if (tab === "branches") {
        const b = (data.branches || []).find((x) => x.id === selectedId);
        if (b) {
          updated.location = String(b.location || "");
          updated.contactNumber = String(b.contactNumber || "");
          const mgr = b.manager as { id?: number } | undefined;
          updated.managerId = mgr?.id ? String(mgr.id) : "";
        }
      } else if (tab === "routes") {
        const r = (data.routes || []).find((x) => x.id === selectedId);
        if (r) {
          updated.distanceKm = r.distanceKm ? String(r.distanceKm) : "";
          const mgr = r.overseenBy as { id?: number } | undefined;
          updated.managerId = mgr?.id ? String(mgr.id) : "";
          const rStops = (data.stops || [])
            .filter((s) => (s.route as Row)?.id === selectedId)
            .sort((a, b) => Number(a.sequenceNumber) - Number(b.sequenceNumber))
            .map((s) => String(s.locationName));
          updated.stops = rStops.join("\n");
        }
      } else if (tab === "buses") {
        const b = (data.buses || []).find((x) => x.id === selectedId);
        if (b) {
          updated.busType = String(b.busType || "");
          const mgr = b.registeredBy as { id?: number } | undefined;
          updated.managerId = mgr?.id ? String(mgr.id) : "";
          const assignedDriver = (data.drivers || []).find((driver) => (driver.assignedBus as { id?: number } | undefined)?.id === selectedId);
          updated.driverId = assignedDriver ? String(assignedDriver.id) : "";
        }
      } else if (tab === "parcels") {
        const parcel = (data.parcels || []).find((x) => x.id === selectedId);
        if (parcel) {
          for (const [target, relation] of Object.entries({ customerId: "customer", originBranchId: "originBranch", destinationBranchId: "destinationBranch", scheduleId: "schedule" })) {
            updated[target] = relatedId(parcel, relation);
          }
        }
      } else if (tab === "groups") {
        const group = (data.groups || []).find((x) => x.id === selectedId);
        if (group) {
          const booking = group.booking as Row | undefined;
          updated.customerId = booking ? relatedId(booking, "customer") : "";
          updated.scheduleId = booking ? relatedId(booking, "schedule") : "";
          updated.eventType = String(group.eventType || "");
        }
      } else if (tab === "cancellations") {
        updated.decision = "APPROVE";
        updated.notes = "";
      }

    setForm(updated);
  };

  const startNewRecord = () => {
    setForm({});
    setError("");
    setNotice("");
    requestAnimationFrame(() => {
      document.getElementById("management-form-card")?.scrollIntoView({ behavior: "smooth", block: "start" });
      document.getElementById(tab === "staff" ? "name" : "location")?.focus();
    });
  };

  const editRecord = (id: number) => {
    handleRecordSelect("id", String(id));
    setError("");
    setNotice("");
    requestAnimationFrame(() => document.getElementById("management-form-card")?.scrollIntoView({ behavior: "smooth", block: "start" }));
  };

  const deleteRecord = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    setError("");
    setNotice("");
    try {
      await apiRequest(`/api/eer/${tab}/${deleteTarget.id}`, { method: "DELETE" });
      if (form.id === String(deleteTarget.id)) setForm({});
      setDeleteTarget(null);
      setNotice("Record permanently deleted from the database.");
      await load();
    } catch (e) {
      setDeleteTarget(null);
      setError(e instanceof Error ? e.message : "Could not delete this record.");
    } finally {
      setDeleting(false);
    }
  };

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!canCreate && !form.id) {
      setError("Choose an existing record before continuing.");
      return;
    }
    setBusy(true);
    setError("");
    setNotice("");

    const payload: Record<string, unknown> = { ...form };
    for (const field of activeModule.fields) {
      if (field.source) {
        payload[field.key] = form[field.key] ? Number(form[field.key]) : null;
      }
    }

    let path = "/api/eer/" + tab;
    let method = "PUT";

    if (canCreate) {
      method = "POST";
    } else {
      path += "/" + form.id;
      delete payload.id;
    }

    if (tab === "routes") {
      payload.stops = (form.stops || "").split("\n").map((x) => x.trim()).filter(Boolean);
      payload.distanceKm = Number(form.distanceKm);
    }
    if (tab === "payments") {
      path += "/verify";
      method = "POST";
    }

    if (tab === "cancellations") {
      const crId = Number(form.id);
      const cr = (data.cancellationRequests || []).find((x) => x.id === crId);
      const resId = (cr?.reservation as { id?: number } | undefined)?.id;
      if (!resId) {
        setError("Invalid reservation reference for this cancellation request.");
        setBusy(false);
        return;
      }
      const approve = form.decision === "APPROVE";
      path = `/api/eer/reservations/${resId}/adjudicate-cancellation?approve=${approve}&notes=${encodeURIComponent(form.notes || "")}`;
      method = "POST";
    }

    try {
      if (tab === "cancellations") {
        await apiRequest(path, { method });
        setNotice(form.decision === "APPROVE" ? "Cancellation approved and simulated refund registered." : "Cancellation request rejected.");
      } else {
        await apiRequest(path, { method, body: JSON.stringify(payload) });
        setNotice(tab === "payments" ? "Payment verified successfully." : form.id ? "Record updated successfully." : "New record created successfully.");
      }
      setForm({});
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Save failed.");
    } finally {
      setBusy(false);
    }
  }

  async function handleStaffRecoverToken(e: React.FormEvent) {
    e.preventDefault();
    setRecovering(true);
    setRecoverError("");
    setRecoveredToken("");
    try {
      const res = await apiRequest<{ guestAccessToken: string; message: string }>("/api/group-bookings/recover-token", {
        method: "POST",
        body: JSON.stringify({
          reference: recoverRef.trim(),
          phone: recoverPhone.trim(),
          name: recoverName.trim() || undefined,
        }),
      });
      setRecoveredToken(res.guestAccessToken);
    } catch (err) {
      setRecoverError(err instanceof Error ? err.message : "Token recovery failed.");
    } finally {
      setRecovering(false);
    }
  }

  const accessibleModules = loading ? [] : Object.entries(modules).filter(([key]) => permissions.includes(key));

  return (
    <div className="max-w-7xl mx-auto space-y-6">
      {/* Page Title & Breadcrumb */}
      <div>
        <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-[var(--ciao-gold)] mb-1">
          <ShieldCheck className="w-4 h-4" /> Operations Management
        </div>
        <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-[#193542]">System Management</h1>
        <p className="text-xs sm:text-sm text-[var(--ciao-muted)] mt-1">
          Manage the operations and service records assigned to your staff role.
        </p>
      </div>

      {/* Module Pill Navigation */}
      <nav className="flex flex-wrap gap-2 pt-2">
        {accessibleModules.map(([key, m]) => {
          const Icon = m.icon;
          const isActive = tab === key;
          return (
            <button
              key={key}
              onClick={() => {
                setTab(key);
                setForm({});
                setError("");
                setNotice("");
              }}
              className={`flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs md:text-sm font-semibold tracking-wide transition-all border ${
                isActive
                  ? "bg-[#087478] text-white border-[#087478] font-bold shadow-md shadow-[#087478]/20"
                  : "bg-[#FFFFFF] text-[#516A74] border-[rgba(25,53,66,0.12)] hover:text-[#193542] hover:bg-[#EAF3F0]"
              }`}
            >
              <Icon className="w-3.5 h-3.5 shrink-0" />
              <span>{m.title}</span>
            </button>
          );
        })}
      </nav>

      {/* Announcements */}
      {error && (
        <div className="p-4 rounded-xl bg-rose-500/15 border border-rose-500/40 flex items-start gap-3">
          <AlertCircle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />
          <p className="text-rose-700 text-sm md:text-base">{error}</p>
        </div>
      )}
      {notice && (
        <div className="p-4 rounded-xl bg-emerald-500/15 border border-emerald-500/40 flex items-start gap-3">
          <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
          <p className="text-emerald-700 text-sm md:text-base">{notice}</p>
        </div>
      )}

      {loading ? (
        <div className="py-20 flex flex-col items-center justify-center gap-3 text-[#516A74]">
          <Loader2 className="w-8 h-8 text-[#087478] animate-spin" />
          <p className="text-sm">Loading management records...</p>
        </div>
      ) : (
        <div className="grid lg:grid-cols-12 gap-6 items-start">
          {/* Form Editor Card */}
          <div className="lg:col-span-6 space-y-6">
            <Card id="management-form-card" className="p-5 sm:p-6 bg-[#FFFFFF] border-[rgba(25,53,66,0.12)] shadow-xl rounded-2xl scroll-mt-6">
              <div className="flex flex-col gap-4 border-b border-[rgba(25,53,66,0.12)] pb-5 mb-5 xl:flex-row xl:items-start xl:justify-between">
                <div className="flex min-w-0 items-start gap-3">
                  <div className="w-10 h-10 shrink-0 rounded-xl bg-[#087478]/10 border border-[#087478]/30 flex items-center justify-center text-[#087478]">
                    <activeModule.icon className="w-5 h-5" />
                  </div>
                  <div className="min-w-0">
                    <h2 className="text-base md:text-lg font-bold text-[#193542]">{activeModule.title}</h2>
                    <p className="mt-1 max-w-md text-xs leading-relaxed text-[#516A74]">{activeModule.description}</p>
                  </div>
                </div>
                <div className="flex shrink-0 flex-wrap items-center gap-2 xl:justify-end">
                  {form.id && <Badge variant="outline" className="inline-flex items-center gap-1.5 whitespace-nowrap px-3 py-2 text-xs text-[#087478]"><Edit3 className="size-3.5" /> Editing #{form.id}</Badge>}
                  {canCreate ? (
                    <button type="button" onClick={startNewRecord} className="inline-flex min-h-10 items-center justify-center gap-2 whitespace-nowrap rounded-xl border border-[#a8d9d0] bg-[#eaf4f2] px-4 py-2 text-xs font-bold text-[#087478] transition hover:border-[#087478] hover:bg-[#d9f1e9] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#087478]">
                      <PlusCircle className="size-4" /> New Record
                    </button>
                  ) : createDestination ? (
                    <Link href={createDestination.href} className="inline-flex min-h-10 items-center justify-center gap-2 whitespace-nowrap rounded-xl border border-[#a8d9d0] bg-[#eaf4f2] px-4 py-2 text-xs font-bold text-[#087478] transition hover:border-[#087478] hover:bg-[#d9f1e9] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#087478]">
                      <PlusCircle className="size-4" /> {createDestination.label}
                    </Link>
                  ) : !form.id && <Badge variant="outline" className="inline-flex items-center whitespace-nowrap px-3 py-2 text-xs text-[#516A74]">Select a record</Badge>}
                </div>
              </div>

              {tab === "reservations" ? (
                <p className="rounded-xl border border-[#c9dbd6] bg-[#f1f8f6] p-4 text-sm leading-relaxed text-[#516A74]">Select a reservation in Current Records to inspect its status. Delete is available separately for pending or cancelled records without linked payments, issued tickets, or cancellation decisions. Confirmed journeys are retained.</p>
              ) : <form onSubmit={submit} className="space-y-4">
                <fieldset disabled={busy} className="min-w-0 space-y-4">
                  {activeModule.fields.map((field) => (
                    <div key={field.key}>
                      <label htmlFor={field.key} className="block text-xs md:text-sm font-semibold uppercase tracking-wider text-[#516A74] mb-1.5">
                        {field.label} {(!field.optional || (field.key === "password" && !form.id)) && <span className="text-rose-500">*</span>}
                      </label>

                      {field.source || field.options ? (
                        <div className="relative">
                          <SearchableSelect
                            id={field.key}
                            required={!field.optional}
                            value={form[field.key] || ""}
                            onChange={(e) => handleRecordSelect(field.key, e.target.value)}
                            className="w-full rounded-xl bg-[#F7FAF9] text-[#193542] border border-[rgba(25,53,66,0.16)] px-3 py-2.5 text-xs md:text-sm focus:border-[#087478] focus:outline-none focus:ring-2 focus:ring-[#087478]/20 transition-colors"
                          >
                            <option value="">{field.key === "id" && canCreate ? "New record (select to edit)" : field.optional ? "None / unassigned" : "Select from directory..."}</option>
                            {field.options
                              ? (tab === "claims" && field.key === "status"
                                  ? selectedClaim?.claimStatus === "APPROVED" ? ["RETURNED"] : ["APPROVED", "REJECTED"]
                                  : field.options).map((x) => (
                                  <option key={x} value={x} className="bg-[#FFFFFF] text-[#193542]">
                                    {x.replaceAll("_", " ")}
                                  </option>
                                ))
                              : (field.source === "claims" ? rosterRows : (sources[field.source!] || [])).map((row) => (
                                  <option key={row.id} value={row.id} className="bg-[#FFFFFF] text-[#193542]">
                                    {label(row)}
                                  </option>
                                ))}
                          </SearchableSelect>
                        </div>
                      ) : field.type === "textarea" ? (
                        <textarea
                          id={field.key}
                          required={!field.optional || (field.key === "password" && !form.id)}
                          rows={4}
                          value={form[field.key] || ""}
                          onChange={(e) => setForm({ ...form, [field.key]: e.target.value })}
                          placeholder="Colombo Fort Interchange&#10;Kadawatha Interchange&#10;Kandy Multi-modal Terminal"
                          className="w-full rounded-xl p-3 bg-[#F7FAF9] border border-[rgba(25,53,66,0.16)] text-xs md:text-sm text-[#193542] font-mono focus:border-[#087478] focus:outline-none transition-colors"
                        />
                      ) : (
                        <Input
                          id={field.key}
                          required={!field.optional}
                          type={field.type || "text"}
                          step={field.type === "number" ? "0.01" : undefined}
                          min={field.type === "number" ? "0.01" : undefined}
                          autoComplete={field.type === "password" ? "new-password" : "off"}
                          value={form[field.key] || ""}
                          onChange={(e) => setForm({ ...form, [field.key]: e.target.value })}
                          className="text-xs md:text-sm"
                        />
                      )}
                    </div>
                  ))}
                </fieldset>

                <Button
                  type="submit"
                  disabled={busy || (!canCreate && !form.id)}
                  variant="gold"
                  className="w-full h-12 text-sm md:text-base font-bold tracking-wider uppercase mt-4 flex items-center justify-center gap-2 shadow-lg"
                >
                  {busy ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin mr-2" /> Processing Transaction...
                    </>
                  ) : tab === "payments" ? (
                    <>
                      <ShieldCheck className="w-4 h-4 mr-2" /> Verify & Settle Payment
                    </>
                  ) : form.id ? (
                    <>
                      <FileCheck2 className="w-4 h-4 mr-2" /> Commit Updated Changes
                    </>
                  ) : canCreate ? (
                    <>
                      <PlusCircle className="w-4 h-4 mr-2" /> Save New Entry
                    </>
                  ) : (
                    <>Select a record to continue</>
                  )}
                </Button>
              </form>}
            </Card>

            {/* Staff-Assisted Guest Token Recovery (Only visible on Groups) */}
            {tab === "groups" && (
              <Card className="p-6 bg-[#FFFFFF] border-[rgba(25,53,66,0.12)] shadow-xl rounded-2xl">
                <div className="flex items-center gap-2.5 text-[#087478] mb-1">
                  <KeyRound className="w-4 h-4" />
                  <h3 className="text-sm md:text-base font-bold text-[#193542] uppercase tracking-wider">
                    Guest Token Rescue Desk
                  </h3>
                </div>
                <p className="text-xs md:text-sm text-[#516A74] mb-4">
                  Retrieve and verify private reservation tokens for phone callers upon telephone verification.
                </p>

                {recoverError && (
                  <div className="p-3 mb-4 text-xs md:text-sm bg-rose-500/15 border border-rose-500/40 text-rose-700 rounded-xl">
                    {recoverError}
                  </div>
                )}

                {recoveredToken && (
                  <div className="p-4 mb-4 bg-emerald-500/15 border border-emerald-500/40 rounded-xl space-y-2">
                    <p className="text-xs md:text-sm font-semibold text-emerald-700">Validated Authorization Key:</p>
                    <div className="flex items-center gap-2">
                      <input
                        readOnly
                        value={recoveredToken}
                        className="w-full bg-[#F7FAF9] border border-emerald-500/40 rounded-xl p-2.5 text-xs md:text-sm font-mono text-emerald-700"
                      />
                      <Button
                        type="button"
                        size="sm"
                        variant="gold"
                        onClick={() => navigator.clipboard.writeText(recoveredToken)}
                        className="shrink-0 text-xs h-9"
                      >
                        <Copy className="w-3.5 h-3.5 mr-1" /> Copy
                      </Button>
                    </div>
                    <p className="text-xs text-[#5E7480]">
                      Provide this key to the passenger to enable secure deposit completion on their status page.
                    </p>
                  </div>
                )}

                <form onSubmit={handleStaffRecoverToken} className="space-y-3 p-4 rounded-xl bg-[#F7FAF9] border border-[rgba(25,53,66,0.12)]">
                  <div>
                    <label className="block text-xs font-semibold text-[#516A74] mb-1 uppercase tracking-wider">
                      Group Booking Reference Number
                    </label>
                    <Input
                      required
                      placeholder="e.g. GRP-12"
                      value={recoverRef}
                      onChange={(e) => setRecoverRef(e.target.value)}
                      className="text-xs md:text-sm"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-[#516A74] mb-1 uppercase tracking-wider">
                      Registered Client Phone
                    </label>
                    <Input
                      required
                      placeholder="e.g. 0771234567"
                      value={recoverPhone}
                      onChange={(e) => setRecoverPhone(e.target.value)}
                      className="text-xs md:text-sm"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-[#516A74] mb-1 uppercase tracking-wider">
                      Passenger Name (Optional Check)
                    </label>
                    <Input
                      placeholder="Client name match"
                      value={recoverName}
                      onChange={(e) => setRecoverName(e.target.value)}
                      className="text-xs md:text-sm"
                    />
                  </div>
                  <Button
                    type="submit"
                    disabled={recovering}
                    variant="outline"
                    className="w-full text-xs md:text-sm h-10 font-semibold"
                  >
                    {recovering ? "Verifying Record..." : "Retrieve Authorization Token"}
                  </Button>
                </form>
              </Card>
            )}
          </div>

          {/* Records Roster List */}
          <div className="lg:col-span-6">
            <Card className="p-6 bg-[#FFFFFF] border-[rgba(25,53,66,0.12)] shadow-xl rounded-2xl">
              <div className="flex items-center justify-between pb-4 border-b border-[rgba(25,53,66,0.12)] mb-4">
                <div>
                  <h2 className="text-base md:text-lg font-bold text-[#193542]">Current Records</h2>
                  <p className="text-xs text-[#516A74]">Records in this section</p>
                </div>
                <Badge variant="outline" className="text-xs border-[rgba(203,213,225,0.2)] text-[#516A74]">
                  {rosterRows.length} Records
                </Badge>
              </div>

              <div className="space-y-3 max-h-[720px] overflow-y-auto pr-1">
                {rosterRows.length === 0 ? (
                  <div className="p-8 text-center text-sm text-[#5E7480]">
                    No records currently need action in this segment.
                  </div>
                ) : (
                  rosterRows.map((row) => (
                    <div
                      key={row.id}
                      className="p-4 rounded-xl bg-[#F7FAF9] border border-[rgba(25,53,66,0.12)] hover:border-[#087478]/40 transition-all group"
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <h3 className="font-semibold text-xs sm:text-sm text-[#193542] truncate">
                            {label(row)}
                          </h3>
                          <div className="flex items-center gap-2 mt-1">
                            <Badge variant="gold" className="text-xs uppercase tracking-wider px-2 py-0.5">
                              {String(row.staffType || row.status || row.claimStatus || row.busType || "ACTIVE")}
                            </Badge>
                          </div>
                        </div>
                        <div className="flex shrink-0 flex-wrap items-center justify-end gap-2">
                          {tab !== "reservations" && <button type="button" onClick={() => editRecord(row.id)} className="inline-flex min-h-9 items-center gap-1.5 rounded-lg border border-[#c9dbd6] bg-white px-3 py-1.5 text-xs font-semibold text-[#087478] transition hover:border-[#087478] hover:bg-[#eaf4f2] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#087478]">
                            <Edit3 className="size-3.5" /> {tab === "payments" || tab === "claims" ? "Review" : "Edit"}
                          </button>}
                          {canDelete && <button type="button" onClick={() => setDeleteTarget(row)} aria-label={`Delete ${label(row)}`} className="inline-flex min-h-9 items-center gap-1.5 rounded-lg border border-rose-200 bg-rose-50 px-3 py-1.5 text-xs font-semibold text-rose-700 transition hover:bg-rose-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-rose-700">
                            <Trash2 className="size-3.5" /> Delete
                          </button>}
                        </div>
                      </div>

                      {row.proofOfOwnership !== undefined && (
                        <div className="text-xs text-[#516A74] mt-2.5 p-2.5 rounded-lg bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)]">
                          <span className="font-semibold text-[#087478]">Proof:</span> {String(row.proofOfOwnership)}
                        </div>
                      )}

                      {tab === "routes" && (
                        <div className="text-xs text-[#516A74] mt-2.5 p-2.5 rounded-lg bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] font-mono">
                          <span className="font-semibold text-[#087478] font-sans">Route Stops:</span>{" "}
                          {(data.stops || [])
                            .filter((s) => (s.route as Row)?.id === row.id)
                            .sort((a, b) => Number(a.sequenceNumber) - Number(b.sequenceNumber))
                            .map((s) => s.locationName)
                            .join(" → ") || "Non-stop direct service"}
                        </div>
                      )}
                    </div>
                  ))
                )}
              </div>
            </Card>
          </div>
        </div>
      )}
      <DeleteRecordDialog open={deleteTarget !== null} record={deleteTarget ? label(deleteTarget) : ""} busy={deleting} onCancel={() => setDeleteTarget(null)} onConfirm={() => void deleteRecord()} />
    </div>
  );
}
