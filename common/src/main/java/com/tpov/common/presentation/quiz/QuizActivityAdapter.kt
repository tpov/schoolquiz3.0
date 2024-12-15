package com.tpov.common.presentation.quiz

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.GranularRoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.tpov.common.MAX_PERCENT_HARD_QUIZ_FULL
import com.tpov.common.MAX_PERCENT_LIGHT_QUIZ_FULL
import com.tpov.common.PERCENT_1STAR_QUIZ_SHORT
import com.tpov.common.R
import com.tpov.common.RATING_QUIZ_ARENA_IN_TOP
import com.tpov.common.data.model.local.FlattenedQuizData
import com.tpov.common.databinding.ActivityQuizItemBinding
import com.tpov.common.presentation.utils.ResizeAndCrop
import com.tpov.log_api.logger.Logger
import com.tpov.shop.CoastValues.CoastValuesNolics.COAST_SEND_QUIZ
import kotlinx.coroutines.InternalCoroutinesApi
import java.io.File

@Logger
class QuizActivityAdapter @OptIn(InternalCoroutinesApi::class) constructor(
    private val listener: Listener,
    private val context: Context,
    private val viewModel: QuizActivityViewModel
) :
    ListAdapter<FlattenedQuizData, QuizActivityAdapter.ItemHolder>(ItemComparator()) {
    var onDeleteButtonClick: ((RecyclerView.ViewHolder) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
        return ItemHolder.create(parent, listener)
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onBindViewHolder(holder: ItemHolder, position: Int) {
        val item = getItem(position)
        holder.setData(item, listener, context, viewModel)
    }


    class ItemComparator : DiffUtil.ItemCallback<FlattenedQuizData>() {
        override fun areItemsTheSame(oldItem: FlattenedQuizData, newItem: FlattenedQuizData): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FlattenedQuizData, newItem: FlattenedQuizData): Boolean {
            return oldItem == newItem
        }
    }

    class ItemHolder(view: View, private val listener: Listener) : RecyclerView.ViewHolder(view),
        View.OnTouchListener {

        val constraintLayout: ConstraintLayout = itemView.findViewById(R.id.constraint_layout)

        private val binding = ActivityQuizItemBinding.bind(view)
        val imvGradLightQuiz: ImageView = itemView.findViewById(R.id.imv_gradient_light_quiz)
        val imvGradHardQuiz: ImageView = itemView.findViewById(R.id.imv_grafient_hard_quiz)

        @OptIn(InternalCoroutinesApi::class)
        private fun showDialog(
            context: Context,
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
            mainViewModel: QuizActivityViewModel
        ) {
            val popupMenu = PopupMenu(view.context, view)
            popupMenu.inflate(R.menu.popup_menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_send -> {
                        showDialog(context, COAST_SEND_QUIZ, id)
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
            quizEntity: FlattenedQuizData,
            listener: Listener,
            context: Context,
            mainViewModel: QuizActivityViewModel
        ) = with(binding) {

            try {

                val file = File(context.cacheDir, "${quizEntity.picture}")

                fun dpToPx(dp: Int, context: Context): Int {
                    val density = context.resources.displayMetrics.density
                    return (dp * density).toInt()
                }

                val widthInDp = 100 // don`t edit
                val heightInDp = 75// don`t edit
                val radius = 25// don`t edit

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

            } catch (e: Exception) {}

            val goHardQuiz = "${context.getString(R.string.go_hard_question)} - ${quizEntity.name}"
            initView(quizEntity, goHardQuiz, mainViewModel, listener)
        }

        @OptIn(InternalCoroutinesApi::class)
        private fun ActivityQuizItemBinding.initViewQuiz5(
            quizEntity: FlattenedQuizData,
            viewModel: QuizActivityViewModel,
            listener: Listener
        ) {

            chbTypeQuiz.visibility = View.GONE
            imvGradLightQuiz.visibility = View.GONE
            imvGradHardQuiz.visibility = View.GONE

            chbTypeQuiz.isChecked = false

            if (quizEntity.starsMaxLocal >= MAX_PERCENT_LIGHT_QUIZ_FULL) {
                imvGradLightQuiz.visibility = View.VISIBLE
                imvGradHardQuiz.visibility = View.GONE
                chbTypeQuiz.isChecked = true
            }

            if (quizEntity.ratingLocal >= RATING_QUIZ_ARENA_IN_TOP) {
                imvGradLightQuiz.visibility = View.GONE
                imvGradHardQuiz.visibility = View.VISIBLE
            }

            chbTypeQuiz.visibility = View.VISIBLE
            chbTypeQuiz.isChecked = quizEntity.starsMaxLocal >= MAX_PERCENT_LIGHT_QUIZ_FULL

            ratingBar.rating = (quizEntity.ratingLocal.toFloat() / PERCENT_1STAR_QUIZ_SHORT)
            ratingBar.rating = quizEntity.ratingLocal.toFloat() / MAX_PERCENT_LIGHT_QUIZ_FULL
            mainTitleButton.text = quizEntity.name

            mainTitleButton.setOnClickListener {
                listener.onClick(quizEntity.id, chbTypeQuiz.isChecked)
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
        private fun ActivityQuizItemBinding.initView(
            quizEntity: FlattenedQuizData,
            goHardQuiz: String,
            viewModel: QuizActivityViewModel,
            listener: Listener
        ) {
            if (quizEntity.starsMaxLocal == MAX_PERCENT_LIGHT_QUIZ_FULL) {
                Toast.makeText(binding.root.context, goHardQuiz, Toast.LENGTH_SHORT).show()
            }

            when (quizEntity.starsMaxLocal) {
                in MAX_PERCENT_LIGHT_QUIZ_FULL until MAX_PERCENT_HARD_QUIZ_FULL -> {
                    imvGradLightQuiz.visibility = View.VISIBLE
                    imvGradHardQuiz.visibility = View.GONE
                    chbTypeQuiz.isChecked = true
                }

                else -> {
                    chbTypeQuiz.visibility = View.GONE
                    imvGradLightQuiz.visibility = View.GONE
                    imvGradHardQuiz.visibility = View.GONE
                    chbTypeQuiz.isChecked = false
                }
            }
            if (quizEntity.starsMaxLocal <= MAX_PERCENT_LIGHT_QUIZ_FULL) ratingBar.rating =
                (quizEntity.starsMaxLocal.toFloat() / 50F)
            else ratingBar.rating = (((quizEntity.starsMaxLocal.toFloat() - 100F) / 20F) + 2F)

            ratingBar.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    showPopupInfo(quizEntity, event, POPUP_STARS, viewModel)
                }
                true
            }

            mainTitleButton.text = quizEntity.name
            mainTitleButton.setOnClickListener {
                listener.onClick(quizEntity.id, chbTypeQuiz.isChecked)
            }
        }

        companion object {

            fun create(parent: ViewGroup, listener: Listener): ItemHolder {
                return ItemHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.activity_quiz_item, parent, false),
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
}