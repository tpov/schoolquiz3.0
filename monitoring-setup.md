# Настройка мониторинга для SchoolQuiz 3.0

## 📊 Firebase Performance Monitoring

### 1. Настройка в Android приложении

```kotlin
// app/src/main/java/com/tpov/schoolquiz/MainApp.kt
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace

class MainApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Инициализация Firebase Performance
        FirebasePerformance.getInstance()
    }
}
```

### 2. Добавление трейсинга в Use Cases

```kotlin
// app/src/main/java/com/tpov/schoolquiz/domain/ProfileUseCaseImproved.kt
import com.google.firebase.perf.metrics.Trace

class ProfileUseCaseImproved @Inject constructor(
    private val repositoryProfile: RepositoryProfile
) {
    suspend fun syncProfile(): Result<ProfileEntity> = runCatching {
        val trace = Trace.create("profile_sync")
        trace.start()
        
        try {
            // ... логика синхронизации
            val result = performSync()
            trace.putAttribute("success", "true")
            result
        } catch (e: Exception) {
            trace.putAttribute("success", "false")
            trace.putAttribute("error", e.message ?: "unknown")
            throw e
        } finally {
            trace.stop()
        }
    }
}
```

### 3. Мониторинг сетевых запросов

```kotlin
// app/src/main/java/com/tpov/schoolquiz/data/network/NetworkMonitor.kt
import com.google.firebase.perf.network.FirebasePerfOkHttpClient
import okhttp3.OkHttpClient

class NetworkMonitor @Inject constructor() {
    fun createMonitoredClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                
                // Логирование метрик
                logNetworkMetrics(request.url.toString(), response.code)
                
                response
            }
            .build()
    }
    
    private fun logNetworkMetrics(url: String, statusCode: Int) {
        val trace = Trace.create("network_request")
        trace.putAttribute("url", url)
        trace.putAttribute("status_code", statusCode.toString())
        trace.start()
        trace.stop()
    }
}
```

## 🔍 Firebase Analytics

### 1. Настройка событий

```kotlin
// app/src/main/java/com/tpov/schoolquiz/presentation/analytics/AnalyticsTracker.kt
import com.google.firebase.analytics.FirebaseAnalytics

class AnalyticsTracker @Inject constructor(
    private val firebaseAnalytics: FirebaseAnalytics
) {
    fun trackQuizStarted(quizId: String, category: String) {
        val bundle = Bundle().apply {
            putString("quiz_id", quizId)
            putString("category", category)
            putString("timestamp", System.currentTimeMillis().toString())
        }
        firebaseAnalytics.logEvent("quiz_started", bundle)
    }
    
    fun trackQuizCompleted(quizId: String, score: Int, timeSpent: Long) {
        val bundle = Bundle().apply {
            putString("quiz_id", quizId)
            putInt("score", score)
            putLong("time_spent", timeSpent)
        }
        firebaseAnalytics.logEvent("quiz_completed", bundle)
    }
    
    fun trackUserRegistration(method: String) {
        val bundle = Bundle().apply {
            putString("registration_method", method)
        }
        firebaseAnalytics.logEvent("user_registration", bundle)
    }
}
```

### 2. Интеграция в ViewModels

```kotlin
// app/src/main/java/com/tpov/schoolquiz/presentation/main/MainViewModel.kt
class MainViewModel @Inject constructor(
    private val analyticsTracker: AnalyticsTracker
) : ViewModel() {
    
    fun startQuiz(quizId: String, category: String) {
        viewModelScope.launch {
            analyticsTracker.trackQuizStarted(quizId, category)
            // ... логика запуска викторины
        }
    }
}
```

## 📈 Custom Dashboards

### 1. Grafana Dashboard Configuration

```json
{
  "dashboard": {
    "title": "SchoolQuiz Analytics",
    "panels": [
      {
        "title": "Active Users",
        "type": "stat",
        "targets": [
          {
            "expr": "sum(firebase_analytics_active_users)",
            "legendFormat": "Active Users"
          }
        ]
      },
      {
        "title": "Quiz Completion Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(quiz_completed_total[5m])",
            "legendFormat": "Completions/min"
          }
        ]
      },
      {
        "title": "Error Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(profile_sync_errors_total[5m])",
            "legendFormat": "Errors/min"
          }
        ]
      }
    ]
  }
}
```

### 2. Prometheus Metrics

```kotlin
// app/src/main/java/com/tpov/schoolquiz/monitoring/MetricsCollector.kt
class MetricsCollector @Inject constructor() {
    private val quizCompletions = AtomicLong(0)
    private val profileSyncErrors = AtomicLong(0)
    private val activeUsers = AtomicLong(0)
    
    fun incrementQuizCompletions() {
        quizCompletions.incrementAndGet()
    }
    
    fun incrementProfileSyncErrors() {
        profileSyncErrors.incrementAndGet()
    }
    
    fun setActiveUsers(count: Long) {
        activeUsers.set(count)
    }
    
    fun getMetrics(): Map<String, Long> {
        return mapOf(
            "quiz_completions_total" to quizCompletions.get(),
            "profile_sync_errors_total" to profileSyncErrors.get(),
            "active_users" to activeUsers.get()
        )
    }
}
```

