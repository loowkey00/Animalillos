package com.example.animalillos

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.animalillos.ui.theme.AnimalillosTheme
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnimalillosTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PetReminderScreen(
                        modifier = Modifier.padding(innerPadding),
                        onScheduleAlarm = { petName, description, hour, minute ->
                            scheduleExactAlarm(petName, description, hour, minute)
                        }
                    )
                }
            }
        }
    }

    private fun scheduleExactAlarm(petName: String, description: String, hour: Int, minute: Int) {
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_PET_NAME, petName)
            putExtra(AlarmReceiver.EXTRA_DESCRIPTION, description)
        }

        val uniqueRequestCode = System.currentTimeMillis().toInt()

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            uniqueRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )

            Toast.makeText(
                this,
                "¡Alarma programada para $petName a las %02d:%02d!".format(hour, minute),
                Toast.LENGTH_LONG
            ).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Error: Se requieren permisos para alarmas exactas.", Toast.LENGTH_LONG).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetReminderScreen(
    modifier: Modifier = Modifier,
    onScheduleAlarm: (String, String, Int, Int) -> Unit
) {
    val context = LocalContext.current
    var petName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    val timePickerState = rememberTimePickerState(
        initialHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        initialMinute = Calendar.getInstance().get(Calendar.MINUTE),
        is24Hour = true
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Recordatorio de Comida",
            fontSize = 22.sp,
            style = MaterialTheme.typography.titleLarge
        )

        OutlinedTextField(
            value = petName,
            onValueChange = { petName = it },
            label = { Text("Nombre de la mascota") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Descripción (Ej: Comida seca + vitaminas)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        TimeInput(
            state = timePickerState
        )

        Button(
            onClick = {
                if (petName.isBlank()) {
                    Toast.makeText(context, "Por favor ingresa el nombre de la mascota", Toast.LENGTH_SHORT).show()
                } else {
                    onScheduleAlarm(
                        petName.trim(),
                        description.trim(),
                        timePickerState.hour,
                        timePickerState.minute
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar y Programar Alarma")
        }
    }
}
