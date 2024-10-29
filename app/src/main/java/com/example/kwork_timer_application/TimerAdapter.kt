package com.example.kwork_timer_application

import android.content.Context
import android.content.Intent
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
class TimerAdapter(
    private val timers: MutableList<TimerItemEntity>,
    private val context: Context,
    private val onDeleteTimer: (TimerItemEntity) -> Unit // Callback для удаления таймера
) : RecyclerView.Adapter<TimerAdapter.TimerViewHolder>() {

    private val activeTimers = mutableMapOf<Int, CountDownTimer?>()

    class TimerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timerName: TextView = itemView.findViewById(R.id.timer_name)
        val timerTime: TextView = itemView.findViewById(R.id.timer_time)
        val buttonStart: Button = itemView.findViewById(R.id.button_start)
        val buttonPause: Button = itemView.findViewById(R.id.button_pause)
        val buttonStop: Button = itemView.findViewById(R.id.button_stop)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.timer_item, parent, false)
        return TimerViewHolder(view)
    }

    override fun onBindViewHolder(holder: TimerViewHolder, position: Int) {
        val timer = timers[position]
        holder.timerName.text = timer.name
        holder.timerTime.text = formatTime(timer.remainingTimeMillis)

        var isRunning = activeTimers.containsKey(timer.id)

        holder.buttonStart.setOnClickListener {
            if (!isRunning) {
                isRunning = true
                val countDownTimer = object : CountDownTimer(timer.remainingTimeMillis, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        timer.remainingTimeMillis = millisUntilFinished
                        holder.timerTime.text = formatTime(millisUntilFinished)
                    }

                    override fun onFinish() {
                        holder.timerTime.text = "00:00:00:00"
                        isRunning = false
                        activeTimers.remove(timer.id)
                    }
                }.start()
                activeTimers[timer.id] = countDownTimer
                val random = (Math.random() * 1000)
                val timerId = random.toInt()
                // Запуск TimerService с передачей оставшегося времени и имени таймера
                val serviceIntent = Intent(context, TimerService::class.java).apply {
                    putExtra("remainingTimeMillis", timer.remainingTimeMillis)
                    putExtra("timerName", timer.name)
                    putExtra("timerId",timerId)
                }
                context.startService(serviceIntent)  // Запускаем сервис
            }
        }

        holder.buttonPause.setOnClickListener {
            activeTimers[timer.id]?.cancel()
            activeTimers.remove(timer.id)
            isRunning = false
            val pauseIntent = Intent(context, TimerService::class.java).apply {
                action = "PAUSE_TIMER" // Определяем действие
                putExtra("timerId", timer.id) // Передаем timerId
            }
            context.startService(pauseIntent)
        }
        holder.buttonStop.setOnClickListener {
            // Создаем AlertDialog для подтверждения удаления
            AlertDialog.Builder(context).apply {
                setTitle("Подтверждение удаления")
                setMessage("Вы уверены, что хотите удалить таймер?")
                setPositiveButton("Да") { _, _ ->
                    // Остановка и удаление таймера
                    activeTimers[timer.id]?.cancel()
                    activeTimers.remove(timer.id)

                    // Остановка сервиса и удаление уведомления
                    val stopIntent = Intent(context, TimerService::class.java).apply {
                        action = "STOP_TIMER" // Определяем действие
                        putExtra("timerId", timer.id) // Передаем timerId
                    }
                    context.startService(stopIntent) // Остановка сервиса

                    // Удаляем таймер из базы данных
                    onDeleteTimer(timer)
                    timers.removeAt(position)
                    notifyItemRemoved(position)
                    notifyItemRangeChanged(position, timers.size)
                    isRunning = false
                }
                setNegativeButton("Нет", null) // Закрыть диалог, если нажата "Нет"
                create()
                show()
            }
        }

    }
    override fun getItemCount() = timers.size
}


fun formatTime(millis: Long): String {
    val seconds = millis / 1000 % 60
    val minutes = millis / 1000 / 60 % 60
    val hours = millis / 1000 / 60 / 60 % 24
    val days = millis / 1000 / 60 / 60 / 24

    return String.format("%02d:%02d:%02d:%02d", days, hours, minutes, seconds)
}
