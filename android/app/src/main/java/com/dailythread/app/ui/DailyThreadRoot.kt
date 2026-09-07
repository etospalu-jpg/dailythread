package com.dailythread.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dailythread.app.DailyThreadApp
import com.dailythread.app.data.local.entity.*
import com.dailythread.app.data.repository.*
import com.dailythread.app.domain.DailyScoreCalculator
import com.dailythread.app.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val zone = ZoneId.of("Asia/Makassar")
private val localeId = Locale("id", "ID")
private enum class Tab(val label:String,val icon:String){TODAY("Hari Ini","⌂"),TIMELINE("Timeline","≋"),PROGRESS("Progress","↗"),PROFILE("Profil","◉")}
private enum class AddType(val label:String){FOCUS("Fokus"),TASK("Tugas"),ACTIVITY("Aktivitas"),HABIT("Habit")}

@Composable
fun DailyThreadRoot(app: DailyThreadApp) {
    val scope=rememberCoroutineScope()
    val userId by app.tokenStore.userIdFlow().collectAsState(initial=null)
    LaunchedEffect(Unit){
        app.tokenStore.ensureLocalUserId(app.deviceId)
        scope.launch { if(app.ensureCloudIdentity()){ runCatching{app.profileRepository.syncDirty()}; runCatching{app.syncEngine.runOnce()} } }
    }
    if(userId.isNullOrBlank()){
        Box(Modifier.fillMaxSize().background(DtBackground),contentAlignment=Alignment.Center){Text("Daily Thread",color=DtPrimaryDeep,fontWeight=FontWeight.ExtraBold,fontSize=24.sp)}
    }else Home(app,requireNotNull(userId))
}

@Composable
private fun Home(app:DailyThreadApp,userId:String){
    val scope=rememberCoroutineScope(); val today=remember{LocalDate.now(zone)}; val weekStart=remember(today){today.minusDays(6)}
    val focusRepo=remember{FocusRepository(app.db,app.tokenStore,app.deviceId)}; val taskRepo=remember{TaskRepository(app.db,app.tokenStore,app.deviceId)}
    val activityRepo=remember{ActivityRepository(app.db,app.tokenStore,app.deviceId)}; val habitRepo=remember{HabitRepository(app.db,app.tokenStore,app.deviceId)}; val reviewRepo=remember{ReviewRepository(app.db,app.tokenStore,app.deviceId)}
    val focus by focusRepo.observe(userId,today).collectAsState(initial=emptyList()); val tasks by taskRepo.observe(userId,today).collectAsState(initial=emptyList()); val acts by activityRepo.observe(userId,today).collectAsState(initial=emptyList())
    val habits by habitRepo.observeHabits(userId).collectAsState(initial=emptyList()); val entries by habitRepo.observeEntries(userId,today).collectAsState(initial=emptyList()); val review by reviewRepo.observe(userId,today).collectAsState(initial=null)
    val weekFocus by app.db.focusDao().observeRange(userId,weekStart.toString(),today.toString()).collectAsState(initial=emptyList()); val weekTasks by app.db.taskDao().observeRange(userId,weekStart.toString(),today.toString()).collectAsState(initial=emptyList()); val weekActs by app.db.activityDao().observeRange(userId,weekStart.toString(),today.toString()).collectAsState(initial=emptyList()); val weekEntries by app.db.habitEntryDao().observeRange(userId,weekStart.toString(),today.toString()).collectAsState(initial=emptyList())
    val name by app.tokenStore.displayNameFlow().collectAsState(initial="Pengguna"); val target by app.tokenStore.targetFocusFlow().collectAsState(initial=120); val pending by app.db.outboxDao().observePendingCount(userId).collectAsState(initial=0)
    val score=remember(focus,tasks,acts,habits,entries,target){DailyScoreCalculator.calculate(focus,tasks,acts,habits,entries,target)}
    var tab by rememberSaveable{mutableStateOf(Tab.TODAY)}; var add by remember{mutableStateOf(false)}
    LaunchedEffect(userId){ runCatching{app.profileRepository.hydrateFromCloud()}; runCatching{app.syncEngine.runOnce()} }
    Scaffold(containerColor=DtBackground,floatingActionButton={FloatingActionButton(onClick={add=true},containerColor=DtPrimaryDeep,contentColor=Color.White,shape=RoundedCornerShape(18.dp)){Text("+",fontSize=30.sp)}},floatingActionButtonPosition=FabPosition.Center,bottomBar={BottomNav(tab){tab=it}}){pad->
        when(tab){
            Tab.TODAY->Today(Modifier.padding(pad),name,today,score,target,focus,tasks,acts,habits,entries,review,pending,focusRepo,taskRepo,habitRepo,reviewRepo)
            Tab.TIMELINE->Timeline(Modifier.padding(pad),userId,today,activityRepo,app)
            Tab.PROGRESS->Progress(Modifier.padding(pad),today,weekFocus,weekTasks,weekActs,habits,weekEntries,target)
            Tab.PROFILE->Profile(Modifier.padding(pad),app,name,target,pending)
        }
    }
    if(add) QuickAdd(today,focusRepo,taskRepo,activityRepo,habitRepo){add=false}
}

