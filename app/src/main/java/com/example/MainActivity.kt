package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00B0FF),      // Vibrant Blue from Vivid Palette
                    secondary = Color(0xFF00E5FF),    // Neon Cyan-Blue
                    background = Color(0xFF121212),   // Dark elegant grey
                    surface = Color(0xFF1E1E1E),      // Elevated surface grey
                    error = Color(0xFFEF5350)         // Soft red
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TimeTrackerApp()
                }
            }
        }
    }
}

@Composable
fun TimeTrackerApp(viewModel: TimeTrackingViewModel = viewModel()) {
    val context = LocalContext.current
    val activeRecord by viewModel.activeRecord.collectAsStateWithLifecycle()
    val allRecords by viewModel.allRecords.collectAsStateWithLifecycle()
    val currentMonthTarget by viewModel.currentMonthObligatoryHours.collectAsStateWithLifecycle()

    var activeTab by remember { mutableIntStateOf(0) }

    // Enforce Right-to-Left (RTL) Layout Direction strictly for Persian (Farsi) UI
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF121212))
                        .statusBarsPadding()
                        .padding(vertical = 12.dp, horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFF00B0FF), shape = RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🕒", fontSize = 20.sp, color = Color.White)
                            }
                            Text(
                                text = "ساعت‌زن هوشمند",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(50))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⚙️", fontSize = 16.sp)
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF1E1E1E),
                    modifier = Modifier
                        .navigationBarsPadding()
                        .drawWithContent {
                            drawContent()
                            drawLine(
                                color = Color.White.copy(alpha = 0.05f),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 3f
                            )
                        }
                ) {
                    NavigationBarItem(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        icon = { Text("🕒", fontSize = 20.sp) },
                        label = { Text("ثبت زمان", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF00B0FF),
                            selectedTextColor = Color(0xFF00B0FF),
                            indicatorColor = Color(0xFF00B0FF).copy(alpha = 0.12f),
                            unselectedIconColor = Color(0xFF64748B),
                            unselectedTextColor = Color(0xFF64748B)
                        )
                    )
                    NavigationBarItem(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        icon = { Text("📊", fontSize = 20.sp) },
                        label = { Text("گزارشات و خروجی", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF00B0FF),
                            selectedTextColor = Color(0xFF00B0FF),
                            indicatorColor = Color(0xFF00B0FF).copy(alpha = 0.12f),
                            unselectedIconColor = Color(0xFF64748B),
                            unselectedTextColor = Color(0xFF64748B)
                        )
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                if (activeTab == 0) {
                    TimeRegistrationTab(viewModel, activeRecord, allRecords, currentMonthTarget)
                } else {
                    ReportsTab(viewModel)
                }
            }
        }
    }
}

