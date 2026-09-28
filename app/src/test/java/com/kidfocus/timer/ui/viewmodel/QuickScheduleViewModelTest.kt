package com.kidfocus.timer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctionsException
import com.kidfocus.timer.data.database.ChildProfileEntity
import com.kidfocus.timer.data.remote.*
import com.kidfocus.timer.data.repository.*
import com.kidfocus.timer.data.schedule.*
import com.kidfocus.timer.domain.schedule.*
import io.mockk.*
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class QuickScheduleViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val profiles = mockk<ChildProfileRepository>()
    private val tasks = mockk<ScheduledTaskRepository>()
    private val anchors = mockk<ScheduleAnchorsRepository>()
    private val parser = mockk<ScheduleParser>()
    private val useCase = mockk<ApplyScheduleUseCase>()
    private val images = mockk<ScheduleImageProcessor>(relaxed = true)
    private val store = mockk<RoomScheduleStore>()
    private val active = MutableStateFlow(ChildProfileEntity.default())
    private lateinit var vm: QuickScheduleViewModel
    private val before = ScheduleState(emptyList(), ScheduleAnchors())
    private val draft = ScheduleDraft(listOf(DraftItem("one", DraftKind.TASK, setOf(DayOfWeek.TUESDAY), LocalTime.of(18, 0),
        name = "English", taskId = 100, confidence = 0.5)), listOf("Missing time?"))
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { profiles.activeProfile } returns active
        every { tasks.allTasks } returns flowOf(emptyList())
        every { anchors.observe(any()) } returns flowOf(ScheduleAnchors())
        coEvery { store.read(any()) } returns before
        vm = QuickScheduleViewModel(profiles, tasks, anchors, parser, useCase, store, images)
    }
    @After fun teardown() { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    @Test fun `quota failure keeps input and preview unchanged and is retryable`() = runTest(dispatcher) {
        runCurrent()
        vm.editText("T3 học Anh 18h trong 60 phút")
        coEvery { parser.parse(any(), any(), any()) } returns ScheduleParseReply(draft, AiUsage(14, 14, false, "early", 10_000))
        vm.parse(); runCurrent()
        assertFalse(vm.state.value.draft!!.items.single().selected)
        val preview = vm.state.value.draft
        val quota = mockk<FirebaseFunctionsException>()
        every { quota.code } returns FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED
        coEvery { parser.parse(any(), any(), any()) } throws quota
        vm.parse(); runCurrent()
        assertEquals("T3 học Anh 18h trong 60 phút", vm.state.value.text)
        assertEquals(preview, vm.state.value.draft)
        assertEquals(QuickScheduleError.QUOTA, vm.state.value.error)
        assertFalse(vm.state.value.busy)
        coEvery { parser.parse(any(), any(), any()) } returns ScheduleParseReply(draft, AiUsage(13, 13, false))
        vm.parse(); runCurrent()
        assertNull(vm.state.value.error)
        assertEquals(13, vm.state.value.usage!!.remainingCredits)
    }
    @Test fun `network and parse failures preserve parent's text`() = runTest(dispatcher) {
        runCurrent(); vm.editText("Keep this text")
        coEvery { parser.parse(any(), any(), any()) } throws IllegalStateException("AI_PARSE_FAILED")
        vm.parse(); runCurrent()
        assertEquals(QuickScheduleError.PARSE, vm.state.value.error)
        assertEquals("Keep this text", vm.state.value.text)
        coEvery { parser.parse(any(), any(), any()) } throws java.io.IOException("offline")
        vm.parse(); runCurrent()
        assertEquals(QuickScheduleError.NETWORK, vm.state.value.error)
        assertEquals("Keep this text", vm.state.value.text)
    }
    @Test fun `apply uses captured schedule preserves draft on stale failure and passes selected edits`() = runTest(dispatcher) {
        runCurrent(); vm.editText("English")
        coEvery { parser.parse(any(), any(), any()) } returns ScheduleParseReply(draft, AiUsage(9, 9, false))
        vm.parse(); runCurrent()
        vm.editItem(draft.items.single().copy(selected = true, durationMin = 60))
        coEvery { useCase.apply(any(), any(), any(), any()) } throws IllegalStateException("stale")
        coEvery { store.read("default") } returns before.copy(anchors = ScheduleAnchors(commuteMinutes = 5))
        vm.apply(); runCurrent()
        assertNotNull(vm.state.value.draft)
        assertEquals(QuickScheduleError.STALE, vm.state.value.error)
        coVerify { useCase.apply("default", before, match { (it.single() as ScheduleChange.AddTask).task.focusDurationMinutes == 60 }, "4-5") }
    }
    @Test fun `switching profile discards pending draft and in-flight response`() = runTest(dispatcher) {
        runCurrent(); vm.editText("old profile")
        val deferred = CompletableDeferred<ScheduleParseReply>()
        coEvery { parser.parse(any(), any(), any()) } coAnswers { deferred.await() }
        vm.parse(); runCurrent()
        active.value = active.value.copy(id = "new-profile")
        runCurrent()
        assertEquals("", vm.state.value.text)
        deferred.complete(ScheduleParseReply(draft, AiUsage(9, 9, false)))
        runCurrent()
        assertNull(vm.state.value.draft)
        assertFalse(vm.state.value.busy)
    }
    @Test fun `returning to same profile still discards an old in-flight response`() = runTest(dispatcher) {
        runCurrent(); vm.editText("old request")
        val deferred = CompletableDeferred<ScheduleParseReply>()
        coEvery { parser.parse(any(), any(), any()) } coAnswers { deferred.await() }
        vm.parse(); runCurrent()
        active.value = active.value.copy(id = "second")
        runCurrent()
        active.value = ChildProfileEntity.default()
        runCurrent()
        vm.editText("new text")
        deferred.complete(ScheduleParseReply(draft, AiUsage(9, 9, false)))
        runCurrent()
        assertNull(vm.state.value.draft)
        assertEquals("new text", vm.state.value.text)
    }

    private fun imageFile() = java.io.File.createTempFile("vm-photo-", ".jpg").apply { writeText("JPEG fixture") }
    private fun mockImage(file: java.io.File): ScheduleImage {
        val image = ScheduleImage(file, 80, 40)
        coEvery { images.prepare(any()) } returns image
        coEvery { images.base64(image) } returns "JPEG_BASE64"
        every { images.delete(any()) } answers { firstArg<ScheduleImage?>()?.file?.delete(); Unit }
        every { images.deleteCapture(any()) } answers { firstArg<ScheduleCapture?>()?.file?.delete(); Unit }
        return image
    }
    @Test fun `photo cannot send before checkbox and uses same draft with optional text then deletes cache`() = runTest(dispatcher) {
        runCurrent()
        val file = imageFile(); val image = mockImage(file)
        vm.beginImageSelection(); vm.pickResult(android.net.Uri.fromFile(file)); runCurrent()
        assertEquals(image, vm.state.value.image); assertFalse(vm.state.value.imageConfirmed)
        vm.parse(); runCurrent(); coVerify(exactly = 0) { parser.parseImage(any(), any(), any(), any()) }
        vm.confirmImage(true)
        coEvery { parser.parseImage("", "JPEG_BASE64", "4-5", before) } returns ScheduleParseReply(draft, AiUsage(12, 12, false, "early"))
        vm.parse(); runCurrent()
        coVerify { parser.parseImage("", "JPEG_BASE64", "4-5", before) }
        assertEquals(draft, vm.state.value.draft); assertNull(vm.state.value.image)
        assertFalse(file.exists()); assertFalse(vm.state.value.imageConfirmed)
        val nextFile = imageFile(); mockImage(nextFile)
        vm.beginImageSelection(); vm.pickResult(android.net.Uri.fromFile(nextFile)); runCurrent()
        vm.editText("giờ vào 7h15, ra 11h15"); vm.confirmImage(true)
        coEvery { parser.parseImage(any(), any(), any(), any()) } returns ScheduleParseReply(draft, AiUsage(9, 9, false, "early"))
        vm.parse(); runCurrent()
        coVerify { parser.parseImage("giờ vào 7h15, ra 11h15", "JPEG_BASE64", "4-5", before) }
        assertFalse(nextFile.exists())
    }
    @Test fun `photo tier and network errors keep text and preview but delete submitted image`() = runTest(dispatcher) {
        runCurrent(); vm.editText("keep text")
        coEvery { parser.parse(any(), any(), any()) } returns ScheduleParseReply(draft, AiUsage(9, 9, false))
        vm.parse(); runCurrent()
        for ((failure, expected) in listOf(IllegalStateException("IMAGE_TIER_REQUIRED") to QuickScheduleError.IMAGE_TIER,
            java.io.IOException("offline") to QuickScheduleError.NETWORK)) {
            val file = imageFile(); mockImage(file)
            vm.beginImageSelection(); vm.pickResult(android.net.Uri.fromFile(file)); runCurrent(); vm.confirmImage(true)
            coEvery { parser.parseImage(any(), any(), any(), any()) } throws failure
            vm.parse(); runCurrent()
            assertEquals(expected, vm.state.value.error); assertEquals("keep text", vm.state.value.text)
            assertEquals(draft, vm.state.value.draft); assertNull(vm.state.value.image); assertFalse(file.exists())
        }
    }
    @Test fun `cancel camera launch or capture and malformed capture always deletes source`() = runTest(dispatcher) {
        runCurrent()
        for (mode in 0..2) {
            val file = imageFile(); mockImage(file)
            every { images.createCapture() } returns ScheduleCapture(file, android.net.Uri.fromFile(file))
            vm.createCapture()
            when (mode) {
                0 -> vm.captureResult(false)
                1 -> vm.imageSelectionFailed()
                else -> { coEvery { images.prepare(any()) } throws java.io.IOException("invalid photo"); vm.captureResult(true) }
            }
            runCurrent(); assertFalse(file.exists()); assertNull(vm.state.value.image)
        }
    }
    @Test fun `remove cancel and profile change discard photo plus checkbox`() = runTest(dispatcher) {
        runCurrent()
        val file = imageFile(); mockImage(file)
        vm.beginImageSelection(); vm.pickResult(android.net.Uri.fromFile(file)); runCurrent(); vm.confirmImage(true)
        vm.discardImages(); assertFalse(file.exists()); assertNull(vm.state.value.image)
        val next = imageFile(); mockImage(next)
        vm.beginImageSelection(); vm.pickResult(android.net.Uri.fromFile(next)); runCurrent(); vm.confirmImage(true)
        active.value = active.value.copy(id = "other"); runCurrent()
        assertFalse(next.exists()); assertNull(vm.state.value.image); assertFalse(vm.state.value.imageConfirmed)
        vm.beginImageSelection(); vm.pickResult(null); assertNull(vm.state.value.image)
    }
    @Test fun `crop clears consent deletes original and rejects late prepared image after profile change`() = runTest(dispatcher) {
        runCurrent()
        val original = imageFile(); val image = mockImage(original)
        val croppedFile = imageFile(); val cropped = ScheduleImage(croppedFile, 40, 40)
        coEvery { images.crop(image, any()) } returns cropped
        vm.beginImageSelection(); vm.pickResult(android.net.Uri.fromFile(original)); runCurrent(); vm.confirmImage(true)
        vm.cropImage(ScheduleCrop(0.5f, 0f, 1f, 1f)); runCurrent()
        assertFalse(original.exists()); assertEquals(cropped, vm.state.value.image); assertFalse(vm.state.value.imageConfirmed)
        vm.discardImages(); assertFalse(croppedFile.exists())
        val lateFile = imageFile(); val late = ScheduleImage(lateFile, 80, 40)
        val pending = CompletableDeferred<ScheduleImage>()
        coEvery { images.prepare(any()) } coAnswers { pending.await() }
        vm.beginImageSelection(); vm.pickResult(android.net.Uri.fromFile(lateFile)); runCurrent()
        active.value = active.value.copy(id = "second"); runCurrent()
        pending.complete(late); runCurrent()
        assertNull(vm.state.value.image); assertFalse(lateFile.exists()); assertFalse(vm.state.value.busy)
    }
    @Test fun `crop failure clears owned cache and ViewModel clearing cleans capture and preview`() = runTest(dispatcher) {
        runCurrent()
        val file = imageFile(); val image = mockImage(file)
        vm.beginImageSelection(); vm.pickResult(android.net.Uri.fromFile(file)); runCurrent()
        coEvery { images.crop(image, any()) } throws java.io.IOException("invalid crop")
        vm.cropImage(ScheduleCrop()); runCurrent()
        assertFalse(file.exists()); assertNull(vm.state.value.image); assertFalse(vm.state.value.busy)
        val pending = imageFile(); mockImage(pending)
        every { images.createCapture() } returns ScheduleCapture(pending, android.net.Uri.fromFile(pending))
        vm.createCapture()
        androidx.lifecycle.ViewModelStore().apply { put("quick", vm); clear() }
        assertFalse(pending.exists()); verify { images.close() }
    }

}
