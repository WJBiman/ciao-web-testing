"use client";

import { SearchableSelect } from "@/components/ui/searchable-select";

import { API_BASE_URL } from "@/lib/api";
import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { 
  PackagePlus, 
  User, 
  Bus, 
  CheckCircle2, 
  Copy, 
  Check, 
  ArrowRight, 
  AlertCircle,
  MapPin
} from "lucide-react";
import { Badge } from "@/components/ui/badge";

interface BusOption {
  id: number;
  plateNumber: string;
  model?: string;
  status: string;
  capacity?: number;
}

export default function BookParcel() {
  const router = useRouter();

  const [weight, setWeight] = useState(1);
  const [senderName, setSenderName] = useState("");
  const [senderPhone, setSenderPhone] = useState("");
  const [receiverName, setReceiverName] = useState("");
  const [receiverPhone, setReceiverPhone] = useState("");
  const [busId, setBusId] = useState("");
  const [buses, setBuses] = useState<BusOption[]>([]);
  const [branches, setBranches] = useState<{ id: number; location: string }[]>([]);
  const [originBranchId, setOriginBranchId] = useState("");
  const [destinationBranchId, setDestinationBranchId] = useState("");
  const [trackingId, setTrackingId] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [copiedTracking, setCopiedTracking] = useState(false);

  // Authoritative Tariff: Base handling LKR 250.00 (up to 1.0kg) + LKR 100.00/kg excess
  const calculatedFee = 250 + (Math.max(0, weight - 1) * 100);

  useEffect(() => {
    fetch(`${API_BASE_URL}/api/fleet/buses/active`)
      .then(res => res.json())
      .then(data => {
        if (Array.isArray(data)) {
          setBuses(data);
          if (data.length > 0) {
            setBusId(data[0].id.toString());
          }
        }
      })
      .catch(err => console.error("Error fetching buses:", err));

    fetch(`${API_BASE_URL}/api/fleet/branches`)
      .then(res => res.json())
      .then(data => {
        if (Array.isArray(data)) {
          setBranches(data);
          if (data.length > 0) {
            setOriginBranchId(data[0].id.toString());
            if (data.length > 1) {
              setDestinationBranchId(data[1].id.toString());
            } else {
              setDestinationBranchId(data[0].id.toString());
            }
          }
        }
      })
      .catch(() => {
        // Fallback demo branches
        setBranches([
          { id: 1, location: "Colombo Central Terminal" },
          { id: 2, location: "Kandy Branch Office" },
          { id: 3, location: "Galle Fort Station" }
        ]);
        setOriginBranchId("1");
        setDestinationBranchId("2");
      });
  }, []);

  const handleBook = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError("");

    try {
      const res = await fetch(`${API_BASE_URL}/api/parcels`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          busId: parseInt(busId),
          senderName,
          senderPhone,
          receiverName,
          receiverPhone,
          weight,
          originBranchId: originBranchId ? parseInt(originBranchId) : undefined,
          destinationBranchId: destinationBranchId ? parseInt(destinationBranchId) : undefined
        })
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || "Failed to book parcel.");
      }

      setTrackingId(data.trackingId);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to book parcel.");
    } finally {
      setLoading(false);
    }
  };

  const copyTracking = () => {
    navigator.clipboard.writeText(trackingId);
    setCopiedTracking(true);
    setTimeout(() => setCopiedTracking(false), 2000);
  };

  return (
    <main className="max-w-3xl mx-auto mt-12 md:mt-16 mb-20 px-5 w-full">
      {/* Header */}
      <div className="text-center mb-8">
        <Badge variant="gold" className="mb-2">Express Bus Logistics</Badge>
        <h1 className="text-2xl md:text-3xl font-extrabold text-[#193542]">Parcel Booking</h1>
        <p className="text-sm text-[#516A74] mt-1 max-w-lg mx-auto">
          Book a parcel shipment along a scheduled bus route
        </p>
      </div>

      {trackingId ? (
        <div className="bg-[#FFFFFF] border border-[rgba(8,127,129,0.25)] p-8 md:p-12 rounded-3xl text-center shadow-2xl">
          <div className="w-16 h-16 bg-emerald-500/20 text-emerald-400 border border-emerald-500/40 rounded-full flex items-center justify-center mx-auto mb-4">
            <CheckCircle2 className="w-8 h-8" />
          </div>
          <h2 className="text-2xl md:text-3xl font-extrabold text-[#193542] mb-2">Parcel Booked</h2>
          <p className="text-sm text-[#516A74] mb-6 max-w-md mx-auto">
            Your package is registered for station dispatch. Keep your unique tracking code for receiver collection.
          </p>

          <div className="bg-[#F7FAF9] border border-[#087478]/40 p-6 rounded-2xl inline-flex flex-col items-center mb-8 shadow-inner">
            <span className="text-xs font-bold text-[#087478] uppercase tracking-widest mb-1">
              Parcel Tracking Number
            </span>
            <span className="text-2xl sm:text-3xl font-mono font-extrabold text-[#193542] tracking-wider">
              {trackingId}
            </span>
            <button
              onClick={copyTracking}
              className="mt-3 flex items-center gap-1.5 text-xs md:text-sm text-[#516A74] hover:text-[#087478] transition-colors cursor-pointer bg-transparent border-none"
            >
              {copiedTracking ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5 text-[#087478]" />}
              {copiedTracking ? "Copied to Clipboard" : "Copy Code"}
            </button>
          </div>

          <div className="flex flex-col sm:flex-row justify-center gap-3">
            <button
              onClick={() => router.push(`/track-parcel?trackingId=${trackingId}`)}
              className="ciao-btn-primary flex items-center justify-center gap-2 !py-3 !px-6"
            >
              Track Parcel <ArrowRight className="w-4 h-4 ml-1" />
            </button>
            <button
              onClick={() => {
                setTrackingId("");
                setSenderName("");
                setReceiverName("");
              }}
              className="ciao-btn-secondary !py-3 !px-6"
            >
              Book Another Parcel
            </button>
          </div>
        </div>
      ) : (
        <div className="bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] p-6 md:p-8 rounded-3xl shadow-xl">
          {error && (
            <div role="alert" className="p-3 bg-red-50 border border-red-500/40 text-red-700 text-xs md:text-sm rounded-xl mb-6 flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0 text-red-400" />
              <span>{error}</span>
            </div>
          )}

          <form onSubmit={handleBook} className="space-y-6">
            {/* Sender Section */}
            <div>
              <h3 className="text-sm font-bold text-[#193542] uppercase tracking-wider mb-3 flex items-center gap-2 border-b border-[rgba(25,53,66,0.12)] pb-2">
                <User className="w-4 h-4 text-[#087478]" /> Sender Information
              </h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="flex flex-col">
                  <label htmlFor="sender_name" className="ciao-label">Sender Full Name *</label>
                  <input 
                    type="text" 
                    id="sender_name" 
                    value={senderName} 
                    onChange={e => setSenderName(e.target.value)} 
                    className="ciao-input" 
                    required 
                    placeholder="e.g. Kasun Fernando" 
                  />
                </div>
                <div className="flex flex-col">
                  <label htmlFor="sender_phone" className="ciao-label">Sender Mobile No *</label>
                  <input 
                    type="tel" 
                    id="sender_phone" 
                    value={senderPhone} 
                    onChange={e => setSenderPhone(e.target.value)} 
                    className="ciao-input" 
                    required 
                    placeholder="077XXXXXXX" 
                  />
                </div>
              </div>
            </div>

            {/* Receiver Section */}
            <div>
              <h3 className="text-sm font-bold text-[#193542] uppercase tracking-wider mb-3 flex items-center gap-2 border-b border-[rgba(25,53,66,0.12)] pb-2">
                <MapPin className="w-4 h-4 text-[#087478]" /> Receiver / Collection Details
              </h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="flex flex-col">
                  <label htmlFor="receiver_name" className="ciao-label">Receiver Full Name *</label>
                  <input 
                    type="text" 
                    id="receiver_name" 
                    value={receiverName} 
                    onChange={e => setReceiverName(e.target.value)} 
                    className="ciao-input" 
                    required 
                    placeholder="e.g. Chaminda Perera" 
                  />
                </div>
                <div className="flex flex-col">
                  <label htmlFor="receiver_phone" className="ciao-label">Receiver Mobile No *</label>
                  <input 
                    type="tel" 
                    id="receiver_phone" 
                    value={receiverPhone} 
                    onChange={e => setReceiverPhone(e.target.value)} 
                    className="ciao-input" 
                    required 
                    placeholder="071XXXXXXX" 
                  />
                </div>
              </div>
            </div>

            {/* Consignment & Coach Specifics */}
            <div>
              <h3 className="text-sm font-bold text-[#193542] uppercase tracking-wider mb-3 flex items-center gap-2 border-b border-[rgba(25,53,66,0.12)] pb-2">
                <Bus className="w-4 h-4 text-[#087478]" /> Route, Branches & Bus Assignment
              </h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-4">
                <div className="flex flex-col">
                  <label htmlFor="origin_branch" className="ciao-label">Origin Intake Branch *</label>
                  <SearchableSelect 
                    id="origin_branch" 
                    value={originBranchId} 
                    onChange={e => setOriginBranchId(e.target.value)} 
                    className="ciao-input cursor-pointer" 
                    required
                  >
                    {branches.length === 0 && <option value="">Loading branches...</option>}
                    {branches.map(b => (
                      <option key={b.id} value={b.id} className="bg-[#FFFFFF] text-[#193542]">
                        {b.location}
                      </option>
                    ))}
                  </SearchableSelect>
                </div>

                <div className="flex flex-col">
                  <label htmlFor="destination_branch" className="ciao-label">Destination Collection Branch *</label>
                  <SearchableSelect 
                    id="destination_branch" 
                    value={destinationBranchId} 
                    onChange={e => setDestinationBranchId(e.target.value)} 
                    className="ciao-input cursor-pointer" 
                    required
                  >
                    {branches.length === 0 && <option value="">Loading branches...</option>}
                    {branches.map(b => (
                      <option key={b.id} value={b.id} className="bg-[#FFFFFF] text-[#193542]">
                        {b.location}
                      </option>
                    ))}
                  </SearchableSelect>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="flex flex-col">
                  <label htmlFor="dispatch_bus" className="ciao-label">Assigned Dispatch Bus *</label>
                  <SearchableSelect 
                    id="dispatch_bus" 
                    value={busId} 
                    onChange={e => setBusId(e.target.value)} 
                    className="ciao-input cursor-pointer" 
                    required
                  >
                    {buses.length === 0 && <option value="">No Active Fleet Buses</option>}
                    {buses.map(b => (
                      <option key={b.id} value={b.id} className="bg-[#FFFFFF] text-[#193542]">
                        {b.plateNumber} (Capacity: {b.capacity || "Standard"})
                      </option>
                    ))}
                  </SearchableSelect>
                </div>

                <div className="flex flex-col">
                  <label htmlFor="parcel_weight" className="ciao-label">Approx Weight (KG) *</label>
                  <input 
                    type="number" 
                    id="parcel_weight" 
                    className="ciao-input" 
                    required 
                    min="1" 
                    max="50" 
                    value={weight} 
                    onChange={(e) => setWeight(Math.max(1, parseInt(e.target.value) || 1))} 
                  />
                </div>
              </div>
            </div>

            {/* Fee Disclosure Box */}
            <div className="p-4 rounded-2xl bg-[#F7FAF9] border border-[rgba(25,53,66,0.12)] flex flex-col sm:flex-row justify-between items-start sm:items-center gap-2">
              <div>
                <span className="text-sm font-bold text-[#193542] block">
                  Authoritative Tariff Fee: <span className="text-[#087478] text-base font-extrabold">LKR {calculatedFee.toFixed(2)}</span>
                </span>
                <span className="text-xs md:text-sm text-[#516A74]">
                  Base handling LKR 250.00 (up to 1.0 kg) + LKR 100.00/kg excess. Validated at intake.
                </span>
              </div>
              <Badge variant="gold">Standard Express</Badge>
            </div>

            <button 
              type="submit" 
              disabled={loading || buses.length === 0} 
              className="ciao-btn-primary w-full justify-center h-12 text-sm md:text-base font-bold disabled:opacity-50"
            >
              <PackagePlus className="w-4 h-4 mr-2" />
              {loading ? "Booking Parcel..." : "Book Parcel & Get Tracking Number"}
            </button>
          </form>
        </div>
      )}
    </main>
  );
}
