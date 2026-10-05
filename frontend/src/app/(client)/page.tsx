"use client";

import { SearchableSelect } from "@/components/ui/searchable-select";

import { useEffect, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { API_BASE_URL } from "@/lib/api";
import { ArrowRight, BusFront, CalendarDays, Check, ChevronRight, MapPin, Package, Search, ShieldCheck, Ticket, Users, ArrowUpRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

type Route = { id: number; origin: string; destination: string; baseFare: number };
type Schedule = {
  id: number;
  departureTime: string;
  arrivalTime: string;
  status: string;
  effectiveSeatFare?: number;
  route?: { origin: string; destination: string; baseFare: number };
  bus?: { plateNumber: string; capacity: number };
};

const localToday = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
};

export default function HomePage() {
  const [routes, setRoutes] = useState<Route[]>([]);
  const [origin, setOrigin] = useState("");
  const [destination, setDestination] = useState("");
  const [date, setDate] = useState(localToday);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [searched, setSearched] = useState(false);
  const [loading, setLoading] = useState(false);
  const [searchError, setSearchError] = useState("");

  useEffect(() => {
    fetch(`${API_BASE_URL}/api/routes`)
      .then((res) => { if (!res.ok) throw new Error("Unable to load routes"); return res.json(); })
      .then((data) => { if (Array.isArray(data)) setRoutes(data); })
      .catch(() => setRoutes([]));
  }, []);

  const origins = Array.from(new Set(routes.map((route) => route.origin)));
  const destinations = Array.from(new Set(routes.filter((route) => !origin || route.origin === origin).map((route) => route.destination)));

  const handleSearch = async (event: React.FormEvent) => {
    event.preventDefault();
    setLoading(true);
    setSearched(true);
    setSearchError("");
    try {
      const query = new URLSearchParams();
      query.set("origin", origin);
      query.set("destination", destination);
      if (date) query.set("date", date);
      const response = await fetch(`${API_BASE_URL}/api/schedules/search?${query}`);
      if (!response.ok) throw new Error("Could not load journeys. Please try again.");
      const data = await response.json();
      setSchedules(Array.isArray(data) ? data : []);
    } catch (error) {
      setSchedules([]);
      setSearchError(error instanceof Error ? error.message : "Could not load journeys. Please try again.");
    } finally {
      setLoading(false);
      requestAnimationFrame(() => document.getElementById("journey-results")?.scrollIntoView({ behavior: "smooth", block: "start" }));
    }
  };

  return (
    <div className="min-h-screen bg-[#f6f8f6] text-[#193542]">
      <section className="relative isolate min-h-[620px] overflow-hidden bg-[#e5f1ed]">
        <Image src="/images/hero-coastal-journey.png" alt="Intercity coach travelling along a scenic coastal road" fill priority sizes="100vw" quality={100} className="object-cover object-[64%_center]" />
        <div className="absolute inset-0" style={{ background: "linear-gradient(90deg, rgba(244,249,245,.98) 0%, rgba(244,249,245,.96) 24%, rgba(244,249,245,.78) 39%, rgba(244,249,245,0) 57%)" }} />
        <div className="absolute inset-0" style={{ background: "linear-gradient(0deg, #f6f8f6 0%, rgba(246,248,246,0) 7%)" }} />
        <div className="relative mx-auto flex min-h-[620px] max-w-7xl items-center px-5 pb-32 pt-12 sm:px-8 lg:px-10">
          <div className="max-w-[650px] animate-hero-content">
            <span className="mb-7 inline-flex items-center gap-3 text-xs font-extrabold uppercase tracking-[.22em] text-[#087478] sm:text-xs">
              <span aria-hidden="true" className="h-px w-8 bg-[#087478]" /> Bus travel, beautifully simple
            </span>
            <h1 className="max-w-[650px] text-[clamp(3.15rem,5.9vw,5.8rem)] font-extrabold leading-[.98] tracking-[-.075em] text-[#193542]">
              <span className="block">The journey</span>
              <span className="block text-[#087478]">starts here.</span>
            </h1>
            <p className="mt-7 max-w-[420px] text-base font-medium leading-7 text-[#3f606b] sm:text-lg sm:leading-8">
              Find your bus, choose your seat, and book your e-ticket in a few simple steps.
            </p>
            <div className="mt-9 flex flex-wrap items-center gap-x-7 gap-y-4">
              <a href="#journey-search" className="inline-flex min-h-12 items-center justify-center gap-3 rounded-full bg-[#087478] px-6 text-sm font-extrabold text-white shadow-[0_12px_28px_rgba(8,116,120,.22)] transition duration-300 hover:-translate-y-0.5 hover:bg-[#075e62] hover:shadow-[0_16px_32px_rgba(8,116,120,.26)] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-[#087478]">
                Find your bus <ArrowRight className="size-4" />
              </a>
              <a href="#services" className="inline-flex min-h-12 items-center gap-2 text-sm font-bold text-[#193542] underline decoration-[#93c9be] underline-offset-8 transition hover:text-[#087478] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-[#087478]">
                Explore our services <ArrowRight className="size-4" />
              </a>
            </div>
          </div>
        </div>
      </section>

      <section id="journey-search" className="relative z-10 mx-auto -mt-24 max-w-7xl scroll-mt-24 px-5 sm:px-8 lg:px-10">
        <div className="rounded-[28px] border border-[#dce7e4] bg-white p-6 shadow-[0_24px_70px_rgba(25,53,66,.13)] sm:p-8 lg:p-10">
          <div className="mb-6 flex flex-wrap items-end justify-between gap-2">
            <div>
              <p className="text-xs font-extrabold uppercase tracking-[.18em] text-[#087478]">Your next trip</p>
              <h2 className="mt-1 text-2xl font-extrabold tracking-tight sm:text-3xl">Where can we take you?</h2>
            </div>
            <span className="text-sm text-[#647b84]">Search available bus schedules</span>
          </div>
          <form onSubmit={handleSearch} className="grid gap-4 lg:grid-cols-[1fr_1fr_1fr_auto] lg:items-end">
            <label className="block text-xs font-extrabold uppercase tracking-wide text-[#516a74]">
              <span className="mb-2 flex items-center gap-2"><MapPin className="size-4 text-[#087478]" /> From</span>
              <SearchableSelect value={origin} onChange={(event) => { setOrigin(event.target.value); setDestination(""); }} className="h-13 w-full rounded-2xl border border-[#d3e3df] bg-[#f7faf9] px-4 text-base font-semibold normal-case tracking-normal text-[#193542] outline-none transition focus:border-[#087478] focus:ring-2 focus:ring-[#b8dfd6]">
                <option value="">Any departure city</option>
                {origins.map((city) => <option key={city} value={city}>{city}</option>)}
              </SearchableSelect>
            </label>
            <label className="block text-xs font-extrabold uppercase tracking-wide text-[#516a74]">
              <span className="mb-2 flex items-center gap-2"><MapPin className="size-4 text-[#087478]" /> To</span>
              <SearchableSelect value={destination} onChange={(event) => setDestination(event.target.value)} className="h-13 w-full rounded-2xl border border-[#d3e3df] bg-[#f7faf9] px-4 text-base font-semibold normal-case tracking-normal text-[#193542] outline-none transition focus:border-[#087478] focus:ring-2 focus:ring-[#b8dfd6]">
                <option value="">Any destination</option>
                {destinations.map((city) => <option key={city} value={city}>{city}</option>)}
              </SearchableSelect>
            </label>
            <label className="block text-xs font-extrabold uppercase tracking-wide text-[#516a74]">
              <span className="mb-2 flex items-center gap-2"><CalendarDays className="size-4 text-[#087478]" /> Travel date</span>
              <Input type="date" value={date} min={localToday()} onChange={(event) => setDate(event.target.value)} className="h-13 w-full rounded-2xl border border-[#d3e3df] bg-[#f7faf9] px-4 text-base font-semibold normal-case tracking-normal text-[#193542] outline-none transition focus:border-[#087478] focus:ring-2 focus:ring-[#b8dfd6]" />
            </label>
            <Button type="submit" size="lg" disabled={loading} className="h-13 rounded-2xl px-7 text-base"><Search className="size-5" /> {loading ? "Searching…" : "Search Bus Schedules"}</Button>
          </form>
        </div>
      </section>

      <section id="journey-results" className="mx-auto max-w-7xl scroll-mt-28 px-5 sm:px-8 lg:px-10">
        {searched && <div className="pt-14 animate-fade-in">
          <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
            <div><p className="text-xs font-extrabold uppercase tracking-[.16em] text-[#087478]">Live seat availability</p><h2 className="mt-1 text-3xl font-extrabold">Available Bus Schedules</h2></div>
            {!loading && !searchError && <span className="rounded-full bg-[#e2f3ee] px-4 py-2 text-sm font-bold text-[#087478]">{schedules.length} departures found</span>}
          </div>
          {searchError ? <div role="alert" className="rounded-2xl border border-red-200 bg-red-50 p-6 text-red-700">{searchError}</div> : loading ? <div role="status" className="rounded-2xl border border-[#dce7e4] bg-white p-8 text-[#516a74]">Finding available buses…</div> : schedules.length === 0 ? <div className="rounded-3xl border border-[#dce7e4] bg-white px-6 py-12 text-center"><BusFront className="mx-auto mb-4 size-10 text-[#087478]" /><h3 className="text-xl font-extrabold">No buses found for this search</h3><p className="mx-auto mt-2 max-w-lg text-sm text-[#516a74]">Try another date or a different city pair. Recurring schedules appear for the dates they operate.</p></div> : <div className="grid gap-4">{schedules.map((schedule) => <div key={schedule.id} className="photo-card flex flex-col justify-between gap-5 rounded-3xl border border-[#dce7e4] bg-white p-6 shadow-sm md:flex-row md:items-center">
            <div className="flex items-center gap-5 sm:gap-8"><div className="rounded-2xl bg-[#e7f4f0] px-5 py-4 text-center"><div className="text-xl font-extrabold text-[#087478]">{new Date(schedule.departureTime).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</div><div className="text-xs font-semibold text-[#516a74]">Departure</div></div><div><h3 className="text-lg font-extrabold sm:text-xl">{schedule.route?.origin} <span className="text-[#087478]">→</span> {schedule.route?.destination}</h3><p className="mt-1 text-sm text-[#647b84]">{schedule.bus?.plateNumber ? `Bus ${schedule.bus.plateNumber}` : "Intercity bus"}{schedule.bus?.capacity ? ` · ${schedule.bus.capacity} seats` : ""}</p></div></div>
            <div className="flex items-center justify-between gap-6 border-t border-[#e5eeea] pt-4 md:border-l md:border-t-0 md:pl-7 md:pt-0"><div><div className="text-xs font-semibold text-[#647b84]">Fare from</div><div className="whitespace-nowrap text-xl font-extrabold text-[#193542]">LKR {Number(schedule.effectiveSeatFare ?? schedule.route?.baseFare).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div></div><Link href={`/select-seats?scheduleId=${schedule.id}`}><Button>Select seats <ArrowRight className="size-4" /></Button></Link></div>
          </div>)}</div>}
        </div>}
      </section>

      <section className="mx-auto max-w-7xl px-5 py-20 sm:px-8 lg:px-10 lg:py-28">
        <div className="mb-9 flex flex-wrap items-end justify-between gap-4"><div><p className="text-xs font-extrabold uppercase tracking-[.18em] text-[#087478]">The places we love</p><h2 className="mt-2 text-3xl font-extrabold tracking-tight sm:text-4xl">Make room for somewhere new.</h2><p className="mt-2 text-[#516a74]">A coast, a hillside, a city you have missed.</p></div><a href="#journey-search" className="inline-flex items-center gap-2 font-bold text-[#087478] hover:underline">Search journeys <ArrowUpRight className="size-4" /></a></div>
        <div className="grid gap-6 md:grid-cols-2">
          {[
            { image: "/images/destination-kandy.png", place: "Kandy", line: "Take the scenic road to the hills." },
            { image: "/images/destination-galle.png", place: "Galle", line: "Follow the coast to your next escape." },
          ].map((destinationCard) => <a href="#journey-search" key={destinationCard.place} className="photo-card group relative block h-80 overflow-hidden rounded-[28px] bg-[#d9e9e2] sm:h-96"><Image src={destinationCard.image} alt={`${destinationCard.place}, Sri Lanka`} fill className="object-cover" /><div className="absolute inset-0 bg-gradient-to-t from-[#102f3b]/85 via-[#102f3b]/10 to-transparent" /><div className="absolute bottom-0 left-0 right-0 flex items-end justify-between p-7 sm:p-9"><div><span className="text-xs font-extrabold uppercase tracking-[.18em] text-[#abe3d6]">Explore Sri Lanka</span><h3 className="mt-1 !text-white text-3xl font-extrabold sm:text-4xl">{destinationCard.place}</h3><p className="mt-1 text-sm text-white/85">{destinationCard.line}</p></div><span className="flex size-11 shrink-0 items-center justify-center rounded-full bg-white text-[#193542] transition group-hover:translate-x-1 group-hover:-translate-y-1"><ArrowUpRight className="size-5" /></span></div></a>)}
        </div>
      </section>

      <section id="services" className="scroll-mt-24 bg-[#eaf3ef] py-20 sm:py-24"><div className="mx-auto max-w-7xl px-5 sm:px-8 lg:px-10"><div className="mb-9"><p className="text-xs font-extrabold uppercase tracking-[.18em] text-[#087478]">More ways to move</p><h2 className="mt-2 text-3xl font-extrabold tracking-tight sm:text-4xl">A little more than a bus ride.</h2><p className="mt-2 text-[#516a74]">The essentials for every kind of journey, all in one place.</p></div><div className="grid gap-5 md:grid-cols-3">
        {[
          { title: "Parcel Booking & Tracking", text: "Book a parcel shipment and follow its delivery status.", href: "/book-parcel", icon: Package, action: "Book a parcel" },
          { title: "Group Booking", text: "Request a bus for your event, school tour, or group journey.", href: "/group-booking", icon: Users, action: "Request a group booking" },
          { title: "Booking History & E-Tickets", text: "View your reservations and download your e-tickets.", href: "/my-bookings", icon: Ticket, action: "My bookings" },
        ].map((service) => { const Icon = service.icon; return <Link href={service.href} key={service.title} className="photo-card group flex min-h-64 flex-col rounded-[26px] border border-[#d9e8e2] bg-white p-7 shadow-sm"><span className="flex size-12 items-center justify-center rounded-2xl bg-[#e4f3ed] text-[#087478] transition-transform duration-300 group-hover:-rotate-6 group-hover:scale-110"><Icon className="size-6" /></span><h3 className="mt-6 text-xl font-extrabold">{service.title}</h3><p className="mt-2 flex-1 text-sm leading-7 text-[#516a74]">{service.text}</p><span className="mt-6 inline-flex items-center gap-2 text-sm font-extrabold text-[#087478]">{service.action} <ChevronRight className="size-4 transition group-hover:translate-x-1" /></span></Link>; })}
      </div></div></section>

      <section className="mx-auto grid max-w-7xl items-center gap-12 px-5 py-20 sm:px-8 lg:grid-cols-2 lg:px-10 lg:py-28"><div className="relative h-[380px] overflow-hidden rounded-[30px] bg-[#e3eee9] shadow-[0_20px_50px_rgba(25,53,66,.1)] sm:h-[460px]"><Image src="/images/why-ciao-passenger.png" alt="Passenger enjoying the hill country view from an intercity bus" fill className="object-cover" /></div><div className="max-w-lg"><p className="text-xs font-extrabold uppercase tracking-[.18em] text-[#087478]">Why Ciao</p><h2 className="mt-3 text-4xl font-extrabold leading-tight tracking-[-.05em] sm:text-5xl">The easy part of getting there.</h2><p className="mt-5 text-base leading-8 text-[#516a74]">We have made the small things simple: clear schedules, a seat that is yours, and your ticket right where you need it. So you can focus on what is ahead.</p><div className="mt-8 grid gap-4 text-sm font-semibold text-[#193542]"><span className="flex items-center gap-3"><Check className="size-5 text-[#087478]" /> Choose your preferred seat</span><span className="flex items-center gap-3"><ShieldCheck className="size-5 text-[#087478]" /> See your booking details anytime</span><span className="flex items-center gap-3"><BusFront className="size-5 text-[#087478]" /> Travel across Sri Lanka with ease</span></div><a href="#journey-search" className="mt-9 inline-flex items-center gap-2 font-extrabold text-[#087478] hover:underline">Plan your journey <ArrowRight className="size-4" /></a></div></section>
    </div>
  );
}
