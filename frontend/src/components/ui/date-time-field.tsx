"use client";

import * as React from "react";
import { createPortal } from "react-dom";
import { CalendarDays, ChevronLeft, ChevronRight, Clock } from "lucide-react";
import { cn } from "@/lib/utils";

type DateTimeProps = Omit<React.InputHTMLAttributes<HTMLInputElement>, "type"> & {
  type: "date" | "datetime-local";
};
type Position = { left: number; width: number; top?: number; bottom?: number };

function fromIso(iso: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!match) return null;
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  return date.getFullYear() === Number(match[1]) && date.getMonth() === Number(match[2]) - 1 && date.getDate() === Number(match[3]) ? date : null;
}

function toIso(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function displayValue(value: string, withTime: boolean): string {
  const date = fromIso(value);
  if (!date) return withTime ? "Select date and time" : "Select a date";
  const label = new Intl.DateTimeFormat("en-GB", { day: "2-digit", month: "short", year: "numeric" }).format(date);
  return withTime && value.includes("T") ? `${label} · ${value.slice(11, 16)}` : label;
}

export const DateTimeField = React.forwardRef<HTMLInputElement, DateTimeProps>(function DateTimeField({
  type, value, defaultValue, onChange, className, id, name, required, disabled, min, max,
  ...nativeProps
}, forwardedRef) {
  const [uncontrolledValue, setUncontrolledValue] = React.useState(String(defaultValue ?? ""));
  const [open, setOpen] = React.useState(false);
  const [month, setMonth] = React.useState(() => {
    const initial = fromIso(String(value ?? defaultValue ?? "")) ?? new Date();
    return new Date(initial.getFullYear(), initial.getMonth(), 1);
  });
  const [draftDate, setDraftDate] = React.useState("");
  const [hour, setHour] = React.useState("09");
  const [minute, setMinute] = React.useState("00");
  const [position, setPosition] = React.useState<Position>({ left: 0, width: 304, top: 0 });
  const triggerRef = React.useRef<HTMLButtonElement>(null);
  const panelRef = React.useRef<HTMLDivElement>(null);
  const inputRef = React.useRef<HTMLInputElement>(null);
  const selectedValue = String(value ?? uncontrolledValue);
  const withTime = type === "datetime-local";
  const minDay = String(min ?? "").slice(0, 10);
  const maxDay = String(max ?? "").slice(0, 10);

  const setInputRef = (node: HTMLInputElement | null) => {
    inputRef.current = node;
    if (typeof forwardedRef === "function") forwardedRef(node);
    else if (forwardedRef) forwardedRef.current = node;
  };

  const measure = React.useCallback(() => {
    const rect = triggerRef.current?.getBoundingClientRect();
    if (!rect) return;
    const width = Math.min(320, window.innerWidth - 16);
    const below = window.innerHeight - rect.bottom;
    const above = rect.top;
    const upward = below < 370 && above > below;
    setPosition({
      left: Math.max(8, Math.min(rect.left, window.innerWidth - width - 8)),
      width,
      ...(upward ? { bottom: window.innerHeight - rect.top + 6 } : { top: rect.bottom + 6 }),
    });
  }, []);

  const show = () => {
    if (disabled) return;
    const current = fromIso(selectedValue) ?? new Date();
    setMonth(new Date(current.getFullYear(), current.getMonth(), 1));
    setDraftDate(fromIso(selectedValue) ? selectedValue.slice(0, 10) : "");
    setHour(selectedValue.includes("T") ? selectedValue.slice(11, 13) : "09");
    setMinute(selectedValue.includes("T") ? selectedValue.slice(14, 16) : "00");
    measure();
    setOpen(true);
  };

  const commit = (next: string) => {
    if (value === undefined) setUncontrolledValue(next);
    const element = inputRef.current;
    if (element) {
      element.value = next;
      onChange?.({ target: element, currentTarget: element } as React.ChangeEvent<HTMLInputElement>);
    }
    setOpen(false);
    requestAnimationFrame(() => triggerRef.current?.focus());
  };

  React.useEffect(() => {
    if (!open) return;
    const outside = (event: PointerEvent) => {
      if (!triggerRef.current?.contains(event.target as Node) && !panelRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === "Escape") { setOpen(false); triggerRef.current?.focus(); }
    };
    document.addEventListener("pointerdown", outside);
    document.addEventListener("keydown", escape);
    window.addEventListener("resize", measure);
    window.addEventListener("scroll", measure, true);
    return () => {
      document.removeEventListener("pointerdown", outside);
      document.removeEventListener("keydown", escape);
      window.removeEventListener("resize", measure);
      window.removeEventListener("scroll", measure, true);
    };
  }, [open, measure]);

  const firstWeekday = (new Date(month.getFullYear(), month.getMonth(), 1).getDay() + 6) % 7;
  const daysInMonth = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
  const today = toIso(new Date());
  const monthName = new Intl.DateTimeFormat("en-GB", { month: "long", year: "numeric" }).format(month);
  const timeValid = /^\d{1,2}$/.test(hour) && /^\d{1,2}$/.test(minute) && Number(hour) < 24 && Number(minute) < 60;
  const timeValue = `${hour.padStart(2, "0")}:${minute.padStart(2, "0")}`;
  const nextValue = withTime ? `${draftDate}T${timeValue}` : draftDate;
  const inRange = Boolean(draftDate) && (!min || nextValue >= String(min)) && (!max || nextValue <= String(max));

  return (
    <span className="relative block w-full">
      <input
        {...nativeProps}
        ref={setInputRef}
        type={type}
        id={id ? `${id}-native` : undefined}
        name={name}
        value={selectedValue}
        onChange={() => {}}
        min={min}
        max={max}
        required={required}
        disabled={disabled}
        tabIndex={-1}
        aria-hidden="true"
        onInvalid={(event) => { event.preventDefault(); triggerRef.current?.focus(); show(); }}
        className="pointer-events-none absolute inset-0 h-full w-full opacity-0"
      />
      <button
        id={id}
        ref={triggerRef}
        type="button"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={nativeProps["aria-label"]}
        disabled={disabled}
        onClick={() => open ? setOpen(false) : show()}
        className={cn("flex min-h-11 w-full items-center justify-between gap-2 rounded-xl border border-[#d3e3df] bg-[#f7faf9] px-3.5 py-2 text-left text-sm font-semibold text-[#193542] transition-all duration-200 hover:border-[#98c9be] hover:bg-white hover:shadow-sm active:scale-[0.99] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#087478] disabled:cursor-not-allowed disabled:opacity-50", className)}
      >
        <span className={cn("truncate", !selectedValue && "text-[#5E7480]")}>{displayValue(selectedValue, withTime)}</span>
        {withTime ? <Clock className="size-4 shrink-0 text-[#087478]" /> : <CalendarDays className="size-4 shrink-0 text-[#087478]" />}
      </button>
      {open && typeof document !== "undefined" && createPortal(
        <div ref={panelRef} role="dialog" aria-label="Choose travel date" className="fixed z-[9999] rounded-2xl border border-[#d3e3df] bg-white p-4 shadow-[0_18px_48px_rgba(25,53,66,.2)] animate-fade-in" style={position}>
          <div className="mb-4 flex items-center justify-between">
            <button type="button" aria-label="Previous month" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() - 1, 1))} className="rounded-lg p-2 text-[#516a74] hover:bg-[#eaf4f2]"><ChevronLeft className="size-4" /></button>
            <span className="text-sm font-extrabold text-[#193542]">{monthName}</span>
            <button type="button" aria-label="Next month" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() + 1, 1))} className="rounded-lg p-2 text-[#516a74] hover:bg-[#eaf4f2]"><ChevronRight className="size-4" /></button>
          </div>
          <div className="grid grid-cols-7 gap-1 text-center text-[11px] font-bold uppercase text-[#5E7480]">{["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"].map((day) => <span key={day} className="py-1">{day}</span>)}</div>
          <div className="mt-1 grid grid-cols-7 gap-1">
            {Array.from({ length: firstWeekday }, (_, index) => <span key={`blank-${index}`} />)}
            {Array.from({ length: daysInMonth }, (_, index) => {
              const iso = toIso(new Date(month.getFullYear(), month.getMonth(), index + 1));
              const disabledDay = (minDay && iso < minDay) || (maxDay && iso > maxDay);
              return <button
                key={iso}
                type="button"
                aria-label={new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "long", year: "numeric" }).format(fromIso(iso)!)}
                aria-pressed={iso === draftDate}
                disabled={Boolean(disabledDay)}
                onClick={() => { if (withTime) setDraftDate(iso); else commit(iso); }}
                className={cn("aspect-square rounded-lg text-sm font-semibold text-[#193542] transition-all duration-150 hover:scale-105 hover:bg-[#eaf4f2] active:scale-95 focus-visible:outline-2 focus-visible:outline-[#087478] disabled:cursor-not-allowed disabled:opacity-30 disabled:hover:scale-100", iso === today && "border border-[#8ccbc0]", iso === draftDate && "bg-[#087478] !text-white hover:bg-[#096E73]")}
              >{index + 1}</button>;
            })}
          </div>
          {withTime && <div className="mt-4 flex items-center gap-2 border-t border-[#e1ebe7] pt-4">
            <Clock className="size-4 text-[#087478]" />
            <label className="text-xs font-bold text-[#516a74]">Hour<input type="number" min="0" max="23" inputMode="numeric" value={hour} onChange={(event) => setHour(event.target.value)} className="ml-2 h-9 w-14 rounded-lg border border-[#d3e3df] bg-[#f7faf9] px-2 text-center text-sm text-[#193542]" /></label>
            <span className="font-bold text-[#516a74]">:</span>
            <label className="text-xs font-bold text-[#516a74]">Minute<input type="number" min="0" max="59" inputMode="numeric" value={minute} onChange={(event) => setMinute(event.target.value)} className="ml-2 h-9 w-14 rounded-lg border border-[#d3e3df] bg-[#f7faf9] px-2 text-center text-sm text-[#193542]" /></label>
          </div>}
          <div className="mt-4 flex items-center justify-between border-t border-[#e1ebe7] pt-3">
            <button type="button" onClick={() => { setMonth(new Date(new Date().getFullYear(), new Date().getMonth(), 1)); setDraftDate(today); if (!withTime && (!minDay || today >= minDay) && (!maxDay || today <= maxDay)) commit(today); }} className="text-xs font-bold text-[#087478] hover:underline">Today</button>
            <div className="flex items-center gap-3">
              {!required && <button type="button" onClick={() => commit("")} className="text-xs font-bold text-[#5E7480] hover:underline">Clear</button>}
              {withTime && <button type="button" disabled={!timeValid || !inRange} onClick={() => commit(nextValue)} className="rounded-lg bg-[#087478] px-3 py-2 text-xs font-bold text-white hover:bg-[#096E73] disabled:cursor-not-allowed disabled:opacity-40">Apply</button>}
            </div>
          </div>
        </div>, document.body
      )}
    </span>
  );
});
