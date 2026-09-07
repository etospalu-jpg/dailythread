import { createClient } from "npm:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;

const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
  auth: { persistSession: false, autoRefreshToken: false },
});

const entityMap: Record<string, string> = {
  focus_item: "focus_items",
  task: "tasks",
  activity: "activities",
  habit: "habits",
  habit_entry: "habit_entries",
  daily_review: "daily_reviews",
};

const immutableFields = new Set([
  "id", "user_id", "version", "created_at", "updated_at", "deleted_at", "origin_device_id",
]);

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

function cleanPayload(payload: unknown) {
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) return {};
  const source = payload as Record<string, unknown>;
  const result: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(source)) {
    if (!immutableFields.has(key)) result[key] = value;
  }
  return result;
}

async function currentUser(req: Request) {
  const authHeader = req.headers.get("authorization") || "";
  const token = authHeader.replace(/^Bearer\s+/i, "").trim();
  if (!token) return null;
  const userClient = createClient(SUPABASE_URL, ANON_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
    global: { headers: { Authorization: `Bearer ${token}` } },
  });
  const { data, error } = await userClient.auth.getUser(token);
  if (error || !data.user) return null;
  return data.user;
}

async function touchDevice(userId: string, deviceId: string | null) {
  if (!deviceId) return;
  await admin.from("devices").upsert({
    id: deviceId,
    user_id: userId,
    platform: "android",
    last_seen_at: new Date().toISOString(),
  }, { onConflict: "id" });
}

async function recordSyncEvent(input: {
  userId: string;
  deviceId?: string | null;
  direction: "PUSH" | "PULL";
  status: "SUCCESS" | "PARTIAL" | "FAILED" | "CONFLICT";
  mutationCount?: number;
  syncedCount?: number;
  conflictCount?: number;
  failedCount?: number;
  cursorFrom?: number | null;
  cursorTo?: number | null;
  durationMs: number;
  errorCode?: string | null;
}) {
  const { error } = await admin.from("sync_events").insert({
    user_id: input.userId,
    device_id: input.deviceId ?? null,
    direction: input.direction,
    status: input.status,
    mutation_count: input.mutationCount ?? 0,
    synced_count: input.syncedCount ?? 0,
    conflict_count: input.conflictCount ?? 0,
    failed_count: input.failedCount ?? 0,
    cursor_from: input.cursorFrom ?? null,
    cursor_to: input.cursorTo ?? null,
    duration_ms: Math.max(0, Math.round(input.durationMs)),
    error_code: input.errorCode ?? null,
  });
  if (error) console.error("sync_event_insert_failed", error.message);
}

async function processMutation(userId: string, mutation: any) {
  const mutationId = String(mutation?.mutation_id || "");
  const entityType = String(mutation?.entity_type || "");
  const entityId = String(mutation?.entity_id || "");
  const operation = String(mutation?.operation || "").toUpperCase();
  const deviceId = mutation?.device_id ? String(mutation.device_id) : null;
  const baseVersion = Number(mutation?.base_version ?? 0);
  const table = entityMap[entityType];

  if (!mutationId || !table || !entityId || !["CREATE", "UPDATE", "DELETE"].includes(operation)) {
    return { mutation_id: mutationId, status: "FAILED", error: "invalid_mutation" };
  }

  await touchDevice(userId, deviceId);

  const { data: already } = await admin
    .from("sync_mutations")
    .select("mutation_id")
    .eq("mutation_id", mutationId)
    .eq("user_id", userId)
    .maybeSingle();

  if (already) {
    return { mutation_id: mutationId, status: "SYNCED", idempotent: true };
  }

  if (operation === "CREATE") {
    const row = {
      ...cleanPayload(mutation?.payload),
      id: entityId,
      user_id: userId,
      origin_device_id: deviceId,
    };
    const { data, error } = await admin.from(table).insert(row).select("*").single();
    if (error) {
      if (error.code === "23505") {
        const { data: existing } = await admin
          .from(table)
          .select("*")
          .eq("id", entityId)
          .eq("user_id", userId)
          .maybeSingle();
        if (existing) {
          await admin.from("sync_mutations").upsert({
            mutation_id: mutationId, user_id: userId, device_id: deviceId,
            entity_type: entityType, entity_id: entityId, operation,
          });
          return { mutation_id: mutationId, status: "SYNCED", recovered: true, entity: existing };
        }

        const input = cleanPayload(mutation?.payload) as Record<string, any>;
        let conflictQuery: any = admin.from(table).select("*").eq("user_id", userId).is("deleted_at", null);
        if (entityType === "focus_item" && input.focus_date && input.sort_order) {
          conflictQuery = conflictQuery.eq("focus_date", input.focus_date).eq("sort_order", input.sort_order);
        } else if (entityType === "habit" && input.name) {
          conflictQuery = conflictQuery.ilike("name", String(input.name));
        } else if (entityType === "habit_entry" && input.habit_id && input.entry_date) {
          conflictQuery = conflictQuery.eq("habit_id", input.habit_id).eq("entry_date", input.entry_date);
        } else if (entityType === "daily_review" && input.review_date) {
          conflictQuery = conflictQuery.eq("review_date", input.review_date);
        } else {
          conflictQuery = null;
        }
        if (conflictQuery) {
          const { data: conflicting } = await conflictQuery.limit(1).maybeSingle();
          if (conflicting) {
            return {
              mutation_id: mutationId,
              status: "CONFLICT",
              error: "unique_conflict",
              server_version: Number(conflicting.version || 0),
              server_updated_at: conflicting.updated_at,
              server_entity: conflicting,
            };
          }
        }
        return { mutation_id: mutationId, status: "CONFLICT", error: "unique_conflict" };
      }
      return { mutation_id: mutationId, status: "FAILED", error: error.message };
    }
    await admin.from("sync_mutations").insert({
      mutation_id: mutationId, user_id: userId, device_id: deviceId,
      entity_type: entityType, entity_id: entityId, operation,
    });
    return { mutation_id: mutationId, status: "SYNCED", entity: data };
  }

  const { data: existing, error: fetchError } = await admin
    .from(table)
    .select("*")
    .eq("id", entityId)
    .eq("user_id", userId)
    .maybeSingle();

  if (fetchError) return { mutation_id: mutationId, status: "FAILED", error: fetchError.message };
  if (!existing) return { mutation_id: mutationId, status: "CONFLICT", error: "not_found" };
  if (baseVersion > 0 && Number(existing.version) !== baseVersion) {
    return {
      mutation_id: mutationId,
      status: "CONFLICT",
      error: "version_mismatch",
      server_version: Number(existing.version),
      server_updated_at: existing.updated_at,
      server_entity: existing,
    };
  }

  const patch = operation === "DELETE"
    ? { deleted_at: new Date().toISOString(), origin_device_id: deviceId }
    : { ...cleanPayload(mutation?.payload), origin_device_id: deviceId };

  const { data, error } = await admin
    .from(table)
    .update(patch)
    .eq("id", entityId)
    .eq("user_id", userId)
    .select("*")
    .single();

  if (error) return { mutation_id: mutationId, status: "FAILED", error: error.message };

  await admin.from("sync_mutations").insert({
    mutation_id: mutationId, user_id: userId, device_id: deviceId,
    entity_type: entityType, entity_id: entityId, operation,
  });

  return { mutation_id: mutationId, status: "SYNCED", entity: data };
}

