const ALLOWED_ORIGIN = "https://cedrickdavebausinbajar-afk.github.io";
const MODEL = "gemini-3.8-flash";
const cors = {
  "Access-Control-Allow-Origin": ALLOWED_ORIGIN,
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
  "Vary": "Origin"
};
function json(data, status=200) {
  return new Response(JSON.stringify(data), {status, headers:{...cors,"Content-Type":"application/json; charset=utf-8","Cache-Control":"no-store"}});
}
export default {
  async fetch(request, env) {
    const origin = request.headers.get("Origin") || "";
    if (origin !== ALLOWED_ORIGIN) return new Response("Origin not allowed", {status:403});
    if (request.method === "OPTIONS") return new Response(null,{status:204,headers:cors});
    if (request.method !== "POST") return json({error:"Use POST to chat with NEXORA AI."},405);
    if (!env.GEMINI_API_KEY) return json({error:"Server setup incomplete: GEMINI_API_KEY secret is missing."},503);
    try {
      const body = await request.json();
      const message = String(body.message || "").trim().slice(0,3000);
      if (!message) return json({error:"Please enter a message."},400);
      const history = Array.isArray(body.history) ? body.history.slice(-12) : [];
      const contents = history.map(item => ({
        role: item.role === "model" ? "model" : "user",
        parts: [{text:String(item.text || "").slice(0,3000)}]
      })).filter(item => item.parts[0].text);
      contents.push({role:"user",parts:[{text:message}]});
      const upstream = await fetch("https://generativelanguage.googleapis.com/v1beta/models/"+MODEL+":generateContent",{
        method:"POST",
        headers:{"Content-Type":"application/json","x-goog-api-key":env.GEMINI_API_KEY},
        body:JSON.stringify({
          systemInstruction:{parts:[{text:"You are NEXORA AI, a helpful, friendly personal assistant. Be clear, practical, honest, and concise. Help with planning, learning, writing, brainstorming, and everyday questions. Do not claim to have created reminders or changed tasks unless the user does so in the NEXORA app."}]},
          contents,
          generationConfig:{temperature:0.7,maxOutputTokens:900}
        })
      });
      const result = await upstream.json();
      if (!upstream.ok) return json({error:result.error?.message || "The AI provider rejected the request."},upstream.status);
      const reply = result.candidates?.[0]?.content?.parts?.map(p=>p.text||"").join("").trim();
      if (!reply) return json({error:"The AI returned no text. Please try again."},502);
      return json({reply});
    } catch (error) {
      return json({error:"The AI service could not process this request. Check your Worker configuration and try again."},500);
    }
  }
};