@Composable private fun BottomNav(tab:Tab,onSelect:(Tab)->Unit){
    NavigationBar(containerColor=Color.White,tonalElevation=8.dp){
        NavigationBarItem(selected=tab==Tab.TODAY,onClick={onSelect(Tab.TODAY)},icon={Text(Tab.TODAY.icon)},label={Text(Tab.TODAY.label)})
        NavigationBarItem(selected=tab==Tab.TIMELINE,onClick={onSelect(Tab.TIMELINE)},icon={Text(Tab.TIMELINE.icon)},label={Text(Tab.TIMELINE.label)})
        Spacer(Modifier.width(64.dp))
        NavigationBarItem(selected=tab==Tab.PROGRESS,onClick={onSelect(Tab.PROGRESS)},icon={Text(Tab.PROGRESS.icon)},label={Text(Tab.PROGRESS.label)})
        NavigationBarItem(selected=tab==Tab.PROFILE,onClick={onSelect(Tab.PROFILE)},icon={Text(Tab.PROFILE.icon)},label={Text(Tab.PROFILE.label)})
    }
}

@Composable private fun Page(mod:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){LazyColumn(mod.fillMaxSize().background(DtBackground),contentPadding=PaddingValues(horizontal=12.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Column(verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}}}
@Composable private fun CardBox(content:@Composable ColumnScope.()->Unit){Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),color=Color.White,shadowElevation=1.dp){Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp),content=content)}}
@Composable private fun Section(title:String,action:String?=null,onAction:(()->Unit)?=null){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(title,fontWeight=FontWeight.ExtraBold,fontSize=17.sp,color=DtPrimaryDeep);if(action!=null)Text(action,color=DtPrimary,fontWeight=FontWeight.Bold,modifier=Modifier.clickable{onAction?.invoke()})}}

