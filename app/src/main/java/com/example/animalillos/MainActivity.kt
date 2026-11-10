package com.example.animalillos

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.animalillos.ui.theme.Agrandir
import com.example.animalillos.ui.theme.AnimalillosTheme
import com.example.animalillos.ui.theme.AristaPro
import com.example.animalillos.ui.theme.OpenSans
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp

private val GreenPrimary = Color(0xFF2D5E18)
private val GreenMuted = Color(0xFF7D9A6D)
private val CreamBg = Color(0xFFFAF8F4)
private val FieldBeige = Color(0xFFEDE8E3)

data class PetReminder(
    val id: Long,
    val name: String,
    val description: String,
    val times: List<String> // "HH:mm"
)

// shortcut: SharedPreferences + org.json, migrate to Room if list exceeds hundreds of items
object ReminderStore {
    private const val PREFS = "animalillos_prefs"
    private const val KEY = "reminders"

    fun encode(list: List<PetReminder>): String {
        val arr = JSONArray()
        for (r in list) {
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("name", r.name)
                put("description", r.description)
                put("times", JSONArray(r.times))
            })
        }
        return arr.toString()
    }

    fun decode(raw: String): List<PetReminder> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val tArr = o.getJSONArray("times")
            PetReminder(
                id = o.getLong("id"),
                name = o.getString("name"),
                description = o.optString("description", ""),
                times = (0 until tArr.length()).map { tArr.getString(it) }
            )
        }
    }

    fun load(context: Context): List<PetReminder> =
        decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]")

    fun save(context: Context, list: List<PetReminder>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, encode(list))
            .apply()
    }
}

class MainActivity : ComponentActivity() {
    private var screen by mutableIntStateOf(1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(AlarmReceiver.EXTRA_OPEN_ALARMS, false) == true) {
            screen = 3
        }
        enableEdgeToEdge()
        setContent {
            AnimalillosTheme {
                val context = LocalContext.current
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { _ -> }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                val reminders = remember { mutableStateListOf<PetReminder>().apply { addAll(ReminderStore.load(context)) } }
                var editingReminder by remember { mutableStateOf<PetReminder?>(null) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = if (screen == 1) GreenPrimary else CreamBg
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        when (screen) {
                            1 -> StartScreen(onStart = { screen = 2 })
                            2 -> MenuScreen(
                                initial = editingReminder,
                                hasSavedReminders = reminders.isNotEmpty(),
                                onSavePet = { pet ->
                                    editingReminder?.let { old ->
                                        cancelAlarms(old)
                                        val idx = reminders.indexOfFirst { it.id == old.id }
                                        if (idx >= 0) reminders[idx] = pet else reminders.add(pet)
                                    } ?: reminders.add(pet)
                                    ReminderStore.save(context, reminders)
                                    scheduleAlarms(pet)
                                    editingReminder = null
                                    screen = 3
                                },
                                onGoToAlarms = {
                                    editingReminder = null
                                    screen = 3
                                }
                            )
                            3 -> AlarmsScreen(
                                reminders = reminders,
                                onAddOrBackToMenu = {
                                    editingReminder = null
                                    screen = 2
                                },
                                onEdit = { pet ->
                                    editingReminder = pet
                                    screen = 2
                                },
                                onDelete = { pet ->
                                    cancelAlarms(pet)
                                    reminders.removeAll { it.id == pet.id }
                                    ReminderStore.save(context, reminders)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(AlarmReceiver.EXTRA_OPEN_ALARMS, false)) {
            screen = 3
        }
    }

    private fun alarmPendingIntent(pet: PetReminder, time: String): PendingIntent {
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_PET_NAME, pet.name)
            putExtra(AlarmReceiver.EXTRA_DESCRIPTION, pet.description.ifBlank { "Es hora de alimentar a ${pet.name}" })
        }
        val code = "${pet.id}_$time".hashCode()
        return PendingIntent.getBroadcast(
            this,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // shortcut: schedules next occurrence only, reschedule inside AlarmReceiver for daily repeat without opening app
    private fun scheduleAlarms(pet: PetReminder) {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        try {
            for (time in pet.times) {
                val (hour, minute) = time.split(":").map { it.toInt() }
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                }
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    cal.timeInMillis,
                    alarmPendingIntent(pet, time)
                )
            }
            Toast.makeText(this, "Recordatorio guardado para ${pet.name}", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Guardado (otorga permiso de alarmas exactas en ajustes)", Toast.LENGTH_LONG).show()
        }
    }

    private fun cancelAlarms(pet: PetReminder) {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        for (time in pet.times) {
            alarmManager.cancel(alarmPendingIntent(pet, time))
        }
    }
}

private val CardShadow = Color(0xFFB8C4A8)
private val NoteBorder = Color(0xFFE8E4DE)

@Composable
private fun WhiteCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = CardShadow, spotColor = CardShadow)
            .background(Color.White, RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 16.dp)
    ) { content() }
}

@Composable
private fun Pill(
    text: String,
    background: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 34.dp,
    radius: Dp = 50.dp,
    bold: Boolean = true,
    align: Alignment = Alignment.Center,
    border: Color? = null
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(background, shape)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .padding(horizontal = 14.dp),
        contentAlignment = align
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    background: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    border: Color? = null,
    textAlign: TextAlign = TextAlign.Start,
    singleLine: Boolean = true,
    minHeight: Dp = 34.dp,
    radius: Dp = 50.dp
) {
    val shape = RoundedCornerShape(radius)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = TextStyle(color = textColor, fontSize = 13.sp, textAlign = textAlign, fontFamily = OpenSans),
        cursorBrush = SolidColor(GreenPrimary),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .background(background, shape)
                    .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = textColor.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        textAlign = textAlign,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                inner()
            }
        }
    )
}

