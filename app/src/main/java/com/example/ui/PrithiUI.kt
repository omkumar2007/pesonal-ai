package com.example.ui

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AutomationLog
import com.example.data.model.ChatMessage
import com.example.data.model.MemoryItem
import com.example.data.model.Reminder
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrithiMainScreen(viewModel: PrithiViewModel) {
    val context = LocalContext.current
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val prithiEmotion by viewModel.prithiEmotion.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val isTtsEnabled by viewModel.isTtsEnabled.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    // Microphone Permission Request
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVoiceListening()
        } else {
            Toast.makeText(context, "Microphone permission is required for voice chat!", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceBackground),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, PrithiPrimaryLavender, CircleShape)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_prithi_avatar),
                                contentDescription = "Prithi Portrait",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column {
                            Text(
                                text = "Prithi",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when {
                                    isListening -> "Listening to you..."
                                    isAnalyzing -> "Prithi is thinking..."
                                    prithiEmotion == "SPEAKING" -> "Speaking to you..."
                                    else -> "Online Companion"
                                },
                                color = if (isListening) PrithiPinkAccent else PrithiSecondaryFuchsia,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                actions = {
                    // Voice playback enable/disable toggle
                    IconButton(
                        onClick = { viewModel.toggleTts() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("tts_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isTtsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "Toggle text to speech feedback",
                            tint = if (isTtsEnabled) PrithiPrimaryLavender else Color.Gray
                        )
                    }

                    // Clear logs / Reset options
                    var showResetMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { showResetMenu = true },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings and resets",
                                tint = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = showResetMenu,
                            onDismissRequest = { showResetMenu = false },
                            modifier = Modifier.background(SpaceCardSurface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Clear Chat History", color = Color.White) },
                                onClick = {
                                    viewModel.clearHistory()
                                    showResetMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Wipe Stored Memories", color = Color.White) },
                                onClick = {
                                    viewModel.clearAllMemories()
                                    showResetMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear Automation Logs", color = Color.White) },
                                onClick = {
                                    viewModel.clearAutomationLogs()
                                    showResetMenu = false
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SpaceBackground.copy(alpha = 0.95f),
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SpaceCardSurface,
                tonalElevation = 8.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chat dashboard") },
                    label = { Text("Chat") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SpaceBackground,
                        selectedTextColor = PrithiPrimaryLavender,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = PrithiPrimaryLavender
                    ),
                    modifier = Modifier.testTag("nav_chat_tab")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = "Smart Reminders") },
                    label = { Text("Reminders") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SpaceBackground,
                        selectedTextColor = PrithiPrimaryLavender,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = PrithiPrimaryLavender
                    ),
                    modifier = Modifier.testTag("nav_reminders_tab")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = "Memories Database") },
                    label = { Text("Memory") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SpaceBackground,
                        selectedTextColor = PrithiPrimaryLavender,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = PrithiPrimaryLavender
                    ),
                    modifier = Modifier.testTag("nav_memories_tab")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    icon = { Icon(Icons.Default.List, contentDescription = "Smartphone Automations Logs") },
                    label = { Text("Automations") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = SpaceBackground,
                        selectedTextColor = PrithiPrimaryLavender,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = PrithiPrimaryLavender
                    ),
                    modifier = Modifier.testTag("nav_automations_tab")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(SpaceBackground, SpaceCardSurface.copy(alpha = 0.5f)),
                        tileMode = TileMode.Clamp
                    )
                )
        ) {
            // Displays any speech system errors prominently but cleanly
            errorMessage?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4A1521)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                        .align(Alignment.TopCenter)
                        .shadow(4.dp, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "Alert error", tint = Color.Red)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = err, color = Color.White, fontSize = 13.sp)
                    }
                }
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    slideInHorizontally { width -> if (targetState > initialState) width else -width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> if (targetState > initialState) -width else width } + fadeOut()
                },
                label = "Navigation Tab Animation"
            ) { targetIndex ->
                when (targetIndex) {
                    0 -> CompanionChatTab(
                        viewModel = viewModel,
                        isListening = isListening,
                        onMicClicked = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                    1 -> RemindersTab(viewModel = viewModel)
                    2 -> MemoriesTab(viewModel = viewModel)
                    3 -> AutomationsTab(viewModel = viewModel)
                }
            }
        }
    }
}

// --- TAB 0: COMPANION CHAT ---

