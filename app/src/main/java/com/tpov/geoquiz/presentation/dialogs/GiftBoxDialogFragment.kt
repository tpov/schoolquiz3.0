package com.tpov.geoquiz.presentation.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.airbnb.lottie.LottieAnimationView
import com.tpov.geoquiz.R // Предполагая, что R-файл вашего модуля app это com.tpov.geoquiz.R

class GiftBoxDialogFragment : DialogFragment() {

    private lateinit var lottieAnimationView: LottieAnimationView
    private lateinit var prizeDescriptionTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_gift_box, container, false)
        lottieAnimationView = view.findViewById(R.id.lottie_animation_view)
        prizeDescriptionTextView = view.findViewById(R.id.tv_prize_description)

        // Устанавливаем анимацию и ставим на паузу
        // В XML уже указано app:lottie_fileName="open_box.json" и app:lottie_autoPlay="false"
        // поэтому дополнительно делать lottieAnimationView.setAnimation("open_box.json") не всегда нужно,
        // но для явности можно оставить.
        // lottieAnimationView.setAnimation("open_box.json") // Если бы не было указано в XML
        lottieAnimationView.pauseAnimation() // Явно ставим на паузу

        // Скрываем текстовое поле для приза по умолчанию
        prizeDescriptionTextView.visibility = View.GONE

        // Обработчик нажатия на LottieAnimationView
        lottieAnimationView.setOnClickListener {
            // Запускаем анимацию тряски
            startShakingAnimation()

            // TODO: Отправить запрос на сервер для получения информации о подарке
            // Имитация запроса на сервер с задержкой
            it.postDelayed({
                // Предположим, сервер вернул "Золото: 100 штук"
                val prize = "Золото: 100 штук"
                openBoxAndShowPrize(prize)
            }, 2000) // Задержка в 2 секунды для имитации ответа сервера
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        // Настройка размеров диалога, если нужно
        dialog?.window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        // Можно также установить фон или другие параметры окна диалога
        // dialog?.window?.setBackgroundDrawableResource(R.drawable.db_background_dialog) // Пример
    }

    // Метод для установки анимации на определенный кадр (закрытой коробки)
    // Этот метод можно будет вызвать извне, если потребуется
    fun setAnimationToClosedBoxFrame(frame: Int) {
        lottieAnimationView.frame = frame
        lottieAnimationView.pauseAnimation()
    }

    // Метод для запуска анимации тряски (пока это плейсхолдер)
    fun startShakingAnimation() {
        // TODO: Реализовать или подключить анимацию тряски
        // Например, можно использовать другую Lottie-анимацию или ObjectAnimator
        // lottieAnimationView.setAnimation("shaking_box.json") // Если есть отдельная анимация тряски
        // lottieAnimationView.playAnimation()
        // В качестве простого примера, можно зациклить часть основной анимации, если она это позволяет
        // или просто показать какой-то эффект.
        // Пока что можно просто немного изменить масштаб для имитации
        lottieAnimationView.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).withEndAction {
            lottieAnimationView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
        }.start()
    }

    // Метод для запуска анимации открытия коробки и отображения приза
    fun openBoxAndShowPrize(prizeText: String) {
        // Останавливаем анимацию тряски, если она была
        lottieAnimationView.animate().cancel() // Отменяем анимацию масштаба, если была
        lottieAnimationView.scaleX = 1.0f
        lottieAnimationView.scaleY = 1.0f

        // Восстанавливаем основную анимацию, если переключались на тряску
        // lottieAnimationView.setAnimation("open_box.json") // Если меняли анимацию

        lottieAnimationView.playAnimation() // Запускаем анимацию открытия

        // Можно добавить слушатель завершения анимации, чтобы показать приз после
        lottieAnimationView.addAnimatorUpdateListener { animation ->
            if (animation.animatedFraction == 1f) {
                prizeDescriptionTextView.text = prizeText
                prizeDescriptionTextView.visibility = View.VISIBLE
            }
        }
        // Или если анимация не зациклена и autoPlay=false, playAnimation() проиграет ее один раз.
        // Если нужно показать приз в середине или конце анимации открытия,
        // можно использовать addAnimatorListener и отслеживать onAnimationEnd или onAnimationRepeat.
        // Для простоты пока отобразим сразу (или почти сразу после начала анимации)
        // prizeDescriptionTextView.text = prizeText
        // prizeDescriptionTextView.visibility = View.VISIBLE
    }

    companion object {
        const val TAG = "GiftBoxDialogFragment"

        fun newInstance(): GiftBoxDialogFragment {
            return GiftBoxDialogFragment()
        }
    }
}
