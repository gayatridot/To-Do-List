package com.example.todolist

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


data class TodoItem(
    var task: String,
    var timePart: String,
    var datePart: String,
    val startTimestamp: Long
)

object TaskStorage {
    private const val PREFS_NAME = "TodoPrefs"
    private const val TASKS_KEY = "tasks"

    @SuppressLint("UseKtx")
    fun saveTasks(context: Context, tasks: List<TodoItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        val json = Gson().toJson(tasks)
        prefs.putString(TASKS_KEY, json)
        prefs.apply()
    }

    fun loadTasks(context: Context): MutableList<TodoItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(TASKS_KEY, null)
        return if (json != null) {
            Gson().fromJson(json, object : TypeToken<MutableList<TodoItem>>() {}.type)
        } else {
            mutableListOf()
        }
    }
}

object AlarmHelper {
    fun setReminder(context: Context, item: TodoItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Toast.makeText(context, "Permission needed for reminders.", Toast.LENGTH_SHORT).show()
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            return
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("TASK_NAME", item.task)
            putExtra("NOTIFICATION_ID", item.startTimestamp.toInt())
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, item.startTimestamp.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.startTimestamp, pendingIntent)
        Toast.makeText(context, "Reminder set for '${item.task}'", Toast.LENGTH_SHORT).show()
    }

    fun cancelReminder(context: Context, item: TodoItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, item.startTimestamp.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskName = intent.getStringExtra("TASK_NAME") ?: "Task Reminder"
        val notificationId = intent.getIntExtra("NOTIFICATION_ID", 0)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("task_reminders", "Task Reminders", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, "task_reminders")
            .setSmallIcon(R.drawable.add)
            .setContentTitle("Task Due!")
            .setContentText(taskName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var todoList: MutableList<TodoItem>
    private lateinit var adapter: TodoAdapter

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) Toast.makeText(this, "Notifications disabled.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        askForNotificationPermission()

        todoList = TaskStorage.loadTasks(this)

        val listView: ListView = findViewById(R.id.todoListView)

        adapter = TodoAdapter(this, todoList,
            onEdit = { position -> showEditDialog(position) },
            onDelete = { position ->
                val itemToRemove = todoList[position]
                AlarmHelper.cancelReminder(this, itemToRemove)
                todoList.removeAt(position)
                sortAndRefresh()
            }
        )

        listView.adapter = adapter
        sortAndRefresh()

        findViewById<ImageView>(R.id.add).setOnClickListener { showAddTodoDialog() }
        findViewById<ImageView>(R.id.btnChatbot).setOnClickListener { openChatbotFragment() }
    }

    private fun openChatbotFragment() {
        val fragment = ChatbotFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun sortAndRefresh() {
        todoList.sortBy { it.startTimestamp }
        adapter.notifyDataSetChanged()
        TaskStorage.saveTasks(this, todoList)
    }

    @SuppressLint("SetTextI18n")
    private fun showAddTodoDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("New Task")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        val input = EditText(this).apply { hint = "Task name..." }
        layout.addView(input)

        val infoDisplay = TextView(this).apply {
            text = "Timing: Default (Created time)"
            setPadding(0, 20, 0, 20)
        }
        layout.addView(infoDisplay)

        var finalTimestamp: Long = 0
        var finalDate = ""
        var finalTime = ""
        var isCustomTimeSet = false

        val btnPick = Button(this).apply { text = "Set Custom Date & Time Range" }
        layout.addView(btnPick)

        btnPick.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                val selectedCal = Calendar.getInstance().apply { set(y, m, d) }
                TimePickerDialog(this, { _, h1, m1 ->
                    selectedCal.set(Calendar.HOUR_OF_DAY, h1)
                    selectedCal.set(Calendar.MINUTE, m1)
                    finalTimestamp = selectedCal.timeInMillis
                    TimePickerDialog(this, { _, h2, m2 ->
                        val startTime = formatToAmPm(h1, m1)
                        val endTime = formatToAmPm(h2, m2)
                        finalDate = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(selectedCal.time)
                        finalTime = "$startTime - $endTime"
                        infoDisplay.text = "Schedule: $finalDate\n$finalTime"
                        isCustomTimeSet = true
                    }, h1, m1, false).show()
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        builder.setView(layout)
        builder.setPositiveButton("Save") { _, _ ->
            val name = input.text.toString()
            if (name.isNotEmpty()) {
                if (!isCustomTimeSet) {
                    val now = Calendar.getInstance()
                    finalTimestamp = now.timeInMillis
                    finalDate = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(now.time)
                    val sTime = formatToAmPm(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
                    finalTime = "$sTime - 00:00"
                }
                val newItem = TodoItem(name, finalTime, finalDate, finalTimestamp)
                todoList.add(newItem)
                AlarmHelper.setReminder(this, newItem)
                sortAndRefresh()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    @SuppressLint("SetTextI18n")
    private fun showEditDialog(position: Int) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Edit Task")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        val currentItem = todoList[position]
        val input = EditText(this).apply { setText(currentItem.task) }
        layout.addView(input)

        val timeDisplay = TextView(this).apply {
            text = "Current: ${currentItem.datePart} | ${currentItem.timePart}"
            setPadding(0, 20, 0, 20)
        }
        layout.addView(timeDisplay)

        var newTimestamp = currentItem.startTimestamp
        var newDate = currentItem.datePart
        var newTime = currentItem.timePart

        val btnUpdatePick = Button(this).apply { text = "Change Date & Time" }
        layout.addView(btnUpdatePick)

        btnUpdatePick.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                val selectedCal = Calendar.getInstance().apply { set(y, m, d) }
                TimePickerDialog(this, { _, h1, m1 ->
                    selectedCal.set(Calendar.HOUR_OF_DAY, h1)
                    selectedCal.set(Calendar.MINUTE, m1)
                    newTimestamp = selectedCal.timeInMillis
                    TimePickerDialog(this, { _, h2, m2 ->
                        val sTime = formatToAmPm(h1, m1)
                        val eTime = formatToAmPm(h2, m2)
                        newDate = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(selectedCal.time)
                        newTime = "$sTime - $eTime"
                        timeDisplay.text = "New: $newDate | $newTime"
                    }, h1, m1, false).show()
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        builder.setView(layout)
        builder.setPositiveButton("Update") { _, _ ->
            val updatedName = input.text.toString()
            if (updatedName.isNotEmpty()) {
                AlarmHelper.cancelReminder(this, currentItem)
                val updatedItem = TodoItem(updatedName, newTime, newDate, newTimestamp)
                todoList[position] = updatedItem
                AlarmHelper.setReminder(this, updatedItem)
                sortAndRefresh()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun formatToAmPm(hour: Int, minute: Int): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
    }

}
