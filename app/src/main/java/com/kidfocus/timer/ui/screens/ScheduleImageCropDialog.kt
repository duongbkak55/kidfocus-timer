package com.kidfocus.timer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.kidfocus.timer.R
import com.kidfocus.timer.data.schedule.ScheduleCrop
import com.kidfocus.timer.data.schedule.ScheduleImage

@Composable
fun ScheduleImageCropDialog(image: ScheduleImage, onDismiss: () -> Unit, onCrop: (ScheduleCrop) -> Unit) {
    var crop by remember(image.file) { mutableStateOf(ScheduleCrop()) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.quick_image_crop), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.quick_image_crop_help))
                Box(Modifier.fillMaxWidth().aspectRatio(image.width.toFloat() / image.height)) {
                    AsyncImage(image.file, stringResource(R.string.quick_image_preview), Modifier.fillMaxSize())
                    Canvas(Modifier.fillMaxSize().pointerInput(image.file) {
                        var corner: Int? = null
                        detectDragGestures(onDragStart = { point ->
                            val corners = listOf(Offset(crop.left * size.width, crop.top * size.height),
                                Offset(crop.right * size.width, crop.top * size.height), Offset(crop.left * size.width, crop.bottom * size.height),
                                Offset(crop.right * size.width, crop.bottom * size.height))
                            corner = corners.indices.minByOrNull { (corners[it] - point).getDistance() }
                                ?.takeIf { (corners[it] - point).getDistance() <= 48.dp.toPx() }
                        }, onDragEnd = { corner = null }, onDragCancel = { corner = null }) { change, drag ->
                            corner?.let { index ->
                                change.consume()
                                val dx = drag.x / size.width; val dy = drag.y / size.height
                                crop = crop.copy(
                                    left = if (index % 2 == 0) (crop.left + dx).coerceIn(0f, crop.right - 0.05f) else crop.left,
                                    right = if (index % 2 == 1) (crop.right + dx).coerceIn(crop.left + 0.05f, 1f) else crop.right,
                                    top = if (index < 2) (crop.top + dy).coerceIn(0f, crop.bottom - 0.05f) else crop.top,
                                    bottom = if (index >= 2) (crop.bottom + dy).coerceIn(crop.top + 0.05f, 1f) else crop.bottom,
                                )
                            }
                        }
                    }) {
                        val left = crop.left * size.width; val top = crop.top * size.height
                        val right = crop.right * size.width; val bottom = crop.bottom * size.height
                        val shade = Color.Black.copy(alpha = 0.5f)
                        drawRect(shade, size = Size(size.width, top))
                        drawRect(shade, Offset(0f, bottom), Size(size.width, size.height - bottom))
                        drawRect(shade, Offset(0f, top), Size(left, bottom - top))
                        drawRect(shade, Offset(right, top), Size(size.width - right, bottom - top))
                        drawRect(Color.White, Offset(left, top), Size(right - left, bottom - top), style = Stroke(2.dp.toPx()))
                        listOf(Offset(left, top), Offset(right, top), Offset(left, bottom), Offset(right, bottom)).forEach { drawCircle(Color.White, 8.dp.toPx(), it) }
                    }
                }
                // Sliders also allow precise cropping and keyboard/TalkBack adjustment.
                val topLabel = stringResource(R.string.quick_crop_top)
                Text(topLabel)
                Slider(modifier = Modifier.semantics { contentDescription = topLabel }, value = crop.top, onValueChange = { crop = crop.copy(top = it) }, valueRange = 0f..(crop.bottom - 0.05f))
                val bottomLabel = stringResource(R.string.quick_crop_bottom)
                Text(bottomLabel)
                Slider(modifier = Modifier.semantics { contentDescription = bottomLabel }, value = crop.bottom, onValueChange = { crop = crop.copy(bottom = it) }, valueRange = (crop.top + 0.05f)..1f)
                val leftLabel = stringResource(R.string.quick_crop_left)
                Text(leftLabel)
                Slider(modifier = Modifier.semantics { contentDescription = leftLabel }, value = crop.left, onValueChange = { crop = crop.copy(left = it) }, valueRange = 0f..(crop.right - 0.05f))
                val rightLabel = stringResource(R.string.quick_crop_right)
                Text(rightLabel)
                Slider(modifier = Modifier.semantics { contentDescription = rightLabel }, value = crop.right, onValueChange = { crop = crop.copy(right = it) }, valueRange = (crop.left + 0.05f)..1f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.quick_image_cancel)) }
                    Button(onClick = { onCrop(crop) }) { Text(stringResource(R.string.quick_image_crop)) }
                }
            }
        }
    }
}
