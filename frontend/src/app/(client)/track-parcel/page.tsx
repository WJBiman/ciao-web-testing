"use client";

import { API_BASE_URL } from "@/lib/api";
import { useState, useEffect, Suspense } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { 
  Search, 
  Bus, 
  Calendar, 
  Clock, 
  CheckCircle2, 
  Truck, 
  RotateCcw, 
  AlertCircle,
  ShieldCheck,
  Headphones
} from "lucide-react";
import { Badge } from "@/components/ui/badge";

interface ParcelTrackInfo {
  id: number;
  trackingId: string;
  senderName: string;
  senderPhone: string;
  receiverName: string;
  receiverPhone: string;
  weight: number;
  totalFee: number;
  status: string;
  createdAt: string;
  busPlateNumber?: string;
  originBranch?: { id: number; location: string };
  destinationBranch?: { id: number; location: string };
}

function TrackParcelContent() {
  const searchParams = useSearchParams();
  const initialId = searchParams.get("trackingId") || "";
  
  const [trackingId, setTrackingId] = useState(initialId);
  const [parcelData, setParcelData] = useState<ParcelTrackInfo | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const fetchTracking = async (code: string) => {
    if (!code.trim()) return;
    setLoading(true);
    setError("");
    setParcelData(null);

    try {
      const res = await fetch(`${API_BASE_URL}/api/parcels/track/${code.trim()}`);
      if (!res.ok) {
        if (res.status === 404) {
          throw new Error("Parcel not found. Please check your tracking number.");
        }
        throw new Error("Failed to retrieve parcel status.");
      }
      const data: ParcelTrackInfo = await res.json();
      setParcelData(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to track parcel.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (initialId) {
      const timer = setTimeout(() => {
        void fetchTracking(initialId);
      }, 0);
      return () => clearTimeout(timer);
    }
  }, [initialId]);

  const handleTrack = (e: React.FormEvent) => {
    e.preventDefault();
    void fetchTracking(trackingId);
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case "PENDING":
        return <Badge variant="warning">PENDING INTAKE</Badge>;
      case "IN_TRANSIT":
        return <Badge variant="info">IN TRANSIT</Badge>;
      case "DELIVERED":
        return <Badge variant="success">DELIVERED</Badge>;
      case "RETURNED":
        return <Badge variant="danger">RETURNED</Badge>;
      default:
        return <Badge variant="secondary">{status}</Badge>;
    }
  };

  const getStepState = (step: string, currentStatus: string) => {
    const sequence = ["PENDING", "IN_TRANSIT", "DELIVERED"];
    if (currentStatus === "RETURNED") {
      return step === "RETURNED" ? "current" : "completed";
    }
    const currentIndex = sequence.indexOf(currentStatus);
    const stepIndex = sequence.indexOf(step);
    if (stepIndex < currentIndex) return "completed";
    if (stepIndex === currentIndex) return "current";
    return "upcoming";
  };

  return (
    <main className="max-w-3xl mx-auto mt-12 md:mt-16 mb-20 px-5 w-full min-h-[60vh]">
      {/* Title */}
      <div className="text-center mb-8">
        <Badge variant="gold" className="mb-2">Logistics Telemetry</Badge>
        <h1 className="text-2xl md:text-3xl font-extrabold text-[#193542]">Track Parcel</h1>
        <p className="text-sm text-[#516A74] mt-1 max-w-md mx-auto">
          Enter your official tracking code to inspect dispatch and delivery status
        </p>
      </div>

      <div className="bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] p-6 md:p-8 rounded-3xl shadow-xl">
        <form onSubmit={handleTrack} className="flex flex-col sm:flex-row sm:items-center gap-3">
          <input 
            type="text" 
            className="ciao-input h-12 min-w-0 flex-1 font-mono uppercase text-sm md:text-base tracking-wider" 
            placeholder="e.g. C-PAR-XXXXXXXX" 
            required 
            value={trackingId}
            onChange={(e) => setTrackingId(e.target.value)}
          />
          <button 
            type="submit" 
            disabled={loading} 
            className="ciao-btn-primary h-12 w-full sm:w-auto px-6 text-sm md:text-base font-bold shrink-0 justify-center disabled:opacity-50 flex items-center gap-2"
          >
            {loading ? "Searching..." : <><Search className="w-4 h-4 mr-1" /> Track Parcel</>}
          </button>
        </form>

        {error && (
          <div role="alert" className="mt-5 p-4 bg-red-50 border border-red-500/40 text-red-700 text-xs md:text-sm rounded-2xl text-center flex items-center justify-center gap-2">
            <AlertCircle className="w-4 h-4 text-red-400 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {parcelData && (
          <div className="bg-[#EAF3F0] rounded-2xl overflow-hidden mt-6 border border-[rgba(25,53,66,0.12)] shadow-lg">
            {/* Header Status Bar */}
            <div className="bg-[#EDF3F1] p-6 border-b border-[rgba(25,53,66,0.12)] flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
              <div>
                <span className="text-xs font-bold text-[#5E7480] uppercase tracking-wider block mb-0.5">
                  Parcel Tracking Number
                </span>
                <span className="text-xl md:text-2xl font-mono font-bold text-[#087478]">
                  {parcelData.trackingId}
                </span>
              </div>
              {getStatusBadge(parcelData.status)}
            </div>

            {/* Lifecycle Visualizer */}
            {parcelData.status !== "RETURNED" ? (
              <div className="px-6 py-8 border-b border-[rgba(25,53,66,0.12)] bg-[#F7FAF9]">
                <div className="grid grid-cols-3 gap-2 relative">
                  {/* Connecting Line */}
                  <div className="absolute top-4 left-1/6 right-1/6 h-0.5 bg-[rgba(25,53,66,0.16)] -z-0" />

                  {/* Step 1: PENDING */}
                  <div className="flex flex-col items-center text-center z-10">
                    <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold mb-2 ${
                      getStepState("PENDING", parcelData.status) === "completed" || getStepState("PENDING", parcelData.status) === "current"
                        ? "bg-[#087478] text-white shadow-sm font-bold"
                        : "bg-[#EAF3F0] text-[#5E7480] border border-[rgba(25,53,66,0.16)]"
                    }`}>
                      <Clock className="w-4 h-4" />
                    </div>
                    <span className="text-xs md:text-sm font-bold text-[#193542]">Registered</span>
                    <span className="text-xs text-[#5E7480]">Branch Intake</span>
                  </div>

                  {/* Step 2: IN_TRANSIT */}
                  <div className="flex flex-col items-center text-center z-10">
                    <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold mb-2 ${
                      getStepState("IN_TRANSIT", parcelData.status) === "completed" || getStepState("IN_TRANSIT", parcelData.status) === "current"
                        ? "bg-[#38BDF8] text-[#070B14] shadow-sm font-bold"
                        : "bg-[#EAF3F0] text-[#5E7480] border border-[rgba(25,53,66,0.16)]"
                    }`}>
                      <Truck className="w-4 h-4" />
                    </div>
                    <span className="text-xs md:text-sm font-bold text-[#193542]">In Transit</span>
                    <span className="text-xs text-[#5E7480]">Intercity Bus</span>
                  </div>

                  {/* Step 3: DELIVERED */}
                  <div className="flex flex-col items-center text-center z-10">
                    <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold mb-2 ${
                      getStepState("DELIVERED", parcelData.status) === "current"
                        ? "bg-emerald-400 text-[#070B14] shadow-sm font-bold"
                        : "bg-[#EAF3F0] text-[#5E7480] border border-[rgba(25,53,66,0.16)]"
                    }`}>
                      <CheckCircle2 className="w-4 h-4" />
                    </div>
                    <span className="text-xs md:text-sm font-bold text-[#193542]">Delivered</span>
                    <span className="text-xs text-[#5E7480]">Ready for Pickup</span>
                  </div>
                </div>
              </div>
            ) : (
              <div className="p-4 bg-red-50 border-b border-red-500/30 text-center text-xs md:text-sm text-red-700 flex items-center justify-center gap-2">
                <RotateCcw className="w-4 h-4 text-red-400" />
                <span>This parcel has been processed as RETURNED to the origin depot.</span>
              </div>
            )}
            
            {/* Information Grid */}
            <div className="p-6 grid grid-cols-1 sm:grid-cols-2 gap-6">
              <div>
                <span className="text-xs font-bold text-[#5E7480] uppercase tracking-wider block mb-1">
                  Assigned Bus
                </span>
                <p className="text-sm md:text-base font-semibold text-[#193542] flex items-center gap-2">
                  <Bus className="w-4 h-4 text-[#087478]" /> 
                  {parcelData.busPlateNumber || "Assigned by Depot Manager"}
                </p>
              </div>

              <div>
                <span className="text-xs font-bold text-[#5E7480] uppercase tracking-wider block mb-1">
                  Registered Date
                </span>
                <p className="text-sm md:text-base font-semibold text-[#193542] flex items-center gap-2">
                  <Calendar className="w-4 h-4 text-[#087478]" /> 
                  {new Date(parcelData.createdAt).toLocaleDateString([], { weekday: "short", year: "numeric", month: "short", day: "numeric" })}
                </p>
              </div>

              <div>
                <span className="text-xs font-bold text-[#5E7480] uppercase tracking-wider block mb-1">
                  Intake & Destination Branches
                </span>
                <p className="text-sm md:text-base font-semibold text-[#193542]">
                  {parcelData.originBranch?.location || "Origin Depot"} → {parcelData.destinationBranch?.location || "Destination Depot"}
                </p>
              </div>

              <div>
                <span className="text-xs font-bold text-[#5E7480] uppercase tracking-wider block mb-1">
                  Receiver / Collection Contact
                </span>
                <p className="text-sm md:text-base font-semibold text-[#193542]">
                  {parcelData.receiverName || "Registered Consignee"}
                </p>
              </div>

              <div>
                <span className="text-xs font-bold text-[#5E7480] uppercase tracking-wider block mb-1">
                  Parcel Weight
                </span>
                <p className="text-sm md:text-base font-semibold text-[#193542]">
                  {parcelData.weight ? `${parcelData.weight} KG` : "Standard Package"}
                </p>
              </div>

              <div>
                <span className="text-xs font-bold text-[#5E7480] uppercase tracking-wider block mb-1">
                  Authoritative Logistics Fee
                </span>
                <p className="text-base md:text-lg font-bold text-[#087478]">
                  {parcelData.totalFee ? `LKR ${Number(parcelData.totalFee).toFixed(2)}` : "LKR 250.00"}
                </p>
              </div>
            </div>

            {/* Assistance Guidance Footer */}
            <div className="p-4 bg-[#EDF3F1] border-t border-[rgba(25,53,66,0.12)] flex items-center justify-between text-xs md:text-sm">
              <span className="text-[#516A74] flex items-center gap-1.5">
                <ShieldCheck className="w-4 h-4 text-emerald-400" /> Station ID verification required at pickup
              </span>
              <Link href="/report-lost" className="text-[#087478] hover:underline font-semibold flex items-center gap-1">
                <Headphones className="w-3.5 h-3.5" /> Support
              </Link>
            </div>
          </div>
        )}
      </div>
    </main>
  );
}

export default function TrackParcel() {
  return (
    <Suspense fallback={<div className="min-h-screen flex items-center justify-center text-sm text-[#5E7480]">Loading telemetry...</div>}>
      <TrackParcelContent />
    </Suspense>
  );
}
