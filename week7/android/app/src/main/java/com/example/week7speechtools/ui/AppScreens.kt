package com.example.week7speechtools.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.week7speechtools.model.AuthUser
import com.example.week7speechtools.model.SaveStatus
import com.example.week7speechtools.model.SpeechRecord
import com.example.week7speechtools.model.TranslationLanguage
import com.example.week7speechtools.viewmodel.AuthUiState
import com.example.week7speechtools.viewmodel.SpeechUiState

@Composable
fun LoginScreen(
    state: AuthUiState,
    permissionPending: Boolean,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = 24.dp,
                    vertical = 32.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Speech Studio",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            MainAction(
                text = when {
                    permissionPending -> "Waiting for permission..."
                    state.isBusy -> "Signing in..."
                    else -> "Sign in with Google"
                },
                enabled = !state.isBusy && !permissionPending,
                onClick = onSignIn
            )

            if (state.isBusy || permissionPending) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (state.isError && state.message.isNotBlank()) {
                StatusMessage(
                    message = state.message,
                    isError = true
                )
            }
        }
    }
}

@Composable
fun SpeechScreen(
    user: AuthUser,
    state: SpeechUiState,
    isSigningOut: Boolean,
    permissionPending: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onTtsTextChange: (String) -> Unit,
    onTargetLanguageChange: (TranslationLanguage) -> Unit,
    onTranslate: () -> Unit,
    onReadAloud: () -> Unit,
    onStopAudio: () -> Unit,
    onReplay: () -> Unit,
    onRetrySave: (String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canInteract =
        !state.isBusy &&
                !state.sessionExpired &&
                !isSigningOut &&
                !permissionPending

    val transcript = state.transcript.trim()

    StudioPage(modifier = modifier) {
        StudioHeader(
            subtitle = "Speak. Translate. Listen."
        )

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "YOUR WORKSPACE",
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = user.name
                                ?.takeIf { it.isNotBlank() }
                                ?: user.email
                                ?: "Signed-in user",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        user.email?.let { email ->
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onLogout,
                        enabled = !isSigningOut && !permissionPending,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            if (isSigningOut) {
                                "Signing out..."
                            } else {
                                "Logout"
                            }
                        )
                    }
                }

                if (isSigningOut) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        ToolCard(
            number = "01",
            title = "Speech to Text",
            description = "Press record and speak naturally. Stop to see your words.",
            accent = MaterialTheme.colorScheme.primary
        ) {
            Text(
                text = "RECORD UP TO 60 SECONDS",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            MainAction(
                text = "Start Recording",
                enabled = canInteract,
                onClick = onStartRecording
            )

            SecondaryAction(
                text = "Stop Recording",
                enabled = state.isRecording && !isSigningOut,
                onClick = onStopRecording
            )

            if (state.isTranscribing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            StatusMessage(
                message = state.message,
                isError = state.isError
            )

            ResultPanel(
                label = "Transcript",
                text = state.transcript,
                placeholder = "Your voice, written down."
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )

            RecordSaveControls(
                record = state.transcriptRecord,
                saveLabel = "Save Transcript",
                emptyMessage = "Your transcript will be ready to save here.",
                enabled = canInteract,
                onSave = onRetrySave
            )
        }

        ToolCard(
            number = "02",
            title = "Text to Speech",
            description = "Choose a language, translate your words, and bring them to life.",
            accent = MaterialTheme.colorScheme.secondary
        ) {
            OutlinedTextField(
                value = state.ttsText,
                onValueChange = onTtsTextChange,
                enabled = canInteract,
                label = {
                    Text("Original Text")
                },
                placeholder = {
                    Text("What would you like to say?")
                },
                supportingText = {
                    Text(
                        text = "${state.ttsText.length}/2000 characters",
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                minLines = 4,
                maxLines = 8,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            SecondaryAction(
                text = "Use Transcript",
                enabled = canInteract &&
                        transcript.isNotBlank() &&
                        transcript.length <= 2000,
                onClick = {
                    onTtsTextChange(transcript)
                }
            )

            if (transcript.length > 2000) {
                StatusMessage(
                    message = "The transcript exceeds 2000 characters. Enter shorter original text.",
                    isError = true
                )
            }

            LanguagePicker(
                selected = state.targetLanguage,
                enabled = canInteract,
                onSelected = onTargetLanguageChange
            )

            MainAction(
                text = if (state.isTranslating) {
                    "Translating..."
                } else {
                    "Translate"
                },
                enabled = canInteract &&
                        state.ttsText.isNotBlank() &&
                        state.ttsText.length <= 2000,
                onClick = onTranslate
            )

            if (state.isTranslating) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            StatusMessage(
                message = state.translationMessage,
                isError = state.translationIsError
            )

            ResultPanel(
                label = "Translated Text",
                text = state.translatedText,
                placeholder = "A different language. The same meaning."
            )

            MainAction(
                text = if (state.isGenerating) {
                    "Generating speech..."
                } else {
                    "Generate Speech"
                },
                enabled = canInteract &&
                        state.translationRecord != null &&
                        state.translatedText.isNotBlank() &&
                        state.translatedText.length <= 2000,
                useSecondaryColor = true,
                onClick = onReadAloud
            )

            if (state.isGenerating || state.isPreparing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            StatusMessage(
                message = state.ttsMessage,
                isError = state.ttsIsError
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SecondaryAction(
                    text = "Stop Audio",
                    enabled = !isSigningOut &&
                            (state.isPreparing || state.isPlaying),
                    onClick = onStopAudio,
                    modifier = Modifier.weight(1f)
                )

                SecondaryAction(
                    text = "Replay",
                    enabled = canInteract && state.hasAudio,
                    onClick = onReplay,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "The voice you hear is AI-generated.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )

            RecordSaveControls(
                record = state.translationRecord,
                saveLabel = "Save Translation",
                emptyMessage = "Translate your text to enable saving.",
                enabled = canInteract,
                onSave = onRetrySave
            )

            Text(
                text = "The original text, translation, and target language are saved together in one record.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "Your words. Saved only when you choose.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StudioPage(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content
        )
    }
}

@Composable
private fun StudioHeader(
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BrandMark()

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "Speech Studio",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BrandMark() {
    val foreground = MaterialTheme.colorScheme.onPrimary
    val background = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .size(50.dp)
            .background(
                color = background,
                shape = RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(26.dp)
        ) {
            val heights = listOf(
                0.35f,
                0.65f,
                1f,
                0.65f,
                0.35f
            )

            val barWidth = size.width / 9f

            heights.forEachIndexed { index, fraction ->
                val barHeight = size.height * fraction

                drawRoundRect(
                    color = foreground,
                    topLeft = Offset(
                        x = index * barWidth * 2f,
                        y = (size.height - barHeight) / 2f
                    ),
                    size = Size(
                        width = barWidth,
                        height = barHeight
                    ),
                    cornerRadius = CornerRadius(
                        barWidth / 2f,
                        barWidth / 2f
                    )
                )
            }
        }
    }
}

@Composable
private fun FeaturePoint(
    number: String,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = number,
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ToolCard(
    number: String,
    title: String,
    description: String,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            color = accent.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = number,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            content()
        }
    }
}

@Composable
private fun MainAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useSecondaryColor: Boolean = false
) {
    val colors = MaterialTheme.colorScheme

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(
            horizontal = 18.dp,
            vertical = 14.dp
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (useSecondaryColor) {
                colors.secondary
            } else {
                colors.primary
            },
            contentColor = if (useSecondaryColor) {
                colors.onSecondary
            } else {
                colors.onPrimary
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SecondaryAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(
            horizontal = 14.dp,
            vertical = 13.dp
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ResultPanel(
    label: String,
    text: String,
    placeholder: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = 0.4f
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .heightIn(min = 128.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            SelectionContainer {
                Text(
                    text = text.ifBlank { placeholder },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (text.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

@Composable
private fun LanguagePicker(
    selected: TranslationLanguage,
    enabled: Boolean,
    onSelected: (TranslationLanguage) -> Unit
) {
    var expanded by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(enabled) {
        if (!enabled) {
            expanded = false
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Target Language",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = {
                    expanded = true
                },
                enabled = enabled,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = selected.displayName,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = selected.code.uppercase(),
                        style = MaterialTheme.typography.labelMedium
                    )

                    ChevronMark()
                }
            }

            DropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = {
                    expanded = false
                }
            ) {
                TranslationLanguage.entries.forEach { language ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "${language.displayName} (${language.code})"
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelected(language)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChevronMark() {
    val color = LocalContentColor.current

    Canvas(
        modifier = Modifier.size(16.dp)
    ) {
        val middle = Offset(
            size.width * 0.5f,
            size.height * 0.65f
        )

        drawLine(
            color = color,
            start = Offset(
                size.width * 0.2f,
                size.height * 0.35f
            ),
            end = middle,
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )

        drawLine(
            color = color,
            start = middle,
            end = Offset(
                size.width * 0.8f,
                size.height * 0.35f
            ),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun StatusMessage(
    message: String,
    isError: Boolean
) {
    if (message.isBlank()) {
        return
    }

    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
            },
        shape = RoundedCornerShape(12.dp),
        color = if (isError) {
            colors.errorContainer
        } else {
            colors.surfaceVariant.copy(alpha = 0.45f)
        }
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = 12.dp
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) {
                colors.onErrorContainer
            } else {
                colors.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun RecordSaveControls(
    record: SpeechRecord?,
    saveLabel: String,
    emptyMessage: String,
    enabled: Boolean,
    onSave: (String) -> Unit
) {
    val canSaveRecord = record != null &&
            (
                    record.status == SaveStatus.PENDING ||
                            record.status == SaveStatus.FAILED
                    )

    val buttonLabel = when (record?.status) {
        SaveStatus.SAVING -> "Saving..."
        SaveStatus.SAVED -> "Saved"
        SaveStatus.FAILED -> "Retry Save"
        else -> saveLabel
    }

    SecondaryAction(
        text = buttonLabel,
        enabled = enabled && canSaveRecord,
        onClick = {
            record?.let {
                onSave(it.recordId)
            }
        }
    )

    when (record?.status) {
        null -> {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SaveStatus.PENDING -> {
            Text(
                text = "Not saved. Tap $saveLabel when you are ready.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SaveStatus.SAVING -> {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }

        SaveStatus.SAVED -> {
            StatusMessage(
                message = "Saved to Google Sheets.",
                isError = false
            )
        }

        SaveStatus.FAILED -> {
            StatusMessage(
                message = record.error
                    ?: "Unable to save to Google Sheets.",
                isError = true
            )
        }
    }
}