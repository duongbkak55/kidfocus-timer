// Manual live eval only; never called by unit tests or deployed Functions.
async function callProvider(body) {
  const response = await fetch("https://openrouter.ai/api/v1/chat/completions", {
    method: "POST", headers: {"Authorization": `Bearer ${process.env.OPENROUTER_API_KEY}`, "Content-Type": "application/json"},
    body: JSON.stringify(body), signal: AbortSignal.timeout(30_000),
  });
  if (!response.ok) throw new Error(`HTTP_${response.status}`);
  return response.json();
}
function usageMeter() {
  let calls = 0; let input = 0; let output = 0;
  return {
    add(payload) {
      const inTokens = payload.usage?.prompt_tokens;
      const outTokens = payload.usage?.completion_tokens;
      if (Number.isFinite(inTokens) && Number.isFinite(outTokens)) { calls++; input += inTokens; output += outTokens; }
    },
    print(label) {
      console.log(`${label} tokens per response with usage: in ${(input / Math.max(1, calls)).toFixed(1)}, out ${(output / Math.max(1, calls)).toFixed(1)} (${calls} responses)`);
    },
  };
}
function failureCode(error) {
  if (/^HTTP_\d+$/.test(error?.message)) return error.message;
  if (error?.name === "TimeoutError" || error?.name === "AbortError") return "TIMEOUT";
  if (error?.message?.startsWith("AI_")) return "INVALID_RESPONSE";
  if (error?.code === "ERR_ASSERTION") return "CONSTRAINT";
  if (error instanceof SyntaxError) return "INVALID_JSON";
  return "EVAL_ERROR";
}
module.exports = {callProvider, usageMeter, failureCode};
