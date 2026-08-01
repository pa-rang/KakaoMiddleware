package com.example.kakaomiddleware

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
        const val ALARM_REQUEST_CODE = 100
        private const val CRON_TIMEOUT_MS = 30_000L // 30초
        
        fun scheduleNextAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

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

            // 예정 시간을 Intent에 포함
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("expectedTime", calendar.timeInMillis)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val nextAlarmTime = sdf.format(Date(calendar.timeInMillis))
            
            // Android 12+ (API 31) 정확한 알람 권한 확인
            val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
            
            if (!canScheduleExact) {
                Log.e(TAG, "정확한 알람 권한 없음 - 설정에서 권한을 허용하세요")
                return
            }
            
            // Doze 모드와 앱 대기 모드에서도 정확한 시간에 알람이 동작하도록 설정
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Android 6.0 이상에서 Doze 모드 대응 + 더 정확한 알람
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                    Log.d(TAG, "📅 다음 알람 설정 완료 (setExactAndAllowWhileIdle): $nextAlarmTime")
                } else {
                    // Android 5.1 이하
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                    Log.d(TAG, "📅 다음 알람 설정 완료 (setExact): $nextAlarmTime")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "알람 설정 권한 없음: ${e.message}")
                Log.e(TAG, "해결방법: 설정 > 앱 > KakaoMiddleware > 정확한 알람 허용")
            }
        }

        fun startPeriodicAlarm(context: Context) {
            Log.d(TAG, "🔧 알람 시작 요청 처리 중...")
            
            // 기존 알람이 있으면 먼저 취소
            cancelAlarm(context)
            
            // 새 알람 스케줄링 시작
            scheduleNextAlarm(context)
            
            Log.i(TAG, "🚀 10분 간격 주기적 알람이 시작되었습니다.")
            Log.i(TAG, "📅 다음 알람은 매시 00, 10, 20, 30, 40, 50분에 실행됩니다.")
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
                Log.d(TAG, "⏹️ 주기적 알람이 취소되었습니다.")
            } else {
                Log.d(TAG, "취소할 알람이 없습니다.")
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

    override fun onReceive(context: Context, intent: Intent) {
        val currentTime = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedTime = sdf.format(Date(currentTime))
        
        // 예정된 시간과 실제 실행 시간 비교
        val expectedTime = intent.getLongExtra("expectedTime", 0L)
        val delay = if (expectedTime > 0) {
            val delayMs = abs(currentTime - expectedTime)
            val delaySeconds = delayMs / 1000.0
            String.format("%.1f초", delaySeconds)
        } else {
            "N/A"
        }
        
        // 1. 기존 10분 간격 로그 출력 (유지)
        Log.d(TAG, "⏰ 10분 간격 알람 로그: $formattedTime (지연: $delay)")
        
        // 2. 크론 작업 실행 (새로운 기능)
        // BroadcastReceiver.onReceive()가 반환된 뒤에도 프로세스가 유지되도록
        // 비동기 작업의 생명주기를 PendingResult에 연결한다.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                executeCronJob(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
        
        // 3. 다음 알람 스케줄링 (유지)
        scheduleNextAlarm(context)
    }
    
    /**
     * 크론 작업 실행 - 안전한 비동기 처리
     */
    private suspend fun executeCronJob(context: Context) {
        try {
            withTimeout(CRON_TIMEOUT_MS) {
                // 현재 시간을 10분 단위로 정규화 (8:03 -> 8:00, 8:13 -> 8:10)
                val calendar = Calendar.getInstance().apply {
                    val currentMinute = get(Calendar.MINUTE)
                    val normalizedMinute = (currentMinute / 10) * 10
                    set(Calendar.MINUTE, normalizedMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                
                val scheduledTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time)
                Log.i(TAG, "🚀 크론 작업 시작 - 예정 시간: $scheduledTime")
                
                val cronService = CronApiService(context)
                val result = cronService.runScheduledMessage(scheduledTime)
                
                result.fold(
                    onSuccess = { cronResponse ->
                        handleCronResponse(context, cronService, cronResponse)
                    },
                    onFailure = { exception ->
                        Log.w(TAG, "⚠️ 크론 작업 실패 - 다음 주기에 재시도: ${exception.message}")
                    }
                )
            }
        } catch (timeout: TimeoutCancellationException) {
            Log.w(TAG, "⏰ 크론 작업 타임아웃 - 다음 주기에 재시도")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 크론 작업 예외: ${e.message}")
        }
    }
    
    /**
     * 크론 응답 처리 및 메시지 전송
     */
    private suspend fun handleCronResponse(
        context: Context,
        cronService: CronApiService,
        cronResponse: CronResponse
    ) {
        if (!cronResponse.success) {
            Log.w(TAG, "⚠️ 크론 작업 서버 오류: ${cronResponse.error}")
            return
        }
        
        val messages = cronResponse.messages
        if (messages.isNullOrEmpty()) {
            Log.i(TAG, "📭 전송할 크론 메시지가 없습니다")
            return
        }
        
        Log.i(TAG, "📤 ${messages.size}개의 크론 메시지 전송 시작")
        
        val replyManager = ReplyManager.getInstance(context)
        var successCount = 0
        var failCount = 0
        
        messages.forEach { cronMessage ->
            var deliveryError: String? = null
            val success = try {
                val sent = replyManager.sendMessageToChat(
                    chatId = cronMessage.chatId,
                    message = cronMessage.message
                )

                if (!sent) {
                    deliveryError = "KakaoTalk 알림을 찾지 못했거나 RemoteInput 전송에 실패했습니다"
                }
                sent
            } catch (e: Exception) {
                deliveryError = "메시지 전송 예외: ${e.message ?: e.javaClass.simpleName}"
                false
            }

            if (success) {
                successCount++
                Log.i(TAG, "✅ 크론 메시지 전송 성공: ${cronMessage.chatId}")
                Log.d(TAG, "   메시지: '${cronMessage.message.take(50)}${if (cronMessage.message.length > 50) "..." else ""}'")
            } else {
                failCount++
                Log.w(TAG, "❌ 크론 메시지 전송 실패: ${cronMessage.chatId} - $deliveryError")
            }

            if (cronMessage.requiresDeliveryAck) {
                val ackResult = cronService.acknowledgeDelivery(
                    cronMessage = cronMessage,
                    ok = success,
                    error = deliveryError
                )

                ackResult.onFailure { error ->
                    Log.e(
                        TAG,
                        "⚠️ 외부 발송 ACK 실패: id=${cronMessage.scheduledMessageId}, " +
                            "attempt=${cronMessage.deliveryAttempt} - ${error.message}"
                    )
                }
            }
        }
        
        Log.i(TAG, "📊 크론 메시지 전송 완료: 성공 ${successCount}개, 실패 ${failCount}개 (${cronResponse.executionTime}ms)")
    }
}