@Composable
private fun BigOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.5.dp, GreenPrimary),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color.Black),
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .shadow(6.dp, RoundedCornerShape(50), ambientColor = CardShadow, spotColor = CardShadow)
    ) {
        Text(text = text, fontSize = 17.sp)
    }
}

@Composable
fun StartScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GreenPrimary)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = "Time to",
                color = Color.White,
                fontSize = 60.sp,
                lineHeight = 64.sp,
                fontFamily = AristaPro
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.paw),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Eat!",
                    color = Color.White,
                    fontSize = 60.sp,
                    lineHeight = 64.sp,
                    fontFamily = AristaPro
                )
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = "Vamos a alimentar a\ntu mascota",
            color = Color.White,
            fontSize = 16.sp,
            fontFamily = Agrandir,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = GreenPrimary),
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .height(46.dp)
        ) {
            Text(text = "Iniciar", fontSize = 22.sp, fontFamily = AristaPro, color = Color(0xFF1B3A0E))
        }
    }
}

@Composable
fun MenuScreen(
    initial: PetReminder?,
    hasSavedReminders: Boolean,
    onSavePet: (PetReminder) -> Unit,
    onGoToAlarms: () -> Unit
) {
    val context = LocalContext.current
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var description by remember(initial) { mutableStateOf(initial?.description ?: "") }
    val times = remember(initial) {
        mutableStateListOf<String>().apply {
            addAll(initial?.times ?: listOf("08:00"))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        WhiteCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PillTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Nombre de la mascota",
                    background = FieldBeige,
                    textColor = Color(0xFF3A3A3A)
                )

                times.forEachIndexed { index, time ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .background(GreenPrimary, RoundedCornerShape(50))
                            .clickable {
                                val parts = time.split(":").map { it.toInt() }
                                TimePickerDialog(context, { _, h, m ->
                                    times[index] = "%02d:%02d".format(h, m)
                                }, parts[0], parts[1], true).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$time hrs", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "✕",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .clickable { times.removeAt(index) }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                Pill(
                    text = "+ Agregar Horario",
                    background = GreenMuted,
                    textColor = Color.White,
                    modifier = Modifier.clickable {
                        val now = Calendar.getInstance()
                        TimePickerDialog(
                            context,
                            { _, h, m ->
                                val formatted = "%02d:%02d".format(h, m)
                                if (!times.contains(formatted)) times.add(formatted)
                            },
                            now.get(Calendar.HOUR_OF_DAY),
                            now.get(Calendar.MINUTE),
                            true
                        ).show()
                    }
                )

                PillTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = "Agregar Nota",
                    background = Color.White,
                    textColor = GreenPrimary,
                    border = NoteBorder,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        BigOutlinedButton(
            text = if (initial == null) "+ Agregar mascota" else "Guardar cambios",
            onClick = {
                if (name.isBlank()) {
                    Toast.makeText(context, "Ingresa el nombre de la mascota", Toast.LENGTH_SHORT).show()
                    return@BigOutlinedButton
                }
                if (times.isEmpty()) {
                    Toast.makeText(context, "Agrega al menos un horario", Toast.LENGTH_SHORT).show()
                    return@BigOutlinedButton
                }
                onSavePet(
                    PetReminder(
                        id = initial?.id ?: System.currentTimeMillis(),
                        name = name.trim(),
                        description = description.trim(),
                        times = times.sorted()
                    )
                )
            }
        )

        if (hasSavedReminders) {
            TextButton(onClick = onGoToAlarms, modifier = Modifier.padding(top = 8.dp)) {
                Text("Ver alarmas guardadas", color = GreenPrimary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun AlarmsScreen(
    reminders: List<PetReminder>,
    onAddOrBackToMenu: () -> Unit,
    onEdit: (PetReminder) -> Unit,
    onDelete: (PetReminder) -> Unit
) {
    BackHandler(onBack = onAddOrBackToMenu)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBg)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        if (reminders.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("No hay recordatorios guardados", color = GreenMuted, fontSize = 16.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(reminders, key = { it.id }) { pet ->
                    WhiteCard {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Pill(
                                text = pet.name,
                                background = FieldBeige,
                                textColor = Color(0xFF3A3A3A),
                                height = 40.dp,
                                bold = false,
                                align = Alignment.CenterStart
                            )

                            pet.times.forEach { time ->
                                Pill(
                                    text = "$time hrs",
                                    background = GreenPrimary,
                                    textColor = Color.White,
                                    height = 46.dp,
                                    radius = 18.dp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 110.dp)
                                    .border(1.dp, NoteBorder, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = if (pet.description.isNotBlank()) "Nota Agregada\n${pet.description}" else "Sin nota",
                                    color = GreenPrimary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { onEdit(pet) }) {
                                    Text("Editar", color = GreenPrimary, fontSize = 13.sp)
                                }
                                TextButton(onClick = { onDelete(pet) }) {
                                    Text("Eliminar", color = Color(0xFFB3261E), fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        BigOutlinedButton(text = "+ Agregar mascota", onClick = onAddOrBackToMenu)
    }
}
