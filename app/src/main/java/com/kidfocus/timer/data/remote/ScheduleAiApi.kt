package com.kidfocus.timer.data.remote

import com.google.firebase.functions.FirebaseFunctions
import com.kidfocus.timer.data.cloud.FirebaseAccountRepository
import com.kidfocus.timer.domain.schedule.*
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

interface ScheduleParser {
    suspend fun parse(text: String, ageBand: String, current: ScheduleState): ScheduleParseReply
}
data class ScheduleParseReply(val draft: ScheduleDraft, val usage: AiUsage)

@Singleton
class ScheduleAiApi @Inject constructor(private val account: FirebaseAccountRepository) : ScheduleParser {
    private fun functions(): FirebaseFunctions = FirebaseFunctions.getInstance(
        account.firebaseApp() ?: error("FIREBASE_NOT_CONFIGURED"), "asia-southeast1")

    suspend fun claimEarlyAccess() {
        withTimeout(30_000) { functions().getHttpsCallable("claimEarlyAccess").call().await() }
    }

    override suspend fun parse(text: String, ageBand: String, current: ScheduleState): ScheduleParseReply = withTimeout(30_000) {
        require(text.isNotBlank() && text.length <= 2000)
        val response = functions().getHttpsCallable("aiSchedule").also { it.setTimeout(30, TimeUnit.SECONDS) }
            .call(mapOf("requestId" to UUID.randomUUID().toString(), "text" to text,
                "ageBand" to (ageBand.takeIf { it in ScheduleThresholds.sleepMinutes } ?: "4-5"),
                "today" to LocalDate.now().toString(), "locale" to Locale.getDefault().let { locale -> locale.language + (locale.country.takeIf { it.length == 2 }?.let { "-$it" } ?: "") },
                "current" to current.tasks.take(60).map { task -> mapOf(
                    "name" to task.name.take(60), "days" to DayCodec.fromCalendar(task.daysOfWeek).sortedBy { it.value }.map { it.name.take(3) },
                    "start" to "%02d:%02d".format(Locale.ROOT, task.hour, task.minute), "durationMin" to task.focusDurationMinutes,
                ) }))
            .await().data as? Map<*, *> ?: error("AI_PARSE_FAILED")
        val rawDraft = response["draft"] as? Map<*, *> ?: error("AI_PARSE_FAILED")
        val usage = response["usage"] as? Map<*, *> ?: error("AI_PARSE_FAILED")
        ScheduleParseReply(ScheduleDraftMapper.fromMap(rawDraft.entries.associate { it.key.toString() to it.value }),
            AiUsage((usage["remainingQuestions"] as? Number)?.toInt() ?: 0,
                (usage["remainingCredits"] as? Number)?.toInt() ?: 0, usage["premium"] == true,
                usage["tier"] as? String ?: "free", (usage["earlyAccessUntil"] as? Number)?.toLong()))
    }
}
