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
import com.google.cloud.translate.TranslateOptions
import com.google.cloud.translate.Translation
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.databinding.ActivityMainItemBinding
import com.tpov.schoolquiz.presentation.custom.Logcat
import com.tpov.schoolquiz.presentation.custom.ResizeAndCrop
import com.tpov.schoolquiz.presentation.custom.SharedPreferencesManager.getTpovId
import kotlinx.android.synthetic.main.activity_main_item.view.*
import kotlinx.coroutines.InternalCoroutinesApi
import org.jetbrains.anko.runOnUiThread
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
                    showPopupInfo(quizEntity, event, POPUP_TRANSLATE, viewModel)
                }
                true
            }

            ratingBar.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    // Rating bar clicked, handle the event here
                    // You can call your method to show the translation popup/dialog
                    showPopupInfo(quizEntity, event, POPUP_STARS, viewModel)
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


        @OptIn(InternalCoroutinesApi::class)
        private fun showPopupInfo(
            quizEntity: QuizEntity,
            event: MotionEvent,
            popupType: Int,
            viewModel: MainActivityViewModel
        ) {
            val context = itemView.context
            // Create the popup window
            val inflater =
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val popupView = inflater.inflate(R.layout.translation_popup_layout, null)
            val popupWindow = PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )

            // Configure the popup window
            val tvPopup1 = popupView.findViewById<TextView>(R.id.tv_popup_1)
            val tvPopup2 = popupView.findViewById<TextView>(R.id.tv_popup_2)
            val tvPopup3 = popupView.findViewById<TextView>(R.id.tv_popup_3)
            val tvPopup4 = popupView.findViewById<TextView>(R.id.tv_popup_4)
            val tvPopup5 = popupView.findViewById<TextView>(R.id.tv_popup_5)
            val tvPopup6 = popupView.findViewById<TextView>(R.id.tv_popup_6)
            val tvPopup7 = popupView.findViewById<TextView>(R.id.tv_popup_7)
            val tvPopup8 = popupView.findViewById<TextView>(R.id.tv_popup_8)
            val tvPopup9 = popupView.findViewById<TextView>(R.id.tv_popup_9)
            val spListPopup1 = popupView.findViewById<Spinner>(R.id.sp_list_popup_1)
            val bPopup1 = popupView.findViewById<Button>(R.id.b_popup_1)
            val layoutSp = popupView.findViewById<LinearLayout>(R.id.l_sp)
            val layoutB = popupView.findViewById<LinearLayout>(R.id.l_b)

            if (popupType == POPUP_TRANSLATE) {
                tvPopup1.visibility = View.VISIBLE
                spListPopup1.visibility = View.VISIBLE
                bPopup1.visibility = View.VISIBLE

                layoutB.visibility = View.VISIBLE
                layoutSp.visibility = View.VISIBLE

                tvPopup2.visibility = View.GONE
                tvPopup3.visibility = View.GONE
                tvPopup4.visibility = View.GONE
                tvPopup5.visibility = View.GONE
                tvPopup6.visibility = View.GONE
                tvPopup7.visibility = View.GONE
                tvPopup8.visibility = View.GONE
                tvPopup9.visibility = View.GONE

                bPopup1.setOnClickListener {
                    // Perform translation logic here
                    translateText(viewModel, context, quizEntity)
                    popupWindow.dismiss()
                }

                val languageString = quizEntity.languages // Получите строку языков из quizEntity
                val languageItems = languageString.split("|") // Разделите строку на элементы

                val languageMap =
                    mutableMapOf<String?, Int?>() // Создайте пустой Map для хранения соответствия язык-число

                for (item in languageItems) {
                    val parts = item.split("-") // Разделите элемент на ключ и значение
                    if (parts.size == 2) {
                        val language = parts[0]
                        val value = parts[1].toIntOrNull()
                        if (value != null) {
                            languageMap[language] = value // Добавьте соответствие язык-число в Map
                        }
                    }
                }
                val adapter = ArrayAdapter(
                    context,
                    android.R.layout.simple_spinner_item,
                    languageMap.keys.toList()
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                log("festgfsdrgdrto,  $tvPopup1")
                try {
                    tvPopup1.text = "lvl: ${languageMap.values.toList()[0]}"
                } catch (e: Exception) {
                    tvPopup1.text = "Квест еще не переведен на ваш язык"
                }
                spListPopup1.adapter = adapter
                spListPopup1.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        val selectedLanguage = spListPopup1.selectedItem.toString()
                        val selectedValue = languageMap[selectedLanguage]
                        if (selectedValue != null) {
                            tvPopup1.text = selectedLanguage
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {
                        // Обработайте случай, когда ничего не выбрано, если необходимо
                    }
                }
            } else if (popupType == POPUP_STARS) {
                tvPopup1.visibility = View.VISIBLE
                tvPopup2.visibility = View.VISIBLE
                tvPopup3.visibility = View.VISIBLE
                tvPopup4.visibility = View.VISIBLE
                tvPopup5.visibility = View.VISIBLE
                tvPopup6.visibility = View.VISIBLE
                tvPopup7.visibility = View.VISIBLE
                tvPopup8.visibility = View.VISIBLE
                tvPopup9.visibility = View.VISIBLE


                layoutB.visibility = View.GONE
                layoutSp.visibility = View.GONE

                spListPopup1.visibility = View.GONE
                bPopup1.visibility = View.GONE

                tvPopup2.text = "К-во легких вопросов: ${quizEntity.numQ}"
                tvPopup1.text = "К-во сложных вопросов: ${quizEntity.numHQ}"
                tvPopup3.text = "Дата создания: ${quizEntity.data}"
                tvPopup4.text = "Рейтинг квеста: ${quizEntity.ratingPlayer}/3"
                tvPopup5.text = "Ваш лучший результат: ${quizEntity.stars}/120"
                tvPopup6.text = "Ваш средний результат: ${quizEntity.starsAll}/120"
                tvPopup7.text = "Средний результат всх игроков: ${quizEntity.starsAllPlayer}/120"
                tvPopup8.text = "Лучший результат всех игроков: ${quizEntity.starsPlayer}/120"
                tvPopup9.text = "Версия квеста: ${quizEntity.versionQuiz}"
            }

            popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val popupWidth = popupView.measuredWidth
            val popupHeight = popupView.measuredHeight
            val touchX = event.rawX
            val touchY = event.rawY
            popupWindow.width = popupWidth.toInt()
            popupWindow.height = popupHeight
            popupWindow.showAtLocation(
                itemView,
                Gravity.NO_GRAVITY,
                touchX.toInt() + 16,
                touchY.toInt() + 16
            )
        }

        @OptIn(InternalCoroutinesApi::class)
        private fun translateText(
            viewModel: MainActivityViewModel,
            context: Context,
            quizEntity: QuizEntity
        ) {
            val questionList = viewModel.getQuestionListByIdQuiz(quizEntity.id!!)

            log("dawdawdf $questionList, $adapterPosition")
            // Создаем мапу, где ключом будет numQuestion, а значением - элемент Question с наибольшим lvlTranslate
            val questionMap: MutableMap<Int, QuestionEntity> = mutableMapOf()

            for (question in questionList) {

                log("dawdawdf $question")
                val existingQuestion = questionMap[question.numQuestion]

                if (existingQuestion == null || question.lvlTranslate > existingQuestion.lvlTranslate) {
                    log("dawdawdf add")
                    questionMap[question.numQuestion] = question
                }
            }

            val filteredQuestionList: List<QuestionEntity> = questionMap.values.toList()
            var i = filteredQuestionList.size
            Thread {
                translateToUserLanguage(filteredQuestionList).forEach {
                    viewModel.insertQuestion(it)
                    log("dawdawdf $i")
                    i--
                    if (i == 0) context.runOnUiThread {
                        viewModel.updateQuiz(
                            quizEntity.copy(
                                languages = removeDuplicateWordsFromLanguages(
                                    if (quizEntity.languages.isNotEmpty()) "${quizEntity.languages}|${Locale.getDefault().language}-100"
                                    else "${Locale.getDefault().language}-100"
                                ), versionQuiz = quizEntity.versionQuiz + 1
                            )
                        )
                        Toast.makeText(context, "Перевод завершен успешно", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }.start()

        }

        fun removeDuplicateWordsFromLanguages(input: String): String {
            val languages = input.split("|") // Разделение строки на отдельные языки
            val uniqueLanguages =
                languages.map { removeDuplicateWords(it) } // Удаление дубликатов слов для каждого языка
            return uniqueLanguages.joinToString("|") // Объединение языков обратно в строку
        }

        private fun removeDuplicateWords(input: String): String {
            val words = input.split(" ")
            val uniqueWords = words.distinct()
            return uniqueWords.joinToString(" ")
        }

        private fun translateToUserLanguage(questionList: List<QuestionEntity>): List<QuestionEntity> {
            // Инициализируем объект Translate с помощью ключа API
            val translate: com.google.cloud.translate.Translate? = TranslateOptions.newBuilder()
                .setApiKey("AIzaSyDlxFuYjnOxDRPPdkG0Qr91TmPIUM193cY")
                .build()
                .service

            // Создаем пустой список для хранения переведенных вопросов
            val translatedQuestionList: MutableList<QuestionEntity> = mutableListOf()

            log("dawdawdf translateToUserLanguage")
            // Перебираем каждый вопрос в исходном списке и выполняем перевод
            for (question in questionList) {
                // Получаем текст для перевода
                val textToTranslate = question.nameQuestion
                val sourceLanguage = question.language

                val userLanguage: String = Locale.getDefault().language

                log("dawdawdf question $question")
                // Выполняем перевод
                log("dawdawdf Thread")
                val translatedText = try {

                    val translation: Translation = translate!!.translate(
                        textToTranslate,
                        com.google.cloud.translate.Translate.TranslateOption.sourceLanguage(
                            sourceLanguage
                        ),
                        com.google.cloud.translate.Translate.TranslateOption.targetLanguage(
                            userLanguage
                        )
                    )

                    translation.translatedText
                } catch (e: Exception) {
                    textToTranslate
                }

                val translatedQuestion = question.copy(
                    nameQuestion = translatedText,
                    language = userLanguage,
                    lvlTranslate = 100,
                    infoTranslater = "0|0"
                )

                translatedQuestionList.add(translatedQuestion)
            }

            return translatedQuestionList
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

    companion object {
        const val POPUP_TRANSLATE = 1
        const val POPUP_STARS = 2
        const val POPUP_LIFE = 3
        const val POPUP_LIFE_GOLD = 4

    }

    @OptIn(InternalCoroutinesApi::class)
    fun log(msg: String) {
        Logcat.log(msg, "MainActivityAdapter", Logcat.LOG_ACTIVITY)
    }
}