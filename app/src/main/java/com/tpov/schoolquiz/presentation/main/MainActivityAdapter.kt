package com.tpov.schoolquiz.presentation.main

import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.GranularRoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.databinding.ActivityMainItemBinding
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.custom.ResizeAndCrop
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getTpovId
import kotlinx.android.synthetic.main.activity_main_item.view.*
import kotlinx.coroutines.InternalCoroutinesApi
import java.io.File
import java.util.*

class MainActivityAdapter @OptIn(InternalCoroutinesApi::class) constructor(
    private val listener: Listener,
    private val context: Context,
    private val viewModel: MainActivityViewModel
) :
    ListAdapter<QuizEntity, MainActivityAdapter.ItemHolder>(ItemComparator()) {
    var onDeleteButtonClick: ((RecyclerView.ViewHolder) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
        return ItemHolder.create(parent, listener)
    }


    @OptIn(InternalCoroutinesApi::class)
    override fun onBindViewHolder(holder: ItemHolder, position: Int) {
        val item = getItem(position)
        holder.setData(item, listener, context, viewModel)
    }


    class ItemComparator : DiffUtil.ItemCallback<QuizEntity>() {
        override fun areItemsTheSame(oldItem: QuizEntity, newItem: QuizEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: QuizEntity, newItem: QuizEntity): Boolean {
            return oldItem == newItem
        }
    }

    class ItemHolder(view: View, private val listener: Listener) : RecyclerView.ViewHolder(view),
        View.OnTouchListener {

        @OptIn(InternalCoroutinesApi::class)
        fun log(msg: String) {
            Logcat.log(msg, "MainActivityAdapter", Logcat.LOG_ACTIVITY)
        }

        val constraintLayout: ConstraintLayout = itemView.findViewById(R.id.constraint_layout)

        private val binding = ActivityMainItemBinding.bind(view)
        val imvGradLightQuiz: ImageView = itemView.findViewById(R.id.imv_gradient_light_quiz)
        val imvGradHardQuiz: ImageView = itemView.findViewById(R.id.imv_grafient_hard_quiz)

        private fun showPopupMenu(view: View, id: Int) {
            val popupMenu = PopupMenu(view.context, view)
            popupMenu.inflate(R.menu.popup_menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_send -> {
                        // Обработка выбора элемента 1
                        listener.sendItem(id)
                        true
                    }

                    R.id.menu_delete -> {
                        // Обработка выбора элемента 2
                        listener.deleteItem(id)
                        true
                    }

                    R.id.menu_edit -> {
                        // Обработка выбора элемента 3
                        listener.editItem(id)
                        true
                    }

                    else -> false
                }
            }
            popupMenu.show()
        }

        @OptIn(InternalCoroutinesApi::class)
        fun setData(
            quizEntity: QuizEntity,
            listener: Listener,
            context: Context,
            viewModel: MainActivityViewModel
        ) = with(binding) {

            log("")
            if (viewModel.getQuizById(quizEntity.id!!).showItemMenu) {
                constraintLayout.setOnLongClickListener {
                    showPopupMenu(it, quizEntity.id!!)
                    true
                }

            } else constraintLayout.visibility = View.VISIBLE
            try {

                val file = File(context.cacheDir, "${quizEntity.picture}")

                fun dpToPx(dp: Int, context: Context): Int {
                    val density = context.resources.displayMetrics.density
                    return (dp * density).toInt()
                }

                val widthInDp = 100
                val heightInDp = 75
                val radius = 25

                val widthInPx = dpToPx(widthInDp, context)
                val heightInPx = dpToPx(heightInDp, context)
                val radinPx = dpToPx(radius, context)

                Glide.with(context)
                    .asBitmap()
                    .load(file)
                    .apply(
                        RequestOptions()
                            .override(widthInPx, heightInPx)
                            .transform(
                                ResizeAndCrop(widthInPx, heightInPx),
                                GranularRoundedCorners(0f, radinPx.toFloat(), radinPx.toFloat(), 0f)
                            )
                    )
                    .into(imageView)

            } catch (e: Exception) {
                log("onBindViewHolder Exception $e")
            }

            var goHardQuiz =
                "${this.root.context.getString(R.string.go_hard_question)} - ${quizEntity.nameQuiz}"

            if (quizEntity.event == 5) initViewQuiz5(quizEntity, viewModel, listener)
            else initView(quizEntity, goHardQuiz, viewModel, listener)

        }

        @OptIn(InternalCoroutinesApi::class)
        private fun ActivityMainItemBinding.initViewQuiz5(
            quizEntity: QuizEntity,
            viewModel: MainActivityViewModel,
            listener: Listener
        ) {
            if (quizEntity.stars >= MAX_PERCENT) {

                log("quizEntity.stars 1")
                imvGradLightQuiz.visibility = View.VISIBLE
                imvGradHardQuiz.visibility = View.GONE

            } else if (quizEntity.ratingPlayer == 3) {

                log("quizEntity.stars 2")
                imvGradLightQuiz.visibility = View.GONE
                imvGradHardQuiz.visibility = View.VISIBLE

            } else if (quizEntity.tpovId == getTpovId()) {

                imvGradLightQuiz.visibility = View.GONE
                imvGradHardQuiz.visibility = View.GONE
                imvGradientTranslateQuiz.visibility = View.GONE

            } else {

                log("quizEntity.stars 3")
                chbTypeQuiz.visibility = View.GONE
                imvGradLightQuiz.visibility = View.GONE
                imvGradHardQuiz.visibility = View.GONE
            }

            chbTypeQuiz.visibility = View.VISIBLE
            chbTypeQuiz.isChecked = quizEntity.stars >= MAX_PERCENT

            imvTranslate.imageAlpha = 128

            val lvlTranslate = viewModel.getLvlTranslateByQuizId(quizEntity.id!!)

            //imvTranslate
            if (lvlTranslate <= 100) imvTranslate.setColorFilter(Color.GRAY)
            else if (lvlTranslate <= 200) imvTranslate.setColorFilter(Color.YELLOW)
            else imvTranslate.setColorFilter(Color.BLUE)

            ratingBar.rating = quizEntity.ratingPlayer.toFloat() / 100

            mainTitleButton.text = quizEntity.nameQuiz
            mainTitleButton.setOnClickListener {
                listener.onClick(quizEntity.id!!, chbTypeQuiz.isChecked)
            }
            /*imvGradHardQuiz.setOnClickListener {
                listener.onClick(quizEntity.id!!, chbTypeQuiz.isChecked)
            }
            imvGradLightQuiz.setOnClickListener {
                listener.onClick(quizEntity.id!!, chbTypeQuiz.isChecked)
            }
            imvTranslate.setOnClickListener {
                listener.onClick(quizEntity.id!!, chbTypeQuiz.isChecked)
            }*/

            tvName.visibility = View.VISIBLE
            tvTime.visibility = View.VISIBLE
            tvName.text = quizEntity.userName
            tvTime.text = quizEntity.data
        }

        @OptIn(InternalCoroutinesApi::class)
        private fun ActivityMainItemBinding.initView(
            quizEntity: QuizEntity,
            goHardQuiz: String,
            viewModel: MainActivityViewModel,
            listener: Listener
        ) {
            if (quizEntity.stars == MAX_PERCENT) {
                Toast.makeText(binding.root.context, goHardQuiz, Toast.LENGTH_SHORT).show()
            }

            if (quizEntity.stars >= MAX_PERCENT) {
                log("quizEntity.stars 1")
                imvGradLightQuiz.visibility = View.VISIBLE
                imvGradHardQuiz.visibility = View.GONE
                chbTypeQuiz.visibility = View.VISIBLE

            } else if (quizEntity.stars == 120) {
                log("quizEntity.stars 2")
                chbTypeQuiz.visibility = View.VISIBLE
                imvGradLightQuiz.visibility = View.GONE
                imvGradHardQuiz.visibility = View.VISIBLE

            } else {
                log("quizEntity.stars 3")
                chbTypeQuiz.visibility = View.GONE
                imvGradLightQuiz.visibility = View.GONE
                imvGradHardQuiz.visibility = View.GONE
            }

            imvTranslate.imageAlpha = 128

            val lvlTranslate = viewModel.getLvlTranslateByQuizId(quizEntity.id!!)

            //imvTranslate
            if (lvlTranslate <= 100) imvTranslate.setColorFilter(Color.GRAY)
            else if (lvlTranslate <= 200) imvTranslate.setColorFilter(Color.YELLOW)
            else imvTranslate.setColorFilter(Color.BLUE)

            if (quizEntity.stars <= MAX_PERCENT) ratingBar.rating =
                (quizEntity.stars.toFloat() / 50)
            else ratingBar.rating = (((quizEntity.stars.toFloat() - 100) / 20) + 2)

            imvTranslate.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    // Rating bar clicked, handle the event here
                    // You can call your method to show the translation popup/dialog
                    showTranslationPopup(quizEntity)
                }
                true
            }

            ratingBar.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    // Rating bar clicked, handle the event here
                    // You can call your method to show the translation popup/dialog
                    showRatingInfo(quizEntity)
                }
                true
            }

            mainTitleButton.text = quizEntity.nameQuiz

            mainTitleButton.setOnClickListener {
                listener.onClick(quizEntity.id!!, chbTypeQuiz.isChecked)
            }/*
            imvGradHardQuiz.setOnClickListener {
                listener.onClick(quizEntity.id!!, chbTypeQuiz.isChecked)
            }
            imvGradLightQuiz.setOnClickListener {
                listener.onClick(quizEntity.id!!, chbTypeQuiz.isChecked)
            }
            imvTranslate.setOnClickListener {
                listener.onClick(quizEntity.id!!, chbTypeQuiz.isChecked)
            }*/
        }

        private fun showRatingInfo(quizEntity: QuizEntity) {
            val context = itemView.context

            val popupMenu = PopupMenu(context, itemView)
            val menuInflater = popupMenu.menuInflater
            menuInflater.inflate(R.menu.translation_menu, popupMenu.menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->

                itemView.findViewById<TextView>(R.id.menu_translate_info).text = quizEntity.stars.toString()

                when (menuItem.itemId) {
                    R.id.menu_translate_button -> {
                        // Perform translation logic here
                        translateText()
                        true
                    }
                    else -> false
                }
            }
        }
        private fun showTranslationPopup(quizEntity: QuizEntity) {
            val context = itemView.context

            val popupMenu = PopupMenu(context, itemView)
            val menuInflater = popupMenu.menuInflater
            menuInflater.inflate(R.menu.translation_menu, popupMenu.menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_translate_button -> {
                        // Perform translation logic here
                        translateText()
                        true
                    }
                    else -> false
                }
            }
            // Show the popup menu
            popupMenu.show()
        }

        private fun translateText() {
//            val originalTexts = viewMode.getList()
//
//            // Создаем экземпляр API для перевода
//            val translationApi = TranslationApi() // Замените на вашу библиотеку или методы для вызова API перевода
//
//            originalTexts.forEach { originalText ->
//                // Выполняем перевод текста
//                val translatedText = translationApi.translate(originalText)
//
//                // Создаем экземпляр вопроса с оригинальным и переведенным текстом
//                val question = Question(originalText, translatedText)
//
//                // Сохраняем вопрос с помощью ViewModel
//                viewModel.insertQuestion(question)
//            }
        }
        companion object {
            const val PERCENT_TWO_STARS = 0.83333
            const val MAX_PERCENT = 100

            fun create(parent: ViewGroup, listener: Listener): ItemHolder {
                return ItemHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.activity_main_item, parent, false),
                    listener
                )
            }
        }

        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            return true
        }
    }

    interface Listener {
        fun deleteItem(id: Int)
        fun onClick(id: Int, type: Boolean)
        fun editItem(id: Int)
        fun sendItem(id: Int)
        fun reloadData()
    }

    @OptIn(InternalCoroutinesApi::class)
    fun log(msg: String) {
        Logcat.log(msg, "MainActivityAdapter", Logcat.LOG_ACTIVITY)
    }
}