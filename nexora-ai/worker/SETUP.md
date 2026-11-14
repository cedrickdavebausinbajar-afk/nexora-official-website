# NEXORA AI — enable the real AI connection

The website and secure Worker source are now in this repository. GitHub Pages cannot keep an API key secret, so the Gemini key belongs only in the Cloudflare Worker secret store.

## 1. Create a Gemini API key
1. Open https://aistudio.google.com/app/apikey
2. Sign in and create an API key for a Gemini API project.
3. Keep the key private. Do not paste it into GitHub, the website, a chat message, or a public file.

Gemini free-tier availability and quotas depend on the model and account. Check https://ai.google.dev/gemini-api/docs/pricing.

## 2. Deploy the Cloudflare Worker
1. Open https://dash.cloudflare.com/ and sign in (create a free account if needed).
2. Go to **Workers & Pages** → **Create** → **Create Worker**.
3. Give it a name such as `nexora-ai-api`, then deploy the starter Worker.
4. Open **Edit code** and replace the starter code with the complete contents of this repository file: `nexora-ai/worker/index.js`.
5. Save and deploy.

## 3. Add the private API key
1. In the Worker, open **Settings** → **Variables and Secrets**.
2. Add a secret named exactly `GEMINI_API_KEY`.
3. Paste your Gemini key as the secret value and save.
4. Redeploy if Cloudflare asks you to.

## 4. Connect the website
1. Open https://cedrickdavebausinbajar-afk.github.io/nexora-official-website/nexora-ai/
2. Open **AI Assistant**.
3. Under **Connect real AI**, paste your Worker URL (for example, `https://nexora-ai-api.YOUR-SUBDOMAIN.workers.dev`).
4. Tap **Save AI connection** and send a normal question to test it.

Task and reminder commands continue to run locally. Other chat questions are sent to Gemini through the Worker. The Worker only accepts requests from the official NEXORA GitHub Pages origin.

## Troubleshooting
- **Server setup incomplete**: check that the Worker secret is named `GEMINI_API_KEY` exactly.
- **Origin not allowed**: use the official NEXORA site URL; do not test from a local file or another domain.
- **AI provider error / quota**: check your Gemini API key, model access, and free-tier limits.
- Never expose the Gemini API key in frontend JavaScript or commit it to this repository.
