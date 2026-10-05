"use client";

import { SearchableSelect } from "@/components/ui/searchable-select";
import { DeleteRecordDialog } from "@/components/ui/delete-record-dialog";

import { useState, useEffect, useCallback } from "react";
import { API_BASE_URL, apiRequest } from "@/lib/api";

import {
  MapPin,
  CalendarDays,
  Plus,
  Edit2,
  Trash2,
  Clock,
  Bus as Repeat,
  CheckCircle2,
  X,
  ShieldCheck,
  Search
} from "lucide-react";

import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";

interface RouteItem {
  id: number;
  origin: string;
  destination: string;
  baseFare: number | string;
  status: string;
}

interface ScheduleItem {
  id: number;
  routeId?: number;
  busId?: number;
  driverId?: number;
  route?: { id: number; origin: string; destination: string };
  bus?: { id: number; plateNumber: string };
  driver?: { id: number; driverName: string };
  departureTime: string;
  arrivalTime: string;
  status: string;
  repeatDaily?: boolean;
  repeatUntil?: string | null;
  recurrenceParentId?: number | null;
}

interface BusItem {
  id: number;
  plateNumber: string;
  status: string;
}

interface DriverItem {
  id: number;
  driverName: string;
  status: string;
}

export default function RoutesManagementPage() {
  const [activeTab, setActiveTab] = useState<"routes" | "schedules">("routes");
  const [routes, setRoutes] = useState<RouteItem[]>([]);
  const [schedules, setSchedules] = useState<ScheduleItem[]>([]);
  const [buses, setBuses] = useState<BusItem[]>([]);
  const [drivers, setDrivers] = useState<DriverItem[]>([]);

  // Modals state
  const [isRouteModalOpen, setIsRouteModalOpen] = useState(false);
  const [isScheduleModalOpen, setIsScheduleModalOpen] = useState(false);

  // Form states
  const [routeForm, setRouteForm] = useState<{
    id: number | null;
    origin: string;
    destination: string;
    baseFare: string | number;
    status: string;
  }>({ id: null, origin: "", destination: "", baseFare: "", status: "ACTIVE" });

  const [scheduleForm, setScheduleForm] = useState<{
    id: number | null;
    routeId: string;
    busId: string;
    driverId: string;
    departureTime: string;
    arrivalTime: string;
    status: string;
    repeatDaily: boolean;
    repeatUntil: string;
  }>({
    id: null,
    routeId: "",
    busId: "",
    driverId: "",
    departureTime: "",
    arrivalTime: "",
    status: "SCHEDULED",
    repeatDaily: false,
    repeatUntil: "",
  });

  const fetchRoutes = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/api/routes`, { credentials: "include" });
      if (res.ok) setRoutes(await res.json());
    } catch {
      console.error("Failed to fetch routes");
    }
  }, []);

  const fetchSchedules = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/api/schedules`, { credentials: "include" });
      if (res.ok) setSchedules(await res.json());
    } catch {
      console.error("Failed to fetch schedules");
    }
  }, []);

  const fetchBuses = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/api/fleet/buses`, { credentials: "include" });
      if (res.ok) setBuses(await res.json());
    } catch {
      console.error("Failed to fetch buses");
    }
  }, []);

  const fetchDrivers = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/api/fleet/drivers`, { credentials: "include" });
      if (res.ok) setDrivers(await res.json());
    } catch {
      console.error("Failed to fetch drivers");
    }
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => {
      void Promise.all([fetchRoutes(), fetchSchedules(), fetchBuses(), fetchDrivers()]).catch((err) =>
        console.error("Error in parallel fetch:", err)
      );
    }, 0);
    return () => clearTimeout(timer);
  }, [fetchRoutes, fetchSchedules, fetchBuses, fetchDrivers]);

  const [routeSearch, setRouteSearch] = useState("");
  const [routeError, setRouteError] = useState("");
  const [scheduleError, setScheduleError] = useState("");
  const [notice, setNotice] = useState("");
  const [deleteTarget, setDeleteTarget] = useState<{ kind: "routes" | "schedules"; id: number; label: string } | null>(null);
  const [deleting, setDeleting] = useState(false);

  const filteredRoutes = routes.filter((r) => {
    if (!routeSearch.trim()) return true;
    const query = routeSearch.toLowerCase().trim();
    const idStr = `rt-${r.id}`.toLowerCase();
    return (
      idStr.includes(query) ||
      String(r.id).includes(query) ||
      r.origin.toLowerCase().includes(query) ||
      r.destination.toLowerCase().includes(query)
    );
  });


  const toDateTimeLocal = (value: string | null | undefined) => (value ? value.slice(0, 16) : "");

  const saveRoute = async () => {
    setRouteError("");
    const url = routeForm.id ? `${API_BASE_URL}/api/routes/${routeForm.id}` : `${API_BASE_URL}/api/routes`;
    const method = routeForm.id ? "PUT" : "POST";
    const res = await fetch(url, {
      method,
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify(routeForm),
    });
    if (res.ok) {
      const saved = await res.json();
      fetchRoutes();
      setIsRouteModalOpen(false);
      if (!routeForm.id) {
        setNotice("Route created. Add a future departure in Schedules before passengers can book it.");
        setScheduleForm({
          id: null,
          routeId: String(saved.id),
          busId: "",
          driverId: "",
          departureTime: "",
          arrivalTime: "",
          status: "SCHEDULED",
          repeatDaily: false,
          repeatUntil: "",
        });
        setActiveTab("schedules");
        setIsScheduleModalOpen(true);
      }
    } else {
      const data = await res.json();
      setRouteError(data.message || "Failed to save route");
    }
  };

  const saveSchedule = async () => {
    setScheduleError("");
    const url = scheduleForm.id ? `/api/schedules/${scheduleForm.id}` : `/api/schedules`;
    const method = scheduleForm.id ? "PUT" : "POST";
    try {
      await apiRequest(url, {
        method,
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({
          ...scheduleForm,
          busId: scheduleForm.busId || null,
          driverId: scheduleForm.driverId || null,
          repeatUntil: scheduleForm.repeatUntil || null,
        }),
      });
      fetchSchedules();
      setIsScheduleModalOpen(false);
      setNotice(
        scheduleForm.repeatDaily
          ? "Daily service saved. Passengers can search future dates from the start date."
          : "Schedule saved. Passengers can search its travel date now."
      );
    } catch (error) {
      setScheduleError(error instanceof Error ? error.message : "Failed to save schedule");
    }
  };

  const deleteRecord = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    setRouteError("");
    setScheduleError("");
    try {
      await apiRequest(`/api/${deleteTarget.kind}/${deleteTarget.id}`, { method: "DELETE" });
      if (deleteTarget.kind === "routes") {
        await Promise.all([fetchRoutes(), fetchSchedules()]);
      } else {
        await fetchSchedules();
      }
      setNotice(`${deleteTarget.label} permanently deleted from the database.`);
      setDeleteTarget(null);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Delete failed.";
      if (deleteTarget.kind === "routes") setRouteError(message);
      else setScheduleError(message);
      setDeleteTarget(null);
    } finally {
      setDeleting(false);
    }
  };


  return (
    <div className="max-w-7xl mx-auto space-y-6">
      {/* Top Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-[var(--ciao-border)]">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-[var(--ciao-gold)] mb-1">
            <ShieldCheck className="w-4 h-4" /> Route Management
          </div>
          <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-[#193542]">
            Route Management & Bus Schedules
          </h1>
          <p className="text-xs sm:text-sm text-[var(--ciao-muted)] mt-1">
            Manage bus routes, base fares, and scheduled departures.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Button
            onClick={() => {
              setRouteForm({ id: null, origin: "", destination: "", baseFare: "", status: "ACTIVE" });
              setIsRouteModalOpen(true);
            }}
            variant="gold"
            className="text-xs h-9 font-semibold flex items-center gap-1.5"
          >
            <Plus className="w-4 h-4" /> Add Route
          </Button>
          <Button
            onClick={() => {
              setScheduleForm({
                id: null,
                routeId: "",
                busId: "",
                driverId: "",
                departureTime: "",
                arrivalTime: "",
                status: "SCHEDULED",
                repeatDaily: false,
                repeatUntil: "",
              });
              setIsScheduleModalOpen(true);
            }}
            variant="outline"
            className="text-xs h-9 font-semibold flex items-center gap-1.5"
          >
            <CalendarDays className="w-4 h-4" /> Add Departure Schedule
          </Button>
        </div>
      </div>

      {notice && (
        <div className="p-4 rounded-xl bg-[var(--ciao-gold)]/10 border border-[var(--ciao-gold)]/30 flex items-start gap-3">
          <CheckCircle2 className="w-5 h-5 text-[var(--ciao-gold)] shrink-0 mt-0.5" />
          <p className="text-[var(--ciao-gold)] text-sm">{notice}</p>
        </div>
      )}
      {(routeError || scheduleError) && !isRouteModalOpen && !isScheduleModalOpen && (
        <div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">{routeError || scheduleError}</div>
      )}

      {/* Tabs and Search */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-[var(--ciao-border)] pb-3">
        <div className="flex items-center gap-2">
          <button
            onClick={() => setActiveTab("routes")}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold tracking-wide transition-all ${
              activeTab === "routes"
                ? "bg-[var(--ciao-gold)] text-white font-bold shadow-md"
                : "text-[var(--ciao-muted)] hover:text-[#193542] hover:bg-white/5"
            }`}
          >
            <MapPin className="w-4 h-4" /> Routes ({routes.length})
          </button>
          <button
            onClick={() => setActiveTab("schedules")}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold tracking-wide transition-all ${
              activeTab === "schedules"
                ? "bg-[var(--ciao-gold)] text-white font-bold shadow-md"
                : "text-[var(--ciao-muted)] hover:text-[#193542] hover:bg-white/5"
            }`}
          >
            <CalendarDays className="w-4 h-4" /> Departure Schedules ({schedules.length})
          </button>
        </div>

        {activeTab === "routes" && (
          <div className="relative w-full md:w-80">
            <Input
              type="text"
              placeholder="Search routes by origin, destination, ID..."
              value={routeSearch}
              onChange={(e) => setRouteSearch(e.target.value)}
              className="text-xs pl-9 pr-8 bg-white border-[var(--ciao-border)] focus:border-[var(--ciao-gold)] rounded-xl"
            />
            <Search className="w-4 h-4 text-[var(--ciao-muted)] absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
            {routeSearch && (
              <button
                type="button"
                onClick={() => setRouteSearch("")}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-xs text-[var(--ciao-muted)] hover:text-[#193542] p-1"
                title="Clear search"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
        )}
      </div>

      {/* Table Content */}
      <Card className="bg-[var(--ciao-surface)] border-[var(--ciao-border)] shadow-xl overflow-hidden">
        {activeTab === "routes" && (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-[rgba(25,53,66,0.12)] bg-[#EAF3F0] text-[#516A74] uppercase tracking-wider font-semibold text-xs">
                  <th className="p-4">Route ID</th>
                  <th className="p-4">Origin Terminal</th>
                  <th className="p-4">Destination Terminal</th>
                  <th className="p-4">Base Fare</th>
                  <th className="p-4">Status</th>
                  <th className="p-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[rgba(203,213,225,0.10)] bg-[#FFFFFF]">
                {filteredRoutes.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="p-8 text-center text-sm text-[#5E7480]">
                      {routeSearch.trim()
                        ? `No routes match "${routeSearch}".`
                        : "No routes configured."}
                    </td>
                  </tr>
                ) : (
                  filteredRoutes.map((r) => (
                    <tr key={r.id} className="hover:bg-[#E2EFEB] transition-colors">
                      <td className="p-4 font-mono font-bold text-[#087478]">

                        RT-{r.id}
                      </td>
                      <td className="p-4 font-semibold text-[#193542]">
                        {r.origin}
                      </td>
                      <td className="p-4 font-semibold text-[#193542]">
                        {r.destination}
                      </td>
                      <td className="p-4 font-bold text-[#087478]">
                        LKR {Number(r.baseFare).toLocaleString()}
                      </td>
                      <td className="p-4">
                        <Badge
                          variant={r.status === "ACTIVE" ? "success" : "secondary"}
                          className="text-xs uppercase tracking-wider font-semibold"
                        >
                          {r.status}
                        </Badge>
                      </td>
                      <td className="p-4 text-right">
                        <div className="inline-flex items-center gap-2">
                          <button
                            onClick={() => {
                              setRouteForm(r);
                              setIsRouteModalOpen(true);
                            }}
                            className="p-1.5 rounded-lg text-[#087478] hover:bg-[#087478]/15 transition-colors"
                            title="Edit route"
                          >
                            <Edit2 className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => setDeleteTarget({ kind: "routes", id: r.id, label: `${r.origin} → ${r.destination} route #${r.id}` })}
                            className="p-1.5 rounded-lg text-rose-700 hover:bg-rose-50 transition-colors"
                            title="Permanently delete route"
                            aria-label={`Permanently delete route ${r.origin} to ${r.destination}`}
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

        {activeTab === "schedules" && (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-[rgba(25,53,66,0.12)] bg-[#EAF3F0] text-[#516A74] uppercase tracking-wider font-semibold text-xs">
                  <th className="p-4">Schedule #</th>
                  <th className="p-4">Route</th>
                  <th className="p-4">Departure & Arrival</th>
                  <th className="p-4">Bus</th>
                  <th className="p-4">Driver</th>
                  <th className="p-4">Status</th>
                  <th className="p-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[rgba(203,213,225,0.10)] bg-[#FFFFFF]">
                {schedules.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="p-8 text-center text-sm text-[#5E7480]">
                      No departure schedules registered.
                    </td>
                  </tr>
                ) : (
                  schedules.map((s) => (
                    <tr key={s.id} className="hover:bg-[#E2EFEB] transition-colors">
                      <td className="p-4 font-mono font-bold text-[#087478]">
                        SCH-{s.id}
                      </td>
                      <td className="p-4 font-medium text-[#193542]">
                        <span className="flex items-center gap-1.5">
                          <MapPin className="w-4 h-4 text-[#5E7480] shrink-0" />
                          {s.route?.origin} → {s.route?.destination}
                        </span>
                      </td>
                      <td className="p-4 text-[#516A74] whitespace-nowrap">
                        <div className="flex items-center gap-1.5 text-[#193542] font-medium">
                          <Clock className="w-4 h-4 text-[#087478] shrink-0" />
                          {new Date(s.departureTime).toLocaleString("en-GB", {
                            day: "2-digit",
                            month: "short",
                            hour: "2-digit",
                            minute: "2-digit",
                          })}
                        </div>
                        <div className="text-xs text-[#5E7480] pl-5 mt-0.5">
                          Arr:{" "}
                          {new Date(s.arrivalTime).toLocaleString("en-GB", {
                            day: "2-digit",
                            month: "short",
                            hour: "2-digit",
                            minute: "2-digit",
                          })}
                        </div>
                      </td>
                      <td className="p-4 font-mono text-[#193542]">
                        {s.bus?.plateNumber || <span className="text-[#5E7480] italic font-sans">Unassigned</span>}
                      </td>
                      <td className="p-4 text-[#193542]">
                        {s.driver?.driverName || <span className="text-[#5E7480] italic">Unassigned</span>}
                      </td>
                      <td className="p-4">
                        <div className="flex flex-col gap-1 items-start">
                          <Badge
                            variant={s.status === "SCHEDULED" ? "success" : "secondary"}
                            className="text-xs uppercase tracking-wider font-semibold"
                          >
                            {s.status}
                          </Badge>
                          {s.repeatDaily && (
                            <span className="inline-flex items-center gap-1 text-xs text-[#087478]">
                              <Repeat className="w-3.5 h-3.5" /> Daily recurrence
                            </span>
                          )}
                          {s.recurrenceParentId && (
                            <span className="text-xs text-[#5E7480]">
                              Parent #{s.recurrenceParentId}
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="p-4 text-right">
                        <div className="inline-flex items-center gap-2">
                          <button
                            onClick={() => {
                              setScheduleForm({
                                ...s,
                                routeId: s.route?.id ? String(s.route.id) : "",
                                busId: s.bus?.id ? String(s.bus.id) : "",
                                driverId: s.driver?.id ? String(s.driver.id) : "",
                                departureTime: toDateTimeLocal(s.departureTime),
                                arrivalTime: toDateTimeLocal(s.arrivalTime),
                                repeatDaily: Boolean(s.repeatDaily),
                                repeatUntil: s.repeatUntil || "",
                              });
                              setIsScheduleModalOpen(true);
                            }}
                            className="p-1.5 rounded-lg text-[#087478] hover:bg-[#087478]/15 transition-colors"
                            title="Edit schedule"
                          >
                            <Edit2 className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => setDeleteTarget({ kind: "schedules", id: s.id, label: `Schedule #${s.id}` })}
                            className="p-1.5 rounded-lg text-rose-700 hover:bg-rose-50 transition-colors"
                            title="Permanently delete schedule"
                            aria-label={`Permanently delete schedule ${s.id}`}
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



      {/* Route Modal */}
      {isRouteModalOpen && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <Card className="w-full max-w-md bg-[var(--ciao-surface)] border-[var(--ciao-border)] p-6 shadow-2xl relative">
            <button
              onClick={() => setIsRouteModalOpen(false)}
              className="absolute right-4 top-4 text-[var(--ciao-muted)] hover:text-[#193542]"
            >
              <X className="w-5 h-5" />
            </button>
            <h3 className="text-base font-bold text-[#193542] mb-4 flex items-center gap-2">
              <MapPin className="w-4 h-4 text-[var(--ciao-gold)]" />
              {routeForm.id ? "Edit Route" : "Add Route"}
            </h3>
            {routeError && (
              <div className="p-3 mb-4 text-sm bg-rose-500/10 border border-rose-500/30 text-rose-700 rounded-lg">
                {routeError}
              </div>
            )}
            <div className="space-y-4">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                  Origin Station
                </label>
                <Input
                  required
                  placeholder="e.g. Colombo Fort"
                  value={routeForm.origin}
                  onChange={(e) => setRouteForm({ ...routeForm, origin: e.target.value })}
                />
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                  Destination Station
                </label>
                <Input
                  required
                  placeholder="e.g. Kandy Central"
                  value={routeForm.destination}
                  onChange={(e) => setRouteForm({ ...routeForm, destination: e.target.value })}
                />
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                  Base Fare (LKR)
                </label>
                <Input
                  required
                  type="number"
                  placeholder="e.g. 1500"
                  value={routeForm.baseFare}
                  onChange={(e) => setRouteForm({ ...routeForm, baseFare: e.target.value })}
                />
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                  Status
                </label>
                <SearchableSelect
                  className="w-full rounded-lg bg-[var(--ciao-bg)] text-[#193542] border border-[var(--ciao-border)] px-3 py-2.5 text-xs focus:border-[var(--ciao-gold)] focus:outline-none"
                  value={routeForm.status}
                  onChange={(e) => setRouteForm({ ...routeForm, status: e.target.value })}
                >
                  <option value="ACTIVE">ACTIVE</option>
                  <option value="INACTIVE">INACTIVE</option>
                </SearchableSelect>
              </div>
              <div className="flex gap-3 justify-end pt-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setIsRouteModalOpen(false)}
                >
                  Cancel
                </Button>
                <Button type="button" onClick={saveRoute} variant="gold" size="sm">
                  Save Route
                </Button>
              </div>
            </div>
          </Card>
        </div>
      )}

      {/* Schedule Modal */}
      {isScheduleModalOpen && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <Card className="w-full max-w-lg bg-[var(--ciao-surface)] border-[var(--ciao-border)] p-6 shadow-2xl relative">
            <button
              onClick={() => setIsScheduleModalOpen(false)}
              className="absolute right-4 top-4 text-[var(--ciao-muted)] hover:text-[#193542]"
            >
              <X className="w-5 h-5" />
            </button>
            <h3 className="text-base font-bold text-[#193542] mb-2 flex items-center gap-2">
              <CalendarDays className="w-4 h-4 text-[var(--ciao-gold)]" />
              {scheduleForm.id ? "Edit Departure Schedule" : "Register Scheduled Departure"}
            </h3>
            <p className="text-xs text-[var(--ciao-muted)] mb-4">
              A departure schedule creates a bookable passage for passengers on public search.
            </p>

            {scheduleError && (
              <div className="p-3 mb-4 text-sm bg-rose-500/10 border border-rose-500/30 text-rose-700 rounded-lg">
                {scheduleError}
              </div>
            )}

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                  Route <span className="text-rose-400">*</span>
                </label>
                <SearchableSelect
                  className="w-full rounded-lg bg-[var(--ciao-bg)] text-[#193542] border border-[var(--ciao-border)] px-3 py-2.5 text-xs focus:border-[var(--ciao-gold)] focus:outline-none"
                  value={scheduleForm.routeId}
                  onChange={(e) => setScheduleForm({ ...scheduleForm, routeId: e.target.value })}
                >
                  <option value="">Select Route</option>
                  {routes
                    .filter((r) => r.status === "ACTIVE")
                    .map((r) => (
                      <option key={r.id} value={r.id}>
                        {r.origin} → {r.destination}
                      </option>
                    ))}
                </SearchableSelect>
              </div>

              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                  Dispatch Status
                </label>
                <SearchableSelect
                  className="w-full rounded-lg bg-[var(--ciao-bg)] text-[#193542] border border-[var(--ciao-border)] px-3 py-2.5 text-xs focus:border-[var(--ciao-gold)] focus:outline-none"
                  value={scheduleForm.status}
                  onChange={(e) => setScheduleForm({ ...scheduleForm, status: e.target.value })}
                >
                  <option value="SCHEDULED">SCHEDULED</option>
                  <option value="IN_TRANSIT">IN_TRANSIT</option>
                  <option value="COMPLETED">COMPLETED</option>
                  <option value="CANCELLED">CANCELLED</option>
                </SearchableSelect>
              </div>

              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                  Bus Assignment
                </label>
                <SearchableSelect
                  className="w-full rounded-lg bg-[var(--ciao-bg)] text-[#193542] border border-[var(--ciao-border)] px-3 py-2.5 text-xs focus:border-[var(--ciao-gold)] focus:outline-none"
                  value={scheduleForm.busId}
                  onChange={(e) => setScheduleForm({ ...scheduleForm, busId: e.target.value })}
                >
                  <option value="">Unassigned</option>
                  {buses
                    .filter((b) => b.status !== "RETIRED")
                    .map((b) => (
                      <option key={b.id} value={b.id}>
                        {b.plateNumber}
                      </option>
                    ))}
                </SearchableSelect>
              </div>

              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                  Driver Assignment
                </label>
                <SearchableSelect
                  className="w-full rounded-lg bg-[var(--ciao-bg)] text-[#193542] border border-[var(--ciao-border)] px-3 py-2.5 text-xs focus:border-[var(--ciao-gold)] focus:outline-none"
                  value={scheduleForm.driverId}
                  onChange={(e) => setScheduleForm({ ...scheduleForm, driverId: e.target.value })}
                >
                  <option value="">Unassigned</option>
                  {drivers
                    .filter((d) => d.status !== "RETIRED")
                    .map((d) => (
                      <option key={d.id} value={d.id}>
                        {d.driverName}
                      </option>
                    ))}
                </SearchableSelect>
              </div>

              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                  Departure Date & Time <span className="text-rose-400">*</span>
                </label>
                <Input
                  type="datetime-local"
                  min={new Date().toISOString().slice(0, 16)}
                  value={scheduleForm.departureTime}
                  onChange={(e) => setScheduleForm({ ...scheduleForm, departureTime: e.target.value })}
                />
              </div>

              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-[var(--ciao-muted)] mb-1.5">
                  Estimated Arrival Time <span className="text-rose-400">*</span>
                </label>
                <Input
                  type="datetime-local"
                  min={scheduleForm.departureTime || new Date().toISOString().slice(0, 16)}
                  value={scheduleForm.arrivalTime}
                  onChange={(e) => setScheduleForm({ ...scheduleForm, arrivalTime: e.target.value })}
                />
              </div>

              <div className="col-span-1 sm:col-span-2 p-3.5 rounded-xl bg-[var(--ciao-bg)] border border-[var(--ciao-border)]">
                <label className="flex items-center gap-2.5 text-xs font-semibold text-[#193542] cursor-pointer">
                  <input
                    type="checkbox"
                    checked={scheduleForm.repeatDaily}
                    onChange={(e) => setScheduleForm({ ...scheduleForm, repeatDaily: e.target.checked })}
                    className="accent-[var(--ciao-gold)] w-4 h-4 rounded"
                  />
                  <span>Recur this departure daily while route is operational</span>
                </label>
                <p className="text-xs text-[var(--ciao-muted)] mt-1.5">
                  Materializes recurring departures automatically for passenger search bookings.
                </p>
                {scheduleForm.repeatDaily && (
                  <div className="mt-3">
                    <label className="block text-xs font-semibold text-[var(--ciao-muted)] mb-1 uppercase tracking-wider">
                      Recurrence End Date (Optional)
                    </label>
                    <Input
                      type="date"
                      min={scheduleForm.departureTime.slice(0, 10)}
                      value={scheduleForm.repeatUntil}
                      onChange={(e) => setScheduleForm({ ...scheduleForm, repeatUntil: e.target.value })}
                    />
                  </div>
                )}
              </div>
            </div>

            <div className="flex gap-3 justify-end pt-5">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setIsScheduleModalOpen(false)}
              >
                Cancel
              </Button>
              <Button type="button" onClick={saveSchedule} variant="gold" size="sm">
                Save Departure
              </Button>
            </div>
          </Card>
        </div>
      )}
      <DeleteRecordDialog open={deleteTarget !== null} record={deleteTarget?.label || ""} busy={deleting} onCancel={() => setDeleteTarget(null)} onConfirm={() => void deleteRecord()} />
    </div>
  );
}