async function pullChanges(userId: string, cursor: number, limit: number) {
  const safeLimit = Math.max(1, Math.min(500, Number(limit || 200)));
  const { data: logs, error } = await admin
    .from("change_log")
    .select("cursor,entity_type,entity_id,operation,version,origin_device_id,changed_at")
    .eq("user_id", userId)
    .gt("cursor", Math.max(0, Number(cursor || 0)))
    .order("cursor", { ascending: true })
    .limit(safeLimit);

  if (error) throw error;
  const changes = [];
  for (const log of logs || []) {
    const table = entityMap[log.entity_type];
    if (!table) continue;
    const { data: entity } = await admin
      .from(table)
      .select("*")
      .eq("id", log.entity_id)
      .eq("user_id", userId)
      .maybeSingle();
    changes.push({ ...log, payload: entity || null });
  }
  const nextCursor = changes.length ? Number(changes[changes.length - 1].cursor) : Math.max(0, Number(cursor || 0));
  return { changes, next_cursor: nextCursor, has_more: (logs || []).length === safeLimit };
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const user = await currentUser(req);
  if (!user) return json({ error: "unauthorized" }, 401);

  let body: any;
  try { body = await req.json(); } catch { return json({ error: "invalid_json" }, 400); }

  const action = String(body?.action || "").toLowerCase();
  const started = performance.now();
  const deviceId = body?.device_id ? String(body.device_id) : null;

  try {
    if (action === "push") {
      const mutations = Array.isArray(body?.mutations) ? body.mutations.slice(0, 100) : [];
      const results = [];
      for (const mutation of mutations) results.push(await processMutation(user.id, mutation));

      const synced = results.filter((r: any) => r.status === "SYNCED").length;
      const conflicts = results.filter((r: any) => r.status === "CONFLICT").length;
      const failed = results.filter((r: any) => r.status === "FAILED").length;
      const status = failed > 0 ? (synced > 0 || conflicts > 0 ? "PARTIAL" : "FAILED") : conflicts > 0 ? "CONFLICT" : "SUCCESS";

      await recordSyncEvent({
        userId: user.id,
        deviceId: deviceId || (mutations[0]?.device_id ? String(mutations[0].device_id) : null),
        direction: "PUSH",
        status,
        mutationCount: mutations.length,
        syncedCount: synced,
        conflictCount: conflicts,
        failedCount: failed,
        durationMs: performance.now() - started,
        errorCode: failed > 0 ? "mutation_failed" : conflicts > 0 ? "mutation_conflict" : null,
      });

      return json({ ok: true, results });
    }

    if (action === "pull") {
      await touchDevice(user.id, deviceId);
      const cursorFrom = Math.max(0, Number(body?.cursor || 0));
      const result = await pullChanges(user.id, cursorFrom, Number(body?.limit || 200));
      await recordSyncEvent({
        userId: user.id,
        deviceId,
        direction: "PULL",
        status: "SUCCESS",
        mutationCount: result.changes.length,
        syncedCount: result.changes.length,
        cursorFrom,
        cursorTo: result.next_cursor,
        durationMs: performance.now() - started,
      });
      return json({ ok: true, ...result });
    }

    return json({ error: "invalid_action" }, 400);
  } catch (error) {
    console.error(error);
    if (action === "push" || action === "pull") {
      await recordSyncEvent({
        userId: user.id,
        deviceId,
        direction: action === "push" ? "PUSH" : "PULL",
        status: "FAILED",
        durationMs: performance.now() - started,
        errorCode: error instanceof Error ? error.message.slice(0, 180) : "internal_error",
      });
    }
    return json({ error: "internal_error" }, 500);
  }
});
