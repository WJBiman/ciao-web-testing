import RouteMotion from "@/components/layout/RouteMotion";

export default function AdminTemplate({ children }: { children: React.ReactNode }) {
  return <RouteMotion area="admin">{children}</RouteMotion>;
}
