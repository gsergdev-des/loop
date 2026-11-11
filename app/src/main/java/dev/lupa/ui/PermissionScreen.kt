package dev.lupa.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lupa.R

/**
 * Экран без доступа к камере: объяснение и путь к разрешению.
 * «Разрешить» — только пока система ещё может показать свой запрос ([canAskAgain]).
 */
@Composable
fun PermissionScreen(canAskAgain: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag(TestTags.PERMISSION_SCREEN),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.permission_rationale),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            if (canAskAgain) {
                Button(onClick = onRequest, modifier = actionModifier(TestTags.BTN_ASK_AGAIN)) {
                    ActionLabel(stringResource(R.string.permission_ask_again))
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = actionModifier(TestTags.BTN_OPEN_SETTINGS),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                ) {
                    ActionLabel(stringResource(R.string.permission_open_settings))
                }
            } else {
                Button(onClick = onOpenSettings, modifier = actionModifier(TestTags.BTN_OPEN_SETTINGS)) {
                    ActionLabel(stringResource(R.string.permission_open_settings))
                }
            }
        }
    }
}

private fun actionModifier(tag: String) = Modifier
    .fillMaxWidth()
    .heightIn(min = 64.dp)
    .testTag(tag)

@Composable
private fun ActionLabel(text: String) {
    Text(text = text, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
}
