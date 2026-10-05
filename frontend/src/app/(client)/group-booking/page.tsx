"use client";

import { SearchableSelect } from "@/components/ui/searchable-select";

import { API_BASE_URL } from "@/lib/api";
import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { 
  User, 
  CalendarDays, 
  Bus, 
  MapPin, 
  ArrowRight, 
  AlertCircle, 
  CheckCircle2, 
  Search
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";

const localToday = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
};

export default function GroupBooking() {
  const router = useRouter();

  const [customerName, setCustomerName] = useState("");
  const [customerPhone, setCustomerPhone] = useState("");
  const [charterType, setCharterType] = useState("CORPORATE");
  const [passengers, setPassengers] = useState("");
  const [busType, setBusType] = useState("LUXURY");
  const [pickupDate, setPickupDate] = useState("");
  const [returnDate, setReturnDate] = useState("");
  const [details, setDetails] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const handlePickupDateChange = (val: string) => {
    setPickupDate(val);
    if (returnDate && val && returnDate < val) {
      setReturnDate(val);
    }
  };

  const handleReturnDateChange = (val: string) => {
    if (pickupDate && val && val < pickupDate) {
      setError("Return date cannot be earlier than departure date.");
      return;
    }
    setError("");
    setReturnDate(val);
  };

  const proceedToDeposit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (loading) return;
    setError("");
    setMessage("");

    if (!customerName.trim() || !customerPhone.trim() || !passengers || !pickupDate || !returnDate) {
      setError("Please fill all required fields.");
      return;
    }

    if (new Date(returnDate) < new Date(pickupDate)) {
      setError("Return date cannot be earlier than pickup date.");
      return;
    }

    setLoading(true);

    try {
      const start = new Date(pickupDate);
      const end = new Date(returnDate);
      const days = Math.max(1, Math.ceil((end.getTime() - start.getTime()) / (1000 * 3600 * 24)) + 1);
      const estimatedTotal = Math.max(15000, Number(passengers) * 1200 * days);
      const advanceDeposit = Math.round(estimatedTotal * 0.30);

      const res = await fetch(`${API_BASE_URL}/api/group-bookings`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          customerName: customerName.trim(),
          customerPhone: customerPhone.trim(),
          eventType: charterType,
          preferredBusType: busType,
          journeyDetails: details.trim(),
          startDate: pickupDate + "T00:00:00",
          endDate: returnDate + "T00:00:00",
          passengerCount: Number(passengers),
          totalCost: estimatedTotal,
          depositAmount: advanceDeposit
        }),
      });

      if (!res.ok) {
        const errorData = await res.json();
        throw new Error(errorData.message || "Failed to submit group booking request.");
      }

      const created = await res.json();
      setMessage(`Request submitted. Your reference is ${created.bookingReference}.`);
      
      setCustomerName("");
      setCustomerPhone("");
      setPassengers("");
      setPickupDate("");
      setReturnDate("");
      setDetails("");
      sessionStorage.setItem("groupBookingPhone", customerPhone.trim());
      if (created.guestAccessToken) {
        sessionStorage.setItem("groupBookingGuestToken", created.guestAccessToken);
      }
      router.push(`/group-booking/status?reference=${encodeURIComponent(created.bookingReference)}`);

    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to submit group booking request");
    } finally {
      setLoading(false);
    }
  };

  const calculatedDays = (pickupDate && returnDate && new Date(returnDate) >= new Date(pickupDate))
    ? Math.max(1, Math.ceil((new Date(returnDate).getTime() - new Date(pickupDate).getTime()) / (1000 * 3600 * 24)) + 1)
    : 1;

  const estimatedTotal = passengers && pickupDate && returnDate && new Date(returnDate) >= new Date(pickupDate)
    ? Math.max(15000, Number(passengers) * 1200 * calculatedDays)
    : 15000;

  const estimatedDeposit = Math.round(estimatedTotal * 0.30);

  return (
    <main className="max-w-4xl mx-auto mt-12 md:mt-16 mb-20 px-5 w-full">
      {/* Title */}
      <div className="text-center mb-8">
        <Badge variant="gold" className="mb-2">Group Booking Management</Badge>
        <h1 className="text-2xl md:text-3xl font-extrabold text-[#193542]">Request a Group Booking</h1>
        <p className="text-sm text-[#516A74] mt-1 max-w-lg mx-auto">
          Request a bus for university tours, corporate events, and organized group travel
        </p>
      </div>

      <div className="bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] p-6 md:p-10 rounded-3xl shadow-xl">
        {error && (
          <div role="alert" className="p-4 bg-red-50 border border-red-500/40 text-red-700 text-xs md:text-sm rounded-2xl mb-6 flex items-center gap-2">
            <AlertCircle className="w-4 h-4 text-red-400 shrink-0" />
            <span>{error}</span>
          </div>
        )}
        {message && (
          <div role="status" className="p-4 bg-emerald-50 border border-emerald-500/40 text-emerald-700 text-xs md:text-sm rounded-2xl mb-6 flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>{message}</span>
          </div>
        )}

        <form onSubmit={proceedToDeposit} className="space-y-6">
          {/* Section: Contact */}
          <div>
            <h3 className="text-sm font-bold text-[#193542] uppercase tracking-wider mb-3 flex items-center gap-2 border-b border-[rgba(25,53,66,0.12)] pb-2">
              <User className="w-4 h-4 text-[#087478]" /> Primary Organizer Details
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="flex flex-col">
                <label htmlFor="customer_name" className="ciao-label">Full Name *</label>
                <input 
                  type="text" 
                  id="customer_name" 
                  className="ciao-input" 
                  placeholder="e.g. Ruwan Jayasinghe" 
                  required 
                  value={customerName} 
                  onChange={(e) => setCustomerName(e.target.value)} 
                />
              </div>

              <div className="flex flex-col">
                <label htmlFor="customer_phone" className="ciao-label">Contact Phone Number *</label>
                <input 
                  type="tel" 
                  id="customer_phone" 
                  className="ciao-input" 
                  placeholder="077XXXXXXX" 
                  required 
                  value={customerPhone} 
                  onChange={(e) => setCustomerPhone(e.target.value)} 
                />
              </div>
            </div>
          </div>

          {/* Section: Charter Requirements */}
          <div>
            <h3 className="text-sm font-bold text-[#193542] uppercase tracking-wider mb-3 flex items-center gap-2 border-b border-[rgba(25,53,66,0.12)] pb-2">
              <Bus className="w-4 h-4 text-[#087478]" /> Group Booking Requirements
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <div className="flex flex-col">
                <label htmlFor="charter_type" className="ciao-label">Event Purpose *</label>
                <SearchableSelect 
                  id="charter_type" 
                  className="ciao-input cursor-pointer" 
                  required 
                  value={charterType} 
                  onChange={(e) => setCharterType(e.target.value)}
                >
                  <option value="CORPORATE" className="bg-[#FFFFFF] text-[#193542]">Corporate Delegation</option>
                  <option value="SCHOOL" className="bg-[#FFFFFF] text-[#193542]">University / School Tour</option>
                  <option value="PRIVATE" className="bg-[#FFFFFF] text-[#193542]">Private Group Journey</option>
                </SearchableSelect>
              </div>

              <div className="flex flex-col">
                <label htmlFor="passenger_count" className="ciao-label">Passenger Count *</label>
                <input 
                  type="number" 
                  id="passenger_count" 
                  className="ciao-input" 
                  placeholder="e.g. 45" 
                  required 
                  min="10" 
                  value={passengers} 
                  onChange={(e) => setPassengers(e.target.value)} 
                />
              </div>

              <div className="flex flex-col">
                <label htmlFor="bus_type" className="ciao-label">Bus Type *</label>
                <SearchableSelect 
                  id="bus_type" 
                  className="ciao-input cursor-pointer" 
                  required 
                  value={busType} 
                  onChange={(e) => setBusType(e.target.value)}
                >
                  <option value="LUXURY" className="bg-[#FFFFFF] text-[#193542]">Luxury AC Bus (49 Seats)</option>
                  <option value="SEMI" className="bg-[#FFFFFF] text-[#193542]">Semi-Luxury Bus (54 Seats)</option>
                  <option value="MINI" className="bg-[#FFFFFF] text-[#193542]">Executive Mini AC (29 Seats)</option>
                </SearchableSelect>
              </div>
            </div>
          </div>

          {/* Section: Dates */}
          <div>
            <h3 className="text-sm font-bold text-[#193542] uppercase tracking-wider mb-3 flex items-center gap-2 border-b border-[rgba(25,53,66,0.12)] pb-2">
              <CalendarDays className="w-4 h-4 text-[#087478]" /> Schedule Dates
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="flex flex-col">
                <label htmlFor="pickup_date" className="ciao-label">Departure Date *</label>
                <Input 
                  type="date" 
                  id="pickup_date" 
                  className="ciao-input cursor-pointer" 
                  required 
                  min={localToday()}
                  value={pickupDate} 
                  onChange={(e) => handlePickupDateChange(e.target.value)} 
                />
              </div>

              <div className="flex flex-col">
                <label htmlFor="return_date" className="ciao-label">Return Date *</label>
                <Input 
                  type="date" 
                  id="return_date" 
                  className="ciao-input cursor-pointer" 
                  required 
                  min={pickupDate || localToday()}
                  value={returnDate} 
                  onChange={(e) => handleReturnDateChange(e.target.value)} 
                />
              </div>
            </div>
          </div>

          {/* Section: Itinerary details */}
          <div className="flex flex-col">
            <label htmlFor="route_details" className="ciao-label flex items-center gap-1.5">
              <MapPin className="w-3.5 h-3.5 text-[#087478]" /> Journey Route & Itinerary Notes *
            </label>
            <textarea 
              id="route_details" 
              className="ciao-input resize-none" 
              rows={3} 
              placeholder="Detail pickup station, destination terminal, stopovers, and special requirements..." 
              required 
              value={details} 
              onChange={(e) => setDetails(e.target.value)}
            />
          </div>

          {/* Quotation & Deposit Breakdown Card */}
          <div className="p-6 rounded-2xl bg-[#F7FAF9] border border-[#087478]/30 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
            <div>
              <span className="text-xs font-bold text-[#087478] uppercase tracking-widest block mb-0.5">
                Estimated Group Booking Cost ({calculatedDays} Day{calculatedDays > 1 ? "s" : ""})
              </span>
              <span className="text-2xl font-extrabold text-[#193542]">
                LKR {estimatedTotal.toLocaleString(undefined, { minimumFractionDigits: 2 })}
              </span>
              <p className="text-xs text-[#516A74] mt-0.5">
                Calculated based on duration, passenger capacity, and vehicle tier
              </p>
            </div>

            <div className="text-left sm:text-right">
              <span className="text-xs text-[#5E7480] uppercase tracking-wider block">
                Required 30% Advance Deposit
              </span>
              <span className="text-xl font-extrabold text-[#087478]">
                LKR {estimatedDeposit.toLocaleString(undefined, { minimumFractionDigits: 2 })}
              </span>
            </div>
          </div>

          <button 
            type="submit" 
            className="ciao-btn-primary w-full justify-center h-12 text-sm md:text-base font-bold disabled:opacity-50" 
            disabled={loading}
          >
            {loading ? "Submitting Group Booking..." : "Submit Group Booking & Proceed to Deposit"}
            <ArrowRight className="w-4 h-4 ml-2" />
          </button>
        </form>

        <div className="mt-6 pt-4 border-t border-[rgba(25,53,66,0.12)] flex justify-between items-center text-xs md:text-sm">
          <span className="text-[#516A74]">Already requested a group booking?</span>
          <Link href="/group-booking/status" className="text-[#087478] hover:underline font-semibold flex items-center gap-1">
            <Search className="w-3.5 h-3.5" /> Check Group Booking Status
          </Link>
        </div>
      </div>
    </main>
  );
}
