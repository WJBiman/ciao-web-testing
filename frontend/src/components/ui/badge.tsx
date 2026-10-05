import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const badgeVariants = cva(
  "inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold tracking-wide transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2",
  {
    variants: {
      variant: {
        default: "bg-[#e2f3ee] text-[#087478] border border-[#bde1d6]",
        gold: "bg-[#e2f3ee] text-[#087478] border border-[#bde1d6]",
        success: "bg-[#e3f5ec] text-[#065f46] border border-[#a7dec3]",
        warning: "bg-[#fff2d7] text-[#92400e] border border-[#f4d185]",
        danger: "bg-[#fde9e9] text-[#b91c1c] border border-[#f3b8b8]",
        destructive: "bg-[#fde9e9] text-[#b91c1c] border border-[#f3b8b8]",
        info: "bg-[#e4f3fb] text-[#075985] border border-[#a9d5eb]",
        secondary: "bg-[#eaf3f0] text-[#516A74] border border-[#dce7e4]",
        outline: "border border-[#dce7e4] text-[#193542] bg-white",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, ...props }: BadgeProps) {
  return (
    <div className={cn(badgeVariants({ variant }), className)} {...props} />
  );
}

export { Badge, badgeVariants };
