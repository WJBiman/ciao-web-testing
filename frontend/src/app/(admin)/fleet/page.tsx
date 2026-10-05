"use client";

import { SearchableSelect } from "@/components/ui/searchable-select";
import { DeleteRecordDialog } from "@/components/ui/delete-record-dialog";

import { useState, useEffect, useCallback, FormEvent } from "react";
import { useRouter } from "next/navigation";
import { API_BASE_URL, apiRequest } from "@/lib/api";

import {
  Bus as BusIcon,
  Users,
  Wrench,
  UserCheck,
  Plus,
  UserPlus,
  Edit2,
  Trash2,
  Loader2,
  AlertCircle,
  X,
  ShieldCheck,
  Search
} from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";

interface Bus {
  id: number;
  plateNumber: string;
  capacity: number;
  amenities: string;
  status: "ACTIVE" | "MAINTENANCE" | "RETIRED";
}

interface Driver {
  id: number;
  driverName: string;
  licenseNumber: string;
  contactNumber: string;
  status: "AVAILABLE" | "ON_DUTY" | "ON_LEAVE" | "RETIRED";
}

export default function FleetManagement() {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<"buses" | "drivers">("buses");

  const [buses, setBuses] = useState<Bus[]>([]);
  const [drivers, setDrivers] = useState<Driver[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState("");
  const [deleteTarget, setDeleteTarget] = useState<{ kind: "buses" | "drivers"; id: number; label: string } | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [notice, setNotice] = useState("");

  const filteredBuses = buses.filter((b) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase().trim();
    return (
      b.plateNumber.toLowerCase().includes(q) ||
      (b.amenities && b.amenities.toLowerCase().includes(q)) ||
      b.status.toLowerCase().includes(q)
    );
  });

  const filteredDrivers = drivers.filter((d) => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase().trim();
    return (
      d.driverName.toLowerCase().includes(q) ||
      d.licenseNumber.toLowerCase().includes(q) ||
      d.contactNumber.toLowerCase().includes(q) ||
      d.status.toLowerCase().includes(q)
    );
  });

  // Modals state
  const [showBusModal, setShowBusModal] = useState(false);
  const [showDriverModal, setShowDriverModal] = useState(false);

  const [busForm, setBusForm] = useState<Partial<Bus>>({});
  const [driverForm, setDriverForm] = useState<Partial<Driver>>({});

  const fetchData = useCallback(async () => {
    try {
      const [busesRes, driversRes] = await Promise.all([
        fetch(`${API_BASE_URL}/api/fleet/buses`, { credentials: "include" }),
        fetch(`${API_BASE_URL}/api/fleet/drivers`, { credentials: "include" }),
      ]);

      if (busesRes.status === 401 || busesRes.status === 403) {
        router.push("/login");
        return;
      }

      if (busesRes.ok && driversRes.ok) {
        setBuses(await busesRes.json());
        setDrivers(await driversRes.json());
      } else {
        setErrorMsg("Failed to load fleet data. You may not have the required permissions.");
      }
    } catch {
      setErrorMsg("Connection error.");
    } finally {
      setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    const timer = setTimeout(() => {
      void fetchData();
    }, 0);
    return () => clearTimeout(timer);
  }, [fetchData]);

  const handleSaveBus = async (e: FormEvent) => {
    e.preventDefault();
    setErrorMsg("");
    const url = busForm.id ? `${API_BASE_URL}/api/fleet/buses/${busForm.id}` : `${API_BASE_URL}/api/fleet/buses`;
    const method = busForm.id ? "PUT" : "POST";

    try {
      const res = await fetch(url, {
        method,
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(busForm),
      });
      if (res.ok) {
        setShowBusModal(false);
        fetchData();
      } else {
        const data = await res.json();
        setErrorMsg(data.message || "Failed to save bus.");
      }
    } catch {
      setErrorMsg("Connection error.");
    }
  };

  const handleSaveDriver = async (e: FormEvent) => {
    e.preventDefault();
    setErrorMsg("");
    const url = driverForm.id ? `${API_BASE_URL}/api/fleet/drivers/${driverForm.id}` : `${API_BASE_URL}/api/fleet/drivers`;
    const method = driverForm.id ? "PUT" : "POST";

    try {
      const res = await fetch(url, {
        method,
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(driverForm),
      });
      if (res.ok) {
        setShowDriverModal(false);
        fetchData();
      } else {
        const data = await res.json();
        setErrorMsg(data.message || "Failed to save driver.");
      }
    } catch {
      setErrorMsg("Connection error.");
    }
  };

  const deleteRecord = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    setErrorMsg("");
    setNotice("");
    try {
      await apiRequest(`/api/fleet/${deleteTarget.kind}/${deleteTarget.id}`, { method: "DELETE" });
      setNotice(`${deleteTarget.label} permanently deleted from the database.`);
      setDeleteTarget(null);
      await fetchData();
    } catch (e) {
      setErrorMsg(e instanceof Error ? e.message : "Delete failed.");
      setDeleteTarget(null);
    } finally {
      setDeleting(false);
    }
  };

  const openBusModal = (bus?: Bus) => {
    setBusForm(bus || { plateNumber: "", capacity: 40, amenities: "", status: "ACTIVE" });
    setShowBusModal(true);
  };

  const openDriverModal = (driver?: Driver) => {
    setDriverForm(driver || { driverName: "", licenseNumber: "", contactNumber: "", status: "AVAILABLE" });
    setShowDriverModal(true);
  };

  return (
    <div className="max-w-7xl mx-auto space-y-6">
      {/* Top Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-[var(--ciao-border)]">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-[var(--ciao-gold)] mb-1">
            <ShieldCheck className="w-4 h-4" /> Asset Management
          </div>
          <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-[#193542]">
            Bus & Driver Records Management
          </h1>
          <p className="text-xs sm:text-sm text-[var(--ciao-muted)] mt-1">
            Manage bus records, driver licenses, and availability.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Button
            onClick={() => openBusModal()}
            variant="gold"
            className="text-xs h-9 font-semibold flex items-center gap-1.5"
          >
            <Plus className="w-4 h-4" /> Add Bus
          </Button>
          <Button
            onClick={() => openDriverModal()}
            variant="outline"
            className="text-xs h-9 font-semibold flex items-center gap-1.5"
          >
            <UserPlus className="w-4 h-4" /> Register Driver
          </Button>
        </div>
      </div>

      {errorMsg && (
        <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 flex items-start gap-3">
          <AlertCircle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />
          <p className="text-rose-700 text-sm">{errorMsg}</p>
        </div>
      )}
      {notice && <div role="status" className="rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">{notice}</div>}

      {/* KPI Cards */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <Card className="p-5 bg-[var(--ciao-surface)] border-[var(--ciao-border)]">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)]">
              Active Buses
            </span>
            <div className="w-8 h-8 rounded-lg bg-emerald-500/10 border border-emerald-500/30 flex items-center justify-center text-emerald-400">
              <BusIcon className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-bold text-[#193542] mt-2">
            {buses.filter((b) => b.status === "ACTIVE").length}
          </div>
        </Card>

        <Card className="p-5 bg-[var(--ciao-surface)] border-[var(--ciao-border)]">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)]">
              Available Drivers
            </span>
            <div className="w-8 h-8 rounded-lg bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
              <Users className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-bold text-[#193542] mt-2">
            {drivers.filter((d) => d.status === "AVAILABLE").length}
          </div>
        </Card>

        <Card className="p-5 bg-[var(--ciao-surface)] border-[var(--ciao-border)]">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)]">
              In Maintenance
            </span>
            <div className="w-8 h-8 rounded-lg bg-amber-500/10 border border-amber-500/30 flex items-center justify-center text-amber-400">
              <Wrench className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-bold text-amber-700 mt-2">
            {buses.filter((b) => b.status === "MAINTENANCE").length}
          </div>
        </Card>

        <Card className="p-5 bg-[var(--ciao-surface)] border-[var(--ciao-border)]">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)]">
              Drivers On Duty
            </span>
            <div className="w-8 h-8 rounded-lg bg-[var(--ciao-gold)]/10 border border-[var(--ciao-gold)]/30 flex items-center justify-center text-[var(--ciao-gold)]">
              <UserCheck className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-bold text-[var(--ciao-gold)] mt-2">
            {drivers.filter((d) => d.status === "ON_DUTY").length}
          </div>
        </Card>
      </div>

      {/* Tabs and Search */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-[var(--ciao-border)] pb-3">
        <div className="flex items-center gap-2">
          <button
            onClick={() => setActiveTab("buses")}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold tracking-wide transition-all ${
              activeTab === "buses"
                ? "bg-[var(--ciao-gold)] text-white font-bold shadow-md"
                : "text-[var(--ciao-muted)] hover:text-[#193542] hover:bg-white/5"
            }`}
          >
            <BusIcon className="w-4 h-4" /> Buses ({buses.length})
          </button>
          <button
            onClick={() => setActiveTab("drivers")}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold tracking-wide transition-all ${
              activeTab === "drivers"
                ? "bg-[var(--ciao-gold)] text-white font-bold shadow-md"
                : "text-[var(--ciao-muted)] hover:text-[#193542] hover:bg-white/5"
            }`}
          >
            <Users className="w-4 h-4" /> Drivers ({drivers.length})
          </button>
        </div>

        <div className="relative w-full md:w-80">
          <Input
            type="text"
            placeholder={activeTab === "buses" ? "Search buses by plate, status..." : "Search drivers by name, license..."}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="text-xs pl-9 pr-8 bg-white border-[var(--ciao-border)] focus:border-[var(--ciao-gold)] rounded-xl"
          />
          <Search className="w-4 h-4 text-[var(--ciao-muted)] absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
          {searchQuery && (
            <button
              type="button"
              onClick={() => setSearchQuery("")}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 text-xs text-[var(--ciao-muted)] hover:text-[#193542] p-1"
              title="Clear search"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      </div>

      {/* Table Content */}
      {loading ? (
        <div className="py-20 flex flex-col items-center justify-center gap-3 text-[var(--ciao-muted)]">
          <Loader2 className="w-8 h-8 text-[var(--ciao-gold)] animate-spin" />
          <p className="text-xs">Loading buses and drivers...</p>
        </div>
      ) : (
        <Card className="bg-[var(--ciao-surface)] border-[var(--ciao-border)] shadow-xl overflow-hidden">
          {activeTab === "buses" && (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-[rgba(25,53,66,0.12)] bg-[#EAF3F0] text-[#516A74] uppercase tracking-wider font-semibold text-xs">
                    <th className="p-4">Registration Number</th>
                    <th className="p-4">Capacity</th>
                    <th className="p-4">Cabin Amenities</th>
                    <th className="p-4">Bus Status</th>
                    <th className="p-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[rgba(203,213,225,0.10)] bg-[#FFFFFF]">
                  {filteredBuses.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="p-8 text-center text-sm text-[#5E7480]">
                        {searchQuery.trim() ? `No buses match "${searchQuery}".` : "No buses registered in the fleet."}
                      </td>
                    </tr>
                  ) : (
                    filteredBuses.map((bus) => (
                      <tr key={bus.id} className="hover:bg-[#E2EFEB] transition-colors">
                        <td className="p-4 font-mono font-bold text-[#193542] text-sm">
                          {bus.plateNumber}
                        </td>
                        <td className="p-4 text-[#193542] font-medium">
                          {bus.capacity} Luxury Seats
                        </td>
                        <td className="p-4 text-[#516A74] max-w-xs truncate">
                          {bus.amenities || "Standard Luxury Suite"}
                        </td>
                        <td className="p-4">
                          <Badge
                            variant={
                              bus.status === "ACTIVE"
                                ? "success"
                                : bus.status === "MAINTENANCE"
                                ? "warning"
                                : "destructive"
                            }
                            className="text-xs uppercase tracking-wider font-semibold"
                          >
                            {bus.status}
                          </Badge>
                        </td>
                        <td className="p-4 text-right">
                          <div className="inline-flex items-center gap-2">
                            <button
                              onClick={() => openBusModal(bus)}
                              className="p-1.5 rounded-lg text-[#087478] hover:bg-[#087478]/15 transition-colors"
                              title="Edit bus details"
                            >
                              <Edit2 className="w-4 h-4" />
                            </button>
                              <button
                                onClick={() => setDeleteTarget({ kind: "buses", id: bus.id, label: `Bus ${bus.plateNumber}` })}
                                className="p-1.5 rounded-lg text-rose-700 hover:bg-rose-50 transition-colors"
                                title="Permanently delete bus"
                                aria-label={`Permanently delete bus ${bus.plateNumber}`}
                              >
                                <Trash2 className="w-4 h-4" />
                              </button>
                          </div>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          )}

          {activeTab === "drivers" && (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-[rgba(25,53,66,0.12)] bg-[#EAF3F0] text-[#516A74] uppercase tracking-wider font-semibold text-xs">
                    <th className="p-4">Driver Name</th>
                    <th className="p-4">License Number</th>
                    <th className="p-4">Contact Phone</th>
                    <th className="p-4">Duty Status</th>
                    <th className="p-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[rgba(203,213,225,0.10)] bg-[#FFFFFF]">
                  {filteredDrivers.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="p-8 text-center text-sm text-[#5E7480]">
                        {searchQuery.trim() ? `No drivers match "${searchQuery}".` : "No drivers recorded."}
                      </td>
                    </tr>
                  ) : (
                    filteredDrivers.map((driver) => (
                      <tr key={driver.id} className="hover:bg-[#E2EFEB] transition-colors">
                        <td className="p-4 font-semibold text-[#193542]">
                          {driver.driverName}
                        </td>
                        <td className="p-4 font-mono text-[#516A74]">
                          {driver.licenseNumber}
                        </td>
                        <td className="p-4 text-[#193542]">
                          {driver.contactNumber}
                        </td>
                        <td className="p-4">
                          <Badge
                            variant={
                              driver.status === "AVAILABLE"
                                ? "success"
                                : driver.status === "ON_DUTY"
                                ? "gold"
                                : driver.status === "ON_LEAVE"
                                ? "warning"
                                : "destructive"
                            }
                            className="text-xs uppercase tracking-wider font-semibold"
                          >
                            {driver.status.replaceAll("_", " ")}
                          </Badge>
                        </td>
                        <td className="p-4 text-right">
                          <div className="inline-flex items-center gap-2">
                            <button
                              onClick={() => openDriverModal(driver)}
                              className="p-1.5 rounded-lg text-[#087478] hover:bg-[#087478]/15 transition-colors"
                              title="Edit driver profile"
                            >
                              <Edit2 className="w-4 h-4" />
                            </button>
                              <button
                                onClick={() => setDeleteTarget({ kind: "drivers", id: driver.id, label: `Driver ${driver.driverName}` })}
                                className="p-1.5 rounded-lg text-rose-700 hover:bg-rose-50 transition-colors"
                                title="Permanently delete driver"
                                aria-label={`Permanently delete driver ${driver.driverName}`}
                              >
                                <Trash2 className="w-4 h-4" />
                              </button>
                          </div>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      )}



      {/* Bus Modal */}
      {showBusModal && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <Card className="w-full max-w-md bg-[var(--ciao-surface)] border-[var(--ciao-border)] p-6 shadow-2xl relative">
            <button
              onClick={() => setShowBusModal(false)}
              className="absolute right-4 top-4 text-[var(--ciao-muted)] hover:text-[#193542]"
            >
              <X className="w-5 h-5" />
            </button>
            <h3 className="text-base font-bold text-[#193542] mb-4 flex items-center gap-2">
              <BusIcon className="w-4 h-4 text-[var(--ciao-gold)]" />
              {busForm.id ? "Edit Bus Details" : "Add Bus"}
            </h3>
            <form onSubmit={handleSaveBus} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                  Plate Registration Number
                </label>
                <Input
                  required
                  placeholder="e.g. ND-4567"
                  value={busForm.plateNumber || ""}
                  onChange={(e) => setBusForm({ ...busForm, plateNumber: e.target.value })}
                />
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                  Seating Capacity
                </label>
                <Input
                  required
                  type="number"
                  min={10}
                  max={60}
                  value={busForm.capacity || 40}
                  onChange={(e) => setBusForm({ ...busForm, capacity: parseInt(e.target.value) })}
                />
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                  Cabin Amenities & Features
                </label>
                <Input
                  placeholder="e.g. Reclining Leather, USB Fast-Charge, AC, Wi-Fi"
                  value={busForm.amenities || ""}
                  onChange={(e) => setBusForm({ ...busForm, amenities: e.target.value })}
                />
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                  Operational Status
                </label>
                <SearchableSelect
                  className="w-full rounded-lg bg-[var(--ciao-bg)] text-[#193542] border border-[var(--ciao-border)] px-3 py-2.5 text-xs focus:border-[var(--ciao-gold)] focus:outline-none"
                  value={busForm.status || "ACTIVE"}
                  onChange={(e) => setBusForm({ ...busForm, status: e.target.value as Bus["status"] })}
                >
                  <option value="ACTIVE">ACTIVE</option>
                  <option value="MAINTENANCE">MAINTENANCE</option>
                  <option value="RETIRED">RETIRED</option>
                </SearchableSelect>
              </div>
              <div className="flex gap-3 justify-end pt-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setShowBusModal(false)}
                >
                  Cancel
                </Button>
                <Button type="submit" variant="gold" size="sm">
                  Save Bus
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}

      {/* Driver Modal */}
      {showDriverModal && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <Card className="w-full max-w-md bg-[var(--ciao-surface)] border-[var(--ciao-border)] p-6 shadow-2xl relative">
            <button
              onClick={() => setShowDriverModal(false)}
              className="absolute right-4 top-4 text-[var(--ciao-muted)] hover:text-[#193542]"
            >
              <X className="w-5 h-5" />
            </button>
            <h3 className="text-base font-bold text-[#193542] mb-4 flex items-center gap-2">
              <Users className="w-4 h-4 text-[var(--ciao-gold)]" />
              {driverForm.id ? "Edit Driver Profile" : "Register Pilot Driver"}
            </h3>
            <form onSubmit={handleSaveDriver} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                  Driver Full Name
                </label>
                <Input
                  required
                  placeholder="e.g. Kamal Perera"
                  value={driverForm.driverName || ""}
                  onChange={(e) => setDriverForm({ ...driverForm, driverName: e.target.value })}
                />
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                  Commercial License ID
                </label>
                <Input
                  required
                  placeholder="e.g. B-98765432"
                  value={driverForm.licenseNumber || ""}
                  onChange={(e) => setDriverForm({ ...driverForm, licenseNumber: e.target.value })}
                />
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                  Mobile Contact Hotline
                </label>
                <Input
                  required
                  placeholder="e.g. 0771234567"
                  value={driverForm.contactNumber || ""}
                  onChange={(e) => setDriverForm({ ...driverForm, contactNumber: e.target.value })}
                />
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                  Duty Status
                </label>
                <SearchableSelect
                  className="w-full rounded-lg bg-[var(--ciao-bg)] text-[#193542] border border-[var(--ciao-border)] px-3 py-2.5 text-xs focus:border-[var(--ciao-gold)] focus:outline-none"
                  value={driverForm.status || "AVAILABLE"}
                  onChange={(e) => setDriverForm({ ...driverForm, status: e.target.value as Driver["status"] })}
                >
                  <option value="AVAILABLE">AVAILABLE</option>
                  <option value="ON_DUTY">ON_DUTY</option>
                  <option value="ON_LEAVE">ON_LEAVE</option>
                  <option value="RETIRED">RETIRED</option>
                </SearchableSelect>
              </div>
              <div className="flex gap-3 justify-end pt-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setShowDriverModal(false)}
                >
                  Cancel
                </Button>
                <Button type="submit" variant="gold" size="sm">
                  Commit Driver
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}
      <DeleteRecordDialog open={deleteTarget !== null} record={deleteTarget?.label || ""} busy={deleting} onCancel={() => setDeleteTarget(null)} onConfirm={() => void deleteRecord()} />
    </div>
  );
}
