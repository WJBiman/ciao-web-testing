import * as React from "react";
import { cn } from "@/lib/utils";
import { DateTimeField } from "@/components/ui/date-time-field";

const Input = React.forwardRef<HTMLInputElement, React.ComponentProps<"input">>(
  ({ className, type, ...props }, ref) => {
    const inputClassName = cn(
      "flex h-11 w-full rounded-xl border border-[#d4e3df] bg-[#f7faf9] px-3.5 py-2 text-base text-[#193542] transition-all hover:border-[#a9ccc4] file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-[#71858B] focus-visible:outline-none focus-visible:border-[#087478] focus-visible:ring-3 focus-visible:ring-[#087478]/15 disabled:cursor-not-allowed disabled:opacity-50",
      className
    );
    if (type === "date" || type === "datetime-local") {
      return <DateTimeField {...props} type={type as "date" | "datetime-local"} ref={ref} className={inputClassName} />;
    }
    return (
      <input
        type={type}
        className={inputClassName}
        ref={ref}
        {...props}
      />
    );
  }
);
Input.displayName = "Input";

export { Input };
