package com.dailythread.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dailythread.app.data.local.entity.*
import com.dailythread.app.data.repository.*
import com.dailythread.app.domain.DailyScoreCalculator
import com.dailythread.app.ui.theme.DailyThreadTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DailyThreadApp
        setContent {
            DailyThreadTheme {
                DailyThreadRoot(app)
            }
        }
    }
}

@Composable
private fun DailyThreadRoot(app: DailyThreadApp) {
    val scope = rememberCoroutineScope()
    val auth = remember { AuthRepository(app.tokenStore) }
    var userId by remember { mutableStateOf<String?>(null) }
    var booting by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        userId = app.tokenStore.userId()
        booting = false
    }

    when {
        booting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        userId == null -> LoginScreen(busy, error) { email, password ->
            scope.launch {
                busy = true
                error = null
                auth.login(email, password)
                    .onSuccess { userId = it }
                    .onFailure { error = it.message ?: "Login gagal" }
                busy = false
            }
        }
        else -> HomeScreen(app, requireNotNull(userId)) {
            scope.launch {
                app.tokenStore.clear()
                userId = null
            }
        }
    }
}

@Composable
private fun LoginScreen(busy: Boolean, error: String?, onLogin: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Daily Thread", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Offline-first productivity", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = { onLogin(email.trim(), password) },
                    enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Masuk…" else "Masuk") }
                Text("Setelah login pertama, data lokal tetap dapat dibuka tanpa internet.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private enum class Tab(val title: String) { TODAY("Today"), TIMELINE("Timeline"), REVIEW("Review"), SYNC("Sync") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(app: DailyThreadApp, userId: String, onLogout: () -> Unit) {
    val date = remember { LocalDate.now() }
    val focusRepo = remember { FocusRepository(app.db, app.tokenStore, app.deviceId) }
    val taskRepo = remember { TaskRepository(app.db, app.tokenStore, app.deviceId) }
    val activityRepo = remember { ActivityRepository(app.db, app.tokenStore, app.deviceId) }
    val habitRepo = remember { HabitRepository(app.db, app.tokenStore, app.deviceId) }
    val reviewRepo = remember { ReviewRepository(app.db, app.tokenStore, app.deviceId) }

    val focus by focusRepo.observe(userId, date).collectAsState(initial = emptyList())
    val tasks by taskRepo.observe(userId, date).collectAsState(initial = emptyList())
    val activities by activityRepo.observe(userId, date).collectAsState(initial = emptyList())
    val habits by habitRepo.observeHabits(userId).collectAsState(initial = emptyList())
    val entries by habitRepo.observeEntries(userId, date).collectAsState(initial = emptyList())
    val review by reviewRepo.observe(userId, date).collectAsState(initial = null)
    val pending by app.db.outboxDao().observePendingCount().collectAsState(initial = 0)

    val score = remember(focus, tasks, activities, habits, entries) {
        DailyScoreCalculator.calculate(focus, tasks, activities, habits, entries)
    }
    var tab by remember { mutableStateOf(Tab.TODAY) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("Daily Thread", fontWeight = FontWeight.Bold); Text(date.toString(), style = MaterialTheme.typography.labelSmall) } },
                actions = {
                    AssistChip(onClick = { tab = Tab.SYNC }, label = { Text(if (pending == 0) "Synced" else "$pending pending") })
                    TextButton(onClick = onLogout) { Text("Keluar") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(item.title.take(1), fontWeight = FontWeight.Bold) },
                        label = { Text(item.title) }
                    )
                }
            }
        }
    ) { padding ->
        when (tab) {
            Tab.TODAY -> TodayScreen(Modifier.padding(padding), score, focus, tasks, habits, entries, focusRepo, taskRepo, habitRepo)
            Tab.TIMELINE -> TimelineScreen(Modifier.padding(padding), activities, activityRepo)
            Tab.REVIEW -> ReviewScreen(Modifier.padding(padding), score, review, reviewRepo)
            Tab.SYNC -> SyncScreen(Modifier.padding(padding), pending) { app.syncEngine.runOnce() }
        }
    }
}

