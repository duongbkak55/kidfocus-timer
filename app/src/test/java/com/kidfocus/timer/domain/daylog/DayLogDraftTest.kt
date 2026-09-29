package com.kidfocus.timer.domain.daylog

import com.kidfocus.timer.domain.model.*
import com.kidfocus.timer.domain.schedule.*
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

class DayLogDraftTest {
    private val date=LocalDate.parse("2026-09-29")
    private val tasks=listOf(ScheduledTask(42,TaskType.HOMEWORK,"Homework","x",19,0,(1..7).toSet(),30,5))
    private fun request(text:String="Hôm qua làm bài lúc 19h")=DayLogDraft.request(text,date,tasks,ScheduleAnchors(),"l1","request-1")
    private fun row(ref:String="p0",confidence:Double=.9)=mapOf("date" to date.minusDays(1).toString(),"planRef" to ref,"name" to "Homework","category" to "STUDY","start" to "19:00","end" to "19:30","confidence" to confidence)
    @Test fun requestUsesDatedTransientRefsAndPreviewKeepsLowConfidenceUnchecked() {
        val r=request();assertEquals(2,r.plans.size);assertFalse(r.payload.toString().contains("taskId"));assertFalse(r.payload.toString().contains("profileId"))
        val parsed=DayLogDraft.fromMap(mapOf("entries" to listOf(row(confidence=.4)),"questions" to listOf("Giờ có đúng không?")),r)
        assertFalse(parsed.entries.single().selectedByDefault);assertEquals(date.minusDays(1),parsed.entries.single().date)
        assertEquals(42L,r.plans.getValue(parsed.entries.single().planRef!!).taskId)
    }
    @Test fun malformedRepliesCannotWriteArbitraryRefsTimesOrOtherDateOccurrences() {
        for (change in listOf(mapOf("planRef" to "p999"),mapOf("date" to "2026-09-29"),mapOf("start" to "24:00"),mapOf("confidence" to Double.NaN),mapOf("category" to "OTHER"))) {
            assertTrue(runCatching { DayLogDraft.fromMap(mapOf("entries" to listOf(row()+change),"questions" to emptyList<String>()),request()) }.isFailure)
        }
    }
}
