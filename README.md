# KakaoMiddleware

카카오톡 자동 응답 미들웨어 애플리케이션

## 기능
- 카카오톡 메시지 자동 감지
- 서버로 메시지 전송 및 AI 응답 수신
- 자동 답장 기능
- FCM wake + WorkManager를 통한 수초 내 outbound/예약 메시지 전달
- 10분 Alarm 및 재부팅/APK 업데이트 후 자동 복구

## 기술 스택
- Kotlin
- Android Jetpack Compose
- OkHttp
- Firebase Cloud Messaging (FID mode)
- WorkManager
- Gradle

## 설치 및 사용법
1. 안드로이드 스튜디오에서 프로젝트 열기
2. 빌드 및 실행
3. Firebase Console에서 받은 `google-services.json`을 `app/`에 배치
4. `local.properties`에 `SERVER_API_KEY=<server env key>` 설정
5. 알림 접근 권한(필수) 및 앱 상태 알림 권한(선택) 설정
6. 카카오톡 메시지가 자동으로 처리됩니다.
