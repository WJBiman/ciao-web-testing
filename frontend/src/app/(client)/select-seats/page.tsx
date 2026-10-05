"use client";

import { API_BASE_URL } from "@/lib/api";
import { useState, useEffect, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Bus, Clock, ShieldCheck, Ticket, AlertCircle, ArrowRight, Check } from "lucide-react";
import { Badge } from "@/components/ui/badge";

function SelectSeatsContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const scheduleId = searchParams.get("scheduleId");
  
  const [selectedSeats, setSelectedSeats] = useState<Set<number>>(new Set());
  const [unavailableSeats, setUnavailableSeats] = useState<number[]>([]);
  const [loading, setLoading] = useState(true);
  const [locking, setLocking] = useState(false);
  const [error, setError] = useState("");
  const [capacity, setCapacity] = useState(0);
  const [bookable, setBookable] = useState(false);
  const [baseFare, setBaseFare] = useState(0);

  useEffect(() => {
    if (!scheduleId) return;
    
    fetch(`${API_BASE_URL}/api/schedules/${scheduleId}/seat-map`)
      .then(res => {
        if (!res.ok) throw new Error("Unable to load this schedule.");
        return res.json();
      })
      .then(data => {
        setUnavailableSeats(data.unavailableSeats.map(Number));
        setCapacity(data.capacity);
        setBookable(data.bookable);
        setBaseFare(Number(data.effectiveSeatFare ?? data.baseFare));
        if (!data.bookable) setError("This schedule is no longer available for booking.");
        setLoading(false);
      })
      .catch(err => {
        console.error(err);
        setError("Unable to load the seat map. Please try again.");
        setLoading(false);
      });
  }, [scheduleId]);

  const toggleSeat = (seatNum: number) => {
    if (unavailableSeats.includes(seatNum)) return;
    
    const newSelected = new Set(selectedSeats);
    if (newSelected.has(seatNum)) {
      newSelected.delete(seatNum);
    } else {
      if (newSelected.size >= 6) {
        setError("Maximum 6 seats allowed per booking transaction.");
        return;
      }
      setError("");
      newSelected.add(seatNum);
    }
    setSelectedSeats(newSelected);
  };

  const proceedToCheckout = async () => {
    if (selectedSeats.size === 0) {
      setError("Please select at least one seat to proceed.");
      return;
    }
    
    setLocking(true);
    setError("");

    try {
      let passengerName = "Guest User";
      let passengerPhone = "0770000000";

      const cachedUser = localStorage.getItem("ciao_user");
      if (cachedUser) {
        try {
          const userObj = JSON.parse(cachedUser);
          if (userObj.fullName) passengerName = userObj.fullName;
          if (userObj.phone) passengerPhone = userObj.phone;
        } catch {
          // ignore cached parse error
        }
      }
      
      const res = await fetch(`${API_BASE_URL}/api/reservations/lock`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          scheduleId: Number(scheduleId),
          passengerName,
          passengerPhone,
          seatNumbers: Array.from(selectedSeats).map(String)
        })
      });

      const data = await res.json();
      
      if (!res.ok) {
        setError(data.message || "Failed to lock seats.");
        setLocking(false);
        const seatsRes = await fetch(`${API_BASE_URL}/api/schedules/${scheduleId}/seats`);
        setUnavailableSeats((await seatsRes.json()).map(Number));
        return;
      }

      if (data.guestToken) {
        sessionStorage.setItem("guestReservationToken", data.guestToken);
      } else {
        sessionStorage.removeItem("guestReservationToken");
      }

      router.push(`/checkout-payment?reservationId=${data.id}`);
    } catch (err) {
      console.error(err);
      setError("Network error. Please try again.");
      setLocking(false);
    }
  };

  const renderSeat = (num: number) => {
    if (num > capacity) return <div key={num} />;
    const isUnavailable = unavailableSeats.includes(num);
    const isSelected = selectedSeats.has(num);
    
    let seatClass = "w-11 h-11 rounded-xl flex items-center justify-center font-bold text-xs md:text-sm cursor-pointer transition-all duration-200 select-none relative ";
    
    if (isUnavailable) {
      seatClass += "bg-[#F7FAF9] text-[#64748B] border border-[rgba(203,213,225,0.06)] cursor-not-allowed opacity-60";
    } else if (isSelected) {
      seatClass += "bg-[#087478] text-white font-extrabold border-2 border-[#087478] shadow-[0_0_15px_rgba(8,127,129,0.25)] scale-105 z-10";
    } else {
      seatClass += "bg-[#EAF3F0] text-[#193542] border border-[rgba(25,53,66,0.16)] hover:border-[#087478] hover:text-[#087478] hover:scale-102";
    }

    return (
      <button 
        key={num} 
        type="button"
        disabled={isUnavailable}
        onClick={() => toggleSeat(num)}
        className={seatClass}
        aria-label={`Seat ${num}, ${isUnavailable ? "Unavailable" : isSelected ? "Selected" : "Available"}`}
      >
        {num < 10 ? `0${num}` : num}
        {isSelected && (
          <span className="absolute -top-1 -right-1 w-3.5 h-3.5 bg-[#F6F8F6] border border-[#087478] rounded-full flex items-center justify-center">
            <Check className="w-2.5 h-2.5 text-[#087478]" />
          </span>
        )}
      </button>
    );
  };

  if (!scheduleId) {
    return (
      <div className="max-w-md mx-auto mt-32 text-center p-8 ciao-card">
        <AlertCircle className="w-10 h-10 text-[#087478] mx-auto mb-3" />
        <h2 className="text-lg font-bold text-[#193542]">Missing Schedule Reference</h2>
        <p className="text-sm text-[#5E7480] mt-1 mb-4">Please select a schedule from the home page to choose seats.</p>
        <button onClick={() => router.push("/")} className="ciao-btn-primary">Browse Schedules</button>
      </div>
    );
  }

  return (
    <div className="min-h-screen pt-28 pb-20 px-5 bg-[#F6F8F6]">
      {/* Page Header */}
      <div className="max-w-[1180px] mx-auto mb-8 text-center md:text-left flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div>
          <Badge variant="gold" className="mb-2">Seat Allocation & Fare Calculation</Badge>
          <h1 className="text-2xl md:text-3xl font-extrabold text-[#193542]">Interactive Seat Selection</h1>
          <p className="text-sm text-[#516A74] mt-1">Schedule #{scheduleId} • Choose up to 6 seats</p>
        </div>
        
        <div className="flex items-center gap-3 bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] py-2 px-4 rounded-xl text-xs md:text-sm text-[#516A74]">
          <Clock className="w-4 h-4 text-[#087478]" />
          <span>Fares authoritative at checkout</span>
        </div>
      </div>

      <main className="max-w-[1180px] mx-auto grid grid-cols-1 lg:grid-cols-[1.5fr_1fr] gap-8">
        {/* Interactive Coach Seat Map */}
        <div className="bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] p-6 md:p-8 rounded-3xl">
          {/* Legend */}
          <div className="flex justify-center flex-wrap gap-6 mb-8 pb-6 border-b border-[rgba(25,53,66,0.12)] text-xs md:text-sm font-semibold">
            <div className="flex items-center gap-2">
              <div className="w-4 h-4 bg-[#EAF3F0] border border-[rgba(203,213,225,0.2)] rounded-md" />
              <span className="text-[#516A74]">Available</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-4 h-4 bg-[#087478] rounded-md shadow-sm" />
              <span className="text-[#087478]">Selected</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-4 h-4 bg-[#F7FAF9] border border-[rgba(203,213,225,0.06)] rounded-md opacity-60" />
              <span className="text-[#5E7480]">Unavailable / Booked</span>
            </div>
          </div>

          {loading ? (
            <div className="text-center py-20 text-[#516A74] text-sm">
              <span className="w-5 h-5 border-2 border-[#087478] border-t-transparent rounded-full inline-block animate-spin mr-2" />
              Loading bus seat layout...
            </div>
          ) : (
            <div className="bg-[#EDF3F1] p-6 md:p-8 rounded-2xl border border-[rgba(25,53,66,0.12)] mx-auto max-w-[420px]">
              {/* Front Cabin & Driver */}
              <div className="flex items-center justify-between pb-6 mb-6 border-b border-dashed border-[rgba(25,53,66,0.16)]">
                <span className="text-xs font-bold tracking-wider text-[#5E7480] uppercase">Front Cabin</span>
                <div className="w-10 h-10 rounded-xl bg-[#EAF3F0] border border-[rgba(25,53,66,0.16)] flex items-center justify-center text-[#087478]" title="Driver Cabin">
                  <Bus className="w-5 h-5" />
                </div>
              </div>

              {/* 2x2 Seating Grid with Central Aisle */}
              <div className="grid grid-cols-[auto_auto_1fr_auto_auto] gap-3 justify-between">
                {Array.from({ length: Math.ceil(capacity / 4) }).map((_, row) => (
                  <div key={row} className="contents">
                    {renderSeat(row * 4 + 1)}
                    {renderSeat(row * 4 + 2)}
                    <div className="w-6 flex items-center justify-center">
                      <div className="w-px h-full bg-[rgba(203,213,225,0.1)]" />
                    </div>
                    {renderSeat(row * 4 + 3)}
                    {renderSeat(row * 4 + 4)}
                  </div>
                ))}
              </div>

              <div className="mt-8 pt-4 text-center border-t border-[rgba(203,213,225,0.1)]">
                <span className="text-xs text-[#5E7480] uppercase tracking-widest font-semibold">Rear Cabin</span>
              </div>
            </div>
          )}
        </div>

        {/* Booking Summary Panel */}
        <div className="flex flex-col gap-5">
          <div className="bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] p-6 md:p-8 rounded-3xl sticky top-28 shadow-xl">
            <div className="flex items-center justify-between pb-4 mb-4 border-b border-[rgba(25,53,66,0.12)]">
              <h3 className="text-lg font-bold text-[#193542] flex items-center gap-2">
                <Ticket className="w-5 h-5 text-[#087478]" /> Reservation Summary
              </h3>
              <Badge variant="gold">Direct Express</Badge>
            </div>

            {error && (
              <div role="alert" className="mb-4 p-3 bg-red-50 border border-red-500/40 text-red-700 text-xs md:text-sm rounded-xl flex items-start gap-2">
                <AlertCircle className="w-4 h-4 shrink-0 text-red-400 mt-0.5" />
                <span>{error}</span>
              </div>
            )}

            <div className="space-y-3.5 mb-6 text-sm md:text-base">
              <div className="flex justify-between items-center text-[#5E7480]">
                <span>Standard Seat Fare</span>
                <span className="text-[#193542] font-medium">LKR {baseFare.toFixed(2)}</span>
              </div>

              <div className="flex justify-between items-center text-[#5E7480]">
                <span>Selected Seats ({selectedSeats.size})</span>
                <span className="text-[#087478] font-bold">
                  {selectedSeats.size > 0 ? Array.from(selectedSeats).sort((a,b)=>a-b).join(", ") : "None"}
                </span>
              </div>

              <div className="h-px bg-[rgba(25,53,66,0.12)] my-2" />

              <div className="flex justify-between items-baseline">
                <span className="text-base font-bold text-[#193542]">Total Calculation</span>
                <div className="text-right">
                  <span className="text-2xl font-extrabold text-[#087478]">
                    LKR {(baseFare * selectedSeats.size).toFixed(2)}
                  </span>
                  <div className="text-xs text-[#5E7480]">Includes taxes & seat lock</div>
                </div>
              </div>
            </div>

            <button 
              type="button"
              className="ciao-btn-primary w-full justify-center h-12 text-sm md:text-base font-bold disabled:opacity-50"
              disabled={selectedSeats.size === 0 || locking || loading || !bookable} 
              onClick={proceedToCheckout}
            >
              {locking ? "Locking 10-Minute Hold..." : "Lock Seats & Proceed to Payment"}
              <ArrowRight className="w-4 h-4 ml-2" />
            </button>

            <div className="mt-4 p-3 rounded-xl bg-[#F7FAF9] border border-[rgba(25,53,66,0.12)] flex items-start gap-2.5 text-xs md:text-sm text-[#516A74]">
              <ShieldCheck className="w-4 h-4 text-[#087478] shrink-0 mt-0.5" />
              <span>
                Seats are held for exactly 10 minutes upon proceeding to prevent concurrent booking conflicts.
              </span>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}

export default function SelectSeats() {
  return (
    <Suspense fallback={<div className="min-h-screen flex items-center justify-center text-sm text-[#5E7480]">Loading seat configuration...</div>}>
      <SelectSeatsContent />
    </Suspense>
  );
}
