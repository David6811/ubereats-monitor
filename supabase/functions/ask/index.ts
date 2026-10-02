// Edge function. One question from the driver plus what the phone knows right
// now, one short spoken answer back from Gemini.
//
// The Gemini key lives here as a secret, never in the app. The caller must be
// a signed-in driver: the request's JWT is checked against Supabase Auth, so
// the function cannot be used as a free Gemini proxy.
//
//   supabase secrets set GEMINI_API_KEY=...
//   supabase functions deploy ask

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const MODEL = "gemini-2.5-flash";

const SYSTEM_ZH = [
  "你是一个外卖司机的语音助手，司机正在开车，只能听、不能看。",
  "司机问什么就答什么：问单子就看「现在的情况」，问别的（天气、路况、换算、闲聊、常识）就正常回答。",
  "关于单子的事只按「现在的情况」说，那里没有的不要编，直说没有。",
  "回答用中文口语，尽量两三句说完，不用列表、不用符号、不用英文缩写。",
  "地址和店名照原文念，不翻译。钱说成「十六块二」这种说法。",
  "你不替司机做接单决定；问该不该接，就把判断结果和理由念出来。",
].join("\n");

const SYSTEM_EN = [
  "You are a food delivery driver's voice assistant. He is driving: he can hear you, he cannot look.",
  "Answer whatever he asks: about his jobs, read \"what is happening now\"; about anything else (weather, traffic, conversions, small talk, general knowledge), answer normally.",
  "About the jobs, say only what \"what is happening now\" says. Do not invent; if it is not there, say so.",
  "Answer in spoken English, two or three sentences at most, no lists, no symbols, no abbreviations.",
  "Read addresses and shop names exactly as written; do not translate them.",
  "You do not decide for him whether to take a job; if he asks, read out the verdict and its reason.",
].join("\n");

type Turn = { question: string; answer: string };

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("POST only", { status: 405 });

  const auth = req.headers.get("Authorization") ?? "";
  const client = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
    { global: { headers: { Authorization: auth } } },
  );
  const { data: { user } } = await client.auth.getUser();
  if (!user) return json({ error: "not signed in" }, 401);

  const key = Deno.env.get("GEMINI_API_KEY");
  if (!key) return json({ error: "no GEMINI_API_KEY secret" }, 500);

  const body = await req.json().catch(() => ({}));
  const question = String(body.question ?? "").trim();
  const context = String(body.context ?? "").trim();
  const history: Turn[] = Array.isArray(body.history) ? body.history.slice(-3) : [];
  // The driver's own language, as the app's settings have it.
  const english = String(body.lang ?? "zh") === "en";
  if (!question) return json({ error: "no question" }, 400);

  const contents = [];
  for (const turn of history) {
    contents.push({ role: "user", parts: [{ text: turn.question }] });
    contents.push({ role: "model", parts: [{ text: turn.answer }] });
  }
  contents.push({
    role: "user",
    parts: [{
      text: english
        ? "What is happening now (use it only when he asks about the jobs):\n" +
          (context || "(nothing known)") + "\n\nHe asks: " + question
        : "现在的情况（只在问到单子时用）：\n" + (context || "（没有信息）") + "\n\n司机问：" + question,
    }],
  });

  const began = Date.now();
  const res = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent?key=${key}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: english ? SYSTEM_EN : SYSTEM_ZH }] },
        contents,
        // No thinking: it eats the output budget and the answer comes back cut
        // off mid-sentence ("你手上一共有十八").
        generationConfig: { temperature: 0.6, maxOutputTokens: 500, thinkingConfig: { thinkingBudget: 0 } },
      }),
    },
  );
  const data = await res.json();
  if (!res.ok) return json({ error: data?.error?.message ?? "gemini failed" }, 502);

  const answer: string = data?.candidates?.[0]?.content?.parts?.map((p: { text?: string }) => p.text ?? "").join("").trim()
    ?? "";
  return json({
    answer: answer || (english ? "I did not catch that, ask again" : "我没听清，再问一次"),
    millis: Date.now() - began,
  });
});

function json(payload: unknown, status = 200) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