@Composable
private fun TodayScreen(
    modifier: Modifier,
    score: Int,
    focus: List<FocusEntity>,
    tasks: List<TaskEntity>,
    habits: List<HabitEntity>,
    entries: List<HabitEntryEntity>,
    focusRepo: FocusRepository,
    taskRepo: TaskRepository,
    habitRepo: HabitRepository
) {
    val scope = rememberCoroutineScope()
    var focusName by remember { mutableStateOf("") }
    var taskName by remember { mutableStateOf("") }
    var habitName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val entryMap = entries.associateBy { it.habitId }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Daily Score", style = MaterialTheme.typography.labelLarge); Text("$score/100", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                    Text("Offline-first", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }

        item { SectionTitle("3 Fokus Utama", "${focus.size}/3") }
        items(focus, key = { it.id }) { item ->
            SimpleRow(
                title = item.title,
                subtitle = "${item.estimateMinutes} menit · ${item.status}",
                checked = item.status == "COMPLETED",
                onChecked = { scope.launch { focusRepo.setStatus(item.id, if (it) "COMPLETED" else "PLANNED") } },
                onDelete = { scope.launch { focusRepo.delete(item.id) } }
            )
        }
        if (focus.size < 3) item {
            InlineAdd("Tambah fokus", focusName, { focusName = it }) {
                scope.launch {
                    runCatching { focusRepo.add(focusName) }
                        .onSuccess { focusName = "" }
                        .onFailure { message = it.message }
                }
            }
        }

        item { SectionTitle("Tasks", "${tasks.count { it.status == "DONE" }}/${tasks.size}") }
        items(tasks, key = { it.id }) { item ->
            SimpleRow(
                item.title,
                "${item.priority} · ${item.status}",
                item.status == "DONE",
                { scope.launch { taskRepo.toggleDone(item.id) } },
                { scope.launch { taskRepo.delete(item.id) } },
                extra = { TextButton(onClick = { scope.launch { taskRepo.moveToTomorrow(item.id) } }) { Text("Besok") } }
            )
        }
        item {
            InlineAdd("Tambah task", taskName, { taskName = it }) {
                scope.launch { runCatching { taskRepo.add(taskName) }.onSuccess { taskName = "" }.onFailure { message = it.message } }
            }
        }

        item { SectionTitle("Habits", "${entries.count { it.completed }}/${habits.size}") }
        items(habits, key = { it.id }) { habit ->
            SimpleRow(
                habit.name,
                "Habit harian",
                entryMap[habit.id]?.completed == true,
                { checked -> scope.launch { habitRepo.setCompleted(habit.id, checked) } },
                { scope.launch { habitRepo.delete(habit.id) } }
            )
        }
        item {
            InlineAdd("Tambah habit", habitName, { habitName = it }) {
                scope.launch { runCatching { habitRepo.add(habitName) }.onSuccess { habitName = "" }.onFailure { message = it.message } }
            }
        }
    }
}

@Composable
private fun TimelineScreen(modifier: Modifier, activities: List<ActivityEntity>, repo: ActivityRepository) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("30") }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Timeline", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        items(activities, key = { it.id }) { item ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.SemiBold); Text("${item.categoryName} · ${item.durationMinutes} menit", style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = { scope.launch { repo.delete(item.id) } }) { Text("Hapus") }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Aktivitas") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("Durasi menit") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { scope.launch { repo.quickAdd(title, minutes.toIntOrNull() ?: 30); title = "" } }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Catat aktivitas") }
            }
        }
    }
}

@Composable
private fun ReviewScreen(modifier: Modifier, score: Int, review: ReviewEntity?, repo: ReviewRepository) {
    val scope = rememberCoroutineScope()
    var achievement by remember(review?.updatedAt) { mutableStateOf(review?.achievement ?: "") }
    var blocker by remember(review?.updatedAt) { mutableStateOf(review?.blocker ?: "") }
    var tomorrow by remember(review?.updatedAt) { mutableStateOf(review?.tomorrowPriority ?: "") }
    var mood by remember(review?.updatedAt) { mutableStateOf(review?.mood ?: "") }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Night Review", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Text("Daily Score: $score/100") }
        item { OutlinedTextField(achievement, { achievement = it }, label = { Text("Pencapaian") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
        item { OutlinedTextField(blocker, { blocker = it }, label = { Text("Hambatan") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
        item { OutlinedTextField(tomorrow, { tomorrow = it }, label = { Text("Prioritas besok") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
        item { OutlinedTextField(mood, { mood = it }, label = { Text("Mood") }, modifier = Modifier.fillMaxWidth()) }
        item { Button(onClick = { scope.launch { repo.save(achievement, blocker, tomorrow, mood, score) } }, modifier = Modifier.fillMaxWidth()) { Text("Simpan review") } }
    }
}

@Composable
private fun SyncScreen(modifier: Modifier, pending: Int, onSync: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Sinkronisasi", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(if (pending == 0) "Semua perubahan tersinkron." else "$pending perubahan masih menunggu.")
        Button(onClick = {
            scope.launch {
                busy = true
                message = runCatching { onSync(); "Sinkronisasi selesai." }.getOrElse { it.message ?: "Sinkronisasi gagal." }
                busy = false
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Sinkronisasi…" else "Sinkronkan sekarang") }
        message?.let { Text(it) }
        Text("Data selalu disimpan ke Room/SQLite terlebih dahulu. WorkManager akan mencoba sinkronisasi lagi saat internet tersedia.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SectionTitle(title: String, badge: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(badge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SimpleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    onDelete: () -> Unit,
    extra: @Composable (() -> Unit)? = null
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked, onCheckedChange = onChecked)
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall) }
            extra?.invoke()
            TextButton(onClick = onDelete) { Text("Hapus") }
        }
    }
}

@Composable
private fun InlineAdd(label: String, value: String, onValue: (String) -> Unit, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value, onValue, label = { Text(label) }, modifier = Modifier.weight(1f), singleLine = true)
        Button(onClick = onAdd, enabled = value.isNotBlank()) { Text("Tambah") }
    }
}
