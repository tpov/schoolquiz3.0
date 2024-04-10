package com.tpov.schoolquiz.presentation.main

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.Html
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
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
import com.tpov.schoolquiz.presentation.COUNT_STARS_QUIZ_RATING
import com.tpov.schoolquiz.presentation.DEFAULT_INFO_TRANSLATOR_BY_GOOGLE_TRANSL
import com.tpov.schoolquiz.presentation.EVENT_QUIZ_ARENA
import com.tpov.schoolquiz.presentation.EVENT_QUIZ_HOME
import com.tpov.schoolquiz.presentation.LVL_TRANSLATOR_1_LVL
import com.tpov.schoolquiz.presentation.LVL_TRANSLATOR_2_LVL
import com.tpov.schoolquiz.presentation.MAX_PERCENT_HARD_QUIZ_FULL
import com.tpov.schoolquiz.presentation.MAX_PERCENT_LIGHT_QUIZ_FULL
import com.tpov.schoolquiz.presentation.PERCENT_1STAR_QUIZ_SHORT
import com.tpov.schoolquiz.presentation.RATING_QUIZ_ALL_POINTS_IN_FB
import com.tpov.schoolquiz.presentation.RATING_QUIZ_ARENA_IN_TOP
import com.tpov.schoolquiz.presentation.SPLIT_BETWEEN_LANGUAGES
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesNolics.COAST_SEND_QUIZ
import com.tpov.schoolquiz.presentation.core.CoastValues.CoastValuesNolics.COEF_COAST_GOOGLE_TRANSLATE
import com.tpov.schoolquiz.presentation.core.Logcat
import com.tpov.schoolquiz.presentation.core.ResizeAndCrop
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager.getTpovId
import com.tpov.schoolquiz.presentation.core.TranslateGoogle.translateText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

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

        @OptIn(InternalCoroutinesApi::class)
        private fun showDialog(
            context: Context,
            mainViewModel: MainActivityViewModel,
            nolics: Int,
            id: Int
        ) {
            val alertDialog = AlertDialog.Builder(context)
                .setTitle(Html.fromHtml("<font color='#FFFFFF'>" + context.getString(R.string.send_to_arena_title) + "</font>"))
                .setMessage(context.getString(R.string.send_to_arena_text))
                .setPositiveButton("(-) $nolics nolics") { _, _ ->
                    // Ваш код обработчика нажатия кнопки "Положительно"
                }
                .setNegativeButton(context.getString(R.string.send_to_arena_negative), null)
                .create()

            alertDialog.setOnShowListener { dialog ->
                val positiveButton = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
                val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

                positiveButton.setTextColor(Color.WHITE)
                negativeButton.setTextColor(Color.WHITE) // Установите белый цвет для кнопок отмены

                dialog.window?.setBackgroundDrawableResource(R.color.back_main_top)
            }

            alertDialog.show()
        }

        @OptIn(InternalCoroutinesApi::class)
        private fun showPopupMenu(
            view: View,
            id: Int,
            context: Context,
            mainViewModel: MainActivityViewModel
        ) {
            val popupMenu = PopupMenu(view.context, view)
            popupMenu.inflate(R.menu.popup_menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_send -> {
                        showDialog(context, mainViewModel, COAST_SEND_QUIZ, id)
                        true
                    }

                    R.id.menu_delete -> {
                        listener.deleteItem(id)
                        true
                    }

                    R.id.menu_edit -> {
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
            mainViewModel: MainActivityViewModel
        ) = with(binding) {

            if (quizEntity.event != EVENT_QUIZ_HOME) constraintLayout.setOnLongClickListener {
                showPopupMenu(it, quizEntity.id!!, context, mainViewModel)
                true
            }

            try {

                val file = File(context.cacheDir, "${quizEntity.picture}")

                fun dpToPx(dp: Int, context: Context): Int {
                    val density = context.resources.displayMetrics.density
                    return (dp * density).toInt()
                }

                val widthInDp = 100
                val heightInDp = 75
                val radius = 10

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
                "${context.getString(R.string.go_hard_question)} - ${quizEntity.nameQuiz}"

            if (quizEntity.event == EVENT_QUIZ_ARENA) initViewQuiz5(
                quizEntity,
                mainViewModel,
                listener
            )
            else initView(quizEntity, goHardQuiz, mainViewModel, listener)

        }

        @OptIn(InternalCoroutinesApi::class)
        private fun ActivityMainItemBinding.initViewQuiz5(
            quizEntity: QuizEntity,
            viewModel: MainActivityViewModel,
            listener: Listener
        ) {

            log("quizEntity.stars 3")
            chbTypeQuiz.visibility = View.GONE
            imvGradLightQuiz.visibility = View.GONE
            imvGradHardQuiz.visibility = View.GONE

            chbTypeQuiz.isChecked = false

            if (quizEntity.stars >= MAX_PERCENT_LIGHT_QUIZ_FULL) {
                log("quizEntity.stars 1")
                imvGradLightQuiz.visibility = View.VISIBLE
                imvGradHardQuiz.visibility = View.GONE
                chbTypeQuiz.isChecked = true
            }

            if (quizEntity.ratingPlayer >= RATING_QUIZ_ARENA_IN_TOP) {
                log("quizEntity.stars 2")
                imvGradLightQuiz.visibility = View.GONE
                imvGradHardQuiz.visibility = View.VISIBLE
            }

            if (quizEntity.tpovId == getTpovId()) {
                imvGradLightQuiz.visibility = View.GONE
                imvGradHardQuiz.visibility = View.GONE
                imvGradientTranslateQuiz.visibility = View.VISIBLE
            }

            chbTypeQuiz.visibility = View.VISIBLE
            chbTypeQuiz.isChecked = quizEntity.stars >= MAX_PERCENT_LIGHT_QUIZ_FULL

            imvTranslate.imageAlpha = 85
            ratingBar.rating = (quizEntity.ratingPlayer.toFloat() / PERCENT_1STAR_QUIZ_SHORT)

            log("sefefefe, ${(quizEntity.ratingPlayer.toFloat() * 100 / PERCENT_1STAR_QUIZ_SHORT)}")
            val lvlTranslate = viewModel.findValueForDeviceLocale(quizEntity.id!!)

            //imvTranslate
            log("sefefefe, $lvlTranslate")
            if (lvlTranslate < LVL_TRANSLATOR_1_LVL) imvTranslate.setColorFilter(Color.GRAY)
            else if (lvlTranslate < LVL_TRANSLATOR_2_LVL) imvTranslate.setColorFilter(Color.YELLOW)
            else imvTranslate.setColorFilter(Color.BLUE)

            log("daegrjg, ${quizEntity.ratingPlayer.toFloat()}")
            ratingBar.rating = quizEntity.ratingPlayer.toFloat() / MAX_PERCENT_LIGHT_QUIZ_FULL
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

            imvTranslate.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_UP)
                    showPopupInfo(quizEntity, event, POPUP_TRANSLATE, viewModel)
                true
            }

            ratingBar.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_UP)
                    showPopupInfo(quizEntity, event, POPUP_STARS, viewModel)
                true
            }

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
            if (quizEntity.stars == MAX_PERCENT_LIGHT_QUIZ_FULL) {
                Toast.makeText(binding.root.context, goHardQuiz, Toast.LENGTH_SHORT).show()
            }

            log("quizEntity.stars")
            when (quizEntity.stars) {
                in MAX_PERCENT_LIGHT_QUIZ_FULL until MAX_PERCENT_HARD_QUIZ_FULL -> {
                    log("quizEntity.stars 1")
                    imvGradLightQuiz.visibility = View.VISIBLE
                    imvGradHardQuiz.visibility = View.GONE
                    if (quizEntity.numHQ > 0) chbTypeQuiz.visibility = View.VISIBLE
                    chbTypeQuiz.isChecked = true

                }
                MAX_PERCENT_HARD_QUIZ_FULL -> {
                    log("quizEntity.stars 2")
                    if (quizEntity.numHQ > 0) chbTypeQuiz.visibility = View.VISIBLE
                    imvGradLightQuiz.visibility = View.GONE
                    imvGradHardQuiz.visibility = View.VISIBLE
                    chbTypeQuiz.isChecked = true

                }
                else -> {
                    log("quizEntity.stars 3")
                    chbTypeQuiz.visibility = View.GONE
                    imvGradLightQuiz.visibility = View.GONE
                    imvGradHardQuiz.visibility = View.GONE
                    chbTypeQuiz.isChecked = false
                }
            }

            imvTranslate.imageAlpha = 85

            val lvlTranslate = viewModel.findValueForDeviceLocale(quizEntity.id!!)

            log("sefefefe, $lvlTranslate")
            if (lvlTranslate < LVL_TRANSLATOR_1_LVL) imvTranslate.setColorFilter(Color.WHITE)
            else if (lvlTranslate < LVL_TRANSLATOR_2_LVL) imvTranslate.setColorFilter(R.color.gold)
            else imvTranslate.setColorFilter(R.color.contour)
            if (quizEntity.stars <= MAX_PERCENT_LIGHT_QUIZ_FULL) ratingBar.rating =
                (quizEntity.stars.toFloat() / 50F)
            else ratingBar.rating = (((quizEntity.stars.toFloat() - 100F) / 20F) + 2F)

            log("sefefefe ${(quizEntity.stars.toFloat() / 50F) }, ${ratingBar.rating}")


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
                        translateText(mainViewModel, context, quizEntity)
                    }
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

        @OptIn(InternalCoroutinesApi::class)
        private fun showPopupInfo(
            quizEntity: QuizEntity,
            event: MotionEvent,
            popupType: Int,
            viewModel: MainActivityViewModel
        ) {
            val context = itemView.context
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val popupView = inflater.inflate(R.layout.translation_popup_layout, null)
            val popupWindow = PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )

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
                    showDialogTranslate(
                        context,
                        viewModel,
                        (quizEntity.numHQ + quizEntity.numQ) * COEF_COAST_GOOGLE_TRANSLATE,
                        quizEntity,
                        popupWindow
                    )
                }

                val languageString = quizEntity.languages // Получите строку языков из quizEntity
                val languageItems =
                    languageString.split(SPLIT_BETWEEN_LANGUAGES) // Разделите строку на элементы

                val languageMap =
                    mutableMapOf<String?, Int?>() // Создайте пустой Map для хранения соответствия язык-число

                if (languageItems.isNotEmpty()) {
                    for (item in languageItems) {
                        val parts =
                            item.split(DEFAULT_INFO_TRANSLATOR_BY_GOOGLE_TRANSL) // Разделите элемент на ключ и значение
                        if (parts.size == 2) {
                            val language = parts[0].toLowerCase()
                            val value = parts[1].toIntOrNull()
                            if (value != null) {
                                languageMap[language] =
                                    value // Добавьте соответствие язык-число в Map
                            }
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
                    tvPopup1.text = "lvl translate:: ${languageMap.values.toList()[0]}"
                    binding.mainTitleButton.isClickable = true
                    binding.mainTitleButton.isEnabled = true
                } catch (e: Exception) {
                    tvPopup1.text = context.getString(R.string.quiz_not_translated)
                    binding.mainTitleButton.isClickable = false
                    binding.mainTitleButton.isEnabled = false
                }
                spListPopup1.adapter = adapter
                spListPopup1.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        val selectedLanguage =
                            spListPopup1.selectedItem.toString().lowercase(Locale.ROOT)
                        val selectedValue = languageMap[selectedLanguage]
                        if (selectedValue != null) {
                            tvPopup1.text = "lvl translate: $selectedValue"
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {
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

                tvPopup2.text = context.getString(R.string.popup_menu_num_easy_questions, quizEntity.numQ)
                tvPopup1.text = context.getString(R.string.popup_menu_num_hard_questions, quizEntity.numHQ)
                tvPopup3.text = context.getString(R.string.popup_menu_date_created, quizEntity.data)
                tvPopup4.text = context.getString(
                    R.string.popup_menu_rating,
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

        companion object {

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