## 🚨 Alerting

### 1. Firebase Functions Alerts

```typescript
// functions/src/monitoring/alerts.ts
import * as functions from "firebase-functions";

export const monitorFunctionErrors = functions.pubsub
    .schedule('every 5 minutes')
    .onRun(async (context) => {
        const db = admin.firestore();
        
        // Проверяем количество ошибок за последние 5 минут
        const errorLogs = await db
            .collection('logs')
            .where('level', '==', 'error')
            .where('timestamp', '>', Date.now() - 5 * 60 * 1000)
            .get();
            
        if (errorLogs.size > 10) {
            // Отправляем уведомление
            await sendAlert({
                title: 'High Error Rate',
                message: `${errorLogs.size} errors in last 5 minutes`,
                severity: 'high'
            });
        }
    });

async function sendAlert(alert: {
    title: string;
    message: string;
    severity: string;
}) {
    // Интеграция с Slack/Email
    console.log(`ALERT: ${alert.title} - ${alert.message}`);
}
```

### 2. Android App Alerts

```kotlin
// app/src/main/java/com/tpov/schoolquiz/monitoring/AppHealthMonitor.kt
class AppHealthMonitor @Inject constructor(
    private val crashlytics: FirebaseCrashlytics
) {
    fun monitorAppHealth() {
        // Мониторинг памяти
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsage = (usedMemory.toFloat() / maxMemory.toFloat()) * 100
        
        if (memoryUsage > 80) {
            crashlytics.log("High memory usage: ${memoryUsage}%")
        }
        
        // Мониторинг производительности
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isDeviceIdleMode) {
                crashlytics.log("Device not in idle mode - battery optimization")
            }
        }
    }
}
```

## 📊 Logging Strategy

### 1. Structured Logging

```kotlin
// app/src/main/java/com/tpov/schoolquiz/logging/StructuredLogger.kt
class StructuredLogger @Inject constructor(
    private val crashlytics: FirebaseCrashlytics
) {
    fun info(message: String, data: Map<String, Any> = emptyMap()) {
        val logData = mapOf(
            "level" to "info",
            "message" to message,
            "timestamp" to System.currentTimeMillis(),
            "data" to data
        )
        
        Log.i("SchoolQuiz", logData.toString())
        crashlytics.log(logData.toString())
    }
    
    fun error(message: String, throwable: Throwable? = null, data: Map<String, Any> = emptyMap()) {
        val logData = mapOf(
            "level" to "error",
            "message" to message,
            "timestamp" to System.currentTimeMillis(),
            "data" to data
        )
        
        Log.e("SchoolQuiz", logData.toString(), throwable)
        crashlytics.recordException(throwable ?: Exception(message))
    }
}
```

### 2. Firebase Functions Logging

```typescript
// functions/src/shared/logging.ts
export const logger = {
    info: (message: string, data?: any) => {
        const logEntry = {
            level: 'info',
            message,
            timestamp: new Date().toISOString(),
            data,
            function: process.env.FUNCTION_NAME
        };
        console.log(JSON.stringify(logEntry));
    },
    
    error: (message: string, error?: any) => {
        const logEntry = {
            level: 'error',
            message,
            timestamp: new Date().toISOString(),
            error: error?.message || error,
            stack: error?.stack,
            function: process.env.FUNCTION_NAME
        };
        console.error(JSON.stringify(logEntry));
    },
    
    warn: (message: string, data?: any) => {
        const logEntry = {
            level: 'warn',
            message,
            timestamp: new Date().toISOString(),
            data,
            function: process.env.FUNCTION_NAME
        };
        console.warn(JSON.stringify(logEntry));
    }
};
```

## 🎯 Key Performance Indicators (KPIs)

### 1. User Engagement Metrics
- Daily Active Users (DAU)
- Monthly Active Users (MAU)
- Session Duration
- Quiz Completion Rate

### 2. Technical Metrics
- App Crash Rate
- API Response Time
- Database Query Performance
- Memory Usage

### 3. Business Metrics
- User Registration Rate
- Quiz Creation Rate
- Ad Revenue
- User Retention Rate

## 📋 Implementation Checklist

- [ ] Firebase Performance Monitoring настроен
- [ ] Firebase Analytics события добавлены
- [ ] Custom метрики созданы
- [ ] Grafana dashboard настроен
- [ ] Alerting правила созданы
- [ ] Structured logging внедрен
- [ ] KPIs определены и отслеживаются
- [ ] Error tracking настроен
- [ ] Performance baselines установлены