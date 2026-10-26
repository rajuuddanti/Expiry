// Supabase Edge Function: expiry-upload
// Upload authorization is based on the signed-in user. Store name + observed IP are
// audit signals; an unknown IP is allowed but flagged for admin review.
// Never ship SUPABASE_SERVICE_ROLE_KEY in Android.
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

// Only accept the edge platform's trusted client-IP header; never trust a caller's
// X-Forwarded-For value. If unavailable, store NULL and flag the upload.
function observedClientIp(req: Request): string | null {
  const value = req.headers.get("cf-connecting-ip")?.trim();
  if (!value || value.length > 64 || !/^[0-9a-fA-F:.]+$/.test(value)) return null;
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

  const claimedStore = String(body.store_name ?? "").trim();
  const id = String(body.id ?? "").trim();
  const skuName = String(body.skuname ?? "").trim();
  const skuCode = String(body.skucode ?? "").trim();
  const ean = String(body.ean ?? "").trim();
  const qty = Number(body.qty);
  const expiryDate = String(body.expirydate ?? "").trim();
  const isDeleted = body.is_deleted === true;
  const ip = observedClientIp(req);

  if (!claimedStore || claimedStore.length > 120 || !id || !skuName || !skuCode || !ean ||
      !Number.isInteger(qty) || qty <= 0 || !/^\d{4}-\d{2}-\d{2}$/.test(expiryDate)) {
    return json({ error: "Missing or invalid upload fields" }, 400);
  }

  // Match an admin-approved store/IP pair. Unknown IPs are deliberately allowed
  // and flagged; they are NOT auto-approved by this upload.
  let securityFlag = ip === null;
  let securityReason: string | null = ip === null ? "client_ip_unavailable" : null;

  if (ip !== null) {
    const { data: approvedPair, error: registryError } = await admin
      .from("expiry_store_ip_registry")
      .select("store_name")
      .ilike("store_name", claimedStore)
      .eq("approved_ip", ip)
      .eq("is_active", true)
      .maybeSingle();

    if (registryError) {
      console.error("Store/IP registry lookup failed", registryError.message);
      return json({ error: "Security configuration unavailable" }, 503);
    }
    if (!approvedPair) {
      securityFlag = true;
      securityReason = "unrecognised_store_ip";
      const { error: auditError } = await admin.from("expiry_security_events").insert({
        event_type: "unrecognised_store_ip",
        owner_id: authData.user.id,
        claimed_store: claimedStore,
        observed_ip: ip,
        details: { upload_id: id },
      });
      if (auditError) console.error("Security event insert failed", auditError.message);
    }
  } else {
    const { error: auditError } = await admin.from("expiry_security_events").insert({
      event_type: "client_ip_unavailable",
      owner_id: authData.user.id,
      claimed_store: claimedStore,
      details: { upload_id: id },
    });
    if (auditError) console.error("Security event insert failed", auditError.message);
  }

  const { error: uploadError } = await admin.from("expiry_scan_uploads").upsert({
    id,
    owner_id: authData.user.id,
    store_name: claimedStore,
    device_id: null,
    ip_address: ip,
    security_flag: securityFlag,
    security_reason: securityReason,
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

  return json({ ok: true, id, ip_recorded: ip !== null, security_flag: securityFlag,
    security_reason: securityReason });
});
