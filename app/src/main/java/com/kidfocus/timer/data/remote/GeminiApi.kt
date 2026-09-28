package com.kidfocus.timer.data.remote

import android.content.Context
import com.google.firebase.functions.FirebaseFunctions
import com.kidfocus.timer.data.cloud.FirebaseAccountRepository
import com.kidfocus.timer.domain.model.ChatMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class AiModelOption(
    val id: String,
    val label: String,
    val description: String,
    val creditCost: Int,
    val dailyLimit: Int,
    val premiumOnly: Boolean,
)

data class AiUsage(
    val remainingQuestions: Int,
    val remainingCredits: Int,
    val premium: Boolean,
    val tier: String = if (premium) "premium" else "free",
    val earlyAccessUntil: Long? = null,
    val remainingScheduleParses: Int? = null,
)

data class AiConfig(
    val scheduleEnabled: Boolean = false,
    val earlyAccessOpen: Boolean = false,
    val scheduleParseCost: Int = 1,
    val scheduleAdviseCost: Int = 2,
    val scheduleImageCost: Int = 3,
    val scheduleImageTiers: Set<String> = emptySet(),
    val scheduleFreeDailyParses: Int = 0,
    val enabled: Boolean = true,
    val models: List<AiModelOption> = listOf(
        AiModelOption(
            id = "openrouter/free",
            label = "Miễn phí",
            description = "Model miễn phí đang sẵn sàng",
            creditCost = 1,
            dailyLimit = 10,
            premiumOnly = false,
        )
    ),
    val usage: AiUsage = AiUsage(10, 10, false),
)

data class AiReply(val text: String, val usage: AiUsage)

@Singleton
class GeminiApi @Inject constructor(
    @ApplicationContext context: Context,
    private val accountRepository: FirebaseAccountRepository,
) {
    private val guestId = context.getSharedPreferences("ai_access", Context.MODE_PRIVATE).let { prefs ->
        prefs.getString("guest_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("guest_id", it).apply()
        }
    }

    suspend fun getConfig(): Result<AiConfig> = runCatching {
        val result = functions().getHttpsCallable("getAiConfig")
            .call(mapOf("guestId" to guestId))
            .await()
            .data.asStringMap()
        result.toAiConfig()
    }

    suspend fun chat(history: List<ChatMessage>, modelId: String): Result<AiReply> = runCatching {
        val safeHistory = history.takeLast(12).map {
            mapOf(
                "role" to if (it.isUser) "user" else "assistant",
                "content" to it.content.take(1_500),
            )
        }
        val result = functions().getHttpsCallable("aiChat")
            .call(
                mapOf(
                    "guestId" to guestId,
                    "requestId" to UUID.randomUUID().toString(),
                    "modelId" to modelId,
                    "messages" to safeHistory,
                )
            )
            .await()
            .data.asStringMap()
        AiReply(
            text = result["text"] as? String ?: error("EMPTY_AI_RESPONSE"),
            usage = result["usage"].asStringMap().toAiUsage(),
        )
    }

    private fun functions(): FirebaseFunctions {
        val app = accountRepository.firebaseApp()
            ?: error("FIREBASE_NOT_CONFIGURED")
        return FirebaseFunctions.getInstance(app, "asia-southeast1")
    }
}

private fun Any?.asStringMap(): Map<String, Any?> =
    (this as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }.orEmpty()

private fun Map<String, Any?>.toAiConfig(): AiConfig = AiConfig(
    scheduleEnabled = this["ai_schedule_enabled"] as? Boolean ?: false,
    earlyAccessOpen = this["early_access_open"] as? Boolean ?: false,
    scheduleAdviseCost = (this["ai_schedule_advise_cost"] as? Number)?.toInt() ?: 2,
    scheduleParseCost = (this["ai_schedule_parse_cost"] as? Number)?.toInt() ?: 1,
    scheduleImageCost = (this["ai_schedule_image_cost"] as? Number)?.toInt() ?: 3,
    scheduleImageTiers = (this["ai_schedule_image_tiers"] as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty(),
    scheduleFreeDailyParses = (this["ai_schedule_free_daily_parses"] as? Number)?.toInt() ?: 0,
    enabled = this["enabled"] as? Boolean ?: true,
    models = (this["models"] as? List<*>).orEmpty().mapNotNull { raw ->
        val model = raw.asStringMap()
        val id = model["id"] as? String ?: return@mapNotNull null
        AiModelOption(
            id = id,
            label = model["label"] as? String ?: id,
            description = model["description"] as? String ?: "",
            creditCost = (model["creditCost"] as? Number)?.toInt() ?: 1,
            dailyLimit = (model["dailyLimit"] as? Number)?.toInt() ?: 10,
            premiumOnly = model["premiumOnly"] as? Boolean ?: false,
        )
    },
    usage = this["usage"].asStringMap().toAiUsage(),
)

private fun Map<String, Any?>.toAiUsage(): AiUsage = AiUsage(
    remainingQuestions = (this["remainingQuestions"] as? Number)?.toInt() ?: 0,
    remainingCredits = (this["remainingCredits"] as? Number)?.toInt() ?: 0,
    tier = this["tier"] as? String ?: if (this["premium"] == true) "premium" else "free",
    earlyAccessUntil = (this["earlyAccessUntil"] as? Number)?.toLong(),
    premium = this["premium"] as? Boolean ?: false,
    remainingScheduleParses = (this["remainingScheduleParses"] as? Number)?.toInt(),
)

fun AiConfig.canImportImage(signedIn: Boolean): Boolean = signedIn && scheduleEnabled && enabled && usage.tier in scheduleImageTiers
