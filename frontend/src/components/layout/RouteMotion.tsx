"use client";

import { useEffect, ViewTransition } from "react";
import { usePathname } from "next/navigation";

type RouteMotionProps = {
  children: React.ReactNode;
  area: "client" | "admin" | "conductor";
};

export default function RouteMotion({ children, area }: RouteMotionProps) {
  const pathname = usePathname();
  useEffect(() => {
    if (typeof document.startViewTransition === "function") {
      document.documentElement.classList.add("ciao-native-view-transitions");
    }
  }, []);

  const motion = area === "admin" || area === "conductor"
    ? "ciao-route-workspace"
    : pathname === "/"
      ? "ciao-route-home"
      : /^\/(select-seats|checkout-payment|view-ticket|my-bookings)(\/|$)/.test(pathname)
        ? "ciao-route-journey"
        : "ciao-route-client";

  return (
    <ViewTransition key={pathname} enter={motion} exit={motion} default="none">
      <div className={`ciao-route-surface ${motion}`} data-route-motion={motion}>
        {children}
      </div>
    </ViewTransition>
  );
}
