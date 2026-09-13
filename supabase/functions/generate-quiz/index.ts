import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

type QuizQuestionType = "MULTIPLE_CHOICE" | "TRUE_FALSE" | "SHORT_ANSWER";

type QuizQuestion = {
  prompt: string;
  answer: string;
  options?: string[];
  type: QuizQuestionType;
  explanation?: string;
};

type Quiz = {
  title: string;
  questions: QuizQuestion[];
};

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};

function respond(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}

function normalizeQuiz(value: unknown, requestedCount: number): Quiz {
  if (!value || typeof value !== "object") throw new Error("AI returned an invalid quiz");
  const root = value as Record<string, unknown>;
  const rawQuestions = Array.isArray(root.questions) ? root.questions : [];
  const questions: QuizQuestion[] = rawQuestions.slice(0, requestedCount).map((raw) => {
    if (!raw || typeof raw !== "object") throw new Error("AI returned an invalid question");
    const item = raw as Record<string, unknown>;
    const prompt = typeof item.prompt === "string" ? item.prompt.trim() : "";
    const answer = typeof item.answer === "string" ? item.answer.trim() : "";
    const type = item.type === "TRUE_FALSE" || item.type === "SHORT_ANSWER" ? item.type : "MULTIPLE_CHOICE";
    const options = Array.isArray(item.options) ? item.options.filter((option): option is string => typeof option === "string").map((option) => option.trim()).filter(Boolean).slice(0, 6) : [];
    const explanation = typeof item.explanation === "string" ? item.explanation.trim() : "";
    if (!prompt || !answer) throw new Error("AI returned an incomplete question");
    if (type === "MULTIPLE_CHOICE" && options.length < 2) throw new Error("AI returned incomplete answer choices");
    if (type === "TRUE_FALSE") return { prompt, answer: answer.toLowerCase() === "true" ? "true" : "false", options: ["true", "false"], type, explanation };
    return { prompt, answer, options, type, explanation };
  });
  if (questions.length === 0) throw new Error("No quiz questions were generated");
  return {
    title: typeof root.title === "string" && root.title.trim() ? root.title.trim() : "AI study quiz",
    questions,
  };
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return respond({ error: "Method not allowed" }, 405);

  const authorization = request.headers.get("Authorization") ?? "";
  const accessToken = authorization.replace(/^Bearer\s+/i, "").trim();
  if (!accessToken) return respond({ error: "Sign in before generating a quiz." }, 401);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const openAiKey = Deno.env.get("OPENAI_API_KEY");
  if (!supabaseUrl || !supabaseAnonKey || !openAiKey) return respond({ error: "AI quiz service is not configured." }, 503);

  const supabase = createClient(supabaseUrl, supabaseAnonKey, { global: { headers: { Authorization: `Bearer ${accessToken}` } } });
  const { data: { user }, error: userError } = await supabase.auth.getUser(accessToken);
  if (userError || !user) return respond({ error: "Your cloud session has expired. Sign in again." }, 401);

  let input: { sourceText?: unknown; requestedCount?: unknown; difficulty?: unknown };
  try {
    input = await request.json();
  } catch {
    return respond({ error: "Request body must be JSON." }, 400);
  }
  const sourceText = typeof input.sourceText === "string" ? input.sourceText.trim().slice(0, 60_000) : "";
  const requestedCount = typeof input.requestedCount === "number" ? Math.min(20, Math.max(1, Math.floor(input.requestedCount))) : 8;
  const difficulty = ["easy", "medium", "hard", "mixed"].includes(String(input.difficulty)) ? String(input.difficulty) : "mixed";
  if (sourceText.length < 40) return respond({ error: "Add more study text before generating a quiz." }, 400);

  const openAiResponse = await fetch("https://api.openai.com/v1/chat/completions", {
    method: "POST",
    headers: { Authorization: `Bearer ${openAiKey}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      model: Deno.env.get("OPENAI_MODEL") ?? "gpt-4o-mini",
      temperature: 0.25,
      max_tokens: Math.min(5000, 900 + requestedCount * 460),
      response_format: { type: "json_object" },
      messages: [
        {
          role: "system",
          content: "You create accurate study quizzes from the supplied material only. Do not invent facts. Return JSON with exactly this shape: {title:string, questions:[{prompt:string, answer:string, options:string[], type:'MULTIPLE_CHOICE'|'TRUE_FALSE'|'SHORT_ANSWER', explanation:string}]}. For MULTIPLE_CHOICE use four choices and make answer exactly one choice. For TRUE_FALSE use answer true or false and options ['true','false']. For SHORT_ANSWER use an answer that can be checked against the source. Keep questions concise and vary the types. Difficulty: " + difficulty,
        },
        { role: "user", content: `Generate ${requestedCount} questions from this study material:\n\n${sourceText}` },
      ],
    }),
  });
  if (!openAiResponse.ok) {
    const detail = await openAiResponse.text();
    console.error("OpenAI request failed", openAiResponse.status, detail.slice(0, 500));
    return respond({ error: "The AI provider could not generate a quiz right now." }, 502);
  }

  try {
    const payload = await openAiResponse.json();
    const content = payload?.choices?.[0]?.message?.content;
    const parsed = typeof content === "string" ? JSON.parse(content) : content;
    return respond(normalizeQuiz(parsed, requestedCount));
  } catch (error) {
    console.error("Invalid OpenAI quiz response", error);
    return respond({ error: "The AI provider returned an unreadable quiz." }, 502);
  }
});
