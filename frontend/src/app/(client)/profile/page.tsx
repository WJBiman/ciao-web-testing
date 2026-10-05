"use client";

import { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import { User, Bell, PackageOpen, Phone, Plus, X, Check, AlertCircle, ArrowLeft } from "lucide-react";
import { Badge } from "@/components/ui/badge";

interface UserProfileData {
  user: {
    id: number;
    fullName: string;
    email: string;
    phone: string;
    username: string | null;
  };
  customer: {
    id?: number;
    firstName?: string;
    lastName?: string;
    address?: string;
    registeredDate?: string;
  };
  staff: {
    id?: number;
    employeeCode?: string;
    staffType?: string;
  };
  phones: Array<{ id: number; phoneNumber: string }>;
}

interface NotificationItem {
  id: number;
  title: string;
  message: string;
  notificationType: string;
  readStatus: boolean;
  readAt?: string;
  sentAt?: string;
}

interface UserClaimItem {
  id: number;
  item: {
    id: number;
    itemDescription: string;
    status: string;
    reportedByName?: string;
  };
  proofOfOwnership: string;
  claimStatus: string;
  claimDate: string;
  returnedAt?: string;
}

export default function CustomerProfilePage() {
  const [profile, setProfile] = useState<UserProfileData | null>(null);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [claims, setClaims] = useState<UserClaimItem[]>([]);
  const [activeTab, setActiveTab] = useState<"profile" | "notifications" | "claims">("profile");

  // Form states
  const [firstName, setFirstName] = useState("");
  const [staffName, setStaffName] = useState("");
  const [lastName, setLastName] = useState("");
  const [address, setAddress] = useState("");
  const [username, setUsername] = useState("");
  const [phoneList, setPhoneList] = useState<string[]>([]);
  const [newPhone, setNewPhone] = useState("");

  const [saving, setSaving] = useState(false);
  const [statusMsg, setStatusMsg] = useState("");
  const [errorMsg, setErrorMsg] = useState("");

  // Claim filing states
  const [showClaimForm, setShowClaimForm] = useState(false);
  const [claimItemId, setClaimItemId] = useState("");
  const [claimProof, setClaimProof] = useState("");
  const [submittingClaim, setSubmittingClaim] = useState(false);
  const [claimError, setClaimError] = useState("");
  const [claimSuccess, setClaimSuccess] = useState("");

  const fetchProfileData = useCallback(async () => {
    try {
      const res = await fetch("/api/eer/profile", { credentials: "include" });
      if (res.ok) {
        const data = await res.json();
        setProfile(data);
        if (data.customer) {
          setFirstName(data.customer.firstName || "");
          setLastName(data.customer.lastName || "");
          setAddress(data.customer.address || "");
        }
        if (data.user) {
          setUsername(data.user.username || "");
          setStaffName(data.user.fullName || "");
        }
        if (data.phones) {
          setPhoneList(data.phones.map((p: { phoneNumber: string }) => p.phoneNumber));
        }
      }
    } catch (e) {
      console.error("Error fetching profile", e);
    }
  }, []);

  const fetchNotifications = useCallback(async () => {
    try {
      const res = await fetch("/api/eer/notifications", { credentials: "include" });
      if (res.ok) {
        const data = await res.json();
        setNotifications(data);
      }
    } catch (e) {
      console.error("Error fetching notifications", e);
    }
  }, []);

  const fetchMyClaims = useCallback(async () => {
    try {
      const res = await fetch("/api/eer/my-claims", { credentials: "include" });
      if (res.ok) {
        const data = await res.json();
        setClaims(data);
      }
    } catch (e) {
      console.error("Error fetching claims", e);
    }
  }, []);

  useEffect(() => {
    let active = true;
    const loadAll = async () => {
      if (active) {
        await Promise.all([fetchProfileData(), fetchNotifications(), fetchMyClaims()]);
      }
    };
    void loadAll();
    return () => { active = false; };
  }, [fetchProfileData, fetchNotifications, fetchMyClaims]);

  const addPhone = () => {
    const p = newPhone.trim();
    if (!p) return;
    if (!phoneList.includes(p)) {
      setPhoneList([...phoneList, p]);
    }
    setNewPhone("");
  };

  const removePhone = (idx: number) => {
    setPhoneList(phoneList.filter((_, i) => i !== idx));
  };

  const submitNewClaim = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmittingClaim(true);
    setClaimError("");
    setClaimSuccess("");

    try {
      const res = await fetch("/api/eer/claims", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({
          itemId: parseInt(claimItemId),
          proofOfOwnership: claimProof.trim(),
        }),
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || "Failed to submit ownership claim.");
      }

      setClaimSuccess(`Claim #${data.id} submitted successfully! Customer Service will review your verification proof.`);
      setClaimItemId("");
      setClaimProof("");
      fetchMyClaims();
    } catch (err: unknown) {
      setClaimError(err instanceof Error ? err.message : "Error submitting claim.");
    } finally {
      setSubmittingClaim(false);
    }
  };

  const handleProfileSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setStatusMsg("");
    setErrorMsg("");

    try {
      const res = await fetch("/api/eer/profile", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({
          firstName,
          lastName,
          address,
          username: username.trim(),
          phoneNumbers: phoneList,
        }),
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.message || "Failed to update profile.");
      }

      setStatusMsg("Profile updated successfully!");
      fetchProfileData();
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : "Error saving profile.");
    } finally {
      setSaving(false);
    }
  };

  const handleStaffProfileSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setStatusMsg("");
    setErrorMsg("");
    try {
      const res = await fetch("/api/eer/profile/staff", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ fullName: staffName.trim(), username: username.trim() }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message || "Failed to update staff profile.");
      setStatusMsg("Your profile was updated successfully.");
      await fetchProfileData();
      window.dispatchEvent(new Event("ciao_auth_change"));
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : "Error saving profile.");
    } finally {
      setSaving(false);
    }
  };

  const markNotificationAsRead = async (id: number) => {
    try {
      await fetch(`/api/eer/notifications/${id}/read`, {
        method: "POST",
        credentials: "include",
      });
      fetchNotifications();
    } catch (e) {
      console.error("Error marking read", e);
    }
  };

  const isStaff = Boolean(profile?.staff?.id);

  return (
    <div className="min-h-screen pt-12 md:pt-16 pb-20 px-5 bg-[#F6F8F6]">
      <div className="max-w-4xl mx-auto">
        {/* Header */}
        <div className="flex flex-col sm:flex-row items-start sm:items-end justify-between border-b border-[rgba(25,53,66,0.12)] pb-6 mb-8 gap-4">
          <div>
            <Badge variant="gold" className="mb-2">{isStaff ? "Staff Account" : "Passenger Account"}</Badge>
            <h1 className="text-2xl md:text-3xl font-extrabold text-[#193542]">{isStaff ? "My Staff Profile" : "Customer Profile & Services"}</h1>
            <p className="text-sm text-[#516A74] mt-1">
              {isStaff ? "Update your display name and account username." : "Manage your personal credentials, contact phone numbers, and lost property claims"}
            </p>
          </div>
          <Link href={isStaff ? "/management" : "/my-bookings"} className="ciao-btn-secondary text-xs md:text-sm flex items-center gap-1.5 self-start sm:self-auto">
            <ArrowLeft className="w-3.5 h-3.5" /> {isStaff ? "Staff Workspace" : "My Bookings"}
          </Link>
        </div>

        {/* Tab Switcher */}
        <div className="flex gap-2 border-b border-[rgba(25,53,66,0.12)] mb-8 pb-3 overflow-x-auto no-scrollbar">
          <button
            onClick={() => setActiveTab("profile")}
            className={`px-4 py-2.5 rounded-xl text-xs md:text-sm font-semibold transition-all flex items-center gap-2 cursor-pointer border ${
              activeTab === "profile" 
                ? "bg-[#087478] text-white border-[#087478] font-bold shadow-md" 
                : "bg-[#FFFFFF] border-[rgba(25,53,66,0.12)] text-[#516A74] hover:text-[#193542] hover:bg-[#EAF3F0]"
            }`}
          >
            <User className="w-4 h-4" /> {isStaff ? "My Details" : "Profile & Verified Phones"}
          </button>
          <button
            onClick={() => setActiveTab("notifications")}
            className={`px-4 py-2.5 rounded-xl text-xs md:text-sm font-semibold transition-all flex items-center gap-2 cursor-pointer border relative ${
              activeTab === "notifications" 
                ? "bg-[#087478] text-white border-[#087478] font-bold shadow-md" 
                : "bg-[#FFFFFF] border-[rgba(25,53,66,0.12)] text-[#516A74] hover:text-[#193542] hover:bg-[#EAF3F0]"
            }`}
          >
            <Bell className="w-4 h-4" /> Notification Inbox
            {notifications.filter((n) => !n.readAt).length > 0 && (
              <span className="ml-1.5 bg-red-600 text-white text-xs font-bold px-2 py-0.5 rounded-full">
                {notifications.filter((n) => !n.readAt).length}
              </span>
            )}
          </button>
          {!isStaff && <button
            onClick={() => setActiveTab("claims")}
            className={`px-4 py-2.5 rounded-xl text-xs md:text-sm font-semibold transition-all flex items-center gap-2 cursor-pointer border ${
              activeTab === "claims" 
                ? "bg-[#087478] text-white border-[#087478] font-bold shadow-md" 
                : "bg-[#FFFFFF] border-[rgba(25,53,66,0.12)] text-[#516A74] hover:text-[#193542] hover:bg-[#EAF3F0]"
            }`}
          >
            <PackageOpen className="w-4 h-4" /> Lost Property Claims
          </button>}
        </div>

        {/* Tab 1: Profile & Multivalued Phones */}
        {activeTab === "profile" && (
          <div className="bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] p-6 md:p-8 rounded-3xl shadow-xl">
            <h2 className="text-lg md:text-xl font-bold text-[#193542] mb-4 flex items-center gap-2">
              <User className="w-5 h-5 text-[#087478]" /> Personal Identity Details
            </h2>

            {statusMsg && (
              <div className="p-3 bg-emerald-50 border border-emerald-500/40 rounded-xl text-emerald-700 text-xs md:text-sm mb-4 flex items-center gap-2">
                <Check className="w-4 h-4 shrink-0 text-emerald-400" />
                <span>{statusMsg}</span>
              </div>
            )}
            {errorMsg && (
              <div role="alert" className="p-3 bg-red-50 border border-red-500/40 rounded-xl text-red-700 text-xs md:text-sm mb-4 flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0 text-red-400" />
                <span>{errorMsg}</span>
              </div>
            )}

            {!profile ? <p className="text-sm text-[#516A74]">Loading your profile...</p> : isStaff ? (
              <form onSubmit={handleStaffProfileSubmit} className="space-y-5">
                <div>
                  <label htmlFor="staff-full-name" className="ciao-label">Full name *</label>
                  <input id="staff-full-name" type="text" required maxLength={100} value={staffName} onChange={(e) => setStaffName(e.target.value)} className="ciao-input" />
                </div>
                <div>
                  <label htmlFor="staff-username" className="ciao-label">Username *</label>
                  <input id="staff-username" type="text" required pattern="[A-Za-z][A-Za-z0-9_]{2,39}" title="Starts with a letter; 3–40 letters, numbers or underscores" value={username} onChange={(e) => setUsername(e.target.value)} className="ciao-input" />
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div><span className="ciao-label">Email address</span><p className="rounded-xl bg-[#F7FAF9] px-4 py-3 text-sm text-[#516A74]">{profile.user.email}</p></div>
                  <div><span className="ciao-label">Staff role</span><p className="rounded-xl bg-[#F7FAF9] px-4 py-3 text-sm text-[#516A74]">{profile.staff.staffType?.replaceAll("_", " ") || "Staff"}</p></div>
                </div>
                <p className="text-xs text-[#5E7480]">Your email and staff role are managed by the system administrator.</p>
                <div className="flex justify-end"><button type="submit" disabled={saving} className="ciao-btn-primary disabled:opacity-50 text-sm md:text-base font-bold !py-3 !px-6">{saving ? "Saving Changes..." : "Save My Profile"}</button></div>
              </form>
            ) : <form onSubmit={handleProfileSubmit} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="flex flex-col">
                  <label className="ciao-label">First Name *</label>
                  <input
                    type="text"
                    required
                    value={firstName}
                    onChange={(e) => setFirstName(e.target.value)}
                    className="ciao-input"
                  />
                </div>
                <div className="flex flex-col">
                  <label className="ciao-label">Last Name *</label>
                  <input
                    type="text"
                    required
                    value={lastName}
                    onChange={(e) => setLastName(e.target.value)}
                    className="ciao-input"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="flex flex-col">
                  <label className="ciao-label">Username (Unique Identifier) *</label>
                  <input
                    type="text"
                    required
                    pattern="[A-Za-z][A-Za-z0-9_]{2,39}"
                    title="Starts with letter, 3-40 alphanumeric/underscore characters"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    placeholder="e.g. biman_warusha"
                    className="ciao-input"
                  />
                </div>
                <div className="flex flex-col">
                  <label className="ciao-label">Primary Email (System Locked)</label>
                  <input
                    type="text"
                    disabled
                    value={profile?.user?.email || ""}
                    className="ciao-input opacity-70 cursor-not-allowed bg-[#F7FAF9] text-[#516A74]"
                  />
                </div>
              </div>

              <div className="flex flex-col">
                <label className="ciao-label">Residential Address *</label>
                <textarea
                  required
                  rows={2}
                  value={address}
                  onChange={(e) => setAddress(e.target.value)}
                  placeholder="Street, City, Postal Code"
                  className="ciao-input resize-none"
                />
              </div>

              {/* Multivalued Phone Numbers */}
              <div className="pt-4 border-t border-[rgba(25,53,66,0.12)]">
                <label className="ciao-label text-[#087478] flex items-center gap-1.5">
                  <Phone className="w-3.5 h-3.5 text-[#087478]" /> Verified Contact Phone Numbers
                </label>
                <p className="text-[#5E7480] text-xs md:text-sm mb-3">
                  Register all mobile and WhatsApp lines associated with your bookings and parcel alerts.
                </p>

                <div className="flex gap-2 mb-3">
                  <input
                    type="text"
                    value={newPhone}
                    onChange={(e) => setNewPhone(e.target.value)}
                    placeholder="+94 77 123 4567"
                    className="ciao-input flex-1"
                  />
                  <button
                    type="button"
                    onClick={addPhone}
                    className="ciao-btn-secondary !text-xs md:!text-sm !py-2 !px-4"
                  >
                    <Plus className="w-3.5 h-3.5 mr-1" /> Add Phone
                  </button>
                </div>

                <div className="flex flex-wrap gap-2">
                  {phoneList.map((ph, idx) => (
                    <span 
                      key={idx} 
                      className="bg-[#EAF3F0] border border-[rgba(203,213,225,0.2)] text-[#087478] text-xs md:text-sm font-semibold px-3 py-1.5 rounded-full flex items-center gap-2"
                    >
                      <Phone className="w-3 h-3 text-[#087478]" /> {ph}
                      <button
                        type="button"
                        onClick={() => removePhone(idx)}
                        className="text-[#5E7480] hover:text-red-400 font-bold ml-1 cursor-pointer bg-transparent border-none"
                        aria-label="Remove phone"
                      >
                        <X className="w-3 h-3" />
                      </button>
                    </span>
                  ))}
                  {phoneList.length === 0 && (
                    <span className="text-xs text-[#71858B] italic">No additional contact lines added.</span>
                  )}
                </div>
              </div>

              <div className="pt-4 flex justify-end">
                <button
                  type="submit"
                  disabled={saving}
                  className="ciao-btn-primary disabled:opacity-50 text-sm md:text-base font-bold !py-3 !px-6"
                >
                  {saving ? "Saving Changes..." : "Save Customer Profile"}
                </button>
              </div>
            </form>}
          </div>
        )}

        {/* Tab 2: Notification Inbox */}
        {activeTab === "notifications" && (
          <div className="bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] p-6 md:p-8 rounded-3xl shadow-xl">
            <h2 className="text-lg md:text-xl font-bold text-[#193542] mb-4 flex items-center gap-2">
              <Bell className="w-5 h-5 text-[#087478]" /> System Notification Inbox
            </h2>
            {notifications.length === 0 ? (
              <div className="text-center py-12 text-[#5E7480] text-sm">
                You have no notifications yet.
              </div>
            ) : (
              <div className="space-y-3">
                {notifications.map((n) => (
                  <div
                    key={n.id}
                    className={`p-4 rounded-2xl border transition-all ${
                      n.readAt 
                        ? "bg-[#EDF3F1] border-[rgba(203,213,225,0.06)] text-[#5E7480]" 
                        : "bg-[#EAF3F0] border-[#087478]/40 text-[#193542]"
                    }`}
                  >
                    <div className="flex justify-between items-start gap-4">
                      <div>
                        <span className="text-xs font-bold uppercase tracking-wider text-[#087478] block mb-1">
                          {n.notificationType}
                        </span>
                        <p className="text-sm font-medium leading-relaxed">{n.message}</p>
                        {n.sentAt && (
                          <span className="text-xs text-[#71858B] mt-1.5 block">
                            {new Date(n.sentAt).toLocaleString()}
                          </span>
                        )}
                      </div>
                      {!n.readAt && (
                        <button
                          onClick={() => markNotificationAsRead(n.id)}
                          className="ciao-btn-secondary text-xs !py-1 !px-2.5 shrink-0"
                        >
                          Mark as Read
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Tab 3: My Claims */}
        {activeTab === "claims" && (
          <div className="bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] p-6 md:p-8 rounded-3xl shadow-xl">
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center mb-6 gap-3">
              <div>
                <h2 className="text-lg md:text-xl font-bold text-[#193542]">Lost Property Claims</h2>
                <p className="text-sm text-[#516A74]">Review status of filed ownership claims</p>
              </div>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => setShowClaimForm(!showClaimForm)}
                  className="ciao-btn-primary text-xs md:text-sm !py-2 !px-3.5"
                >
                  {showClaimForm ? "✕ Close Form" : "+ File Ownership Claim"}
                </button>
                <Link href="/report-lost" className="ciao-btn-secondary text-xs md:text-sm !py-2 !px-3.5">
                  Report Found Property
                </Link>
              </div>
            </div>

            {showClaimForm && (
              <form onSubmit={submitNewClaim} className="mb-6 p-5 rounded-2xl bg-[#F7FAF9] border border-[#087478]/30 space-y-4">
                <h3 className="text-sm md:text-base font-bold text-[#087478]">Submit Ownership Verification Proof</h3>
                <p className="text-xs md:text-sm text-[#5E7480]">
                  Provide the Item ID and distinguishing marks, serial numbers, or receipt references.
                </p>
                {claimError && (
                  <div role="alert" className="p-2.5 text-xs md:text-sm bg-red-50 border border-red-500/40 text-red-700 rounded-xl">
                    {claimError}
                  </div>
                )}
                {claimSuccess && (
                  <div className="p-2.5 text-xs md:text-sm bg-emerald-50 border border-emerald-500/40 text-emerald-700 rounded-xl">
                    {claimSuccess}
                  </div>
                )}
                <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
                  <div className="sm:col-span-1 flex flex-col">
                    <label className="ciao-label">Found Item ID</label>
                    <input
                      type="number"
                      required
                      placeholder="e.g. 1"
                      value={claimItemId}
                      onChange={(e) => setClaimItemId(e.target.value)}
                      className="ciao-input"
                    />
                  </div>
                  <div className="sm:col-span-3 flex flex-col">
                    <label className="ciao-label">Proof of Ownership & Item Specifics</label>
                    <textarea
                      required
                      rows={2}
                      placeholder="Detail serial numbers, color, purchase proof, or contents..."
                      value={claimProof}
                      onChange={(e) => setClaimProof(e.target.value)}
                      className="ciao-input resize-none"
                    />
                  </div>
                </div>
                <div className="flex justify-end pt-1">
                  <button
                    type="submit"
                    disabled={submittingClaim}
                    className="ciao-btn-primary text-xs md:text-sm disabled:opacity-50"
                  >
                    {submittingClaim ? "Submitting..." : "Submit Claim for Staff Decision"}
                  </button>
                </div>
              </form>
            )}

            {claims.length === 0 ? (
              <div className="text-center py-12 text-[#5E7480] text-sm">
                You haven&apos;t filed any lost property claims yet.
              </div>
            ) : (
              <div className="space-y-3">
                {claims.map((c) => (
                  <div key={c.id} className="p-5 rounded-2xl bg-[#F7FAF9] border border-[rgba(25,53,66,0.12)]">
                    <div className="flex justify-between items-start gap-4">
                      <div>
                        <span className="text-xs font-bold text-[#5E7480]">Claim #{c.id}</span>
                        <h3 className="text-base md:text-lg font-bold text-[#193542] mt-0.5">{c.item?.itemDescription || "Lost Property Item"}</h3>
                        <p className="text-xs md:text-sm text-[#516A74] mt-1.5 leading-relaxed">
                          <strong className="text-[#193542]">Proof Provided:</strong> {c.proofOfOwnership}
                        </p>
                      </div>
                      <Badge variant={
                        c.claimStatus === "APPROVED" ? "success" : 
                        c.claimStatus === "REJECTED" ? "danger" : 
                        c.claimStatus === "RETURNED" ? "info" : "warning"
                      }>
                        {c.claimStatus}
                      </Badge>
                    </div>
                    <div className="mt-4 pt-3 border-t border-[rgba(25,53,66,0.12)] text-xs md:text-sm text-[#5E7480] flex justify-between items-center">
                      <span>Submitted: {new Date(c.claimDate).toLocaleDateString()}</span>
                      {c.returnedAt && (
                        <span className="text-emerald-700 font-semibold">
                          Handed Over: {new Date(c.returnedAt).toLocaleDateString()}
                        </span>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
