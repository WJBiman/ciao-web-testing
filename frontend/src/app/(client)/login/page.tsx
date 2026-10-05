"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Image from "next/image";
import Link from "next/link";
import { apiRequest, ApiError, SessionUser, cacheUser } from "@/lib/api";
import {
  Lock,
  Mail,
  User,
  ArrowRight,
  Loader2,
  AlertCircle,
  Building2,
  Sparkles,
  Eye,
  EyeOff,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

export default function LoginPage() {
  const router = useRouter();
  const [roleMode, setRoleMode] = useState<"passenger" | "staff">("passenger");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");


  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError("");

    try {
      await apiRequest<SessionUser>("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: email.trim(), password }),
      });

      const user = await apiRequest<SessionUser>("/api/auth/me");
      cacheUser(user);
      window.dispatchEvent(new Event("ciao_auth_change"));

      if (user.roles.some((r) => r === "ROLE_ADMIN" || r === "ROLE_STAFF")) {
        router.push("/management");
      } else {
        router.push("/my-bookings");
      }
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.message || "Invalid credentials. Please verify and try again.");
      } else {
        setError("Network error. Could not connect to the Ciao service.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[var(--ciao-bg)] flex items-center justify-center p-4 sm:p-8">
      <div className="grid w-full max-w-4xl grid-cols-1 overflow-hidden rounded-3xl border border-[var(--ciao-border)] bg-[var(--ciao-surface)] shadow-2xl md:min-h-[36rem] md:grid-cols-2">
        {/* Left Side: Complementary Travel Photography */}
        <div className="relative hidden md:block bg-[var(--ciao-surface-elevated)] overflow-hidden">
          <Image
            src="/images/login-terminal.png"
            alt="Traveler approaching an intercity coach at a bus terminal"
            fill
            priority
            className="object-cover object-center animate-hero-img"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-[#12333d]/80 via-[#12333d]/15 to-transparent" />
          
          <div className="absolute bottom-8 left-8 right-8 text-white space-y-2">
            <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[var(--ciao-surface)]/80 backdrop-blur-md border border-[var(--ciao-border)] text-xs font-semibold">
              <Sparkles className="w-3.5 h-3.5 text-[var(--ciao-gold)]" />
              <span className="text-[var(--ciao-text-primary)]">Travel with Ciao</span>
            </div>
            <h2 className="text-xl font-bold tracking-tight leading-snug !text-white">
              Every Journey, Perfectly Planned.
            </h2>
            <p className="text-xs text-white/85 leading-relaxed font-normal">
              Log in to manage bookings, access e-tickets, or track your parcels.
            </p>
          </div>
        </div>

        {/* Right Side: Clean Login Form */}
        <div className="flex min-w-0 flex-col justify-between bg-[var(--ciao-surface)] p-8 sm:p-12">
          <div>
            {/* Brand Logo & Header */}
            <div className="mb-8">
              <Link href="/" className="inline-block mb-4">
                <span className="text-3xl font-extrabold tracking-[-.08em] text-[#193542]">ciao<span className="text-[#087478]">.</span></span>
              </Link>
              <h1 className="text-2xl font-extrabold text-[var(--ciao-text-primary)] tracking-tight">
                Welcome Back
              </h1>
              <p className="text-xs text-[var(--ciao-text-secondary)] mt-1">
                Enter your credentials to access your account portal.
              </p>
            </div>

            {/* Role Switcher Pill */}
            <div className="relative mb-6 grid grid-cols-2 rounded-xl border border-[var(--ciao-border)] bg-[var(--ciao-bg)] p-1">
              <span
                aria-hidden="true"
                className={`pointer-events-none absolute inset-y-1 left-1 w-[calc(50%-0.25rem)] rounded-lg border border-[var(--ciao-border)] bg-[var(--ciao-surface-elevated)] shadow-sm transition-transform duration-500 ease-[cubic-bezier(0.22,1,0.36,1)] ${roleMode === "staff" ? "translate-x-full" : "translate-x-0"}`}
              />
              <button
                type="button"
                aria-pressed={roleMode === "passenger"}
                onClick={() => {
                  setRoleMode("passenger");
                  setError("");
                }}
                className={`relative z-10 flex min-h-9 min-w-0 items-center justify-center gap-1.5 rounded-lg px-2 text-xs font-bold transition-colors duration-300 cursor-pointer sm:text-sm ${
                  roleMode === "passenger"
                    ? "text-[var(--ciao-gold)]"
                    : "text-[var(--ciao-text-secondary)] hover:text-[#193542]"
                }`}
              >
                <User className="w-3.5 h-3.5" />
                <span>Passenger</span>
              </button>
              <button
                type="button"
                aria-pressed={roleMode === "staff"}
                onClick={() => {
                  setRoleMode("staff");
                  setError("");
                }}
                className={`relative z-10 flex min-h-9 min-w-0 items-center justify-center gap-1.5 rounded-lg px-2 text-xs font-bold transition-colors duration-300 cursor-pointer sm:text-sm ${
                  roleMode === "staff"
                    ? "text-[var(--ciao-gold)]"
                    : "text-[var(--ciao-text-secondary)] hover:text-[#193542]"
                }`}
              >
                <Building2 className="w-3.5 h-3.5" />
                <span>Operations Staff</span>
              </button>
            </div>

            <div key={roleMode} className={roleMode === "staff" ? "ciao-login-slide-in-right" : "ciao-login-slide-in-left"}>
            {/* Error Announcement */}
            {error && (
              <div role="alert" className="p-3.5 rounded-xl bg-red-50 border border-red-200 flex items-start gap-2.5 mb-5 text-xs text-red-700">
                <AlertCircle className="w-4 h-4 shrink-0 mt-0.5 text-red-400" />
                <span>{error}</span>
              </div>
            )}

            {/* Login Form */}
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-[var(--ciao-text-secondary)] mb-1.5">
                  Email Address
                </label>
                <div className="relative">
                  <Input
                    required
                    type="email"
                    placeholder={
                      roleMode === "staff"
                        ? "staff@ciao.lk"
                        : "passenger@example.com"
                    }
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    className="pl-10"
                  />
                  <Mail className="w-4 h-4 text-[var(--ciao-text-muted)] absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                </div>
              </div>

              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-[var(--ciao-text-secondary)] mb-1.5">
                  Account Password
                </label>
                <div className="relative">
                  <Input
                    required
                    type={showPassword ? "text" : "password"}
                    placeholder="••••••••"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="pl-10 pr-10"
                  />
                  <Lock className="w-4 h-4 text-[var(--ciao-text-muted)] absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--ciao-text-muted)] hover:text-[#193542] p-1"
                    title={showPassword ? "Hide password" : "Show password"}
                  >
                    {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              <Button
                type="submit"


                disabled={loading}
                variant="gold"
                className="w-full h-11 text-xs font-bold uppercase tracking-wider mt-2 flex items-center justify-center gap-2"
              >
                {loading ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" /> Authorizing...
                  </>
                ) : (
                  <>
                    Sign In to Portal <ArrowRight className="w-4 h-4" />
                  </>
                )}
              </Button>
            </form>
            </div>
          </div>

          {/* Footer note */}
          <div className="flex min-h-16 items-end justify-center pt-6 text-center text-xs text-[var(--ciao-text-secondary)]">
            {roleMode === "passenger" ? (
              <p>
                Don&apos;t have an account?{" "}
                <Link
                  href="/register"
                  className="font-bold text-[var(--ciao-gold)] hover:underline"
                >
                  Register here
                </Link>
              </p>
            ) : (
              <p className="text-xs text-[var(--ciao-text-muted)]">
                Staff portal protected by HttpOnly credentials & EER policy guards.
              </p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