@Composable private fun Today(mod:Modifier,name:String,date:LocalDate,score:Int,target:Int,focus:List<FocusEntity>,tasks:List<TaskEntity>,acts:List<ActivityEntity>,habits:List<HabitEntity>,entries:List<HabitEntryEntity>,review:ReviewEntity?,pending:Int,focusRepo:FocusRepository,taskRepo:TaskRepository,habitRepo:HabitRepository,reviewRepo:ReviewRepository){
    val scope=rememberCoroutineScope(); val doneHabits=entries.filter{it.completed}.map{it.habitId}.toSet(); val productive=acts.filter{!it.categoryName.equals("Istirahat",true)}.sumOf{it.durationMinutes}
    Page(mod){
        Text("DAILY THREAD",fontSize=11.sp,fontWeight=FontWeight.Black,color=DtPrimary,letterSpacing=1.5.sp);Text("Halo, ${name.ifBlank{"Pengguna"}} 👋",fontSize=28.sp,fontWeight=FontWeight.ExtraBold,color=DtText);Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy",localeId)),color=DtMuted)
        Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=Color.Transparent){Column(Modifier.background(Brush.linearGradient(listOf(DtPrimaryDeep,DtPrimary))).padding(20.dp)){Text("DAILY SCORE",color=Color.White.copy(.8f),fontWeight=FontWeight.Bold,fontSize=11.sp);Row(verticalAlignment=Alignment.Bottom){Text("$score",color=Color.White,fontWeight=FontWeight.Black,fontSize=52.sp);Text(" / 100",color=Color.White.copy(.75f),modifier=Modifier.padding(bottom=8.dp))};Text(if(score>=70)"Harimu bergerak dengan baik." else "Hari masih terbuka. Fokus pada satu langkah berikutnya.",color=Color.White)} }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Metric("Fokus","${focus.count{it.status=="COMPLETED"}}/${focus.size}",Modifier.weight(1f));Metric("Tugas","${tasks.count{it.status=="DONE"}}/${tasks.size}",Modifier.weight(1f));Metric("Produktif","${productive}m",Modifier.weight(1f));Metric("Target","${target}m",Modifier.weight(1f))}
        Section("3 Fokus Hari Ini")
        if(focus.isEmpty()) Empty("Belum ada fokus. Tekan + untuk menambahkan.") else focus.forEach{f->CardBox{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${f.sortOrder}",fontWeight=FontWeight.Black,color=DtPrimary,modifier=Modifier.width(28.dp));Column(Modifier.weight(1f)){Text(f.title,fontWeight=FontWeight.Bold);Text("${f.estimateMinutes} menit · ${labelFocus(f.status)}",color=DtMuted,fontSize=12.sp)};Checkbox(checked=f.status=="COMPLETED",onCheckedChange={scope.launch{focusRepo.setStatus(f.id,if(it)"COMPLETED" else "PLANNED")}})}}}
        Section("Thread Hari Ini")
        if(acts.isEmpty()) Empty("Aktivitas yang kamu catat akan membentuk thread harian.") else acts.take(5).forEach{a->CardBox{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(a.title,fontWeight=FontWeight.Bold);Text(a.categoryName,color=DtMuted,fontSize=12.sp)};Text("${a.durationMinutes}m",fontWeight=FontWeight.Bold,color=DtPrimaryDeep)}}}
        Section("Tasks Hari Ini")
        if(tasks.isEmpty()) Empty("Belum ada tugas hari ini.") else tasks.forEach{t->CardBox{Row(verticalAlignment=Alignment.CenterVertically){Checkbox(t.status=="DONE",onCheckedChange={scope.launch{taskRepo.toggleDone(t.id)}});Column(Modifier.weight(1f)){Text(t.title,fontWeight=FontWeight.Bold);Text(t.priority,color=DtMuted,fontSize=11.sp)};TextButton(onClick={scope.launch{taskRepo.moveToTomorrow(t.id)}}){Text("Besok")}}}}
        Section("Habit Hari Ini")
        if(habits.isEmpty()) Empty("Tambahkan habit kecil yang ingin dijaga setiap hari.") else habits.forEach{h->CardBox{Row(verticalAlignment=Alignment.CenterVertically){Checkbox(doneHabits.contains(h.id),onCheckedChange={scope.launch{habitRepo.setCompleted(h.id,it)}});Text(h.name,fontWeight=FontWeight.Bold)}}}
        NightReview(score,review,reviewRepo)
        Text(if(pending>0)"● Offline-first · $pending perubahan menunggu sinkronisasi" else "✓ Data lokal aman · sinkronisasi berjalan di belakang layar",color=DtMuted,fontSize=11.sp)
    }
}

@Composable private fun Metric(label:String,value:String,mod:Modifier){Surface(mod,shape=RoundedCornerShape(14.dp),color=Color.White){Column(Modifier.padding(10.dp)){Text(label,color=DtMuted,fontSize=10.sp);Text(value,fontWeight=FontWeight.ExtraBold,color=DtPrimaryDeep,fontSize=16.sp)}}}
@Composable private fun Empty(text:String){Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=Color.White){Text(text,Modifier.padding(14.dp),color=DtMuted,fontSize=12.sp)}}
private fun labelFocus(s:String)=when(s){"COMPLETED"->"Selesai";"IN_PROGRESS"->"Dikerjakan";else->"Belum Mulai"}

