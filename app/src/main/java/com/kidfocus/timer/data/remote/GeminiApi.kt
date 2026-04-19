package com.kidfocus.timer.data.remote

import com.kidfocus.timer.domain.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiApi @Inject constructor() {
    companion object {
        private const val BASE_URL =
            "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent"

        private const val SYSTEM_PROMPT = """Bạn là trợ lý học tập thân thiện tên là "Cú học" 🦉, dành cho học sinh tiểu học và trung học cơ sở Việt Nam.
Hãy giải thích ngắn gọn, dễ hiểu, dùng tiếng Việt đơn giản phù hợp với trẻ em.
Chỉ trả lời câu hỏi liên quan đến học tập, bài vở, kiến thức phổ thông.
Nếu câu hỏi không phù hợp, nhẹ nhàng từ chối và khuyến khích học bài.
Trả lời tối đa 4-5 câu, dùng ví dụ cụ thể khi cần."""
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun chat(history: List<ChatMessage>, apiKey: String): Result<String> =
        withContext(Dispatchers.IO) {
            android.util.Log.d("GeminiApi", "key=${apiKey.take(8)}... len=${apiKey.length}")
            try {
                val contentsArray = JSONArray()
                // Prepend system prompt as first user/model exchange
                contentsArray.put(
                    JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
                )
                contentsArray.put(
                    JSONObject().put("role", "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", "Được rồi! Tôi sẽ hỗ trợ bé học tập nhé 🦉")))
                )
                history.forEach { msg ->
                    contentsArray.put(
                        JSONObject()
                            .put("role", if (msg.isUser) "user" else "model")
                            .put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
                    )
                }

                val body = JSONObject()
                    .put("contents", contentsArray)
                    .put("generationConfig", JSONObject().put("maxOutputTokens", 600).put("temperature", 0.7))
                    .toString()

                val request = Request.Builder()
                    .url("$BASE_URL?key=$apiKey")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    android.util.Log.e("GeminiApi", "HTTP ${response.code}: $responseBody")
                    return@withContext Result.failure(Exception("API_ERROR_${response.code}"))
                }

                val text = JSONObject(responseBody)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                Result.success(text.trim())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