@Composable
fun TimeRegistrationTab(
    viewModel: TimeTrackingViewModel,
    activeRecord: TimeRecord?,
    allRecords: List<TimeRecord>,
    currentMonthTarget: Double
) {
    val today = JalaliCalendarHelper.fromEpochMillis(System.currentTimeMillis())
    val currentMonthRecords = allRecords.filter { it.jalaliYear == today.year && it.jalaliMonth == today.month }
    val currentMillis = System.currentTimeMillis()
    val activeDurationMs = if (activeRecord?.jalaliYear == today.year && activeRecord?.jalaliMonth == today.month) {
        currentMillis - activeRecord.startTimeMillis
    } else {
        0L
    }
    val workedMs = currentMonthRecords.sumOf { record ->
        if (record.endTimeMillis != null) {
            record.endTimeMillis - record.startTimeMillis
        } else {
            0L
        }
    } + activeDurationMs

    val totalMinutes = workedMs / (1000 * 60)
    val workedHours = totalMinutes / 60
    val workedMins = totalMinutes % 60
    val totalWorkedString = String.format(Locale.US, "%02d:%02d", workedHours, workedMins)

    val targetHoursInt = currentMonthTarget.toInt()
    val targetMinutesInt = ((currentMonthTarget - targetHoursInt) * 60).toInt()
    val targetString = String.format(Locale.US, "%d:%02d", targetHoursInt, targetMinutesInt)

    val workedHoursDouble = workedMs.toDouble() / (1000 * 60 * 60)
    val diffHours = workedHoursDouble - currentMonthTarget
    val diffAbs = kotlin.math.abs(diffHours)
    val diffHoursInt = diffAbs.toInt()
    val diffMinutesInt = ((diffAbs - diffHoursInt) * 60).toInt()
    val sign = if (diffHours >= 0) "+" else "-"
    val diffString = String.format(Locale.US, "%s%d:%02d", sign, diffHoursInt, diffMinutesInt)

    val lastRecord = allRecords.firstOrNull { it.endTimeMillis != null }
    val lastExitText = if (lastRecord != null) {
        val lastExitJalali = JalaliCalendarHelper.fromEpochMillis(lastRecord.endTimeMillis!!)
        val lastExitTime = SimpleDateFormat("HH:mm", Locale.US).format(Date(lastRecord.endTimeMillis))
        "آخرین خروج: ${lastExitJalali.day} ${JalaliCalendarHelper.getPersianMonthName(lastExitJalali.month)} در ساعت $lastExitTime"
    } else {
        "آخرین خروج: ثبت نشده"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        item {
            // High-fidelity Summary Dashboard Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val monthName = JalaliCalendarHelper.getPersianMonthName(today.month)
                        Text(
                            text = "وضعیت کارکرد $monthName ${today.year}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8)
                        )
                        val isWorking = activeRecord != null
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isWorking) Color(0x33388E3C) else Color(0x1AEF5350),
                                    shape = RoundedCornerShape(50)
                                )
                                .border(
                                    BorderStroke(1.dp, if (isWorking) Color(0x4D388E3C) else Color(0x4DEF5350)),
                                    shape = RoundedCornerShape(50)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isWorking) "تراز هوشمند فعال" else "متوقف",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isWorking) Color(0xFF81C784) else Color(0xFFEF5350)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text("کارکرد کل", fontSize = 11.sp, color = Color(0xFF64748B))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(totalWorkedString, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }

                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .width(1.dp)
                                .background(Color.White.copy(alpha = 0.05f))
                        )

                        Column(
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text("موظفی", fontSize = 11.sp, color = Color(0xFF64748B))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(targetString, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }

                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .width(1.dp)
                                .background(Color.White.copy(alpha = 0.05f))
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            val deltaLabel = if (diffHours >= 0) "اضافه" else "مانده"
                            Text(deltaLabel, fontSize = 11.sp, color = Color(0xFF64748B))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = diffString,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = if (diffHours >= 0) Color(0xFF81C784) else Color(0xFFEF5350)
                            )
                        }
                    }

                    val progressPercentage = if (currentMonthTarget > 0) (workedHoursDouble / currentMonthTarget).toFloat() else 0f
                    val progressClamped = progressPercentage.coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(50))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressClamped)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF00E5FF), Color(0xFF00B0FF))
                                    ),
                                    RoundedCornerShape(50)
                                )
                        )
                    }
                }
            }
        }

        item {
            // Main Clock In/Out Pulsing Controller
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                val isWorking = activeRecord != null
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )

                Box(
                    modifier = Modifier.size(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isWorking) {
                        Box(
                            modifier = Modifier
                                .size(190.dp)
                                .scale(pulseScale)
                                .background(
                                    Color(0xFF00B0FF).copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(100.dp)
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(215.dp)
                                .scale(pulseScale * 0.95f)
                                .background(
                                    Color(0xFF00B0FF).copy(alpha = 0.04f),
                                    shape = RoundedCornerShape(100.dp)
                                )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(175.dp)
                            .border(8.dp, Color(0xFF121212), RoundedCornerShape(100.dp))
                            .clip(RoundedCornerShape(100.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = if (isWorking) {
                                        listOf(Color(0xFFEF5350), Color(0xFFC62828))
                                    } else {
                                        listOf(Color(0xFF00B0FF), Color(0xFF0081CB))
                                    }
                                )
                            )
                            .clickable { viewModel.toggleShift() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (isWorking) "اتمام کار" else "شروع کار",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isWorking) "PRESS TO STOP" else "PRESS TO START",
                                fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = lastExitText,
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                val dayName = JalaliCalendarHelper.getDayOfWeekPersian(today.year, today.month, today.day)
                val monthName = JalaliCalendarHelper.getPersianMonthName(today.month)
                Text(
                    text = "$dayName ${today.day} $monthName ${today.year}",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )
            }
        }

        item {
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 4.dp))
        }

        item {
            ManualRecordSection(viewModel)
        }

        item {
            Text(
                text = "سوابق اخیر (۱۵ مورد آخر)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (allRecords.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "هیچ سابقه ای ثبت نشده است.",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(allRecords.take(15)) { record ->
                RecordItem(record, onDelete = { viewModel.deleteRecord(record) })
            }
        }
    }
}

