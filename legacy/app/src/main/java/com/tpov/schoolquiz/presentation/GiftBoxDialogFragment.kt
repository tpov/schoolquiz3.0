package com.tpov.schoolquiz.presentation

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.airbnb.lottie.LottieAnimationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.presentation.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

class GiftBoxDialogFragment : DialogFragment() {

    private lateinit var lottieAnimationView: LottieAnimationView
    private lateinit var prizeDescriptionTextView: TextView
    private lateinit var prizeIconView: ImageView
    private val functions: FirebaseFunctions by lazy { Firebase.functions }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_gift_box, container, false)
        lottieAnimationView = view.findViewById(R.id.lottie_animation_view)
        prizeDescriptionTextView = view.findViewById(R.id.tv_prize_description)
        prizeIconView = view.findViewById(R.id.prize_icon)
        val imageView = view.findViewById<ImageView>(R.id.imv_back_main)

        imageView.scaleType = ImageView.ScaleType.MATRIX
        val matrix = Matrix()
        matrix.postScale(1.5f, 1.5f, imageView.width / 2f, imageView.height / 2f) // Увеличить 1.5x с центром
        imageView.imageMatrix = matrix

        lottieAnimationView.pauseAnimation()

        // Скрываем текстовое поле для приза по умолчанию
        prizeDescriptionTextView.visibility = View.GONE
        prizeIconView.visibility = View.GONE

        // Обработчик нажатия на LottieAnimationView
        lottieAnimationView.setOnClickListener {
            // Вызываем Cloud Function для получения награды
            // (анимация тряски будет запущена внутри метода getGiftBoxReward)
            getGiftBoxReward()
        }

        return view
    }

    override fun onStart() {
        super.onStart()

        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    
    override fun onDestroyView() {
        // Останавливаем все анимации при уничтожении представления
        lottieAnimationView.cancelAnimation()
        lottieAnimationView.clearAnimation()
        lottieAnimationView.removeCallbacks(null)
        super.onDestroyView()
    }

    private fun getGiftBoxReward() {
        // Проверяем аутентификацию
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(context, "Необходимо авторизоваться", Toast.LENGTH_SHORT).show()
            return
        }

        // Запускаем анимацию тряски и зацикливаем первую половину анимации
        startShakingAnimation()

        // Добавляем небольшую задержку перед вызовом Cloud Function для лучшего UX
        CoroutineScope(Dispatchers.Main).launch {
            // Задержка перед запросом для лучшего восприятия анимации
            kotlinx.coroutines.delay(1500) // 1.5 секунды анимации тряски

            // Переключаемся на IO поток для сетевого запроса
            withContext(Dispatchers.IO) {
                try {
                    // Вызываем Cloud Function
                    val result = functions
                        .getHttpsCallable("getGiftBoxReward")
                        .call()
                        .await()
                        .data as HashMap<*, *>

                    // Обрабатываем результат в UI потоке
                    withContext(Dispatchers.Main) {
                        handleReward(result)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Log.e(TAG, "Error calling getGiftBoxReward: ${e.message}", e)
                        Toast.makeText(context, "Ошибка получения награды: ${e.message}", Toast.LENGTH_SHORT).show()

                        // Останавливаем анимацию в случае ошибки
                        lottieAnimationView.cancelAnimation()
                        lottieAnimationView.progress = 0f
                    }
                }
            }
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun handleReward(rewardData: HashMap<*, *>) {
        val type = rewardData["type"] as String
        val amount = (rewardData["amount"] as Number).toLong()
        val itemName = rewardData["itemName"] as? String

        // Подготавливаем текст и иконку награды
        when (type) {
            "addNolics" -> {
                prizeDescriptionTextView.text = "$amount"
                prizeIconView.setImageResource(R.drawable.ic_gold) // Замените на иконку для "ноликов"
            }
            "addGold" -> {
                prizeDescriptionTextView.text = "$amount"
                prizeIconView.setImageResource(R.drawable.ic_gold)
            }
            "datePremium" -> {
                val days = amount / 86400 // Конвертируем секунды в дни
                prizeDescriptionTextView.text = "$days дн."
                prizeIconView.setImageResource(R.drawable.ic_gold) // Замените на иконку для премиума
            }
            "logo" -> {
                prizeDescriptionTextView.text = itemName ?: "Логотип"
                prizeIconView.setImageResource(R.drawable.ic_gold) // Замените на иконку для логотипа
            }
            "trophy" -> {
                prizeDescriptionTextView.text = "$amount"
                prizeIconView.setImageResource(R.drawable.ic_gold) // Замените на иконку для трофея
            }
        }

        // Запускаем анимацию открытия коробки и показываем награду
        openBoxAndShowPrize()

        // Отправляем информацию о полученной награде в MainActivity для обновления профиля
        val mainActivity = activity as? MainActivity
        mainActivity?.processReward(type, amount, itemName)
    }

    // Метод для запуска анимации тряски с зацикливанием первой половины
    fun startShakingAnimation() {
        // Устанавливаем минимальный и максимальный прогресс для зацикливания начала анимации
        lottieAnimationView.setMinAndMaxProgress(0f, 0.5f)
        // Включаем зацикливание
        lottieAnimationView.repeatCount = -1 // Бесконечное зацикливание
        // Запускаем анимацию
        lottieAnimationView.playAnimation()

        // Добавляем эффект тряски
        val shakeAnimation = object : Runnable {
            var isEnlarged = false
            override fun run() {
                if (lottieAnimationView.isAnimating) {
                    if (isEnlarged) {
                        lottieAnimationView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    } else {
                        lottieAnimationView.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).start()
                    }
                    isEnlarged = !isEnlarged
                    lottieAnimationView.postDelayed(this, 200)
                }
            }
        }
        lottieAnimationView.post(shakeAnimation)
    }

    // Метод для запуска анимации открытия коробки и отображения приза
    fun openBoxAndShowPrize() {
        // Останавливаем анимацию тряски, если она была
        lottieAnimationView.animate().cancel() // Отменяем анимацию масштаба, если была
        lottieAnimationView.scaleX = 1.0f
        lottieAnimationView.scaleY = 1.0f

        // Сбрасываем настройки зацикливания и устанавливаем полный диапазон анимации
        lottieAnimationView.repeatCount = 0 // Без повторений
        lottieAnimationView.setMinAndMaxProgress(0f, 1f) // Полная анимация

        // Запускаем анимацию открытия с начала
        lottieAnimationView.progress = 0f
        lottieAnimationView.playAnimation()

        // Показываем награду после небольшой задержки, чтобы анимация успела дойти до момента открытия
        lottieAnimationView.postDelayed({
            prizeDescriptionTextView.visibility = View.VISIBLE
            prizeIconView.visibility = View.VISIBLE
        }, 1000) // Задержка в 1 секунду перед показом награды
    }

    companion object {
        const val TAG = "GiftBoxDialogFragment"

        fun newInstance(): GiftBoxDialogFragment {
            return GiftBoxDialogFragment()
        }
    }
}
