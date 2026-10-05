"use client";

import { apiRequest } from "@/lib/api";
import Link from "next/link";
import { IdCard, Mail, Phone, Lock, UserPlus, AlertCircle } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";

export default function Register() {
  const router = useRouter();
  
  const [formData, setFormData] = useState({
    fullName: "",
    email: "",
    phone: "",
    nic: "",
    password: ""
  });
  const [errorMsg, setErrorMsg] = useState("");
  const [loading, setLoading] = useState(false);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData({ ...formData, [e.target.id]: e.target.value });
  };

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg("");
    setLoading(true);

    try {
      await apiRequest("/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ 
          ...formData, 
          fullName: formData.fullName.trim(),
          email: formData.email.trim(), 
          phone: formData.phone.trim(), 
          nic: formData.nic.trim() 
        }),
      });
      router.push("/login?registered=true");
    } catch (error) {
      setErrorMsg(error instanceof Error ? error.message : "Registration failed. Please check your details.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-[480px] mx-auto mt-12 md:mt-16 mb-20 px-5 relative z-10 w-full">
      <div className="bg-[#FFFFFF] border border-[rgba(25,53,66,0.12)] p-8 md:p-10 rounded-3xl shadow-2xl">
        <div className="text-center mb-6">
          <div className="text-4xl font-extrabold tracking-[-.08em] text-[#193542] mb-4">ciao<span className="text-[#087478]">.</span></div>
          <h1 className="text-xl md:text-2xl font-extrabold text-[#193542] tracking-tight">
            Create Passenger Account
          </h1>
          <p className="text-sm text-[#516A74] mt-1">
            Enjoy instant seat reservations, booking history, and parcel tracking
          </p>
        </div>

        {errorMsg && (
          <div role="alert" className="bg-red-50 border border-red-200 text-red-700 text-xs md:text-sm p-3.5 rounded-xl mb-4 text-center flex items-center justify-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0 text-red-400" />
            <span>{errorMsg}</span>
          </div>
        )}

        <form onSubmit={handleRegister} className="space-y-4">
          <div className="flex flex-col">
            <label htmlFor="fullName" className="ciao-label flex items-center gap-1.5">
              <IdCard className="w-3.5 h-3.5 text-[#087478]" /> Full Name
            </label>
            <input 
              type="text" 
              id="fullName" 
              className="ciao-input" 
              placeholder="Perera Silva" 
              value={formData.fullName} 
              onChange={handleChange} 
              required 
            />
          </div>

          <div className="flex flex-col">
            <label htmlFor="email" className="ciao-label flex items-center gap-1.5">
              <Mail className="w-3.5 h-3.5 text-[#087478]" /> Email Address
            </label>
            <input 
              type="email" 
              id="email" 
              className="ciao-input" 
              placeholder="passenger@example.com" 
              value={formData.email} 
              onChange={handleChange} 
              required 
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="flex flex-col">
              <label htmlFor="phone" className="ciao-label flex items-center gap-1.5">
                <Phone className="w-3.5 h-3.5 text-[#087478]" /> Mobile Number
              </label>
              <input 
                type="tel" 
                id="phone" 
                className="ciao-input" 
                placeholder="0771234567" 
                value={formData.phone} 
                onChange={handleChange} 
                required 
              />
            </div>
            
            <div className="flex flex-col">
              <label htmlFor="nic" className="ciao-label flex items-center gap-1.5">
                <IdCard className="w-3.5 h-3.5 text-[#087478]" /> National Identity Card
              </label>
              <input 
                type="text" 
                id="nic" 
                className="ciao-input" 
                placeholder="200012345678" 
                value={formData.nic} 
                onChange={handleChange} 
                required 
              />
            </div>
          </div>

          <div className="flex flex-col">
            <label htmlFor="password" className="ciao-label flex items-center gap-1.5">
              <Lock className="w-3.5 h-3.5 text-[#087478]" /> Password
            </label>
            <input 
              type="password" 
              minLength={6} 
              maxLength={72} 
              autoComplete="new-password" 
              id="password" 
              className="ciao-input" 
              placeholder="••••••••" 
              value={formData.password} 
              onChange={handleChange} 
              required 
            />
          </div>

          <button 
            type="submit" 
            className="ciao-btn-primary w-full justify-center h-12 text-sm md:text-base font-bold mt-2 disabled:opacity-50 flex items-center gap-2" 
            disabled={loading}
          >
            <UserPlus className="w-4 h-4 mr-1" /> 
            {loading ? "Creating Account..." : "Complete Registration"}
          </button>
        </form>

        <div className="text-center mt-6 text-xs md:text-sm text-[#516A74]">
          Already registered?{" "}
          <Link href="/login" className="text-[#087478] font-semibold hover:underline">
            Sign In Here
          </Link>
        </div>
      </div>
    </div>
  );
}
