# AI quiz generation

Inkora provides a **ChatGPT quiz** action. The action sends the current study text to the
`generate-quiz` Supabase Edge Function. The function verifies the user's
Supabase session, calls the OpenAI API, validates the returned JSON, and sends
only the structured quiz back to Inkora.

Inkora does not automate a consumer ChatGPT login. A ChatGPT subscription and
an OpenAI API key are separate products and sessions. Keeping the key in the
Edge Function prevents it from being extracted from the Windows executable or
Android APK.

## Configure the function

From a machine that has access to the Inkora Supabase project:

```bash
supabase functions deploy generate-quiz --project-ref gfhjwencfqhxfswscyv
supabase secrets set OPENAI_API_KEY=sk-... OPENAI_MODEL=gpt-4o-mini --project-ref gfhjwencfqhxfswscyv
```

The function also needs the standard `SUPABASE_URL` and
`SUPABASE_ANON_KEY` values that Supabase supplies automatically. The caller
must sign in under **Account** before using the ChatGPT action.

If the function or key is unavailable, Inkora reports the error and leaves the
document unchanged. Quiz generation requires a signed-in cloud account.