@Composable
fun CompanionChatTab(
    viewModel: PrithiViewModel,
    isListening: Boolean,
    onMicClicked: () -> Unit
) {
    val messages by viewModel.chatHistory.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val currentInputText by viewModel.currentInputText.collectAsStateWithLifecycle()
    val prithiEmotion by viewModel.prithiEmotion.collectAsStateWithLifecycle()

    val chatListState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Keep scrolling to the last message automatically
    LaunchedEffect(messages.size, isAnalyzing) {
        if (messages.isNotEmpty()) {
            chatListState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {
        // Visual holographic companion engine (Glow orb showing emotional pulse of Prithi)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            contentAlignment = Alignment.Center
        ) {
            PrithiGlowOrb(emotion = prithiEmotion, isThinking = isAnalyzing)
        }

        // Messages List Container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (messages.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "No messaging icon",
                        tint = PrithiSecondaryFuchsia.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Say hello to Prithi!",
                        color = Color.LightGray,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "She will introduce herself and start building a real friendship with you.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            } else {
                LazyColumn(
                    state = chatListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(messages) { message ->
                        ChatBubble(message = message)
                    }
                    if (isAnalyzing) {
                        item {
                            PrithiThinkingIndicator()
                        }
                    }
                }
            }
        }

        // Input Tray Area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Voice Input Trigger
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (isListening) PrithiPinkAccent else PrithiPrimaryLavender
                    )
                    .clickable {
                        if (isListening) {
                            viewModel.stopVoiceListening()
                        } else {
                            onMicClicked()
                        }
                    }
                    .testTag("record_voice_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Close else Icons.Default.Mic,
                    contentDescription = "Toggle Speech Recognition",
                    tint = SpaceBackground,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Keyboard/Text entry
            OutlinedTextField(
                value = currentInputText,
                onValueChange = { viewModel.updateInputText(it) },
                placeholder = { Text("Talk to Prithi...", color = Color.Gray, fontSize = 14.sp) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                shape = RoundedCornerShape(26.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrithiPrimaryLavender,
                    unfocusedBorderColor = SpaceCardOverlay,
                    focusedContainerColor = SpaceCardSurface,
                    unfocusedContainerColor = SpaceCardSurface
                ),
                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 15.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        viewModel.submitMessage()
                        keyboardController?.hide()
                    }
                ),
                trailingIcon = {
                    if (currentInputText.isNotBlank()) {
                        IconButton(
                            onClick = {
                                viewModel.submitMessage()
                                keyboardController?.hide()
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("send_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send text description",
                                tint = PrithiPrimaryLavender
                            )
                        }
                    }
                }
            )
        }
    }
}

