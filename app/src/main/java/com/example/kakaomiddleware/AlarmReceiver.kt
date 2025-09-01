package com.example.kakaomiddleware

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val currentTime = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedTime = sdf.format(Date(currentTime))
        
        // 10분 간격 정확한 시각에 로그 찍기
        Log.d("AlarmReceiver", "⏰ 10분 간격 알람 로그: $formattedTime")
        
        // 다음 알람을 다시 스케줄링
        scheduleNextAlarm(context)
    }

    companion object {
        const val ALARM_REQUEST_CODE = 100

        fun scheduleNextAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 다음 10분 단위 시간 계산
            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                
                // 현재 분을 10으로 나눈 나머지를 구해서 다음 10분 단위로 맞춤
                val currentMinute = get(Calendar.MINUTE)
                val remainder = currentMinute % 10
                
                // 다음 10분 단위로 설정 (00, 10, 20, 30, 40, 50분)
                add(Calendar.MINUTE, 10 - remainder)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val nextAlarmTime = sdf.format(Date(calendar.timeInMillis))
            
            // Doze 모드와 앱 대기 모드에서도 정확한 시간에 알람이 동작하도록 설정
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Android 6.0 이상에서 Doze 모드 대응
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                    Log.d("AlarmReceiver", "📅 다음 알람 설정 완료 (setExactAndAllowWhileIdle): $nextAlarmTime")
                } else {
                    // Android 5.1 이하
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                    Log.d("AlarmReceiver", "📅 다음 알람 설정 완료 (setExact): $nextAlarmTime")
                }
            } catch (e: SecurityException) {
                Log.e("AlarmReceiver", "알람 설정 권한 없음: ${e.message}")
            }
        }

        fun startPeriodicAlarm(context: Context) {
            // 기존 알람이 있으면 먼저 취소
            cancelAlarm(context)
            
            // 새 알람 스케줄링 시작
            scheduleNextAlarm(context)
            
            Log.d("AlarmReceiver", "🚀 10분 간격 주기적 알람이 시작되었습니다.")
        }

        fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            
            // PendingIntent가 존재하면 알람 취소
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d("AlarmReceiver", "⏹️ 주기적 알람이 취소되었습니다.")
            } else {
                Log.d("AlarmReceiver", "취소할 알람이 없습니다.")
            }
        }
        
        fun isAlarmActive(context: Context): Boolean {
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            return pendingIntent != null
        }
    }
}