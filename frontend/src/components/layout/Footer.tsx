import Link from "next/link";
import { ArrowRight, ArrowUpRight } from "lucide-react";

const footerGroups = [
  {
    title: "Travel",
    links: [
      { label: "Find a bus", href: "/#journey-search" },
      { label: "My bookings & e-tickets", href: "/my-bookings" },
      { label: "Group booking", href: "/group-booking" },
    ],
  },
  {
    title: "Services",
    links: [
      { label: "Book a parcel", href: "/book-parcel" },
      { label: "Track a parcel", href: "/track-parcel" },
      { label: "Lost & found", href: "/report-lost" },
    ],
  },
  {
    title: "Your account",
    links: [
      { label: "Profile", href: "/profile" },
      { label: "Sign in", href: "/login" },
    ],
  },
];

export default function Footer() {
  return (
    <footer className="ciao-footer relative isolate overflow-hidden bg-[#12323d] text-white">
      <div aria-hidden="true" className="ciao-footer-pattern pointer-events-none absolute inset-0 -z-10" />
      <div aria-hidden="true" className="pointer-events-none absolute -right-28 -top-40 -z-10 h-[460px] w-[460px] rounded-full border border-white/[.08] sm:-right-16" />
      <div aria-hidden="true" className="pointer-events-none absolute -right-12 -top-20 -z-10 h-[310px] w-[310px] rounded-full border border-white/[.06] sm:right-4" />

      <div className="relative mx-auto max-w-7xl px-5 sm:px-8 lg:px-10">
        <div className="flex flex-col gap-7 border-b border-white/15 py-12 sm:py-14 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p className="mb-3 text-xs font-extrabold uppercase tracking-[.22em] text-[#8fd8cb]">Your journey starts with Ciao</p>
            <h2 className="max-w-2xl text-[clamp(2rem,4vw,3.5rem)] font-extrabold leading-tight tracking-[-.055em] text-white">
              Ready to see where the road takes you?
            </h2>
          </div>
          <Link
            href="/#journey-search"
            className="inline-flex min-h-12 shrink-0 items-center justify-center gap-3 self-start rounded-full bg-[#a7e1d3] px-6 text-sm font-extrabold text-[#12323d] transition duration-300 hover:-translate-y-0.5 hover:bg-white focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-white lg:self-auto"
          >
            Find your bus <ArrowUpRight className="size-4" />
          </Link>
        </div>

        <div className="grid gap-10 py-14 sm:grid-cols-2 lg:grid-cols-[1.6fr_1fr_1fr_1fr] lg:gap-14">
          <div className="max-w-sm">
            <Link href="/" aria-label="Ciao home" className="inline-flex text-[2.7rem] font-extrabold leading-none tracking-[-.09em] text-white focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-white">
              ciao<span className="text-[#8fd8cb]">.</span>
            </Link>
            <p className="mt-5 max-w-xs text-sm leading-7 text-[#c2d3d7]">
              Plan your bus journey, choose your seat, and keep your travel details close at hand.
            </p>
            <div className="mt-7 flex items-center gap-3 text-xs font-semibold uppercase tracking-[.18em] text-[#8fd8cb]">
              <span className="h-px w-8 bg-[#8fd8cb]" /> Made for the way you move
            </div>
          </div>

          {footerGroups.map((group) => (
            <nav key={group.title} aria-label={group.title}>
              <h3 className="mb-5 text-sm font-extrabold tracking-wide text-white">{group.title}</h3>
              <ul className="space-y-3.5">
                {group.links.map((link) => (
                  <li key={link.href}>
                    <Link
                      href={link.href}
                      className="group inline-flex items-center gap-1.5 text-sm text-[#c2d3d7] transition-colors hover:text-white focus-visible:rounded-sm focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-white"
                    >
                      {link.label}
                      <ArrowRight aria-hidden="true" className="size-3.5 -translate-x-1 opacity-0 transition-all group-hover:translate-x-0 group-hover:opacity-100 group-focus-visible:translate-x-0 group-focus-visible:opacity-100" />
                    </Link>
                  </li>
                ))}
              </ul>
            </nav>
          ))}
        </div>

        <div className="flex flex-col gap-2 border-t border-white/15 py-6 text-xs text-[#a5bbc1] sm:flex-row sm:items-center sm:justify-between">
          <p>© {new Date().getFullYear()} Ciao (Pvt) Ltd. All rights reserved.</p>
          <p>Travel across Sri Lanka with Ciao.</p>
        </div>
      </div>
    </footer>
  );
}
