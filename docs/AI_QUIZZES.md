# ChatGPT quiz handoff

Inkora can create a quiz with the ChatGPT account already signed in on the user's browser without storing an API key or reading browser cookies.

1. Open a document and choose **Quiz**.
2. Choose a difficulty and select **Open ChatGPT**.
3. ChatGPT opens with a prompt copied to the clipboard. Paste the prompt into ChatGPT.
4. Ask ChatGPT to return the requested JSON only, copy its response, then choose **Paste JSON** in Inkora.
5. Inkora validates the response and starts the quiz locally.

The quiz prompt asks for this shape:

```json
{
  "title": "Short title",
  "questions": [
    {
      "prompt": "Question text",
      "answer": "Correct answer",
      "options": ["Choice A", "Choice B", "Choice C", "Choice D"],
      "type": "MULTIPLE_CHOICE",
      "explanation": "Why this answer is correct"
    }
  ]
}
```

Use `TRUE_FALSE` with `["true", "false"]` options or `SHORT_ANSWER` with an empty options list when appropriate. Inkora accepts fenced JSON, ignores unknown fields, limits imports to 20 questions, and rejects missing prompts or answers.

This flow is intentionally manual. A consumer ChatGPT login cannot be silently reused by a desktop or Android app. 9router can authenticate a Codex account and expose a local OpenAI-compatible endpoint, but that still requires running a separate local service and making an API request. Inkora does not copy 9router's private token or ChatGPT endpoint handling.