@Composable private fun NightReview(score:Int,review:ReviewEntity?,repo:ReviewRepository){
    val scope=rememberCoroutineScope(); var ach by remember(review?.id){mutableStateOf(review?.achievement.orEmpty())};var block by remember(review?.id){mutableStateOf(review?.blocker.orEmpty())};var tomorrow by remember(review?.id){mutableStateOf(review?.tomorrowPriority.orEmpty())};var mood by remember(review?.id){mutableStateOf(review?.mood.orEmpty())}
    CardBox{Section("Night Review");OutlinedTextField(ach,{ach=it},label={Text("Pencapaian hari ini")},modifier=Modifier.fillMaxWidth());OutlinedTextField(block,{block=it},label={Text("Hambatan")},modifier=Modifier.fillMaxWidth());OutlinedTextField(tomorrow,{tomorrow=it},label={Text("Prioritas besok")},modifier=Modifier.fillMaxWidth());Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){listOf("😞","😐","🙂","🔥").forEach{m->FilterChip(selected=mood==m,onClick={mood=m},label={Text(m,fontSize=19.sp)})}};Button(onClick={scope.launch{repo.save(ach,block,tomorrow,mood,score)}},modifier=Modifier.fillMaxWidth()){Text("Simpan Review")}}
}

@Composable private fun Timeline(mod:Modifier,userId:String,today:LocalDate,repo:ActivityRepository,app:DailyThreadApp){
    var date by rememberSaveable{mutableStateOf(today.toString())}; val parsed=LocalDate.parse(date); val acts by repo.observe(userId,parsed).collectAsState(initial=emptyList())
    Page(mod){Text("Timeline",fontSize=28.sp,fontWeight=FontWeight.ExtraBold,color=DtText);Text(parsed.format(DateTimeFormatter.ofPattern("EEEE, d MMMM",localeId)),color=DtMuted);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={date=parsed.minusDays(1).toString()}){Text("‹ Sebelumnya")};OutlinedButton(onClick={date=today.toString()}){Text("Hari ini")};OutlinedButton(onClick={date=parsed.plusDays(1).toString()}){Text("Berikutnya ›")}};if(acts.isEmpty())Empty("Belum ada aktivitas pada tanggal ini.") else acts.forEach{a->CardBox{Text(a.title,fontWeight=FontWeight.Bold);Text("${a.categoryName} · ${a.durationMinutes} menit",color=DtMuted)}}}
}

@Composable private fun Progress(mod:Modifier,today:LocalDate,focus:List<FocusEntity>,tasks:List<TaskEntity>,acts:List<ActivityEntity>,habits:List<HabitEntity>,entries:List<HabitEntryEntity>,target:Int){
    val focusDone=focus.count{it.status=="COMPLETED"};val taskDone=tasks.count{it.status=="DONE"};val productive=acts.filter{!it.categoryName.equals("Istirahat",true)}.sumOf{it.durationMinutes};val activeHabitIds=habits.filter{it.isActive}.map{it.id}.toSet();val habitDone=entries.count{it.completed&&it.habitId in activeHabitIds};val estimate=((if(focus.isEmpty())0.0 else focusDone.toDouble()/focus.size*40)+(if(tasks.isEmpty())0.0 else taskDone.toDouble()/tasks.size*20)+(productive.toDouble()/target.coerceAtLeast(15)*20).coerceAtMost(20.0)+(if(activeHabitIds.isEmpty())0.0 else habitDone.toDouble()/activeHabitIds.size*20)).toInt().coerceIn(0,100)
    Page(mod){Text("Progress 7 Hari",fontSize=28.sp,fontWeight=FontWeight.ExtraBold);Text("Ringkasan tetap dihitung dari database lokal, jadi bisa dibuka tanpa internet.",color=DtMuted);Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),color=DtPrimaryDeep){Column(Modifier.padding(18.dp)){Text("SKOR MINGGU INI",color=Color.White.copy(.7f),fontSize=11.sp);Text("$estimate",color=Color.White,fontSize=48.sp,fontWeight=FontWeight.Black)}};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Metric("Fokus","$focusDone/${focus.size}",Modifier.weight(1f));Metric("Tugas","$taskDone/${tasks.size}",Modifier.weight(1f));Metric("Waktu","${productive}m",Modifier.weight(1f))};Section("Life Categories");val cats=acts.groupBy{it.categoryName}.mapValues{e->e.value.sumOf{it.durationMinutes}}.entries.sortedByDescending{it.value};if(cats.isEmpty())Empty("Belum cukup data aktivitas minggu ini.")else cats.take(6).forEach{CardBox{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(it.key,fontWeight=FontWeight.Bold);Text("${it.value}m",color=DtPrimaryDeep,fontWeight=FontWeight.Bold)}}}}
}

