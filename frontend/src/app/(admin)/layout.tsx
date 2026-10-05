"use client";

import { apiRequest, ApiError, SessionUser } from "@/lib/api";
import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  LayoutDashboard,
  Bus,
  CalendarDays,
  Package,
  Users,
  Search,
  QrCode,
  SlidersHorizontal,
  LogOut,
  Shield,
  User,
  Loader2,
  AlertCircle,
  Menu,
  X
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

type Access = { staffType: string; permissions: string[] };

const pages = [
  { path: "/management", title: "System Management", icon: SlidersHorizontal, permission: null },
  { path: "/dashboard", title: "Operations Dashboard", icon: LayoutDashboard, permission: "buses" },
  { path: "/fleet", title: "Bus & Driver Records", icon: Bus, permission: "buses" },
  { path: "/routes", title: "Route Management", icon: CalendarDays, permission: "routes" },
  { path: "/parcels", title: "Parcel Booking & Tracking", icon: Package, permission: "parcels" },
  { path: "/group-bookings", title: "Group Booking Management", icon: Users, permission: "groups" },
  { path: "/lost-and-found", title: "Lost & Found Management", icon: Search, permission: "claims" },
  { path: "/conductor", title: "E-Ticket Check-in", icon: QrCode, permission: "checkin" },
];

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [access, setAccess] = useState<Access | null>(null);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  useEffect(() => {
    let active = true;
    apiRequest<SessionUser>("/api/auth/me")
      .then(async (user) => {
        if (!active) return;
        if (!user.roles.some((role) => role === "ROLE_ADMIN" || role === "ROLE_STAFF")) {
          router.replace("/");
          return;
        }
        const granted = await apiRequest<Access>("/api/eer/access");
        if (active) setAccess(granted);
      })
      .catch((e) => {
        if (!active) return;
        if (e instanceof ApiError && e.status === 401) router.replace("/login");
        else setError(e instanceof Error ? e.message : "Unable to check your session.");
      });
    return () => {
      active = false;
    };
  }, [router, retry]);

  const required = pages.find((page) => page.path === pathname)?.permission;
  const denied = !!access && !!required && !access.permissions.includes(required);

  useEffect(() => {
    if (denied) router.replace("/management");
  }, [denied, router]);

  if (error) {
    return (
      <div className="min-h-screen bg-[var(--ciao-bg)] text-[#193542] flex items-center justify-center p-6">
        <div className="max-w-md w-full p-8 rounded-2xl bg-[var(--ciao-surface)] border border-rose-500/30 text-center">
          <AlertCircle className="w-12 h-12 text-rose-400 mx-auto mb-4" />
          <h2 className="text-xl font-bold mb-2">Access Error</h2>
          <p className="text-sm text-[var(--ciao-muted)] mb-6">{error}</p>
          <Button
            variant="gold"
            onClick={() => {
              setError("");
              setRetry((value) => value + 1);
            }}
          >
            Retry Connection
          </Button>
        </div>
      </div>
    );
  }

  if (!access || denied) {
    return (
      <div className="min-h-screen bg-[var(--ciao-bg)] text-[#193542] flex flex-col items-center justify-center gap-3">
        <Loader2 className="w-8 h-8 text-[var(--ciao-gold)] animate-spin" />
        <p className="text-sm text-[var(--ciao-muted)]">Authorizing staff workspace...</p>
      </div>
    );
  }

  const allowedPages = pages.filter(
    (page) => !page.permission || access.permissions.includes(page.permission)
  );

  return (
    <div className="min-h-screen bg-[var(--ciao-bg)] text-[var(--ciao-text)] flex flex-col lg:flex-row">
      {/* Mobile Header Bar */}
      <div className="lg:hidden flex items-center justify-between p-4 bg-[var(--ciao-surface)] border-b border-[var(--ciao-border)] sticky top-0 z-40">
        <div className="flex items-center gap-3">
          <span className="text-2xl font-extrabold tracking-[-.08em] text-[#193542]">ciao<span className="text-[#087478]">.</span></span>
          <Badge variant="gold" className="text-xs uppercase tracking-wider px-2 py-0.5">Staff</Badge>
        </div>
        <button
          onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          className="p-2 text-[var(--ciao-muted)] hover:text-[#193542]"
          aria-label="Toggle Navigation"
        >
          {mobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
        </button>
      </div>

      {/* Admin Sidebar */}
      <aside
        className={`fixed lg:sticky top-0 left-0 h-screen w-64 bg-[#FFFFFF] border-r border-[rgba(203,213,225,0.12)] flex flex-col justify-between p-6 z-50 transition-transform duration-300 ${
          mobileMenuOpen ? "translate-x-0" : "-translate-x-full lg:translate-x-0"
        }`}
      >
        <div>
          {/* Brand & Role */}
          <div className="mb-6">
            <Link href="/management" className="block mb-4">
              <span className="text-4xl font-extrabold tracking-[-.08em] text-[#193542]">ciao<span className="text-[#087478]">.</span></span>
            </Link>
            <div className="p-3 rounded-xl bg-[#EAF3F0] border border-[rgba(203,213,225,0.12)] flex items-center gap-3">
              <div className="w-9 h-9 rounded-lg bg-[#087478]/15 border border-[#087478]/35 flex items-center justify-center text-[#087478] shrink-0">
                <Shield className="w-5 h-5" />
              </div>
              <div className="min-w-0">
                <div className="text-xs uppercase font-bold text-[#087478] tracking-wider">Access Tier</div>
                <div className="text-sm font-semibold leading-snug text-[#193542] break-words capitalize">
                  {access.staffType.toLowerCase().replaceAll("_", " ")}
                </div>
              </div>
            </div>
          </div>

          {/* Navigation Links */}
          <nav className="space-y-1">
            <p className="text-xs font-bold uppercase tracking-widest text-[#5E7480] px-3 mb-2">
              Operations Menu
            </p>
            {allowedPages.map((page) => {
              const Icon = page.icon;
              const isActive = pathname === page.path;
              return (
                <Link
                  key={page.path}
                  href={page.path}
                  onClick={() => setMobileMenuOpen(false)}
                  className={`group flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-semibold tracking-wide transition-all duration-200 hover:translate-x-0.5 ${
                    isActive
                      ? "bg-[#087478]/12 text-[#087478] border border-[#087478]/35 shadow-sm"
                      : "text-[#516A74] hover:text-[#193542] hover:bg-[#EAF3F0] border border-transparent"
                  }`}
                >
                  <Icon className={`w-4 h-4 shrink-0 transition-transform duration-200 group-hover:scale-110 ${isActive ? "text-[#087478]" : "text-[#516A74]"}`} />
                  <span>{page.title}</span>
                </Link>
              );
            })}
          </nav>
        </div>

        {/* Footer Actions */}
        <div className="pt-4 border-t border-[rgba(203,213,225,0.12)] space-y-2">
          <Link
            href="/profile"
            className="flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm font-medium text-[#516A74] hover:text-[#087478] hover:bg-[#EAF3F0] transition-colors"
          >
            <User className="w-4 h-4 text-[#516A74]" />
            <span>My Profile</span>
          </Link>
          <Link
            href="/"
            className="flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm font-medium text-[#516A74] hover:text-[#087478] hover:bg-[#EAF3F0] transition-colors"
          >
            <LogOut className="w-4 h-4 text-[#516A74]" />
            <span>Passenger Portal</span>
          </Link>
          <div className="text-xs text-[#5E7480] px-3">
            Ciao Bus Reservation System
          </div>
        </div>
      </aside>

      {/* Backdrop for mobile */}
      {mobileMenuOpen && (
        <div
          onClick={() => setMobileMenuOpen(false)}
          className="fixed inset-0 bg-black/60 z-40 lg:hidden"
        />
      )}

      {/* Main Content Pane */}
      <main className="flex-1 min-w-0 p-4 sm:p-6 lg:p-8 overflow-y-auto">
        {children}
      </main>
    </div>
  );
}
