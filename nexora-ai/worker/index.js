const ALLOWED_ORIGIN = "https://cedrickdavebausinbajar-afk.github.io";
// Use the same model name that was used in the user's successful Gemini API test.
const MODEL = "gemini-flash-latest";

function corsHeaders(origin) {
  // This Worker is intended to be called by the published NEXORA GitHub Pages site.
  if (origin !== ALLOWED_ORIGIN) return {};
  return {
    "Access-Control-Allow-Origin": ALLOWED_ORIGIN,
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
    "Access-Control-Max-Age": "86400",
    "Vary": "Origin"
  };
}

function json(data, status=200, headers={}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...headers,
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store"
    }
  });
}

export default {
  async fetch(request, env) {
    const origin = request.headers.get("Origin") || "";
    const cors = corsHeaders(origin);

    // Answer browser preflight requests before processing the actual POST.
    if (request.method === "OPTIONS") {
      if (!cors["Access-Control-Allow-Origin"]) {
        return new Response("Origin not allowed", {status: 403});
      }
      return new Response(null, {status: 204, headers: cors});
    }

    if (origin !== ALLOWED_ORIGIN) {
      return json({error: "Origin not allowed. Open NEXORA from its official website."}, 403);
    }
    if (request.method !== "POST") {
      return json({error: "Use POST to chat with NEXORA AI."}, 405, cors);
    }
    if (!env.GEMINI_API_KEY) {
      return json({error: "Server setup incomplete: GEMINI_API_KEY secret is missing in Cloudflare."}, 503, cors);
    }

    try {
      const body = await request.json();
      const message = String(body.message || "").trim().slice(0, 3000);
      if (!message) return json({error: "Please enter a message."}, 400, cors);

      const history = Array.isArray(body.history) ? body.history.slice(-12) : [];
      const contents = history.map(item => ({
        role: item.role === "model" ? "model" : "user",
        parts: [{text: String(item.text || "").slice(0, 3000)}]
      })).filter(item => item.parts[0].text);

      contents.push({role: "user", parts: [{text: message}]});

      const upstream = await fetch(
        "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "x-goog-api-key": env.GEMINI_API_KEY
          },
          body: JSON.stringify({
            systemInstruction: {
              parts: [{
                text: "You are NEXORA AI, a helpful, friendly personal assistant. Be clear, practical, honest, and concise. Help with planning, learning, writing, brainstorming, and everyday questions. Do not claim to have created reminders or changed tasks unless the user does so in the NEXORA app."
              }]
            },
            contents,
            generationConfig: {temperature: 0.7, maxOutputTokens: 900}
          })
        }
      );

      const result = await upstream.json();
      if (!upstream.ok) {
        return json({
          error: result.error?.message || "The Gemini API rejected the request. Check the model and API key configuration."
        }, upstream.status, cors);
      }

      const reply = result.candidates?.[0]?.content?.parts?.map(part => part.text || "").join("").trim();
      if (!reply) {
        return json({error: "Gemini returned no text. Please try again."}, 502, cors);
      }
      return json({reply}, 200, cors);
    } catch (error) {
      return json({
        error: "The AI service could not process this request. Check the Worker logs and GEMINI_API_KEY secret."
      }, 500, cors);
    }
  }
};