// Glow Orb Canvas illustrating active companion system state
@Composable
fun PrithiGlowOrb(emotion: String, isThinking: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_trans")
    val alphaScale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val scaleFactor by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val color = when (emotion) {
        "LISTENING" -> Color(0xFF00FFCC) // cyan of active listening
        "THINKING" -> Color(0xFFFFCC00) // yellow thinking light
        "SPEAKING" -> PrithiPinkAccent // pink active voice output
        "WAVE" -> PrithiPrimaryLavender // lavender wave hello
        "SLEEPING" -> Color(0x99A080FF) // dim sleeping indigo
        else -> PrithiPrimaryLavender
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        // Pulse circles behind
        Canvas(modifier = Modifier.size(90.dp)) {
            val radius = size.minDimension / 1.8f
            drawCircle(
                color = color.copy(alpha = alphaScale * 0.15f),
                radius = radius * scaleFactor
            )
            drawCircle(
                color = color.copy(alpha = alphaScale * 0.35f),
                radius = radius * 0.75f,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = color,
                radius = radius * 0.4f
            )
        }
        // Small informative state caption printed in subtle details
        Text(
            text = when (emotion) {
                "LISTENING" -> "I'm listening..."
                "THINKING" -> "Prithi is thinking..."
                "SPEAKING" -> "I'm speaking!"
                "WAVE" -> "Hello!"
                else -> ""
            },
            color = color.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(y = 52.dp)
        )
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.sender == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bgColors = if (isUser) {
        Brush.horizontalGradient(listOf(SpaceCardOverlay, SpaceCardSurface))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF281F4B), SpaceCardOverlay))
    }
    val borderColor = if (isUser) PrithiSecondaryFuchsia.copy(alpha = 0.5f) else PrithiPrimaryLavender.copy(alpha = 0.4f)
    val alignCorner = if (isUser) RoundedCornerShape(18.dp, 18.dp, 2.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 2.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = alignment
    ) {
        Row(
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.heightIn(max = 500.dp)
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .border(1.dp, PrithiPrimaryLavender, CircleShape)
                        .align(Alignment.Top)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_prithi_avatar),
                        contentDescription = "Mini Portrait",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            Column(
                modifier = Modifier
                    .shadow(3.dp, alignCorner)
                    .background(bgColors, alignCorner)
                    .border(1.dp, borderColor, alignCorner)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .widthIn(max = 280.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "You" else "Prithi",
                        color = if (isUser) PrithiSecondaryFuchsia else PrithiPrimaryLavender,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp)),
                        color = Color.LightGray.copy(alpha = 0.6f),
                        fontSize = 9.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.message,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
fun PrithiThinkingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val count by infiniteTransition.animateValue(
        initialValue = 1,
        targetValue = 4,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "think_dots"
    )

    val dots = ".".repeat(count)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(SpaceCardOverlay)
                .border(0.5.dp, PrithiPrimaryLavender, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_prithi_avatar),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = SpaceCardSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Prithi is typing$dots",
                fontSize = 12.sp,
                color = PrithiSecondaryFuchsia,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

// --- TAB 1: SMART REMINDERS ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersTab(viewModel: PrithiViewModel) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Prithi's Reminders",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "She will remind you in chat, or you can manage them here.",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }
            // Manually add reminder button
            Button(
                onClick = { showDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = PrithiPrimaryLavender),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("add_reminder_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add reminder", tint = SpaceBackground)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add", color = SpaceBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (reminders.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No reminders scheduled", color = Color.Gray, fontSize = 14.sp)
                    Text(
                        text = "Tip: Tell Prithi \"Remind me to call Mom in 5 minutes\" inside Chat!",
                        color = PrithiSecondaryFuchsia.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(reminders) { reminder ->
                    ReminderItemView(
                        reminder = reminder,
                        onToggle = { viewModel.toggleReminderCompleted(reminder) },
                        onDelete = { viewModel.deleteReminder(reminder.id) }
                    )
                }
            }
        }
    }

    // Insert manual reminder popup dialogue
    if (showDialog) {
        var reminderTitle by remember { mutableStateOf("") }
        var relativeTimeCount by remember { mutableStateOf("15") }
        var relativeTimeUnit by remember { mutableStateOf("minute") }
        var selectedCategory by remember { mutableStateOf("Personal") }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = SpaceCardSurface,
            title = { Text("Schedule New Reminder", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = reminderTitle,
                        onValueChange = { reminderTitle = it },
                        label = { Text("Reminder Title", color = Color.White) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PrithiPrimaryLavender,
                            unfocusedBorderColor = SpaceCardOverlay
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = relativeTimeCount,
                            onValueChange = { relativeTimeCount = it },
                            label = { Text("In how many...", color = Color.White) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = PrithiPrimaryLavender,
                                unfocusedBorderColor = SpaceCardOverlay
                            ),
                            singleLine = true,
                            modifier = Modifier.width(110.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Unit", color = Color.White, fontSize = 11.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("minute", "hour", "day").forEach { unit ->
                                    Box(
                                        modifier = Modifier
                                            .border(
                                                1.dp,
                                                if (relativeTimeUnit == unit) PrithiPrimaryLavender else Color.Gray,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .background(
                                                if (relativeTimeUnit == unit) SpaceCardOverlay else Color.Transparent
                                            )
                                            .clickable { relativeTimeUnit = unit }
                                            .padding(horizontal = 6.dp, vertical = 6.dp)
                                    ) {
                                        Text(unit + "s", color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    Column {
                        Text("Category", color = Color.White, fontSize = 12.sp)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            listOf("Personal", "Work", "Health", "Social").forEach { cat ->
                                Box(
                                    modifier = Modifier
                                        .border(
                                            1.dp,
                                            if (selectedCategory == cat) PrithiPrimaryLavender else Color.Gray,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .background(
                                            if (selectedCategory == cat) SpaceCardOverlay else Color.Transparent
                                        )
                                        .clickable { selectedCategory = cat }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(cat, color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val count = relativeTimeCount.toLongOrNull() ?: 15
                        val delay = when (relativeTimeUnit) {
                            "minute" -> count * 60 * 1000
                            "hour" -> count * 3600 * 1000
                            "day" -> count * 24 * 3600 * 1000
                            else -> count * 60 * 1000
                        }
                        val reminder = Reminder(
                            title = reminderTitle.ifBlank { "Personal Task" },
                            dateTime = System.currentTimeMillis() + delay,
                            category = selectedCategory
                        )
                        viewModel.insertCustomReminder(reminder)
                        showDialog = false
                    },
                    modifier = Modifier.testTag("submit_reminder_button")
                ) {
                    Text("Schedule", color = PrithiPrimaryLavender, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun ReminderItemView(
    reminder: Reminder,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val relativeStr = getRelativeTimeString(reminder.dateTime)

    Card(
        colors = CardDefaults.cardColors(containerColor = SpaceCardSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, SpaceCardOverlay, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = reminder.isCompleted,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = PrithiPrimaryLavender,
                    checkmarkColor = SpaceBackground
                ),
                modifier = Modifier.testTag("reminder_checkbox")
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    color = if (reminder.isCompleted) Color.Gray else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                when (reminder.category) {
                                    "Health" -> Color(0xFFC0FFC0).copy(alpha = 0.15f)
                                    "Work" -> Color(0xFFC0E0FF).copy(alpha = 0.15f)
                                    "Social" -> Color(0xFFFFC0E0).copy(alpha = 0.15f)
                                    else -> Color.White.copy(alpha = 0.1f)
                                },
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = reminder.category,
                            color = when (reminder.category) {
                                "Health" -> Color.Green
                                "Work" -> Color.Cyan
                                "Social" -> PrithiPinkAccent
                                else -> Color.LightGray
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Due: $relativeStr",
                        color = if (System.currentTimeMillis() > reminder.dateTime && !reminder.isCompleted) {
                            Color.Red
                        } else {
                            Color.LightGray
                        },
                        fontSize = 11.sp
                    )
                }
            }

            IconButton(
                onClick = { onDelete() },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("delete_reminder_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Reminder",
                    tint = Color.Red.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private fun getRelativeTimeString(time: Long): String {
    val diff = time - System.currentTimeMillis()
    if (diff < 0) {
        val ago = -diff
        return when {
            ago < 60000 -> "just now"
            ago < 3600000 -> "${ago / 60000}m ago"
            else -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(time))
        }
    }
    return when {
        diff < 60000 -> "in less than a min"
        diff < 3600000 -> "in ${diff / 60000} min"
        diff < 86400000 -> "in ${diff / 3600000} hr"
        else -> SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(time))
    }
}

// --- TAB 2: PERSONAL MEMORIES ---

@Composable
fun MemoriesTab(viewModel: PrithiViewModel) {
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Prithi's Friendship Database",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Stored details of your unique friendship profile.",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = PrithiPrimaryLavender),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("add_memory_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add memory details", tint = SpaceBackground)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add", color = SpaceBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (memories.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Friendship database empty", color = Color.Gray, fontSize = 14.sp)
                    Text(
                        text = "Tell Prithi about yourself in chat to see them pop up here!",
                        color = PrithiSecondaryFuchsia.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(memories) { memory ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SpaceCardSurface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, SpaceCardOverlay, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = memory.key.uppercase(Locale.getDefault()),
                                    color = PrithiSecondaryFuchsia,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = memory.value,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteMemory(memory.key) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("delete_memory_${memory.key}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete memory preference detail",
                                    tint = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var memKey by remember { mutableStateOf("") }
        var memVal by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = SpaceCardSurface,
            title = { Text("Save Friendship Fact", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = memKey,
                        onValueChange = { memKey = it },
                        label = { Text("Key (e.g., hobby, pet name, home)", color = Color.White) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PrithiPrimaryLavender,
                            unfocusedBorderColor = SpaceCardOverlay
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = memVal,
                        onValueChange = { memVal = it },
                        label = { Text("What to remember", color = Color.White) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PrithiPrimaryLavender,
                            unfocusedBorderColor = SpaceCardOverlay
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (memKey.isNotBlank() && memVal.isNotBlank()) {
                            viewModel.addNewMemory(memKey.trim().lowercase(Locale.getDefault()), memVal.trim())
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Save Fact", color = PrithiPrimaryLavender, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

// --- TAB 3: SMARTPHONE AUTOMATION ---

@Composable
fun AutomationsTab(viewModel: PrithiViewModel) {
    val logs by viewModel.automationLogs.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Prithi's Automations",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Actions run by Prithi on your phone dynamically.",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }

            IconButton(
                onClick = { viewModel.clearAutomationLogs() },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("clear_logs_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Clear run logs history",
                    tint = PrithiPrimaryLavender
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.List,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No task runs recorded", color = Color.Gray, fontSize = 14.sp)
                    Text(
                        text = "When you suggest: \"Open YouTube\" or \"Email John\", Prithi executes it and indexes it here.",
                        color = PrithiSecondaryFuchsia.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(logs) { log ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SpaceCardSurface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, SpaceCardOverlay, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (log.actionType) {
                                    "OPEN_WEB" -> Icons.Default.Home
                                    "DRAFT_MAIL" -> Icons.Default.Create
                                    "DIAL_PHONE" -> Icons.Default.Check
                                    "SHOW_MAP" -> Icons.Default.Home
                                    "SET_REMINDER" -> Icons.Default.Notifications
                                    else -> Icons.Default.Info
                                },
                                contentDescription = "Action category icon",
                                tint = if (log.success) PrithiPrimaryLavender else Color.Red,
                                modifier = Modifier.size(32.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = log.description,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${log.actionType} | " + SimpleDateFormat("MMM d, h:mm:ss a", Locale.getDefault()).format(Date(log.timestamp)),
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .background(
                                        if (log.success) Color(0xFFC0FFC0).copy(alpha = 0.15f) else Color(0xFFFFC0C0).copy(alpha = 0.15f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (log.success) "SUCCESS" else "FAILED",
                                    color = if (log.success) Color.Green else Color.Red,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
