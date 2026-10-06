"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { apiRequest, ApiError, SessionUser, cacheUser } from "@/lib/api";
import {
  Menu,
  X,
  User,
  Bell,
  LogOut,
  Ticket,
  ShieldAlert,
  ChevronRight,
  ShieldCheck,
} from "lucide-react";
import { Button } from "@/components/ui/button";

export default function Header() {
  const pathname = usePathname();
  const router = useRouter();
  const [currentUser, setCurrentUser] = useState<SessionUser | null>(null);
  const [authVersion, setAuthVersion] = useState(0);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [authError, setAuthError] = useState("");
  const [loggingOut, setLoggingOut] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);

  useEffect(() => {
    if (!currentUser) {
      setUnreadCount(0);
      return;
    }
    let active = true;
    const checkNotifications = async () => {
      try {
        const res = await fetch("/api/eer/notifications", { credentials: "include" });
        if (res.ok && active) {
          const data = await res.json();
          if (Array.isArray(data)) {
            const count = data.filter((n: { readStatus?: boolean; readAt?: string | null }) => !n.readStatus && !n.readAt).length;
            setUnreadCount(count);
          }
        }
      } catch {
        // ignore network error
      }
    };

    void checkNotifications();
    const interval = setInterval(checkNotifications, 10000);
    return () => {
      active = false;
      clearInterval(interval);
    };
  }, [currentUser, authVersion, pathname]);

  useEffect(() => {
    const handleAuthChange = () => setAuthVersion((v) => v + 1);
    window.addEventListener("ciao_auth_change", handleAuthChange);
    window.addEventListener("ciao_notification_change", handleAuthChange);
    return () => {
      window.removeEventListener("ciao_auth_change", handleAuthChange);
      window.removeEventListener("ciao_notification_change", handleAuthChange);
    };
  }, []);

  useEffect(() => {
    let active = true;
    apiRequest<SessionUser>("/api/auth/me", {
      headers: { "Cache-Control": "no-cache" },
    })
      .then((user) => {
        if (!active) return;
        cacheUser(user);
        setCurrentUser(user);
        setAuthError("");
      })
      .catch((err) => {
        if (!active) return;
        if (err instanceof ApiError && err.status === 401) {
          cacheUser(null);
          setCurrentUser(null);
          setAuthError("");
        } else {
          setAuthError("Session could not be checked. Please refresh to retry.");
        }
      });
    return () => {
      active = false;
    };
  }, [authVersion, pathname]);

  useEffect(() => {
    const timer = setTimeout(() => setMobileMenuOpen(false), 0);
    return () => clearTimeout(timer);
  }, [pathname]);

  const handleLogout = async () => {
    if (loggingOut) return;
    setLoggingOut(true);
    setAuthError("");
    try {
      await apiRequest("/api/auth/logout", { method: "POST" });
      cacheUser(null);
      setCurrentUser(null);
      window.dispatchEvent(new Event("ciao_auth_change"));
      router.replace("/login");
    } catch {
      setAuthError("Sign out failed. Please try again.");
    } finally {
      setLoggingOut(false);
    }
  };

  const navLinks = [
    { label: "Bus Tickets", href: "/" },
    { label: "Track Parcel", href: "/track-parcel" },
    { label: "Group Booking", href: "/group-booking" },
    { label: "Lost & Found", href: "/report-lost" },
  ];

  return (
    <header className="sticky top-0 z-50 w-full ciao-glass-header transition-all">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-20 flex items-center justify-between">
        {/* Brand Logo */}
        <Link href="/" aria-label="Ciao home" className="flex items-center gap-2.5 group">
          <span className="text-[2rem] font-extrabold tracking-[-.08em] leading-none text-[#193542] transition-transform group-hover:-translate-y-0.5">ciao<span className="text-[#087478]">.</span></span>
        </Link>

        {/* Desktop Navigation */}
        <nav className="hidden min-[1200px]:flex items-center gap-6">
          {navLinks.map((link) => {
            const isActive = pathname === link.href;
            return (
              <Link
                key={link.href}
                href={link.href}
                aria-current={isActive ? "page" : undefined}
                className={`ciao-nav-link text-[15px] font-semibold tracking-wide transition-colors ${
                  isActive
                    ? "text-[#087478]"
                    : "text-[#516A74] hover:text-[#193542]"
                }`}
              >
                {link.label}
              </Link>
            );
          })}
        </nav>

        {/* Right Action / Auth Panel */}
        <div className="hidden min-[1200px]:flex items-center gap-3">
          {currentUser ? (
            <div className="flex items-center gap-3">
              <Link
                href="/my-bookings"
                className="flex items-center gap-1.5 text-sm font-semibold text-[#516A74] hover:text-[#087478] transition-colors py-2 px-3 rounded-lg hover:bg-[#EAF3F0]"
              >
                <Ticket className="w-4 h-4 text-[#087478]" />
                <span>My Bookings</span>
              </Link>
              {currentUser.roles.some((r) => r === "ROLE_ADMIN" || r === "ROLE_STAFF") && (
                <Link
                  href="/management"
                  className="flex items-center gap-1.5 text-sm font-semibold text-[#087478] bg-[#087478]/15 border border-[#087478]/35 py-2 px-3 rounded-xl hover:bg-[#087478]/25 transition-colors"
                >
                  <ShieldCheck className="w-4 h-4 text-[#087478]" />
                  <span>Staff Management</span>
                </Link>
              )}
              <Link
                href="/profile?tab=notifications"
                title="Notifications"
                className="relative flex items-center justify-center w-9 h-9 rounded-xl text-[#516A74] hover:text-[#087478] hover:bg-[#EAF3F0] transition-colors border border-transparent hover:border-[#087478]/30"
              >
                <Bell className="w-4 h-4" />
                {unreadCount > 0 && (
                  <span className="absolute -top-1 -right-1 flex h-4 min-w-[16px] items-center justify-center rounded-full bg-rose-500 px-1 text-[10px] font-extrabold text-white shadow-sm ring-2 ring-white">
                    {unreadCount > 9 ? "9+" : unreadCount}
                  </span>
                )}
              </Link>
              <Link
                href="/profile"
                className="flex items-center gap-1.5 text-sm font-semibold text-[#193542] bg-[#EAF3F0] border border-[rgba(203,213,225,0.16)] hover:border-[#087478]/50 transition-colors py-2 px-3.5 rounded-xl"
              >
                <User className="w-4 h-4 text-[#087478]" />
                <span className="max-w-[120px] truncate">{currentUser.fullName}</span>
              </Link>
              <Button
                variant="ghost"
                size="sm"
                onClick={handleLogout}
                disabled={loggingOut}
                className="text-xs h-9 font-medium text-[#5E7480] hover:text-[#EF4444]"
              >
                <LogOut className="w-4 h-4" />
              </Button>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <Link href="/login">
                <Button variant="ghost" size="sm" className="text-sm font-semibold text-[#516A74] hover:text-[#193542]">
                  Sign In
                </Button>
              </Link>
              <Link href="/register">
                <Button variant="gold" size="sm" className="text-sm font-bold text-white">
                  Create Account
                </Button>
              </Link>
            </div>
          )}
        </div>

        {/* Mobile Menu Button */}
        <div className="min-[1200px]:hidden flex items-center">
          <button
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            className="p-2 rounded-xl text-[#193542] hover:bg-[#EAF3F0] transition-colors"
            aria-label="Toggle menu"
          >
            {mobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
          </button>
        </div>
      </div>

      {/* Auth Error Banner */}
      {authError && (
        <div className="bg-[#EF4444]/15 border-b border-[#EF4444]/30 px-4 py-2 text-xs text-[#EF4444] flex items-center justify-center gap-2">
          <ShieldAlert className="w-4 h-4 shrink-0" />
          <span>{authError}</span>
        </div>
      )}

      {/* Mobile Drawer */}
      {mobileMenuOpen && (
        <div className="min-[1200px]:hidden border-b border-[rgba(203,213,225,0.12)] bg-[#FFFFFF] px-5 py-6 space-y-5 animate-fade-in shadow-2xl">
          <nav className="flex flex-col space-y-3">
            {navLinks.map((link) => (
              <Link
                key={link.href}
                href={link.href}
                onClick={() => setMobileMenuOpen(false)}
                className="flex items-center justify-between py-2 text-base font-semibold text-[#193542]"
              >
                <span>{link.label}</span>
                <ChevronRight className="w-4 h-4 text-[#5E7480]" />
              </Link>
            ))}
          </nav>

          <div className="pt-4 border-t border-[rgba(203,213,225,0.12)]">
            {currentUser ? (
              <div className="space-y-3">
                <div className="flex items-center gap-2.5 py-1">
                  <div className="w-9 h-9 rounded-full bg-[#087478]/20 text-[#087478] flex items-center justify-center font-bold text-sm border border-[#087478]/40">
                    {currentUser.fullName[0]}
                  </div>
                  <div>
                    <div className="text-sm font-bold text-[#193542]">{currentUser.fullName}</div>
                    <div className="text-xs text-[#516A74]">{currentUser.email}</div>
                  </div>
                </div>
                <div className="grid grid-cols-3 gap-2 pt-2">
                  <Link href="/my-bookings" onClick={() => setMobileMenuOpen(false)}>
                    <Button variant="outline" size="sm" className="w-full text-xs px-1">
                      My Bookings
                    </Button>
                  </Link>
                  <Link href="/profile?tab=notifications" onClick={() => setMobileMenuOpen(false)}>
                    <Button variant="outline" size="sm" className="w-full text-xs px-1 relative">
                      Alerts
                      {unreadCount > 0 && (
                        <span className="ml-1 bg-rose-500 text-white text-[10px] font-bold px-1.5 py-0.2 rounded-full">
                          {unreadCount}
                        </span>
                      )}
                    </Button>
                  </Link>
                  <Link href="/profile" onClick={() => setMobileMenuOpen(false)}>
                    <Button variant="outline" size="sm" className="w-full text-xs px-1">
                      Profile
                    </Button>
                  </Link>
                </div>
                {currentUser.roles.some((r) => r === "ROLE_ADMIN" || r === "ROLE_STAFF") && (
                  <Link href="/management" onClick={() => setMobileMenuOpen(false)}>
                    <Button variant="secondary" size="sm" className="w-full text-sm text-[#087478]">
                      Staff Management
                    </Button>
                  </Link>
                )}
                <Button
                  variant="destructive"
                  size="sm"
                  onClick={handleLogout}
                  className="w-full text-sm mt-2"
                >
                  Sign Out
                </Button>
              </div>
            ) : (
              <div className="grid grid-cols-2 gap-3">
                <Link href="/login" onClick={() => setMobileMenuOpen(false)}>
                  <Button variant="outline" className="w-full text-sm font-semibold">
                    Sign In
                  </Button>
                </Link>
                <Link href="/register" onClick={() => setMobileMenuOpen(false)}>
                  <Button variant="gold" className="w-full text-sm font-bold">
                    Register
                  </Button>
                </Link>
              </div>
            )}
          </div>
        </div>
      )}
    </header>
  );
}
