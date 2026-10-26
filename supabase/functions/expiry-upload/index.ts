// Supabase Edge Function: expiry-upload
// Requires migration 005 to be applied and SUPABASE_URL + SUPABASE_SERVICE_ROLE_KEY
// configured as Edge Function secrets. Never ship the service-role key in Android.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}

// Only use the platform edge IP header when present. Do not trust caller-provided
// X-Forwarded-For fallbacks. If the trusted header is unavailable, retain NULL.
function observedClientIp(req: Request): string | null {
  const value = req.headers.get("cf-connecting-ip")?.trim();
  if (!value || value.length > 64) return null;
  // inet validation is ultimately enforced by Postgres; reject obvious malformed values here.
  if (!/^[0-9a-fA-F:.]+$/.test(value)) return null;
  return value;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const authorization = req.headers.get("authorization");
  if (!supabaseUrl || !serviceKey || !authorization?.startsWith("Bearer ")) {
    return json({ error: "Unauthorized" }, 401);
  }

  const admin = createClient(supabaseUrl, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const token = authorization.slice("Bearer ".length);
  const { data: authData, error: authError } = await admin.auth.getUser(token);
  if (authError || !authData.user) return json({ error: "Invalid session" }, 401);

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return json({ error: "Invalid JSON body" }, 400);
  }

  const deviceId = String(body.device_id ?? "").trim();
  const claimedStore = String(body.store_name ?? "").trim();
  const id = String(body.id ?? "").trim();
  const skuName = String(body.skuname ?? "").trim();
  const skuCode = String(body.skucode ?? "").trim();
  const ean = String(body.ean ?? "").trim();
  const qty = Number(body.qty);
  const expiryDate = String(body.expirydate ?? "").trim();
  const isDeleted = body.is_deleted === true;
  const ip = observedClientIp(req);

  if (!deviceId || !claimedStore || !id || !skuName || !skuCode || !ean ||
      !Number.isInteger(qty) || qty <= 0 || !/^\d{4}-\d{2}-\d{2}$/.test(expiryDate)) {
    return json({ error: "Missing or invalid upload fields" }, 400);
  }

  const { data: registration, error: registryError } = await admin
    .from("expiry_device_registry")
    .select("registered_store,is_active")
    .eq("device_id", deviceId)
    .maybeSingle();

  if (registryError) {
    console.error("Device registry lookup failed", registryError.message);
    return json({ error: "Security configuration unavailable" }, 503);
  }

  if (!registration || !registration.is_active ||
      registration.registered_store.trim().toLowerCase() !== claimedStore.toLowerCase()) {
    const { error: auditError } = await admin.from("expiry_security_events").insert({
      event_type: !registration ? "unregistered_device_upload" :
        !registration.is_active ? "inactive_device_upload" : "store_mismatch",
      device_id: deviceId,
      owner_id: authData.user.id,
      registered_store: registration?.registered_store ?? null,
      claimed_store: claimedStore,
      observed_ip: ip,
      details: { upload_id: id },
    });
    if (auditError) console.error("Security event insert failed", auditError.message);
    return json({ error: "Device is not authorized for this store" }, 403);
  }

  const { error: uploadError } = await admin.from("expiry_scan_uploads").upsert({
    id,
    owner_id: authData.user.id,
    store_name: registration.registered_store,
    device_id: deviceId,
    ip_address: ip,
    skuname: skuName,
    skucode: skuCode,
    ean,
    qty,
    expirydate: expiryDate,
    is_deleted: isDeleted,
    updated_at: new Date().toISOString(),
  }, { onConflict: "id" });

  if (uploadError) {
    console.error("Expiry upload failed", uploadError.message);
    return json({ error: "Upload failed" }, 500);
  }

  return json({ ok: true, id, ip_recorded: ip !== null });
});