@Composable private fun Profile(mod:Modifier,app:DailyThreadApp,name:String,target:Int,pending:Int){
    val scope=rememberCoroutineScope();var draftName by remember(name){mutableStateOf(name)};var draftTarget by remember(target){mutableStateOf(target.toString())};var saved by remember{mutableStateOf(false)}
    Page(mod){Text("Profil",fontSize=28.sp,fontWeight=FontWeight.ExtraBold);Text("Tidak ada akun, email, atau password yang perlu kamu ingat.",color=DtMuted);CardBox{Text("Nama pengguna",fontWeight=FontWeight.Bold);OutlinedTextField(draftName,{draftName=it.take(80)},modifier=Modifier.fillMaxWidth(),singleLine=true);OutlinedTextField(draftTarget,{draftTarget=it.filter(Char::isDigit).take(3)},label={Text("Target fokus per hari (menit)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth());Button(onClick={scope.launch{app.tokenStore.saveLocalProfile(draftName.ifBlank{"Pengguna"},draftTarget.toIntOrNull()?:120);app.enqueueImmediateSync();saved=true}},modifier=Modifier.fillMaxWidth()){Text("Simpan Profil")};if(saved)Text("Tersimpan di HP.",color=DtSuccess)};CardBox{Text("Offline-first",fontWeight=FontWeight.ExtraBold);Text("Semua input utama masuk ke Room/SQLite dulu. Internet hanya dipakai untuk sinkronisasi latar belakang.",color=DtMuted);Text(if(pending>0)"$pending perubahan menunggu sinkronisasi" else "Tidak ada antrean perubahan",fontWeight=FontWeight.Bold,color=DtPrimaryDeep)}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun QuickAdd(today:LocalDate,focusRepo:FocusRepository,taskRepo:TaskRepository,activityRepo:ActivityRepository,habitRepo:HabitRepository,onDismiss:()->Unit){
    val scope=rememberCoroutineScope();var type by remember{mutableStateOf(AddType.FOCUS)};var name by remember{mutableStateOf("")};var number by remember{mutableStateOf("30")};var priority by remember{mutableStateOf("MEDIUM")};var category by remember{mutableStateOf("Belajar")};var error by remember{mutableStateOf<String?>(null)}
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=Color.White){Column(Modifier.fillMaxWidth().padding(horizontal=12.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Quick Add",fontSize=22.sp,fontWeight=FontWeight.ExtraBold);Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){AddType.entries.forEach{FilterChip(selected=type==it,onClick={type=it},label={Text(it.label,fontSize=11.sp)})}};OutlinedTextField(name,{name=it},label={Text(when(type){AddType.FOCUS->"Nama fokus";AddType.TASK->"Nama tugas";AddType.ACTIVITY->"Nama aktivitas";AddType.HABIT->"Nama habit"})},modifier=Modifier.fillMaxWidth());if(type!=AddType.HABIT)OutlinedTextField(number,{number=it.filter(Char::isDigit).take(3)},label={Text(if(type==AddType.ACTIVITY)"Durasi menit" else "Estimasi menit")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth());if(type==AddType.TASK)Row{listOf("LOW","MEDIUM","HIGH").forEach{p->FilterChip(selected=priority==p,onClick={priority=p},label={Text(p)},modifier=Modifier.padding(end=6.dp))}};if(type==AddType.ACTIVITY)OutlinedTextField(category,{category=it},label={Text("Kategori")},modifier=Modifier.fillMaxWidth());error?.let{Text(it,color=DtDanger,fontSize=12.sp)};Button(onClick={scope.launch{val r=runCatching{when(type){AddType.FOCUS->focusRepo.add(name,number.toIntOrNull()?:60,today);AddType.TASK->taskRepo.add(name,priority,today,number.toIntOrNull()?:0);AddType.ACTIVITY->activityRepo.quickAdd(name,number.toIntOrNull()?:30,category);AddType.HABIT->habitRepo.add(name)}};r.onSuccess{onDismiss()}.onFailure{error=it.message}}},enabled=name.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Simpan")}}}
}
