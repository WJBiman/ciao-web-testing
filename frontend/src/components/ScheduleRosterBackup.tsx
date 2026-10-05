"use client";

// Preserved Schedule Roster for Phase 2C
export default function ScheduleRosterBackup() {
  return (
    <div className="bg-[#1e293b] rounded-[var(--radius-lg)] p-[25px] border border-white/10 overflow-x-auto">
      <h3 className="text-[18px] mb-[20px] font-[family-name:var(--font-inter)] text-white font-bold">Active Schedule Roster</h3>
      <table className="admin-table min-w-[900px]">
        <thead>
          <tr>
            <th>Route & Time</th>
            <th>Bus Reg No.</th>
            <th>Bus Type & Capacity</th>
            <th>Assigned Driver</th>
            <th>Status</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td className="text-[#94a3b8]">Route 01 (Kandy) - 06:30 AM</td>
            <td><strong className="text-white">ND-8492</strong></td>
            <td className="text-[#94a3b8]">Luxury AC (49 Seats)</td>
            <td className="text-[#94a3b8]">Kamal Perera (Lic: #D9482)</td>
            <td><span className="bg-[#10b981]/15 text-[#10b981] px-2 py-1 rounded-[4px] text-[11px] font-bold">ON ROUTE</span></td>
            <td><button className="bg-transparent border-none text-[var(--color-cyan-glow)] cursor-pointer hover:underline"><i className="fa-solid fa-pen-to-square"></i> Edit</button></td>
          </tr>
          <tr>
            <td className="text-[#94a3b8]">Route EX-01 (Galle) - 07:15 AM</td>
            <td><strong className="text-white">WP-CAD-5021</strong></td>
            <td className="text-[#94a3b8]">Super Luxury (42 Seats)</td>
            <td className="text-[#94a3b8]">Sunil Silva (Lic: #D8211)</td>
            <td><span className="bg-[#10b981]/15 text-[#10b981] px-2 py-1 rounded-[4px] text-[11px] font-bold">ON ROUTE</span></td>
            <td><button className="bg-transparent border-none text-[var(--color-cyan-glow)] cursor-pointer hover:underline"><i className="fa-solid fa-pen-to-square"></i> Edit</button></td>
          </tr>
          <tr>
            <td className="text-[#94a3b8]">Route 99 (Badulla) - 10:00 AM</td>
            <td><strong className="text-white">NC-7721</strong></td>
            <td className="text-[#94a3b8]">Semi-Luxury (54 Seats)</td>
            <td><span className="text-[var(--color-amber-gold)]"><i className="fa-solid fa-triangle-exclamation mr-1"></i> Unassigned</span></td>
            <td><span className="bg-[#f59e0b]/15 text-[#f59e0b] px-2 py-1 rounded-[4px] text-[11px] font-bold">PENDING DISPATCH</span></td>
            <td><button className="bg-[var(--color-royal-blue)] border-none text-white cursor-pointer px-2.5 py-1.5 rounded-[4px] text-[12px] hover:bg-[var(--color-royal-blue)]/80 transition-colors">Assign Driver</button></td>
          </tr>
        </tbody>
      </table>
    </div>
  );
}
