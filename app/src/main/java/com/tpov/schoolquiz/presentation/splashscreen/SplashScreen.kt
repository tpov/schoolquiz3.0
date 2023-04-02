package com.tpov.schoolquiz.presentation.splashscreen

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.*
import com.tpov.geoquiz.activity.workers.RefreshDataWorker
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.presentation.mainactivity.MainActivity
import com.tpov.schoolquiz.databinding.ActivitySplashScreenBinding
import com.tpov.schoolquiz.presentation.MainApp
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import kotlinx.coroutines.InternalCoroutinesApi
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@SuppressLint("CustomSplashScreen")
@InternalCoroutinesApi
class SplashScreen : AppCompatActivity() {

    private lateinit var binding: ActivitySplashScreenBinding

    private lateinit var viewModel: SplashScreenViewModel
    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    private val component by lazy {
        (application as MainApp).component
    }

    private val localBroadcastManager by lazy {
        LocalBroadcastManager.getInstance(this)
    }
    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "loaded") {
                val percent = intent.getIntExtra("percent", 0)
                binding.tvQuestion.text = "$percent, %"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme);
        component.inject(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySplashScreenBinding.inflate(layoutInflater)
        viewModel = ViewModelProvider(this, viewModelFactory)[SplashScreenViewModel::class.java]
        setContentView(binding.root)
        visibleTPOV(false)
        viewModel.getQuestionDay()

        val intentFilter = IntentFilter().apply {
            addAction("loaded")
        }
        localBroadcastManager.registerReceiver(receiver, intentFilter)
        checkQuestionNotDate(viewModel.loadDate())
    }

    override fun onDestroy() {
        super.onDestroy()
        localBroadcastManager.unregisterReceiver(receiver)
    }

    private fun visibleTPOV(visible: Boolean) = with(binding) {
        Log.d("WorkManager", "Видимость ТПОВ.")
        if (visible) {
            tvT.visibility = View.VISIBLE
            tvP.visibility = View.VISIBLE
            tvO.visibility = View.VISIBLE
            tvV.visibility = View.VISIBLE
        } else {
            tvT.visibility = View.GONE
            tvP.visibility = View.GONE
            tvO.visibility = View.GONE
            tvV.visibility = View.GONE
        }
    }

    private fun checkQuestionNotDate(systemDate: String) {
        viewModel.numQuestionNotDate = 0
        viewModel.allGetQuestionDay.observe(this) { item ->
            Log.d("WorkManager", "Загрузка из бд.")
            item.forEach { it ->
                viewModel.generateQuestion = apiQuestion(it)
                Log.d("WorkManager", it.date)
                if (it.date == "0") {
                    viewModel.numQuestionNotDate++
                    viewModel.questionNotNetwork = it.questionTranslate
                    viewModel.answerNotNetwork = it.answerTranslate
                    viewModel.generateQuestionNotNetwork = viewModel.generateQuestion
                    Log.d("WorkManager", "найден пустой квиз")
                }
                if (it.date == systemDate || viewModel.numQuestionNotDate == 9 && !viewModel.numSystemDate) {
                    Log.d("WorkManager", "Создаем 10 квиз с датой")
                    viewModel.updateQuestionDay(
                        viewModel.generateQuestion.copy(date = systemDate)
                    )        //Если из первых девяти вопросов не найдено который нужно отобразить, мы назначаем вопрос для отображения 10й

                    viewModel.numSystemDate = true
                    binding.tvQuestion.text = it.questionTranslate
                    binding.tvAnswer.text = it.answerTranslate
                    viewModel.questionNotNetworkDate = it.questionTranslate
                    viewModel.answerNotNetworkDate = it.answerTranslate
                    createAnimation()
                }
            }

            Log.d("WorkManager", "Пустых вопросов $viewModel.numQuestionNotDate")
            if (viewModel.numQuestionNotDate < 10) {
                if (viewModel.numQuestionNotDate == 0) {
                    Toast.makeText(
                        this,
                        "Пожалуйста подождите, вопросы загружаются с сервера (~1Мб)",
                        Toast.LENGTH_LONG
                    )
                }
                Log.d("WorkManager", "Пустых вопросов меньше 10, загружаем еще раз")
                loadApi()
                viewModel.checkLoadApi = true
            }
        }
    }

    private fun loadNotification(title: String, name: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(notificationChannel)
        }

        val largeIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher3)
        val smallIcon = Bitmap.createScaledBitmap(largeIcon, 128, 128, false)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(name)
            .setSmallIcon(R.mipmap.ic_launcher3)
            .setLargeIcon(smallIcon)
            .build()
        notificationManager.notify(1, notification)
    }

    private fun loadApi() {
        Log.d("WorkManager", "Создание воркера.")
        val workManager = WorkManager.getInstance(application)

        val requeust2 = RefreshDataWorker.makeRequest(viewModel.numQuestionNotDate)
        val requeust3 = PeriodicWorkRequestBuilder<RefreshDataWorker>(
            20,
            TimeUnit.HOURS,
            28,
            TimeUnit.HOURS
        ).build()

        workManager.getWorkInfoByIdLiveData(requeust2.id)
            .observe(this) {
                Log.d("WorkManager", "finish воркер")
                if (it.state.isFinished) {
                    Log.d(
                        "WorkManager",
                        "${it.progress.getStringArray(RefreshDataWorker.QUESTION)}, " +
                                "${it.outputData.getStringArray(RefreshDataWorker.QUESTION)}, Принимаем данные из воркера"
                    )

                    viewModel.questionApiArray =
                        it.outputData.getStringArray(RefreshDataWorker.QUESTION)
                    viewModel.answerApiArray =
                        it.outputData.getStringArray(RefreshDataWorker.ANSWER)


                    if (viewModel.questionApiArray != null) {

                        Log.d("WorkManager", "Квест апи не пустой")
                        loadQuestion()
                    } else {
                        loadNotification("Ошибка", "Вопросы не были загружены, ошибка сети.")
                        if (viewModel.numQuestionNotDate in 1..9) {
                            Log.d("WorkManager", "Создаем последний свободный вопрос")
                            viewModel.updateQuestionDay(
                                viewModel.generateQuestionNotNetwork.copy(date = viewModel.loadDate())
                            )        //Если из первых девяти вопросов не найдено который нужно отобразить, мы назначаем вопрос для отображения 10й

                            viewModel.numSystemDate = true
                            if (viewModel.questionNotNetworkDate != "") {
                                binding.tvQuestion.text = viewModel.questionNotNetworkDate
                                binding.tvAnswer.text = viewModel.answerNotNetworkDate
                            } else {
                                binding.tvQuestion.text = viewModel.questionNotNetwork
                                binding.tvAnswer.text = viewModel.answerNotNetwork
                                viewModel.numQuestionNotDate--
                            }

                            createAnimation()
                            visibleTPOV(true)
                        } else {
                            binding.tvQuestion.text = "У вас закончились вопросы"
                        }
                        Log.d("WorkManager", "Квест апи пустой")
                        Toast.makeText(
                            this,
                            "Не удалось подключится к интернету",
                            Toast.LENGTH_LONG
                        )
                            .show()
                        createAnimation()
                    }
                }
            }
        Log.d("WorkManager", "Передаем - $viewModel.numQuestionNotDate")
        loadNotification("Загрузка", "Подключение к сети")
        workManager.enqueue(requeust2)
        workManager.enqueue(requeust3)
    }

    private fun loadQuestion() {
        viewModel.loadQuestion()

        loadNotification("Успех", "Загружены вопросы")
        Log.d("WorkManager", "Закончилась загрузка квеста /n ищем еще раз")
        Thread.sleep(250)
        viewModel.getQuestionDay()
    }

    private fun createAnimation() = with(binding) {
        visibleTPOV(true)

        tvT.startAnimation(AnimationUtils.loadAnimation(this@SplashScreen, R.anim.anim_splash_t))
        tvP.startAnimation(AnimationUtils.loadAnimation(this@SplashScreen, R.anim.anim_splash_p))
        tvO.startAnimation(AnimationUtils.loadAnimation(this@SplashScreen, R.anim.anim_splash_o))

        var anim3 = AnimationUtils.loadAnimation(this@SplashScreen, R.anim.anim_splash_v)
        animationListener(anim3)
        tvV.startAnimation(anim3)
    }

    private fun animationListener(anim: Animation) {

        anim.setAnimationListener(object : AnimationListener {
            override fun onAnimationStart(p0: Animation?) {
                visibleTPOV(true)
            }

            override fun onAnimationEnd(p0: Animation?) {
                visibleTPOV(false)
                startActivity()
            }

            override fun onAnimationRepeat(p0: Animation?) {

            }
        })
    }

    private fun startActivity() {
        var intent = Intent(this, MainActivity::class.java)
        intent.putExtra(MainActivity.NUM_QUESTION_NOT_NUL, viewModel.numQuestionNotDate)
        startActivity(intent)
        finish()
    }

    companion object {
        const val CHANNEL_ID = "channel_id"
        const val CHANNEL_NAME = "load_question"
    }
}