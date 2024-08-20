package com.tpov.schoolquiz.presentation.main

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.GranularRoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.tpov.common.CoastValues.CoastValuesNolics.COAST_SEND_QUIZ
import com.tpov.common.EVENT_QUIZ_ARENA
import com.tpov.common.EVENT_QUIZ_HOME
import com.tpov.common.LVL_TRANSLATOR_1_LVL
import com.tpov.common.LVL_TRANSLATOR_2_LVL
import com.tpov.common.MAX_PERCENT_HARD_QUIZ_FULL
import com.tpov.common.MAX_PERCENT_LIGHT_QUIZ_FULL
import com.tpov.common.PERCENT_1STAR_QUIZ_SHORT
import com.tpov.common.RATING_QUIZ_ARENA_IN_TOP
import com.tpov.common.data.model.local.QuizEntity
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.databinding.ActivityMainItemBinding
import com.tpov.schoolquiz.presentation.core.Logcat
import com.tpov.schoolquiz.presentation.core.ResizeAndCrop
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager.getTpovId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import java.io.File

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
                .setTitle(context.getString(R.string.send_to_arena_title))
                .setMessage(context.getString(R.string.send_to_arena_text))
                .setPositiveButton("(-) $nolics nolics") { _, _ ->

                    listener.sendItem(id)
                }
                .setNegativeButton(context.getString(R.string.send_to_arena_negative), null)
                .create()

            alertDialog.setOnShowListener { dialog ->
                val positiveButton =
                    (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
                val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

                positiveButton.setTextColor(Color.WHITE)
                negativeButton.setTextColor(Color.YELLOW)

                dialog.window?.setBackgroundDrawableResource(R.drawable.db_design3_main)
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
                    ).into(imageView)

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

            if (quizEntity.starsMaxLocal >= MAX_PERCENT_LIGHT_QUIZ_FULL) {
                log("quizEntity.stars 1")
                imvGradLightQuiz.visibility = View.VISIBLE
                imvGradHardQuiz.visibility = View.GONE
                chbTypeQuiz.isChecked = true
            }

            if (quizEntity.ratingLocal >= RATING_QUIZ_ARENA_IN_TOP) {
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
            chbTypeQuiz.isChecked = quizEntity.starsMaxLocal >= MAX_PERCENT_LIGHT_QUIZ_FULL

            imvTranslate.imageAlpha = 85
            ratingBar.rating = (quizEntity.ratingLocal.toFloat() / PERCENT_1STAR_QUIZ_SHORT)

            log("sefefefe, ${(quizEntity.ratingLocal.toFloat() * 100 / PERCENT_1STAR_QUIZ_SHORT)}")
            val lvlTranslate = 100

            //imvTranslate
            log("sefefefe, $lvlTranslate")
            if (lvlTranslate < LVL_TRANSLATOR_1_LVL) imvTranslate.setColorFilter(Color.GRAY)
            else if (lvlTranslate < LVL_TRANSLATOR_2_LVL) imvTranslate.setColorFilter(Color.YELLOW)
            else imvTranslate.setColorFilter(Color.BLUE)

            log("daegrjg, ${quizEntity.ratingLocal.toFloat()}")
            ratingBar.rating = quizEntity.ratingLocal.toFloat() / MAX_PERCENT_LIGHT_QUIZ_FULL
            mainTitleButton.text = quizEntity.nameQuiz

            mainTitleButton.setOnClickListener {
                listener.onClick(quizEntity.id!!, chbTypeQuiz.isChecked)
            }

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
            tvTime.text = quizEntity.dataUpdate
        }

        @OptIn(InternalCoroutinesApi::class)
        private fun ActivityMainItemBinding.initView(
            quizEntity: QuizEntity,
            goHardQuiz: String,
            viewModel: MainActivityViewModel,
            listener: Listener
        ) {
            if (quizEntity.starsMaxLocal == MAX_PERCENT_LIGHT_QUIZ_FULL) {
                Toast.makeText(binding.root.context, goHardQuiz, Toast.LENGTH_SHORT).show()
            }

            log("quizEntity.stars")
            when (quizEntity.starsMaxLocal) {
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

            val lvlTranslate = 100

            log("sefefefe, $lvlTranslate")
            if (lvlTranslate < LVL_TRANSLATOR_1_LVL) imvTranslate.setColorFilter(Color.GRAY)
            else if (lvlTranslate < LVL_TRANSLATOR_2_LVL) imvTranslate.setColorFilter(Color.YELLOW)
            else imvTranslate.setColorFilter(Color.BLUE)
            if (quizEntity.starsMaxLocal <= MAX_PERCENT_LIGHT_QUIZ_FULL) ratingBar.rating =
                (quizEntity.starsMaxLocal.toFloat() / 50F)
            else ratingBar.rating = (((quizEntity.starsMaxLocal.toFloat() - 100F) / 20F) + 2F)

            log("sefefefe ${(quizEntity.starsMaxLocal.toFloat() / 50F) }, ${ratingBar.rating}")


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


                    CoroutineScope(Dispatchers.IO).launch {

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

                dialog.window?.setBackgroundDrawableResource(R.drawable.db_design3_main)
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