"use client";

import { useEffect, useRef } from "react";
import { AlertTriangle, Loader2, Trash2, X } from "lucide-react";

type Props = {
  open: boolean;
  record: string;
  busy?: boolean;
  onCancel: () => void;
  onConfirm: () => void;
};

export function DeleteRecordDialog({ open, record, busy = false, onCancel, onConfirm }: Props) {
  const cancelRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    cancelRef.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !busy) onCancel();
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [open, busy, onCancel]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-[80] flex items-center justify-center bg-[#102f39]/60 p-4 backdrop-blur-sm" role="presentation">
      <div role="alertdialog" aria-modal="true" aria-labelledby="delete-record-title" aria-describedby="delete-record-description" className="w-full max-w-md rounded-2xl border border-[#d7e5e2] bg-white p-6 shadow-2xl">
        <div className="flex items-start justify-between gap-4">
          <div className="flex size-11 items-center justify-center rounded-xl bg-rose-50 text-rose-700"><AlertTriangle className="size-5" /></div>
          <button type="button" onClick={onCancel} disabled={busy} aria-label="Close delete confirmation" className="rounded-lg p-2 text-[#516a74] hover:bg-[#eaf4f2] focus-visible:outline-2 focus-visible:outline-[#087478]"><X className="size-4" /></button>
        </div>
        <h2 id="delete-record-title" className="mt-4 text-xl font-bold text-[#193542]">Permanently delete record?</h2>
        <p id="delete-record-description" className="mt-2 break-words text-sm leading-relaxed text-[#516a74]">
          <strong className="text-[#193542]">{record}</strong> will be removed from the database. This cannot be undone. Records linked to bookings, journeys, claims, or settled payments are protected and cannot be deleted until their dependencies are resolved.
        </p>
        <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button ref={cancelRef} type="button" onClick={onCancel} disabled={busy} className="min-h-11 rounded-xl border border-[#c9dbd6] bg-white px-5 text-sm font-semibold text-[#193542] hover:bg-[#eaf4f2] focus-visible:outline-2 focus-visible:outline-[#087478]">Keep record</button>
          <button type="button" onClick={onConfirm} disabled={busy} className="inline-flex min-h-11 items-center justify-center gap-2 rounded-xl bg-rose-700 px-5 text-sm font-semibold text-white hover:bg-rose-800 disabled:opacity-60 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-rose-700">
            {busy ? <Loader2 className="size-4 animate-spin" /> : <Trash2 className="size-4" />} Delete permanently
          </button>
        </div>
      </div>
    </div>
  );
}
