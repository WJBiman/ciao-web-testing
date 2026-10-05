import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-xl text-sm font-semibold transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#087478] focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 active:scale-[0.98] hover:-translate-y-0.5 cursor-pointer",
  {
    variants: {
      variant: {
        default:
          "ciao-action bg-[#087478] text-white font-bold hover:bg-[#096E73] shadow-[0_6px_18px_rgba(8,127,129,0.18)] hover:shadow-[0_10px_24px_rgba(8,127,129,0.25)]",
        gold:
          "ciao-action bg-[#087478] text-white font-bold hover:bg-[#096E73] shadow-[0_6px_18px_rgba(8,127,129,0.18)] hover:shadow-[0_10px_24px_rgba(8,127,129,0.25)]",
        primary:
          "ciao-action bg-[#087478] text-white font-bold hover:bg-[#096E73] shadow-[0_6px_18px_rgba(8,127,129,0.18)]",
        outline:
          "border border-[#cddfda] bg-white text-[#193542] hover:bg-[#eaf4f2] hover:border-[#087478]/50",
        secondary:
          "bg-[#eaf4f2] text-[#193542] hover:bg-[#dceee8] border border-[#d3e5df]",
        ghost:
          "text-[#516A74] hover:bg-[#eaf4f2] hover:text-[#193542]",
        destructive:
          "bg-[#EF4444] text-white hover:bg-[#DC2626] shadow-sm",
        link:
          "text-[#087478] underline-offset-4 hover:underline p-0 h-auto",
      },
      size: {
        default: "h-11 px-5 py-2.5",
        sm: "h-9 rounded-lg px-3.5 text-xs",
        lg: "h-12 rounded-xl px-7 text-base font-bold",
        icon: "h-10 w-10",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean;
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, ...props }, ref) => {
    return (
      <button
        className={cn(buttonVariants({ variant, size, className }))}
        ref={ref}
        {...props}
      />
    );
  }
);
Button.displayName = "Button";

export { Button, buttonVariants };
