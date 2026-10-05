import RouteMotion from "@/components/layout/RouteMotion";

export default function ConductorTemplate({ children }: { children: React.ReactNode }) {
  return <RouteMotion area="conductor">{children}</RouteMotion>;
}
