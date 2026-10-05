import RouteMotion from "@/components/layout/RouteMotion";

export default function ClientTemplate({ children }: { children: React.ReactNode }) {
  return <RouteMotion area="client">{children}</RouteMotion>;
}
