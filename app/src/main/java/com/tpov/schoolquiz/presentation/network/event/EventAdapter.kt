package com.tpov.schoolquiz.presentation.network.event

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.*
import android.widget.*
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.presentation.*
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesNolics.COEF_COAST_GOOGLE_TRANSLATE
import com.tpov.schoolquiz.presentation.core.TranslateGoogle.translateText
import com.tpov.schoolquiz.presentation.main.MainActivityAdapter
import com.tpov.schoolquiz.presentation.main.MainActivityViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import java.util.*

interface DataObserver {
    fun onDataUpdated()
}

class EventAdapter @OptIn(InternalCoroutinesApi::class) constructor(
    private val quiz2List: List<QuizEntity>,
    private val quiz3List: List<QuizEntity>,
    private val quiz4List: List<QuizEntity>,
    private val translate1EventList: List<QuestionEntity>,
    private val translate2EventList: List<QuestionEntity>,
    private val translateEditQuestionList: List<QuestionEntity>,
    private val moderatorEventList: List<ChatEntity>,
    private val adminEventList: List<ChatEntity>,
    private val developerEventList: List<ChatEntity>,
    private val listener: ListenerEvent,
    private val viewModel: MainActivityViewModel
) : RecyclerView.Adapter<EventAdapter.MyViewHolder>(), DataObserver {

    private var dataObserver: DataObserver? = null

    fun setDataObserver(observer: DataObserver) {
        dataObserver = observer
    }

    override fun onDataUpdated() {

        size1 = quiz2List.size
        size2 = quiz3List.size + size1
        size3 = quiz4List.size + size2
        size4 = translate1EventList.size + size3
        size5 = translate2EventList.size + size4
        size6 = translateEditQuestionList.size + size5
        size7 = moderatorEventList.size + size6
        size8 = adminEventList.size + size7
        size9 = developerEventList.size + size8
        log("fun onDataUpdated() $size1")
        notifyDataSetChanged()
    }

    private val viewTypes by lazy {
        arrayOf(
            Pair(QUIZ2_LIST, quiz2List),
            Pair(QUIZ3_LIST, quiz3List),
            Pair(QUIZ4_LIST, quiz4List),
            Pair(TRANSLATE1_EVENT_LIST, translate1EventList),
            Pair(TRANSLATE2_EVENT_LIST, translate2EventList),
            Pair(TRANSLATE_EDIT_QUESTION_LIST, translateEditQuestionList),
            Pair(MODERATOR_EVENT_LIST, moderatorEventList),
            Pair(ADMIN_EVENT_LIST, adminEventList),
            Pair(DEVELOPER_EVENT_LIST, developerEventList),
            Pair(HEADER_VIEW, quiz2List)
        )
    }

    private var size1 = 0
    private var size2 = 0
    private var size3 = 0
    private var size4 = 0
    private var size5 = 0
    private var size6 = 0
    private var size7 = 0
    private var size8 = 0
    private var size9 = 0

    private val headers = arrayOf(
        "Quiz 2",
        "Quiz 3",
        "Quiz 4",
        "Translate 1",
        "Translate 2",
        "Translate Edit Question",
        "Moderator Event",
        "Admin Event",
        "Developer Event"
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {

        log("fun onCreateViewHolder()")
        return when (viewType) {
            HEADER_VIEW -> {
                val itemHeader = LayoutInflater.from(parent.context)
                    .inflate(R.layout.title_header, parent, false)
                MyViewHolder(itemHeader)
            }

            else -> {
                val itemAdminEvent = LayoutInflater.from(parent.context)
                    .inflate(R.layout.activity_main_item, parent, false)
                MyViewHolder(itemAdminEvent)
            }
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun getItemViewType(position: Int): Int {
        log("fun getItemViewType(), position: $position")
        log("fun getItemViewType(), size1:      $size1")
        log("fun getItemViewType(), size2:      $size2")
        log("fun getItemViewType(), size3:      $size3")
        log("fun getItemViewType(), size4:      $size4")
        log("fun getItemViewType(), size5:      $size5")
        log("fun getItemViewType(), size6:      $size6")
        log("fun getItemViewType(), size7:      $size7")
        log("fun getItemViewType(), size8:      $size8")
        log("fun getItemViewType(), size9:      $size9")
        log("fun getItemViewType(), translate1EventList:      $translate1EventList")
        log("fun getItemViewType(), translate1EventList:      ${translate1EventList.size}")
        log("fun getItemViewType(), _______________________________________")

        return if (position < size1) QUIZ2_LIST
        else if (position < size2) QUIZ3_LIST
        else if (position < size3) QUIZ4_LIST
        else if (position < size4 && viewModel.getProfile().translater!! > 100) TRANSLATE1_EVENT_LIST
        else if (position < size5 && viewModel.getProfile().translater!! > 200) TRANSLATE2_EVENT_LIST
        else if (position < size6 && viewModel.getProfile().translater!! > 200) TRANSLATE_EDIT_QUESTION_LIST
        else if (position < size7) MODERATOR_EVENT_LIST
        else if (position < size8) ADMIN_EVENT_LIST
        else if (position < size9) DEVELOPER_EVENT_LIST
        else -1
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        log("fun onBindViewHolder(), quiz2List: $quiz2List")

        when (getItemViewType(position)) {
            QUIZ2_LIST -> {
                val quiz = quiz2List[position]
                holder.bindQuiz2(quiz)
            }

            QUIZ3_LIST -> {
                val quiz = quiz3List[position - size1]
                holder.bindQuiz3(quiz)
            }

            QUIZ4_LIST -> {
                val quiz = quiz4List[position - size2]
                holder.bindQuiz4(quiz)
            }

            TRANSLATE1_EVENT_LIST -> {
                val question =
                    translate1EventList[position - size3]
                holder.bindTranslate1Event(question)
            }

            TRANSLATE2_EVENT_LIST -> {
                val question = translate2EventList[position - size4]
                holder.bindTranslate2Event(question)
            }

            TRANSLATE_EDIT_QUESTION_LIST -> {
                val question = translateEditQuestionList[position - size5]
                holder.bindTranslateEditQuestion(question)
            }

            MODERATOR_EVENT_LIST -> {
                val question = moderatorEventList[position - size6]
                holder.bindModeratorEvent(question)
            }

            ADMIN_EVENT_LIST -> {
                val quiz = adminEventList[position - size7]
                holder.bindAdminEvent(quiz)
            }

            DEVELOPER_EVENT_LIST -> {
                val quiz = developerEventList[position - size8]
                holder.bindDeveloperEvent(quiz)
            }

            HEADER_VIEW -> {
                val header = headers[position]
                holder.bindHeader(header)
            }

            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun getItemCount(): Int {

        return size9
    }


    @OptIn(InternalCoroutinesApi::class)
    private fun showDialogTranslate(
        context: Context,
        mainViewModel: MainActivityViewModel,
        nolics: Int,
        quizEntity: QuizEntity,
        popupWindow: PopupWindow,

        ) {
        val alertDialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.translate_title))
            .setMessage(context.getString(R.string.translate_message))
            .setPositiveButton("(-) $nolics nolics") { _, _ ->
                mainViewModel.profileUseCase.updateProfile(
                    mainViewModel.getProfile().copy(
                        pointsNolics = mainViewModel.getProfileNolic()!! - nolics
                    )
                )

                CoroutineScope(Dispatchers.IO).launch {
                translateText(viewModel, context, quizEntity)}
                popupWindow.dismiss()
            }
            .setNegativeButton(context.getString(R.string.translate_negative), null)
            .create()

        alertDialog.setOnShowListener { dialog ->
            val positiveButton =
                (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            positiveButton.setTextColor(Color.WHITE)
            negativeButton.setTextColor(Color.YELLOW)

            dialog.window?.setBackgroundDrawableResource(R.color.back_main_top)
        }
        alertDialog.show()
    }

    inner class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
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

            if (popupType == MainActivityAdapter.POPUP_TRANSLATE) {
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
                    showDialogTranslate(
                        context,
                        viewModel,
                        (quizEntity.numHQ + quizEntity.numQ) * COEF_COAST_GOOGLE_TRANSLATE,
                        quizEntity,
                        popupWindow
                    )
                }

                val languageString = quizEntity.languages // Получите строку языков из quizEntity
                val languageItems = languageString.split(SPLIT_BETWEEN_LANGUAGES) // Разделите строку на элементы

                val languageMap =
                    mutableMapOf<String?, Int?>()

                for (item in languageItems) {
                    val parts = item.split(SPLIT_BETWEEN_LVL_TRANSLATE_AND_LANG) // Разделите элемент на ключ и значение
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
                    tvPopup1.text = "lvl: ${languageMap.keys.toList()[0]}"
                } catch (e: Exception) {
                    tvPopup1.text = context.getString(R.string.quiz_not_translated)
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
            } else if (popupType == MainActivityAdapter.POPUP_STARS) {
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

                tvPopup2.text = context.getString(R.string.popup_menu_num_easy_questions, quizEntity.numQ)
                tvPopup1.text = context.getString(R.string.popup_menu_num_hard_questions, quizEntity.numHQ)
                tvPopup3.text = context.getString(R.string.popup_menu_date_created, quizEntity.data)
                tvPopup4.text = context.getString(R.string.popup_menu_rating,
                    quizEntity.ratingPlayer / RATING_QUIZ_ALL_POINTS_IN_FB,
                    COUNT_STARS_QUIZ_RATING
                )
                tvPopup5.text = context.getString(
                    R.string.popup_menu_best_result,
                    quizEntity.stars,
                    MAX_PERCENT_HARD_QUIZ_FULL
                )
                tvPopup6.text = context.getString(
                    R.string.popup_menu_average_result,
                    quizEntity.starsAll,
                    MAX_PERCENT_HARD_QUIZ_FULL
                )
                tvPopup7.text = context.getString(
                    R.string.popup_menu_avg_all_players,
                    quizEntity.starsAllPlayer,
                    MAX_PERCENT_HARD_QUIZ_FULL
                )
                tvPopup8.text = context.getString(
                    R.string.popup_menu_best_all_players,
                    quizEntity.starsPlayer,
                    MAX_PERCENT_HARD_QUIZ_FULL
                )
                tvPopup9.text = context.getString(R.string.popup_menu_quiz_version, quizEntity.versionQuiz)

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
        fun bindQuiz2(quiz: QuizEntity) {
            log("bindQuiz2")
            itemView.findViewById<Button>(R.id.main_title_button).text = quiz.nameQuiz
            itemView.findViewById<LinearLayout>(R.id.swipe_layout).visibility = View.GONE
            itemView.findViewById<ImageView>(R.id.imv_gradient_light_quiz).visibility = View.VISIBLE
            itemView.findViewById<ImageView>(R.id.imv_grafient_hard_quiz).visibility = View.GONE
            itemView.findViewById<TextView>(R.id.tvNumHardQuiz).visibility = View.GONE
            val ratingBar = itemView.findViewById<RatingBar>(R.id.ratingBar)
            ratingBar.visibility = View.GONE
            itemView.findViewById<AppCompatCheckBox>(R.id.chb_type_quiz).visibility = View.GONE
            itemView.findViewById<TextView>(R.id.tvNumQuestion).text = quiz.numQ.toString()
            itemView.findViewById<TextView>(R.id.tvNumHardQuiz).visibility = View.GONE
            itemView.findViewById<TextView>(R.id.tvNumQuestion).visibility = View.VISIBLE
            itemView.findViewById<TextView>(R.id.tvName).text = quiz.userName
            itemView.findViewById<TextView>(R.id.tvTime).text = quiz.data
            val imvTranslate = itemView.findViewById<ImageView>(R.id.imv_translate)
            val imageQuiz = itemView.findViewById<ImageView>(R.id.imageView)

            val lvlTranslate = viewModel.findValueForDeviceLocale(quiz.id!!)
            if (lvlTranslate < LVL_TRANSLATOR_1_LVL) imvTranslate.setColorFilter(Color.GRAY)
            else if (lvlTranslate < LVL_TRANSLATOR_2_LVL) imvTranslate.setColorFilter(Color.YELLOW)
            else imvTranslate.setColorFilter(Color.BLUE)

            if (quiz.picture != "") Picasso.get().load(quiz.picture).into(imageQuiz)
            imvTranslate.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    // Rating bar clicked, handle the event here
                    // You can call your method to show the translation popup/dialog
                    showPopupInfo(quiz, event, MainActivityAdapter.POPUP_TRANSLATE, viewModel)
                }
                true
            }

            imvTranslate.imageAlpha = 85
            ratingBar.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    // Rating bar clicked, handle the event here
                    // You can call your method to show the translation popup/dialog
                    showPopupInfo(quiz, event, MainActivityAdapter.POPUP_STARS, viewModel)
                }
                true
            }
            itemView.findViewById<Button>(R.id.main_title_button).setOnClickListener {
                log("bindQuiz2 setOnClickListener")
                listener.onQuiz2Clicked(quiz.id!!)
            }
        }

        @OptIn(InternalCoroutinesApi::class)
        fun bindQuiz3(quiz: QuizEntity) {
            log("bindQuiz3")
            itemView.findViewById<Button>(R.id.main_title_button).text = quiz.nameQuiz
            itemView.findViewById<LinearLayout>(R.id.swipe_layout).visibility = View.GONE
            itemView.findViewById<ImageView>(R.id.imv_gradient_light_quiz).visibility = View.GONE
            itemView.findViewById<ImageView>(R.id.imv_grafient_hard_quiz).visibility = View.VISIBLE
            itemView.findViewById<TextView>(R.id.tvNumHardQuiz).visibility = View.VISIBLE
            itemView.findViewById<AppCompatCheckBox>(R.id.chb_type_quiz).visibility = View.GONE
            itemView.findViewById<TextView>(R.id.tvNumQuestion).visibility = View.GONE
            itemView.findViewById<TextView>(R.id.tvNumHardQuiz).text = quiz.numHQ.toString()
            itemView.findViewById<TextView>(R.id.tvName).text = quiz.userName
            itemView.findViewById<TextView>(R.id.tvTime).text = quiz.data

            val imageQuiz = itemView.findViewById<ImageView>(R.id.imageView)
            if (quiz.picture != "") Picasso.get().load(quiz.picture).into(imageQuiz)

            val imvTranslate = itemView.findViewById<ImageView>(R.id.imv_translate)
            val lvlTranslate = viewModel.findValueForDeviceLocale(quiz.id!!)
            if (lvlTranslate < LVL_TRANSLATOR_1_LVL) imvTranslate.setColorFilter(Color.GRAY)
            else if (lvlTranslate < LVL_TRANSLATOR_2_LVL) imvTranslate.setColorFilter(Color.YELLOW)
            else imvTranslate.setColorFilter(Color.BLUE)
            val ratingBar = itemView.findViewById<RatingBar>(R.id.ratingBar)
            ratingBar.visibility = View.GONE

            imvTranslate.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    // Rating bar clicked, handle the event here
                    // You can call your method to show the translation popup/dialog
                    showPopupInfo(quiz, event, MainActivityAdapter.POPUP_TRANSLATE, viewModel)
                }
                true
            }

            imvTranslate.imageAlpha = 85
            ratingBar.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    // Rating bar clicked, handle the event here
                    // You can call your method to show the translation popup/dialog
                    showPopupInfo(quiz, event, MainActivityAdapter.POPUP_STARS, viewModel)
                }
                true
            }
            itemView.findViewById<Button>(R.id.main_title_button).setOnClickListener {
                log("bindQuiz3 setOnClickListener")
                listener.onQuiz3Clicked(quiz.id!!)
            }
        }

        @OptIn(InternalCoroutinesApi::class)
        fun bindQuiz4(quiz: QuizEntity) {
            log("bindQuiz4")
            val chbTypeQuiz = itemView.findViewById<AppCompatCheckBox>(R.id.chb_type_quiz)

            itemView.findViewById<Button>(R.id.main_title_button).text = quiz.nameQuiz
            itemView.findViewById<LinearLayout>(R.id.swipe_layout).visibility = View.GONE
            itemView.findViewById<TextView>(R.id.tvNumHardQuiz).visibility = View.VISIBLE
            itemView.findViewById<TextView>(R.id.tvNumQuestion).visibility = View.VISIBLE
            itemView.findViewById<RatingBar>(R.id.ratingBar).visibility = View.GONE
            chbTypeQuiz.visibility = if (quiz.numHQ == 0) View.GONE else View.VISIBLE
            itemView.findViewById<TextView>(R.id.tvNumHardQuiz).text = quiz.numHQ.toString()
            itemView.findViewById<TextView>(R.id.tvNumQuestion).text = quiz.numQ.toString()
            itemView.findViewById<TextView>(R.id.tvName).text = quiz.userName
            itemView.findViewById<TextView>(R.id.tvTime).text = quiz.data
            val imageQuiz = itemView.findViewById<ImageView>(R.id.imageView)
            if (quiz.picture != "") Picasso.get().load(quiz.picture).into(imageQuiz)

            var imvTranslateBindQuiz4 = itemView.findViewById<ImageView>(R.id.imv_translate)
            val lvlTranslate = viewModel.findValueForDeviceLocale(quiz.id!!)
            if (lvlTranslate < LVL_TRANSLATOR_1_LVL) imvTranslateBindQuiz4.setColorFilter(Color.GRAY)
            else if (lvlTranslate < LVL_TRANSLATOR_2_LVL) imvTranslateBindQuiz4.setColorFilter(Color.YELLOW)
            else imvTranslateBindQuiz4.setColorFilter(Color.BLUE)
            val ratingBar = itemView.findViewById<RatingBar>(R.id.ratingBar)
            val imvTranslate = itemView.findViewById<ImageView>(R.id.imv_translate)
            imvTranslate.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    // Rating bar clicked, handle the event here
                    // You can call your method to show the translation popup/dialog
                    showPopupInfo(quiz, event, MainActivityAdapter.POPUP_TRANSLATE, viewModel)
                }
                true
            }

            imvTranslate.imageAlpha = 85
            ratingBar.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    // Rating bar clicked, handle the event here
                    // You can call your method to show the translation popup/dialog
                    showPopupInfo(quiz, event, MainActivityAdapter.POPUP_STARS, viewModel)
                }
                true
            }
            itemView.findViewById<Button>(R.id.main_title_button).setOnClickListener {
                log("bindQuiz4 setOnClickListener")
                listener.onQuiz4Clicked(quiz.id!!)
            }
        }

        @OptIn(InternalCoroutinesApi::class)
        fun bindTranslate1Event(question: QuestionEntity) {
            log("bindTranslate1Event")
            itemView.findViewById<LinearLayout>(R.id.swipe_layout).visibility = View.GONE
            itemView.findViewById<Button>(R.id.main_title_button).text =
                "${question.language} - lvl:${question.lvlTranslate} \"${question.nameQuestion}\""
            itemView.findViewById<ImageView>(R.id.imv_gradient_translate_quiz).visibility =
                View.VISIBLE
            itemView.findViewById<RatingBar>(R.id.ratingBar).visibility = View.GONE
            itemView.findViewById<AppCompatCheckBox>(R.id.chb_type_quiz).visibility = View.GONE
            var imvTranslate = itemView.findViewById<ImageView>(R.id.imv_translate)
            val lvlTranslate = viewModel.findValueForDeviceLocale(question.idQuiz)
            if (lvlTranslate < LVL_TRANSLATOR_1_LVL) imvTranslate.setColorFilter(Color.GRAY)
            else if (lvlTranslate < LVL_TRANSLATOR_2_LVL) imvTranslate.setColorFilter(Color.YELLOW)
            else imvTranslate.setColorFilter(Color.BLUE)
            imvTranslate.imageAlpha = 85
            imvTranslate.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    // Rating bar clicked, handle the event here
                    // You can call your method to show the translation popup/dialog
                    showPopupInfo(
                        viewModel.getQuizById(question.idQuiz),
                        event,
                        MainActivityAdapter.POPUP_TRANSLATE,
                        viewModel
                    )
                }
                true
            }


            itemView.findViewById<Button>(R.id.main_title_button).setOnClickListener {
                log("bindTranslate1Event setOnClickListener")
                listener.onTranslate1EventClicked(question.id!!)
            }
        }

        @OptIn(InternalCoroutinesApi::class)
        fun bindTranslate2Event(question: QuestionEntity) {
            log("bindTranslate2Event")
            itemView.findViewById<LinearLayout>(R.id.swipe_layout).visibility = View.GONE
            itemView.findViewById<Button>(R.id.main_title_button).text =
                "${question.language} - lvl:${question.lvlTranslate} \"${question.nameQuestion}\""
            itemView.findViewById<ImageView>(R.id.imv_gradient_translate_quiz).visibility =
                View.VISIBLE
            itemView.findViewById<RatingBar>(R.id.ratingBar).visibility = View.GONE
            itemView.findViewById<AppCompatCheckBox>(R.id.chb_type_quiz).visibility = View.GONE
            val lvlTranslate = viewModel.findValueForDeviceLocale(question.idQuiz)
            var imvTranslate = itemView.findViewById<ImageView>(R.id.imv_translate)
            if (lvlTranslate < LVL_TRANSLATOR_1_LVL) imvTranslate.setColorFilter(Color.GRAY)
            else if (lvlTranslate < LVL_TRANSLATOR_2_LVL) imvTranslate.setColorFilter(Color.YELLOW)
            else imvTranslate.setColorFilter(Color.BLUE)
            imvTranslate.imageAlpha = 85
            imvTranslate.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    // Rating bar clicked, handle the event here
                    // You can call your method to show the translation popup/dialog
                    showPopupInfo(
                        viewModel.getQuizById(question.idQuiz),
                        event,
                        MainActivityAdapter.POPUP_TRANSLATE,
                        viewModel
                    )
                }
                true
            }
            itemView.findViewById<Button>(R.id.main_title_button).setOnClickListener {
                log("bindTranslate2Event setOnClickListener")
                listener.onTranslate2EventClicked(question.id!!)
            }
        }

        @OptIn(InternalCoroutinesApi::class)
        fun bindTranslateEditQuestion(question: QuestionEntity) {
            log("bindTranslateEditQuestion")
            itemView.findViewById<LinearLayout>(R.id.swipe_layout).visibility = View.GONE
            itemView.findViewById<Button>(R.id.main_title_button).text =
                "${question.language} - lvl:${question.lvlTranslate} \"${question.nameQuestion}\""
            itemView.findViewById<ImageView>(R.id.imv_gradient_translate_quiz).visibility =
                View.VISIBLE
            itemView.findViewById<RatingBar>(R.id.ratingBar).visibility = View.GONE
            itemView.findViewById<AppCompatCheckBox>(R.id.chb_type_quiz).visibility = View.GONE
            val lvlTranslate = viewModel.findValueForDeviceLocale(question.idQuiz)
            var imvTranslate = itemView.findViewById<ImageView>(R.id.imv_translate)
            if (lvlTranslate < LVL_TRANSLATOR_1_LVL) imvTranslate.setColorFilter(Color.GRAY)
            else if (lvlTranslate < LVL_TRANSLATOR_2_LVL) imvTranslate.setColorFilter(Color.YELLOW)
            else imvTranslate.setColorFilter(Color.BLUE)

            imvTranslate.imageAlpha = 85
            imvTranslate.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    showPopupInfo(
                        viewModel.getQuizById(question.idQuiz),
                        event,
                        MainActivityAdapter.POPUP_TRANSLATE,
                        viewModel
                    )
                }
                true
            }
            itemView.findViewById<Button>(R.id.main_title_button).setOnClickListener {
                log("bindTranslateEditQuestion setOnClickListener")
                listener.onTranslateEditQuestionClicked(question.id!!)
            }
        }

        fun bindModeratorEvent(chat: ChatEntity) {
            log("bindModeratorEvent")
            itemView.findViewById<LinearLayout>(R.id.swipe_layout).visibility = View.GONE
            itemView.findViewById<Button>(R.id.main_title_button).text = "${chat.msg}"
            itemView.findViewById<ImageView>(R.id.imv_gradient_translate_quiz).visibility =
                View.VISIBLE
            itemView.findViewById<RatingBar>(R.id.ratingBar).visibility = View.GONE
            itemView.findViewById<AppCompatCheckBox>(R.id.chb_type_quiz).visibility = View.GONE

            itemView.findViewById<Button>(R.id.main_title_button).setOnClickListener {
                log("bindModeratorEvent setOnClickListener")
                listener.onModeratorEventClicked(it.id)
            }
        }

        fun bindAdminEvent(chat: ChatEntity) {
            log("bindAdminEvent")
            itemView.findViewById<LinearLayout>(R.id.swipe_layout).visibility = View.GONE
            itemView.findViewById<Button>(R.id.main_title_button).text = "${chat.msg}"
            itemView.findViewById<ImageView>(R.id.imv_gradient_translate_quiz).visibility =
                View.VISIBLE
            itemView.findViewById<RatingBar>(R.id.ratingBar).visibility = View.GONE
            itemView.findViewById<AppCompatCheckBox>(R.id.chb_type_quiz).visibility = View.GONE

            itemView.findViewById<Button>(R.id.main_title_button).setOnClickListener {
                log("bindAdminEvent setOnClickListener")
                listener.onAdminEventClicked(it.id)
            }
        }

        fun bindDeveloperEvent(chat: ChatEntity) {
            log("bindDeveloperEvent")
            itemView.findViewById<LinearLayout>(R.id.swipe_layout).visibility = View.GONE
            itemView.findViewById<Button>(R.id.main_title_button).text = "${chat.msg}"
            itemView.findViewById<ImageView>(R.id.imv_gradient_translate_quiz).visibility =
                View.VISIBLE
            itemView.findViewById<RatingBar>(R.id.ratingBar).visibility = View.GONE
            itemView.findViewById<AppCompatCheckBox>(R.id.chb_type_quiz).visibility = View.GONE

            itemView.findViewById<Button>(R.id.main_title_button).setOnClickListener {
                log("bindDeveloperEvent setOnClickListener")
                listener.onDeveloperEventClicked(it.id)
            }
        }

        fun bindHeader(header: String) {
            itemView.findViewById<TextView>(R.id.tv_header).text = "$header"
        }
    }

    interface ListenerEvent {
        fun onQuiz2Clicked(quizId: Int)
        fun onQuiz3Clicked(quizId: Int)
        fun onQuiz4Clicked(quizId: Int)
        fun onTranslate1EventClicked(questionId: Int)
        fun onTranslate2EventClicked(questionId: Int)
        fun onTranslateEditQuestionClicked(questionId: Int)
        fun onModeratorEventClicked(quizId: Int)
        fun onAdminEventClicked(quizId: Int)
        fun onDeveloperEventClicked(quizId: Int)
    }

    companion object {

        private const val QUIZ2_LIST = 0
        private const val QUIZ3_LIST = 1
        private const val QUIZ4_LIST = 2
        private const val TRANSLATE1_EVENT_LIST = 3
        private const val TRANSLATE2_EVENT_LIST = 4
        private const val TRANSLATE_EDIT_QUESTION_LIST = 5
        private const val MODERATOR_EVENT_LIST = 6
        private const val ADMIN_EVENT_LIST = 7
        private const val DEVELOPER_EVENT_LIST = 8
        private const val HEADER_VIEW = 9

        // Add more types as needed
    }
}