package dev.lupa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag

/** Заготовка экрана лупы: чёрное поле на месте превью. Камеру подключает таск 02 (`MagnifierScreen`). */
@Composable
fun MagnifierPlaceholder() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag(TestTags.PREVIEW),
    )
}