@Composable
fun ManualRecordSection(viewModel: TimeTrackingViewModel) {
    val context = LocalContext.current
    
    // Get current Persian year/month/day as defaults
    val today = JalaliCalendarHelper.fromEpochMillis(System.currentTimeMillis())
    
    var year by remember { mutableStateOf(today.year.toString()) }
    var month by remember { mutableStateOf(today.month.toString()) }
    var day by remember { mutableStateOf(today.day.toString()) }
    var startHour by remember { mutableStateOf("07") }
    var startMinute by remember { mutableStateOf("00") }
    var endHour by remember { mutableStateOf("14") }
    var endMinute by remember { mutableStateOf("00") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "افزودن دستی کارکرد (اصلاحات)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00B0FF)
            )

            // Date Selection Inputs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text("سال", fontSize = 11.sp, color = Color(0xFF94A3B8)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1.2f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00B0FF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                    )
                )
                OutlinedTextField(
                    value = month,
                    onValueChange = { month = it },
                    label = { Text("ماه", fontSize = 11.sp, color = Color(0xFF94A3B8)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00B0FF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                    )
                )
                OutlinedTextField(
                    value = day,
                    onValueChange = { day = it },
                    label = { Text("روز", fontSize = 11.sp, color = Color(0xFF94A3B8)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00B0FF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                    )
                )
            }

            // Time Selection Inputs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Start
                OutlinedTextField(
                    value = startHour,
                    onValueChange = { startHour = it },
                    label = { Text("ساعت شروع", fontSize = 10.sp, color = Color(0xFF94A3B8)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00B0FF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                    )
                )
                OutlinedTextField(
                    value = startMinute,
                    onValueChange = { startMinute = it },
                    label = { Text("دقیقه شروع", fontSize = 10.sp, color = Color(0xFF94A3B8)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00B0FF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // End
                OutlinedTextField(
                    value = endHour,
                    onValueChange = { endHour = it },
                    label = { Text("ساعت پایان", fontSize = 10.sp, color = Color(0xFF94A3B8)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00B0FF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                    )
                )
                OutlinedTextField(
                    value = endMinute,
                    onValueChange = { endMinute = it },
                    label = { Text("دقیقه پایان", fontSize = 10.sp, color = Color(0xFF94A3B8)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00B0FF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                    )
                )
            }

            // Save Manual Entry Button
            Button(
                onClick = {
                    val y = year.toIntOrNull() ?: today.year
                    val m = month.toIntOrNull() ?: today.month
                    val d = day.toIntOrNull() ?: today.day
                    val sh = startHour.toIntOrNull() ?: 7
                    val sm = startMinute.toIntOrNull() ?: 0
                    val eh = endHour.toIntOrNull() ?: 14
                    val em = endMinute.toIntOrNull() ?: 0

                    if (m < 1 || m > 12 || d < 1 || d > JalaliCalendarHelper.getDaysInMonth(y, m)) {
                        Toast.makeText(context, "تاریخ وارد شده نامعتبر است", Toast.LENGTH_LONG).show()
                    } else if (sh < 0 || sh > 23 || sm < 0 || sm > 59 || eh < 0 || eh > 23 || em < 0 || em > 59) {
                        Toast.makeText(context, "ساعت وارد شده نامعتبر است", Toast.LENGTH_LONG).show()
                    } else {
                        viewModel.addManualRecord(y, m, d, sh, sm, eh, em)
                        Toast.makeText(context, "سابقه کارکرد با موفقیت ثبت شد", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF))
            ) {
                Text("ثبت کارکرد دستی", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun RecordItem(record: TimeRecord, onDelete: () -> Unit) {
    val dayOfWeek = JalaliCalendarHelper.getDayOfWeekPersian(record.jalaliYear, record.jalaliMonth, record.jalaliDay)
    val startTime = SimpleDateFormat("HH:mm", Locale.US).format(Date(record.startTimeMillis))
    val endTime = if (record.endTimeMillis != null) {
        SimpleDateFormat("HH:mm", Locale.US).format(Date(record.endTimeMillis))
    } else {
        "در حال ثبت"
    }

    val durationText = if (record.endTimeMillis != null) {
        val diffMs = record.endTimeMillis - record.startTimeMillis
        val hours = diffMs / (1000 * 60 * 60)
        val mins = (diffMs % (1000 * 60 * 60)) / (1000 * 60)
        String.format(Locale.US, "%d ساعت و %d دقیقه", hours, mins)
    } else {
        "نامشخص"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "$dayOfWeek ${record.jalaliYear}/${record.jalaliMonth}/${record.jalaliDay}" +
                            if (record.isManual) " (ثبت دستی)" else "",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "ساعت حضور: $startTime الی $endTime",
                    fontSize = 13.sp,
                    color = Color.LightGray
                )
                Text(
                    text = "مدت کارکرد: $durationText",
                    fontSize = 12.sp,
                    color = Color(0xFF00B0FF),
                    fontWeight = FontWeight.SemiBold
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "حذف سابقه",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun ReportsTab(viewModel: TimeTrackingViewModel) {
    val context = LocalContext.current
    val today = JalaliCalendarHelper.fromEpochMillis(System.currentTimeMillis())

    // Month Obligatory Settings State
    var targetYear by remember { mutableStateOf(today.year.toString()) }
    var targetMonth by remember { mutableStateOf(today.month.toString()) }
    var targetHoursInput by remember { mutableStateOf("170") }

    // Date Range Report Query State
    var startY by remember { mutableStateOf(today.year.toString()) }
    var startM by remember { mutableStateOf(today.month.toString()) }
    var startD by remember { mutableStateOf("1") }

    var endY by remember { mutableStateOf(today.year.toString()) }
    var endM by remember { mutableStateOf(today.month.toString()) }
    var endD by remember { mutableStateOf(JalaliCalendarHelper.getDaysInMonth(today.year, today.month).toString()) }

    val reportWorkedHours by viewModel.reportWorkedHours.collectAsStateWithLifecycle()
    val reportObligatoryHours by viewModel.reportObligatoryHours.collectAsStateWithLifecycle()

    // Excel Export State
    var exportYear by remember { mutableStateOf(today.year.toString()) }
    var exportMonth by remember { mutableStateOf(today.month.toString()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        // Section 1: Monthly Obligatory Setting
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "تعیین ساعت موظفی ماهانه",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00B0FF)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = targetYear,
                            onValueChange = { targetYear = it },
                            label = { Text("سال", color = Color(0xFF94A3B8), fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00B0FF),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                        OutlinedTextField(
                            value = targetMonth,
                            onValueChange = { targetMonth = it },
                            label = { Text("ماه", color = Color(0xFF94A3B8), fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00B0FF),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                        OutlinedTextField(
                            value = targetHoursInput,
                            onValueChange = { targetHoursInput = it },
                            label = { Text("ساعت موظفی", color = Color(0xFF94A3B8), fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1.5f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00B0FF),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }

                    Button(
                        onClick = {
                            val y = targetYear.toIntOrNull() ?: today.year
                            val m = targetMonth.toIntOrNull() ?: today.month
                            val h = targetHoursInput.toDoubleOrNull() ?: 170.0
                            if (m in 1..12) {
                                viewModel.saveObligatoryHours(y, m, h)
                                Toast.makeText(context, "ساعت موظفی با موفقیت ثبت شد", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "ماه نامعتبر است", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF))
                    ) {
                        Text("ثبت موظفی ماه", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // Section 2: Flexible Reporting Screen (Selecting Custom Range)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "تنظیم بازه گزارش کارکرد",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00B0FF)
                    )

                    Text("تاریخ شروع بازه شمسی:", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = startY,
                            onValueChange = { startY = it },
                            label = { Text("سال", color = Color(0xFF94A3B8), fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00B0FF),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                        OutlinedTextField(
                            value = startM,
                            onValueChange = { startM = it },
                            label = { Text("ماه", color = Color(0xFF94A3B8), fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00B0FF),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                        OutlinedTextField(
                            value = startD,
                            onValueChange = { startD = it },
                            label = { Text("روز", color = Color(0xFF94A3B8), fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00B0FF),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }

                    Text("تاریخ پایان بازه شمسی:", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = endY,
                            onValueChange = { endY = it },
                            label = { Text("سال", color = Color(0xFF94A3B8), fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00B0FF),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                        OutlinedTextField(
                            value = endM,
                            onValueChange = { endM = it },
                            label = { Text("ماه", color = Color(0xFF94A3B8), fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00B0FF),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                        OutlinedTextField(
                            value = endD,
                            onValueChange = { endD = it },
                            label = { Text("روز", color = Color(0xFF94A3B8), fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00B0FF),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }

                    Button(
                        onClick = {
                            val sy = startY.toIntOrNull() ?: today.year
                            val sm = startM.toIntOrNull() ?: today.month
                            val sd = startD.toIntOrNull() ?: 1

                            val ey = endY.toIntOrNull() ?: today.year
                            val em = endM.toIntOrNull() ?: today.month
                            val ed = endD.toIntOrNull() ?: 30

                            viewModel.generateReport(sy, sm, sd, ey, em, ed)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                    ) {
                        Text("محاسبه گزارش کارکرد بازه", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Summary Results Box
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "خلاصه وضعیت کارکرد در بازه انتخابی",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("کل ساعات کارکرد واقعی:", fontSize = 14.sp, color = Color(0xFF94A3B8))
                        Text(
                            text = String.format(Locale.US, "%.2f", reportWorkedHours) + " ساعت",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("کل ساعات موظفی این بازه:", fontSize = 14.sp, color = Color(0xFF94A3B8))
                        Text(
                            text = String.format(Locale.US, "%.2f", reportObligatoryHours) + " ساعت",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    val delta = reportWorkedHours - reportObligatoryHours
                    val deltaLabel = if (delta >= 0) "اضافه کاری مابه التفاوت:" else "کسری کار مابه التفاوت:"
                    val deltaColor = if (delta >= 0) Color(0xFF81C784) else Color(0xFFEF5350)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(deltaLabel, fontSize = 14.sp, color = Color(0xFF94A3B8))
                        Text(
                            text = String.format(Locale.US, "%+.2f", delta) + " ساعت",
                            fontWeight = FontWeight.Bold,
                            color = deltaColor
                        )
                    }
                }
            }
        }

        // Section 3: Smart Excel/CSV Export
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "خروجی هوشمند اکسل (CSV)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00B0FF)
                    )
                    Text(
                        text = "گزارش ماهانه خروجی شامل ساعات ورود و خروج همپوشانی شده به صورت کاملاً فارسی و سازگار با سیستم های مالی اداری است.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = exportYear,
                            onValueChange = { exportYear = it },
                            label = { Text("سال شمسی", color = Color(0xFF94A3B8), fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00B0FF),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                        OutlinedTextField(
                            value = exportMonth,
                            onValueChange = { exportMonth = it },
                            label = { Text("ماه", color = Color(0xFF94A3B8), fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00B0FF),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }

                    Button(
                        onClick = {
                            val ey = exportYear.toIntOrNull() ?: today.year
                            val em = exportMonth.toIntOrNull() ?: today.month
                            if (em in 1..12) {
                                viewModel.exportMonthCsv(context, ey, em) { uri ->
                                    if (uri != null) {
                                        Toast.makeText(
                                            context,
                                            "فایل با موفقیت صادر شد و در پوشه Downloads ذخیره گردید",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        Toast.makeText(context, "خطا در برون بری فایل", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "ماه نامعتبر شمسی است", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF))
                    ) {
                        Text("تولید و دانلود گزارش کارکرد ماه", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
