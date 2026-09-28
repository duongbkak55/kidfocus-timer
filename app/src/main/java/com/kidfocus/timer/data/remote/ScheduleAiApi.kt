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
    suspend fun parseImage(text: String, image: String, ageBand: String, current: ScheduleState): ScheduleParseReply
}
interface ScheduleAdviser {
    suspend fun advise(payload: Map<String, Any>): ScheduleAdviseReply
}
data class ScheduleAdviseReply(val advice: ScheduleAdvice, val usage: AiUsage)
data class ScheduleParseReply(val draft: ScheduleDraft, val usage: AiUsage)

@Singleton
class ScheduleAiApi @Inject constructor(private val account: FirebaseAccountRepository) : ScheduleParser, ScheduleAdviser {
    private fun functions(): FirebaseFunctions = FirebaseFunctions.getInstance(
        account.firebaseApp() ?: error("FIREBASE_NOT_CONFIGURED"), "asia-southeast1")

    suspend fun claimEarlyAccess() {
        withTimeout(30_000) { functions().getHttpsCallable("claimEarlyAccess").call().await() }
    }

    override suspend fun advise(payload: Map<String, Any>): ScheduleAdviseReply = withTimeout(30_000) {
        val reply = functions().getHttpsCallable("aiSchedule").also { it.setTimeout(30, TimeUnit.SECONDS) }.call(payload).await().data as? Map<*, *> ?: error("AI_ADVISE_FAILED")
        ScheduleAdviseReply(ScheduleAdvicePayload.advice(reply["advice"] as? Map<*, *> ?: error("AI_ADVISE_FAILED")),
            (reply["usage"] as? Map<*, *>)?.let { usage -> AiUsage((usage["remainingQuestions"] as? Number)?.toInt() ?: 0, (usage["remainingCredits"] as? Number)?.toInt() ?: 0, usage["premium"] == true, usage["tier"] as? String ?: "free", (usage["earlyAccessUntil"] as? Number)?.toLong(), (usage["remainingScheduleParses"] as? Number)?.toInt()) } ?: error("AI_ADVISE_FAILED"))
    }

    override suspend fun parse(text: String, ageBand: String, current: ScheduleState) = parseRequest(text, null, ageBand, current)
    override suspend fun parseImage(text: String, image: String, ageBand: String, current: ScheduleState) = parseRequest(text, image, ageBand, current)

    private suspend fun parseRequest(text: String, image: String?, ageBand: String, current: ScheduleState): ScheduleParseReply = withTimeout(30_000) {
        require((text.isNotBlank() || image != null) && text.length <= 2000)
        require(image == null || image.length in 1..1_400_000)
        val response = functions().getHttpsCallable("aiSchedule").also { it.setTimeout(30, TimeUnit.SECONDS) }
            .call(mapOf("requestId" to UUID.randomUUID().toString(), "text" to text,
                "ageBand" to (ageBand.takeIf { it in ScheduleThresholds.sleepMinutes } ?: "4-5"),
                "today" to LocalDate.now().toString(), "locale" to Locale.getDefault().let { locale -> locale.language + (locale.country.takeIf { it.length == 2 }?.let { "-$it" } ?: "") },
                "current" to current.tasks.take(60).map { task -> mapOf(
                    "name" to task.name.take(60), "days" to DayCodec.fromCalendar(task.daysOfWeek).sortedBy { it.value }.map { it.name.take(3) },
                    "start" to "%02d:%02d".format(Locale.ROOT, task.hour, task.minute), "durationMin" to task.focusDurationMinutes,
                ) }, "currentSchool" to current.anchors.school.take(30).map { block -> mapOf(
                    "days" to block.days.sortedBy { it.value }.map { it.name.take(3) },
                    "start" to block.start.toString(), "end" to block.end.toString(),
                ) }) + (image?.let { mapOf("image" to it) } ?: emptyMap()))
            .await().data as? Map<*, *> ?: error("AI_PARSE_FAILED")
        val rawDraft = response["draft"] as? Map<*, *> ?: error("AI_PARSE_FAILED")
        val usage = response["usage"] as? Map<*, *> ?: error("AI_PARSE_FAILED")
        ScheduleParseReply(ScheduleDraftMapper.fromMap(rawDraft.entries.associate { it.key.toString() to it.value }),
            AiUsage((usage["remainingQuestions"] as? Number)?.toInt() ?: 0,
                (usage["remainingCredits"] as? Number)?.toInt() ?: 0, usage["premium"] == true,
                usage["tier"] as? String ?: "free", (usage["earlyAccessUntil"] as? Number)?.toLong(),
                (usage["remainingScheduleParses"] as? Number)?.toInt()))
    }
}
