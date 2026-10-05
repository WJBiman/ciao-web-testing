"use client";

import * as React from "react";
import { createPortal } from "react-dom";
import { Check, ChevronDown, Search } from "lucide-react";
import { cn } from "@/lib/utils";

type Choice = { value: string; label: string; disabled: boolean };
type SelectProps = React.SelectHTMLAttributes<HTMLSelectElement>;
type PanelPosition = { left: number; width: number; top?: number; bottom?: number };

function plainText(node: React.ReactNode): string {
  if (typeof node === "string" || typeof node === "number") return String(node);
  if (Array.isArray(node)) return node.map(plainText).join("");
  if (React.isValidElement<{ children?: React.ReactNode }>(node)) return plainText(node.props.children);
  return "";
}

function readOptions(children: React.ReactNode): Choice[] {
  const choices: Choice[] = [];
  React.Children.forEach(children, (child) => {
    if (!React.isValidElement(child)) return;
    if (child.type === "option") {
      const props = child.props as React.OptionHTMLAttributes<HTMLOptionElement>;
      const label = plainText(props.children).trim();
      choices.push({ value: String(props.value ?? label), label, disabled: Boolean(props.disabled) });
      return;
    }
    const props = child.props as { children?: React.ReactNode };
    if (props.children) choices.push(...readOptions(props.children));
  });
  return choices;
}

export function SearchableSelect({
  children, className, value, defaultValue, onChange, id, name, required, disabled,
  ...nativeProps
}: SelectProps) {
  const [uncontrolledValue, setUncontrolledValue] = React.useState(String(defaultValue ?? ""));
  const [open, setOpen] = React.useState(false);
  const [query, setQuery] = React.useState("");
  const [active, setActive] = React.useState(0);
  const [position, setPosition] = React.useState<PanelPosition>({ left: 0, width: 240, top: 0 });
  const triggerRef = React.useRef<HTMLButtonElement>(null);
  const panelRef = React.useRef<HTMLDivElement>(null);
  const searchRef = React.useRef<HTMLInputElement>(null);
  const selectRef = React.useRef<HTMLSelectElement>(null);
  const listId = React.useId();
  const choices = React.useMemo(() => readOptions(children), [children]);
  const selectedValue = String(value ?? uncontrolledValue);
  const selected = choices.find((choice) => choice.value === selectedValue);
  const filtered = choices.filter((choice) => choice.label.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase()));

  const measure = React.useCallback(() => {
    const rect = triggerRef.current?.getBoundingClientRect();
    if (!rect) return;
    const below = window.innerHeight - rect.bottom;
    const above = rect.top;
    const upward = below < 245 && above > below;
    setPosition({
      left: Math.max(8, Math.min(rect.left, window.innerWidth - rect.width - 8)),
      width: rect.width,
      ...(upward ? { bottom: window.innerHeight - rect.top + 6 } : { top: rect.bottom + 6 }),
    });
  }, []);

  const show = () => {
    if (disabled) return;
    measure();
    setQuery("");
    setActive(Math.max(0, choices.findIndex((choice) => choice.value === selectedValue)));
    setOpen(true);
    requestAnimationFrame(() => searchRef.current?.focus());
  };

  const choose = (next: string) => {
    if (value === undefined) setUncontrolledValue(next);
    const element = selectRef.current;
    if (element) {
      element.value = next;
      onChange?.({ target: element, currentTarget: element } as React.ChangeEvent<HTMLSelectElement>);
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

  return (
    <span className="relative block w-full min-w-0 max-w-full">
      <select
        {...nativeProps}
        ref={selectRef}
        id={id ? `${id}-native` : undefined}
        name={name}
        value={selectedValue}
        onChange={() => {}}
        required={required}
        disabled={disabled}
        tabIndex={-1}
        aria-hidden="true"
        onInvalid={(event) => { event.preventDefault(); triggerRef.current?.focus(); show(); }}
        className="pointer-events-none absolute inset-0 h-full w-full opacity-0"
      >{children}</select>
      <button
        ref={triggerRef}
        id={id}
        type="button"
        role="combobox"
        aria-expanded={open}
        aria-controls={listId}
        aria-haspopup="listbox"
        aria-label={nativeProps["aria-label"]}
        disabled={disabled}
        onClick={() => open ? setOpen(false) : show()}
        onKeyDown={(event) => {
          if (event.key === "ArrowDown" || event.key === "ArrowUp") { event.preventDefault(); show(); }
        }}
        className={cn("flex min-h-11 w-full min-w-0 max-w-full items-center justify-between gap-2 rounded-xl border border-[#d3e3df] bg-[#f7faf9] px-3.5 py-2 text-left text-sm font-semibold text-[#193542] transition-all duration-200 hover:border-[#98c9be] hover:bg-white hover:shadow-sm active:scale-[0.99] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#087478] disabled:cursor-not-allowed disabled:opacity-50", className)}
      >
        <span className="min-w-0 truncate">{selected?.label ?? choices[0]?.label ?? "Select an option"}</span>
        <ChevronDown className={cn("size-4 shrink-0 text-[#087478] transition-transform", open && "rotate-180")} />
      </button>
      {open && typeof document !== "undefined" && createPortal(
        <div ref={panelRef} className="fixed z-[9999] overflow-hidden rounded-2xl border border-[#d3e3df] bg-white p-2 shadow-[0_18px_48px_rgba(25,53,66,.2)] animate-fade-in" style={position}>
          <div className="relative mb-2">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-[#5E7480]" />
            <input
              ref={searchRef}
              type="search"
              value={query}
              onChange={(event) => { setQuery(event.target.value); setActive(0); }}
              onKeyDown={(event) => {
                if (event.key === "ArrowDown") { event.preventDefault(); setActive((index) => Math.min(index + 1, Math.max(0, filtered.length - 1))); }
                if (event.key === "ArrowUp") { event.preventDefault(); setActive((index) => Math.max(index - 1, 0)); }
                if (event.key === "Enter") { event.preventDefault(); const choice = filtered[Math.max(0, Math.min(active, filtered.length - 1))]; if (choice && !choice.disabled) choose(choice.value); }
              }}
              aria-label="Search options"
              placeholder="Search options..."
              className="h-10 w-full rounded-xl border border-[#dce7e4] bg-[#f7faf9] pl-9 pr-3 text-sm text-[#193542] outline-none focus:border-[#087478]"
            />
          </div>
          <div id={listId} role="listbox" className="max-h-60 overflow-y-auto overscroll-contain">
            {filtered.length === 0 ? <p className="px-3 py-4 text-center text-sm text-[#5E7480]">No matching options</p> : filtered.map((choice, index) => (
              <button
                key={`${choice.value}-${index}`}
                type="button"
                role="option"
                aria-selected={choice.value === selectedValue}
                disabled={choice.disabled}
                onMouseEnter={() => setActive(index)}
                onClick={() => choose(choice.value)}
                className={cn("flex w-full items-center justify-between gap-3 rounded-xl px-3 py-2.5 text-left text-sm text-[#193542] transition-colors duration-150 hover:bg-[#eaf4f2] active:bg-[#d7ece5] disabled:cursor-not-allowed disabled:opacity-40", (index === active || choice.value === selectedValue) && "bg-[#eaf4f2]")}
              >
                <span className="min-w-0 truncate">{choice.label}</span>
                {choice.value === selectedValue && <Check className="size-4 shrink-0 text-[#087478]" />}
              </button>
            ))}
          </div>
        </div>, document.body
      )}
    </span>
  );
}
