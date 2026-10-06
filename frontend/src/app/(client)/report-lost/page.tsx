"use client";

import { SearchableSelect } from "@/components/ui/searchable-select";

import { useState, useEffect } from "react";
import Link from "next/link";
import { API_BASE_URL } from "@/lib/api";
import { 
  Search, 
  CheckCircle2, 
  AlertCircle, 
  Send, 
  Loader2, 
  Info, 
  ArrowLeft,
  ShieldAlert
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";

interface Bus {
  id: number;
  plateNumber: string;
  status: string;
}

interface Route {
  id: number;
  origin: string;
  destination: string;
}

export default function ReportLostPage() {
  const [buses, setBuses] = useState<Bus[]>([]);
  const [routes, setRoutes] = useState<Route[]>([]);
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState("");

  const [form, setForm] = useState({
    itemDescription: "",
    reportedByName: "",
    reportedByPhone: "",
    busId: "",
    routeId: "",
  });

  const [createdItemId, setCreatedItemId] = useState<number | null>(null);

  useEffect(() => {
    fetch(`${API_BASE_URL}/api/fleet/buses/active`)
      .then((r) => r.json())
      .then((data) => setBuses(Array.isArray(data) ? data : []))
      .catch(() => setBuses([]));

    fetch(`${API_BASE_URL}/api/routes`)
      .then((r) => r.json())
      .then((data) => setRoutes(Array.isArray(data) ? data : []))
      .catch(() => setRoutes([]));

    fetch(`${API_BASE_URL}/api/auth/me`, { credentials: "include" })
      .then((r) => (r.ok ? r.json() : null))
      .then((user) => {
        if (user) {
          setForm((prev) => ({
            ...prev,
            reportedByName: prev.reportedByName || user.fullName || "",
            reportedByPhone: prev.reportedByPhone || user.phone || "",
          }));
        }
      })
      .catch(() => {});
  }, []);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError("");
    setSuccess(false);
    setCreatedItemId(null);

    const payload: Record<string, string | number> = {
      itemDescription: form.itemDescription.trim(),
      reportedByName: form.reportedByName.trim(),
      reportedByPhone: form.reportedByPhone.trim(),
    };
    if (form.busId) payload.busId = parseInt(form.busId);
    if (form.routeId) payload.routeId = parseInt(form.routeId);

    try {
      const res = await fetch(`${API_BASE_URL}/api/lost-items`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify(payload),
      });

      const data = await res.json();

      if (!res.ok) {
        setError(data.message || "Submission failed. Please check your details.");
      } else {
        setSuccess(true);
        setCreatedItemId(data.id || null);
        setForm((prev) => ({ ...prev, itemDescription: "", busId: "", routeId: "" }));
      }
    } catch {
      setError("Network error. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#F6F8F6] text-[#516A74] px-4 py-16 flex items-start justify-center">
      <div className="w-full max-w-xl">
        {/* Navigation & Header */}
        <div className="mb-6">
          <Link
            href="/"
            className="flex w-fit items-center gap-2 text-xs md:text-sm font-semibold uppercase tracking-wider text-[#5E7480] hover:text-[#087478] transition-colors mb-4"
          >
            <ArrowLeft className="w-4 h-4" /> Return to Home
          </Link>
          <Badge variant="outline" className="mb-3 ml-16 text-xs uppercase tracking-wider border-[#087478]/40 text-[#087478]">Assistance Desk</Badge>
          <div className="flex items-start gap-3 sm:gap-4">
            <div className="size-12 shrink-0 rounded-full bg-[#087478]/10 border border-[#087478]/30 flex items-center justify-center text-[#087478]">
              <Search className="size-6" />
            </div>
            <div className="min-w-0">
              <h1 className="text-2xl md:text-3xl leading-tight font-extrabold tracking-tight text-[#193542]">Report a Lost Item</h1>
              <p className="text-sm leading-relaxed text-[#516A74] mt-1.5">
                Submit details about belongings left on a Ciao bus. Staff can review your lost item report and contact you.
              </p>
            </div>
          </div>
        </div>

        {/* Success Announcement */}
        {success && (
          <div className="mb-6 p-4 rounded-xl bg-emerald-500/15 border border-emerald-500/40 flex items-start gap-3 animate-fade-in">
            <CheckCircle2 className="w-5 h-5 text-emerald-400 mt-0.5 shrink-0" />
            <div>
              <p className="text-emerald-700 font-semibold text-sm md:text-base">
                Report Logged Successfully {createdItemId && <span className="font-mono font-bold text-xs bg-emerald-100 text-emerald-800 px-2 py-0.5 rounded-full ml-1.5 border border-emerald-300">Docket LF-#{String(createdItemId).padStart(4, "0")} (Item ID: #{createdItemId})</span>}
              </p>
              <p className="text-[#516A74] text-xs md:text-sm mt-1">
                A confirmation notification has been sent to your account. If station staff locates and secures this item, you will be notified with your Item ID to file an ownership claim.
              </p>
            </div>
          </div>
        )}

        {/* Error Notification */}
        {error && (
          <div className="mb-6 p-4 rounded-xl bg-rose-500/15 border border-rose-500/40 flex items-start gap-3 animate-fade-in">
            <AlertCircle className="w-5 h-5 text-rose-400 mt-0.5 shrink-0" />
            <p className="text-rose-700 text-sm md:text-base">{error}</p>
          </div>
        )}

        {/* Form Card */}
        <Card className="p-6 md:p-8 bg-[#FFFFFF] border-[rgba(25,53,66,0.12)] shadow-xl rounded-3xl">
          <CardContent className="p-0">
            <form onSubmit={handleSubmit} className="space-y-6">
              <div>
                <label className="block text-xs md:text-sm font-semibold uppercase tracking-wider text-[#516A74] mb-2">
                  Item Description <span className="text-rose-400">*</span>
                </label>
                <textarea
                  name="itemDescription"
                  value={form.itemDescription}
                  onChange={handleChange}
                  required
                  maxLength={255}
                  rows={3}
                  className="w-full rounded-xl bg-[#F7FAF9] text-[#193542] border border-[rgba(25,53,66,0.16)] p-3 text-sm md:text-base focus:border-[#087478] focus:outline-none focus:ring-2 focus:ring-[#087478]/20 transition-colors resize-none placeholder:text-[#71858B]"
                  placeholder="E.g., Black leather Montblanc wallet, Navy Herschel backpack with silver zipper..."
                />
                <div className="flex justify-between items-center text-xs text-[#5E7480] mt-1.5">
                  <span>Be as specific as possible (brand, color, distinct marks)</span>
                  <span>{form.itemDescription.length}/255</span>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs md:text-sm font-semibold uppercase tracking-wider text-[#516A74] mb-2">
                    Your Full Name <span className="text-rose-400">*</span>
                  </label>
                  <Input
                    name="reportedByName"
                    value={form.reportedByName}
                    onChange={handleChange}
                    required
                    maxLength={100}
                    type="text"
                    placeholder="Passenger name"
                  />
                </div>
                <div>
                  <label className="block text-xs md:text-sm font-semibold uppercase tracking-wider text-[#516A74] mb-2">
                    Contact Phone Number <span className="text-rose-400">*</span>
                  </label>
                  <Input
                    name="reportedByPhone"
                    value={form.reportedByPhone}
                    onChange={handleChange}
                    required
                    maxLength={15}
                    type="tel"
                    placeholder="07X XXXXXXX"
                  />
                </div>
              </div>

              <div className="border-t border-[rgba(25,53,66,0.12)] pt-5">
                <div className="flex items-center gap-2 mb-3 text-xs md:text-sm text-[#087478]">
                  <Info className="w-4 h-4 shrink-0" />
                  <span className="font-semibold">Optional Journey Identifiers</span>
                </div>
                <p className="text-xs md:text-sm text-[#516A74] mb-4">
                  Adding the bus registration number or route helps staff locate the vehicle.
                </p>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs md:text-sm font-semibold uppercase tracking-wider text-[#516A74] mb-2">
                      Bus (Optional)
                    </label>
                    <div className="relative">
                      <SearchableSelect
                        name="busId"
                        value={form.busId}
                        onChange={handleChange}
                        className="w-full rounded-xl bg-[#F7FAF9] text-[#193542] border border-[rgba(25,53,66,0.16)] px-3 py-2.5 text-sm md:text-base focus:border-[#087478] focus:outline-none focus:ring-2 focus:ring-[#087478]/20 transition-colors appearance-none"
                      >
                        <option value="">Unknown / Not sure</option>
                        {buses.map((b) => (
                          <option key={b.id} value={b.id} className="bg-[#FFFFFF] text-[#193542]">
                            {b.plateNumber}
                          </option>
                        ))}
                      </SearchableSelect>
                    </div>
                  </div>
                  <div>
                    <label className="block text-xs md:text-sm font-semibold uppercase tracking-wider text-[#516A74] mb-2">
                      Traveled Route (Optional)
                    </label>
                    <div className="relative">
                      <SearchableSelect
                        name="routeId"
                        value={form.routeId}
                        onChange={handleChange}
                        className="w-full rounded-xl bg-[#F7FAF9] text-[#193542] border border-[rgba(25,53,66,0.16)] px-3 py-2.5 text-sm md:text-base focus:border-[#087478] focus:outline-none focus:ring-2 focus:ring-[#087478]/20 transition-colors appearance-none"
                      >
                        <option value="">Unknown / Not sure</option>
                        {routes.map((r) => (
                          <option key={r.id} value={r.id} className="bg-[#FFFFFF] text-[#193542]">
                            {r.origin} → {r.destination}
                          </option>
                        ))}
                      </SearchableSelect>
                    </div>
                  </div>
                </div>
              </div>

              <Button
                type="submit"
                disabled={loading}
                variant="gold"
                className="w-full h-12 text-sm md:text-base font-bold tracking-wide flex items-center justify-center gap-2 shadow-lg"
              >
                {loading ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin mr-2" /> Submitting Report...
                  </>
                ) : (
                  <>
                    <Send className="w-4 h-4 mr-2" /> Submit Lost Item Report
                  </>
                )}
              </Button>
            </form>
          </CardContent>
        </Card>

        {/* Security & Verification Note */}
        <div className="mt-6 flex items-start gap-2.5 text-xs md:text-sm text-[#516A74] p-4 rounded-2xl border border-[rgba(25,53,66,0.12)] bg-[#FFFFFF]">
          <ShieldAlert className="w-4 h-4 text-[#087478] shrink-0 mt-0.5" />
          <span>
            Lost & Found returns require identity and ownership verification before the item is handed over at a branch.
          </span>
        </div>
      </div>
    </div>
  );
}
