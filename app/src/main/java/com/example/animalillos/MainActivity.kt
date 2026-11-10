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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.animalillos.ui.theme.AnimalillosTheme
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

                var screen by remember { mutableIntStateOf(1) }
                val reminders = remember { mutableStateListOf<PetReminder>().apply { addAll(ReminderStore.load(context)) } }
                var editingReminder by remember { mutableStateOf<PetReminder?>(null) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
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

@Composable
fun StartScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GreenPrimary)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Time to\n🐾 Eat!",
            color = Color.White,
            fontSize = 54.sp,
            lineHeight = 60.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(36.dp))
        Text(
            text = "Vamos a alimentar a\ntu mascota",
            color = Color.White,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = GreenPrimary
            ),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(56.dp)
        ) {
            Text(text = "Iniciar", fontSize = 22.sp, fontWeight = FontWeight.Medium)
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
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Nombre de la mascota") },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = FieldBeige,
                        unfocusedContainerColor = FieldBeige,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                times.forEachIndexed { index, time ->
                    Button(
                        onClick = {
                            val parts = time.split(":").map { it.toInt() }
                            TimePickerDialog(context, { _, h, m ->
                                times[index] = "%02d:%02d".format(h, m)
                            }, parts[0], parts[1], true).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GreenPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                            Text("$time hrs", fontWeight = FontWeight.Bold)
                            Text(
                                text = "✕",
                                modifier = Modifier
                                    .clickable { times.removeAt(index) }
                                    .padding(horizontal = 8.dp)
                            )
                        }
                    }
                }

                Button(
                    onClick = {
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
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GreenMuted,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Agregar Horario", fontWeight = FontWeight.SemiBold)
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = {
                        Text(
                            "Nota / Descripción\nEj: Medicamento 1, 2 tazas de comida",
                            color = GreenPrimary.copy(alpha = 0.7f)
                        )
                    },
                    minLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = GreenPrimary,
                        unfocusedTextColor = GreenPrimary,
                        focusedBorderColor = FieldBeige,
                        unfocusedBorderColor = FieldBeige
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = {
                if (name.isBlank()) {
                    Toast.makeText(context, "Ingresa el nombre de la mascota", Toast.LENGTH_SHORT).show()
                    return@OutlinedButton
                }
                if (times.isEmpty()) {
                    Toast.makeText(context, "Agrega al menos un horario", Toast.LENGTH_SHORT).show()
                    return@OutlinedButton
                }
                onSavePet(
                    PetReminder(
                        id = initial?.id ?: System.currentTimeMillis(),
                        name = name.trim(),
                        description = description.trim(),
                        times = times.sorted()
                    )
                )
            },
            shape = RoundedCornerShape(50),
            border = BorderStroke(2.dp, GreenPrimary),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = if (initial == null) "+ Añadir mascota" else "Guardar cambios",
                fontSize = 18.sp
            )
        }

        if (hasSavedReminders) {
            TextButton(onClick = onGoToAlarms, modifier = Modifier.padding(top = 8.dp)) {
                Text("Ver alarmas guardadas", color = GreenPrimary)
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
            .padding(24.dp)
    ) {
        if (reminders.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("No hay recordatorios guardados", color = GreenMuted, fontSize = 18.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(reminders, key = { it.id }) { pet ->
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(FieldBeige, RoundedCornerShape(50))
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(text = pet.name, color = Color.DarkGray, fontWeight = FontWeight.Medium)
                            }

                            pet.times.forEach { time ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(GreenPrimary, RoundedCornerShape(50))
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "$time hrs", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (pet.description.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White, RoundedCornerShape(50))
                                        .padding(vertical = 8.dp, horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = pet.description, color = GreenPrimary, fontSize = 14.sp)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { onEdit(pet) }) {
                                    Text("Editar", color = GreenPrimary)
                                }
                                TextButton(onClick = { onDelete(pet) }) {
                                    Text("Eliminar", color = Color(0xFFB3261E))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onAddOrBackToMenu,
            shape = RoundedCornerShape(50),
            border = BorderStroke(2.dp, GreenPrimary),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("+ Agregar mascota", fontSize = 18.sp)
        }
    }
}
