import { Bus } from "lucide-react";

export default function SiteLoading({ intro = false }: { intro?: boolean }) {
  return (
    <div
      className={intro ? "ciao-site-intro" : "ciao-route-loading"}
      role="status"
      aria-label="Loading Ciao"
    >
      <div className="ciao-loading-content">
        <span className="ciao-loading-wordmark">ciao<span>.</span></span>
        <div className="ciao-loading-road" aria-hidden="true">
          <Bus className="ciao-loading-bus" size={30} strokeWidth={1.8} />
        </div>
        <span className="ciao-loading-caption">Getting your journey ready</span>
      </div>
    </div>
  );
}
