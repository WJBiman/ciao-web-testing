"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { apiRequest, SessionUser } from "@/lib/api";
import {
  Bus,
  Users,
  CalendarCheck,
  RotateCw,
  ArrowRight,
  Loader2,
  AlertCircle,
  Clock,
  MapPin,
  ShieldCheck
} from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";

type BusRecord = { id: number; status: string };
type Driver = { id: number };
type Schedule = {
  id: number;
  departureTime: string;
  status: string;
  route?: { origin: string; destination: string };
  bus?: { plateNumber: string };
  driver?: { driverName: string };
};
type Overview = { user: SessionUser; buses: BusRecord[]; drivers: Driver[]; schedules: Schedule[] };

export default function AdminDashboard() {
  const [data, setData] = useState<Overview | null>(null);
  const [error, setError] = useState("");
  const [refresh, setRefresh] = useState(0);

  useEffect(() => {
    let active = true;
    Promise.all([
      apiRequest<SessionUser>("/api/auth/me"),
      apiRequest<BusRecord[]>("/api/fleet/buses"),
      apiRequest<Driver[]>("/api/fleet/drivers"),
      apiRequest<Schedule[]>("/api/schedules"),
    ])
      .then(([user, buses, drivers, schedules]) => {
        if (active) setData({ user, buses, drivers, schedules });
      })
      .catch((e) => {
        if (active) setError(e instanceof Error ? e.message : "Unable to load dashboard.");
      });
    return () => {
      active = false;
    };
  }, [refresh]);

  const reload = () => {
    setError("");
    setData(null);
    setRefresh((value) => value + 1);
  };

  if (error) {
    return (
      <div className="p-8 max-w-xl mx-auto rounded-2xl bg-[var(--ciao-surface)] border border-rose-500/30 text-center space-y-4">
        <AlertCircle className="w-10 h-10 text-rose-400 mx-auto" />
        <h2 className="text-lg font-bold text-[#193542]">Dashboard Offline</h2>
        <p className="text-xs text-[var(--ciao-muted)]">{error}</p>
        <Button variant="gold" onClick={reload} className="text-xs">
          <RotateCw className="w-3.5 h-3.5 mr-1.5" /> Reconnect
        </Button>
      </div>
    );
  }

  if (!data) {
    return (
      <div className="py-24 flex flex-col items-center justify-center gap-3 text-[var(--ciao-muted)]">
        <Loader2 className="w-8 h-8 text-[var(--ciao-gold)] animate-spin" />
        <p className="text-xs">Loading buses and schedules...</p>
      </div>
    );
  }

  const activeBuses = data.buses.filter((bus) => bus.status === "ACTIVE").length;
  const activeSchedules = data.schedules.filter((s) => s.status === "SCHEDULED").length;

  return (
    <div className="max-w-7xl mx-auto space-y-6">
      {/* Header Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-[rgba(25,53,66,0.12)]">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-[#087478] mb-1">
            <ShieldCheck className="w-4 h-4" /> Operations Command
          </div>
          <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-[#193542]">
            Operations Dashboard
          </h1>
          <p className="text-xs sm:text-sm text-[#516A74] mt-1">
            Welcome back, <span className="text-[#193542] font-semibold">{data.user.fullName}</span>. Real-time telemetry across network assets.
          </p>
        </div>
        <Button
          onClick={reload}
          variant="outline"
          className="self-start sm:self-auto text-xs md:text-sm h-10 font-semibold flex items-center gap-1.5"
        >
          <RotateCw className="w-3.5 h-3.5" /> Refresh Schedules
        </Button>
      </div>

      {/* KPI Metrics */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card className="p-6 bg-[#FFFFFF] border-[rgba(25,53,66,0.12)] shadow-md">
          <div className="flex items-center justify-between">
            <span className="text-xs md:text-sm font-semibold uppercase tracking-wider text-[#5E7480]">
              Active Buses
            </span>
            <div className="w-10 h-10 rounded-xl bg-[#087478]/10 border border-[#087478]/30 flex items-center justify-center text-[#087478]">
              <Bus className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-extrabold text-[#193542] tracking-tight">{activeBuses}</span>
            <span className="text-xs md:text-sm text-[#5E7480]">of {data.buses.length} deployed</span>
          </div>
        </Card>

        <Card className="p-6 bg-[#FFFFFF] border-[rgba(25,53,66,0.12)] shadow-md">
          <div className="flex items-center justify-between">
            <span className="text-xs md:text-sm font-semibold uppercase tracking-wider text-[#5E7480]">
              Licensed Drivers
            </span>
            <div className="w-10 h-10 rounded-xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
              <Users className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-extrabold text-[#193542] tracking-tight">{data.drivers.length}</span>
            <span className="text-xs md:text-sm text-[#5E7480]">registered pilots</span>
          </div>
        </Card>

        <Card className="p-6 bg-[#FFFFFF] border-[rgba(25,53,66,0.12)] shadow-md">
          <div className="flex items-center justify-between">
            <span className="text-xs md:text-sm font-semibold uppercase tracking-wider text-[#5E7480]">
              Scheduled Departures
            </span>
            <div className="w-10 h-10 rounded-xl bg-emerald-500/10 border border-emerald-500/30 flex items-center justify-center text-emerald-400">
              <CalendarCheck className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-extrabold text-[#193542] tracking-tight">{activeSchedules}</span>
            <span className="text-xs md:text-sm text-[#5E7480]">of {data.schedules.length} in system</span>
          </div>
        </Card>
      </div>

      {/* Schedule Roster Table */}
      <Card className="bg-[#FFFFFF] border-[rgba(25,53,66,0.12)] shadow-xl overflow-hidden rounded-2xl">
        <div className="p-5 sm:p-6 border-b border-[rgba(25,53,66,0.12)] flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <h2 className="text-lg font-bold text-[#193542] tracking-tight">Active Bus Schedules</h2>
            <p className="text-xs md:text-sm text-[#516A74]">Scheduled bus departures in time order</p>
          </div>
          <Link
            href="/routes"
            className="inline-flex items-center gap-1.5 text-xs md:text-sm font-semibold text-[#087478] hover:underline"
          >
            Manage Timetables & Schedules <ArrowRight className="w-3.5 h-3.5" />
          </Link>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-[rgba(25,53,66,0.12)] bg-[#EAF3F0] text-[#516A74] uppercase tracking-wider font-semibold text-xs">
                <th className="p-4">Schedule #</th>
                <th className="p-4">Route</th>
                <th className="p-4">Departure Time</th>
                <th className="p-4">Bus</th>
                <th className="p-4">Driver</th>
                <th className="p-4">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[rgba(203,213,225,0.08)] bg-[#FFFFFF]">
              {data.schedules.length === 0 ? (
                <tr>
                  <td colSpan={6} className="p-8 text-center text-sm text-[#5E7480]">
                    No departure schedules recorded in the system.
                  </td>
                </tr>
              ) : (
                [...data.schedules]
                  .sort((a, b) => a.departureTime.localeCompare(b.departureTime))
                  .map((schedule) => {
                    const isScheduled = schedule.status === "SCHEDULED";
                    return (
                      <tr key={schedule.id} className="hover:bg-[#E2EFEB] transition-colors">
                        <td className="p-4 font-mono font-bold text-[#087478]">
                          SCH-{schedule.id}
                        </td>
                        <td className="p-4 font-medium text-[#193542]">
                          {schedule.route ? (
                            <span className="flex items-center gap-1.5">
                              <MapPin className="w-3.5 h-3.5 text-[#516A74] shrink-0" />
                              {schedule.route.origin} → {schedule.route.destination}
                            </span>
                          ) : (
                            <span className="text-[#5E7480] italic">Unassigned Route</span>
                          )}
                        </td>
                        <td className="p-4 text-[#516A74] whitespace-nowrap">
                          <span className="flex items-center gap-1.5">
                            <Clock className="w-3.5 h-3.5 text-[#5E7480] shrink-0" />
                            {new Date(schedule.departureTime).toLocaleString("en-GB", {
                              day: "2-digit",
                              month: "short",
                              year: "numeric",
                              hour: "2-digit",
                              minute: "2-digit",
                            })}
                          </span>
                        </td>
                        <td className="p-4 font-mono text-[#193542]">
                          {schedule.bus?.plateNumber || (
                            <span className="text-[#5E7480] italic font-sans">Pending</span>
                          )}
                        </td>
                        <td className="p-4 text-[#193542]">
                          {schedule.driver?.driverName || (
                            <span className="text-[#5E7480] italic">Pending</span>
                          )}
                        </td>
                        <td className="p-4">
                          <Badge variant={isScheduled ? "success" : "secondary"} className="text-xs uppercase tracking-wider">
                            {schedule.status}
                          </Badge>
                        </td>
                      </tr>
                    );
                  })
              )}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}
