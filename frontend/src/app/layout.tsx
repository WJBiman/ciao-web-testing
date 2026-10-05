import type { Metadata } from "next";
import { Manrope } from "next/font/google";
import SiteLoading from "@/components/layout/SiteLoading";
import "./globals.css";

const manrope = Manrope({
  variable: "--font-manrope",
  subsets: ["latin"],
  weight: ["300", "400", "500", "600", "700", "800"],
});

export const metadata: Metadata = {
  title: "Ciao | Bus Ticket Reservation System",
  description: "Ciao bus ticket reservations with seat selection, group booking, parcel booking and tracking, and lost and found services.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html
      lang="en"
      suppressHydrationWarning
      className={`${manrope.variable} h-full antialiased font-sans`}
    >
      <head>
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" />
      </head>
      <body suppressHydrationWarning className="min-h-full flex flex-col bg-[var(--ciao-bg)] text-[var(--ciao-text-primary)]">
        <SiteLoading intro />
        {children}
      </body>
    </html>

  );
}
