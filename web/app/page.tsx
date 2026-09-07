"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import type { Session } from "@supabase/supabase-js";
import { supabase } from "../lib/supabase";

type DashboardData = {
  profile: any;
  focus: any[];
  tasks: any[];
  activities: any[];
  habits: any[];
  habitEntries: any[];
  review: any | null;
  devices: any[];
  syncEvents: any[];
};

const emptyData: DashboardData = {
  profile: null,
  focus: [],
  tasks: [],
  activities: [],
  habits: [],
  habitEntries: [],
  review: null,
  devices: [],
  syncEvents: [],
};

function todayMakassar() {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: "Asia/Makassar",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date());
  const map = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${map.year}-${map.month}-${map.day}`;
}

function productiveMinutes(data: DashboardData) {
  return data.activities
    .filter((item) => String(item.category_name || "").toLowerCase() !== "istirahat")
    .reduce((sum, item) => sum + Math.max(0, Number(item.duration_minutes || 0)), 0);
}

function activeHabitIds(data: DashboardData) {
  return new Set(data.habits.filter((item) => item.is_active && !item.deleted_at).map((item) => item.id));
}

function calculateScore(data: DashboardData) {
  const focusDone = data.focus.filter((item) => item.status === "COMPLETED").length;
  const focusScore = data.focus.length ? (focusDone / data.focus.length) * 40 : 0;

  const taskDone = data.tasks.filter((item) => item.status === "DONE").length;
  const taskScore = data.tasks.length ? (taskDone / data.tasks.length) * 20 : 0;

  const minutes = productiveMinutes(data);
  const target = Math.max(15, Number(data.profile?.target_focus_minutes || 120));
  const timeScore = Math.min(1, minutes / target) * 20;

  const habitIds = activeHabitIds(data);
  const doneHabits = data.habitEntries.filter((item) => item.completed && habitIds.has(item.habit_id)).length;
  const habitScore = habitIds.size ? (Math.min(doneHabits, habitIds.size) / habitIds.size) * 20 : 0;

  return Math.max(0, Math.min(100, Math.floor(focusScore + taskScore + timeScore + habitScore)));
}

function statusClass(status: string) {
  if (status === "SUCCESS") return "status success";
  if (status === "CONFLICT") return "status conflict";
  if (status === "FAILED") return "status failed";
  return "status partial";
}

export default function Home() {
  const [session, setSession] = useState<Session | null>(null);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [creatingAccount, setCreatingAccount] = useState(false);
  const [data, setData] = useState<DashboardData>(emptyData);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const date = useMemo(() => todayMakassar(), []);

  const loadDashboard = useCallback(async () => {
    if (!session?.user.id) return;
    setLoading(true);
    setError(null);

    const userId = session.user.id;
    const [profile, focus, tasks, activities, habits, habitEntries, review, devices, syncEvents] = await Promise.all([
      supabase.from("profiles").select("*").eq("id", userId).maybeSingle(),
      supabase.from("focus_items").select("*").eq("focus_date", date).is("deleted_at", null).order("sort_order"),
      supabase.from("tasks").select("*").eq("scheduled_date", date).is("deleted_at", null).order("created_at"),
      supabase.from("activities").select("*").eq("activity_date", date).is("deleted_at", null).order("started_at"),
      supabase.from("habits").select("*").is("deleted_at", null).order("created_at"),
      supabase.from("habit_entries").select("*").eq("entry_date", date).is("deleted_at", null),
      supabase.from("daily_reviews").select("*").eq("review_date", date).is("deleted_at", null).maybeSingle(),
      supabase.from("devices").select("*").order("last_seen_at", { ascending: false }),
      supabase.from("sync_events").select("*").order("created_at", { ascending: false }).limit(12),
    ]);

    const firstError = [profile.error, focus.error, tasks.error, activities.error, habits.error, habitEntries.error, review.error, devices.error, syncEvents.error].find(Boolean);
    if (firstError) setError(firstError.message);

    setData({
      profile: profile.data,
      focus: focus.data ?? [],
      tasks: tasks.data ?? [],
      activities: activities.data ?? [],
      habits: habits.data ?? [],
      habitEntries: habitEntries.data ?? [],
      review: review.data,
      devices: devices.data ?? [],
      syncEvents: syncEvents.data ?? [],
    });
    setLoading(false);
  }, [date, session?.user.id]);

  useEffect(() => {
    supabase.auth.getSession().then(({ data: current }) => {
      setSession(current.session);
      setLoading(false);
    });
    const { data: listener } = supabase.auth.onAuthStateChange((_event, nextSession) => setSession(nextSession));
    return () => listener.subscription.unsubscribe();
  }, []);

  useEffect(() => {
    if (session) void loadDashboard();
    else setData(emptyData);
  }, [session, loadDashboard]);

  async function submitAuth(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setInfo(null);

    if (creatingAccount) {
      if (password.length < 6) {
        setError("Password minimal 6 karakter.");
        return;
      }
      if (password !== confirmPassword) {
        setError("Konfirmasi password tidak sama.");
        return;
      }
    }

    setLoading(true);
    if (creatingAccount) {
      const { data: signupData, error: signupError } = await supabase.auth.signUp({ email: email.trim(), password });
      if (signupError) setError(signupError.message);
      else if (!signupData.session) setInfo("Akun dibuat. Cek email untuk konfirmasi, lalu masuk.");
      else setInfo("Akun berhasil dibuat.");
    } else {
      const { error: loginError } = await supabase.auth.signInWithPassword({ email: email.trim(), password });
      if (loginError) setError(loginError.message);
    }
    setLoading(false);
  }

  if (!session) {
    return (
      <main className="login-shell">
        <section className="login-card">
          <div className="brand-mark">DT</div>
          <p className="eyebrow">DAILY THREAD</p>
          <h1>{creatingAccount ? "Buat akun" : "Masuk ke dashboard"}</h1>
          <p className="muted">Dashboard memakai Supabase Auth dan Row Level Security yang sama dengan APK.</p>
          <form onSubmit={submitAuth} className="login-form">
            <label>Email<input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="nama@email.com" /></label>
            <label>Password<input type="password" required minLength={creatingAccount ? 6 : undefined} value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" /></label>
            {creatingAccount && <label>Ulangi password<input type="password" required minLength={6} value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} placeholder="••••••••" /></label>}
            <button disabled={loading}>{loading ? "Memproses…" : creatingAccount ? "Buat akun" : "Masuk"}</button>
          </form>
          <button
            type="button"
            className="auth-switch"
            disabled={loading}
            onClick={() => {
              setCreatingAccount((value) => !value);
              setConfirmPassword("");
              setError(null);
              setInfo(null);
            }}
          >
            {creatingAccount ? "Sudah punya akun? Masuk" : "Belum punya akun? Buat akun"}
          </button>
          {error && <p className="error-box">{error}</p>}
          {info && <p className="info-box">{info}</p>}
        </section>
      </main>
    );
  }

  const score = calculateScore(data);
  const focusDone = data.focus.filter((x) => x.status === "COMPLETED").length;
  const taskDone = data.tasks.filter((x) => x.status === "DONE").length;
  const minutes = productiveMinutes(data);
  const habitIds = activeHabitIds(data);
  const habitsDone = data.habitEntries.filter((x) => x.completed && habitIds.has(x.habit_id)).length;

  return (
    <main className="dashboard-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">DAILY THREAD · {date}</p>
          <h1>Halo, {data.profile?.display_name || session.user.email?.split("@")[0] || "User"}</h1>
          <p className="muted">APK offline-first dan cloud dashboard berbagi database Supabase yang sama.</p>
        </div>
        <div className="top-actions">
          <button className="secondary" onClick={() => void loadDashboard()}>Refresh</button>
          <button className="secondary" onClick={() => void supabase.auth.signOut()}>Keluar</button>
        </div>
      </header>

      {error && <p className="error-box">{error}</p>}

      <section className="hero-grid">
        <article className="score-card"><span>Daily Score</span><strong>{score}</strong><small>/ 100</small></article>
        <article className="metric-card"><span>Fokus</span><strong>{focusDone}/{data.focus.length}</strong><small>selesai</small></article>
        <article className="metric-card"><span>Tugas</span><strong>{taskDone}/{data.tasks.length}</strong><small>selesai</small></article>
        <article className="metric-card"><span>Waktu produktif</span><strong>{minutes}</strong><small>menit</small></article>
        <article className="metric-card"><span>Habit hari ini</span><strong>{habitsDone}/{habitIds.size}</strong><small>check-in</small></article>
        <article className="metric-card"><span>Perangkat</span><strong>{data.devices.length}</strong><small>terdaftar</small></article>
      </section>

      <section className="content-grid">
        <article className="panel">
          <div className="panel-head"><div><p className="eyebrow">TODAY</p><h2>3 Fokus Utama</h2></div></div>
          <div className="stack">
            {data.focus.length === 0 && <p className="empty">Belum ada fokus hari ini.</p>}
            {data.focus.map((item) => <div className="row-item" key={item.id}><div><b>{item.sort_order}. {item.title}</b><small>{item.estimate_minutes || 0} menit</small></div><span className={`pill ${item.status === "COMPLETED" ? "done" : ""}`}>{item.status}</span></div>)}
          </div>
        </article>

        <article className="panel">
          <div className="panel-head"><div><p className="eyebrow">TASKS</p><h2>Tugas hari ini</h2></div></div>
          <div className="stack">
            {data.tasks.length === 0 && <p className="empty">Belum ada tugas terjadwal.</p>}
            {data.tasks.slice(0, 8).map((item) => <div className="row-item" key={item.id}><div><b>{item.title}</b><small>{item.priority} · {item.estimate_minutes || 0} menit</small></div><span className={`pill ${item.status === "DONE" ? "done" : ""}`}>{item.status}</span></div>)}
          </div>
        </article>

        <article className="panel wide">
          <div className="panel-head"><div><p className="eyebrow">SYNC OBSERVABILITY</p><h2>Riwayat sinkronisasi terbaru</h2></div><span className="muted">Edge Function v5</span></div>
          <div className="sync-table">
            <div className="sync-row sync-head"><span>Waktu</span><span>Arah</span><span>Status</span><span>Data</span><span>Durasi</span></div>
            {data.syncEvents.length === 0 && <p className="empty">Belum ada event sinkronisasi. Jalankan APK lalu sinkronkan.</p>}
            {data.syncEvents.map((event) => (
              <div className="sync-row" key={event.id}>
                <span>{new Date(event.created_at).toLocaleString("id-ID")}</span>
                <span>{event.direction}</span>
                <span><i className={statusClass(event.status)}>{event.status}</i></span>
                <span>{event.synced_count}/{event.mutation_count}</span>
                <span>{event.duration_ms} ms</span>
              </div>
            ))}
          </div>
        </article>

        <article className="panel">
          <div className="panel-head"><div><p className="eyebrow">DEVICES</p><h2>Perangkat aktif</h2></div></div>
          <div className="stack">
            {data.devices.length === 0 && <p className="empty">Belum ada perangkat.</p>}
            {data.devices.map((device) => <div className="row-item" key={device.id}><div><b>{device.name || "Android device"}</b><small>{device.platform} · {device.app_version || "versi belum dilaporkan"}</small></div><small>{device.last_seen_at ? new Date(device.last_seen_at).toLocaleString("id-ID") : "belum sinkron"}</small></div>)}
          </div>
        </article>

        <article className="panel">
          <div className="panel-head"><div><p className="eyebrow">REVIEW</p><h2>Night Review</h2></div></div>
          {data.review ? <div className="review"><b>{data.review.mood || "Mood belum diisi"}</b><p>{data.review.achievement || "Belum ada catatan pencapaian."}</p><small>Skor tersimpan: {data.review.daily_score}</small></div> : <p className="empty">Review hari ini belum dibuat.</p>}
        </article>
      </section>
    </main>
  );
}
