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
import kotlinx.android.synthetic.main.activity_main_item.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

        //val deleteButton: ImageButton = itemView.findViewById(R.id.delete_button_swipe)
        private val binding = ActivityMainItemBinding.bind(view)
        val editButton: ImageButton = itemView.findViewById(R.id.edit_button_swipe)
        val deleteButton: ImageButton = itemView.findViewById(R.id.delete_button_swipe)
        val sendButton: ImageButton = itemView.findViewById(R.id.send_button_swipe)
        val imvGradLightQuiz: ImageView = itemView.findViewById(R.id.imv_gradient_light_quiz)
        val imvGradHardQuiz: ImageView = itemView.findViewById(R.id.imv_grafient_hard_quiz)
        val imvTranslate: ImageView = itemView.findViewById(R.id.imv_translate)
        val chbTypeQuiz: CheckBox = itemView.findViewById(R.id.chb_type_quiz)

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
        fun setData(quizEntity: QuizEntity, listener: Listener, context: Context, viewModel: MainActivityViewModel) = with(binding) {

            log("")
            GlobalScope.launch {
                withContext(Dispatchers.IO) {
                    if (viewModel.getQuizById(quizEntity.id!!).showItemMenu) {
                        constraintLayout.setOnLongClickListener {
                            showPopupMenu(it, quizEntity.id!!)
                            true
                        }

                } else constraintLayout.visibility = View.VISIBLE
            }}
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

            if (quizEntity.stars == MAX_PERCENT) {
                Toast.makeText(binding.root.context, goHardQuiz, Toast.LENGTH_SHORT).show()
            }

            log("quizEntity.stars = ${quizEntity.stars}")
            log("quizEntity.stars position = $position")

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
            GlobalScope.launch {
                withContext(Dispatchers.IO) {
                    val lvlTranslate = viewModel.getLvlTranslateByQuizId(quizEntity.id!!)
                    //imvTranslate
                    if (lvlTranslate <= 100) imvTranslate.setColorFilter(Color.GRAY)
                    else if (lvlTranslate <= 200) imvTranslate.setColorFilter(Color.YELLOW)
                    else imvTranslate.setColorFilter(Color.BLUE)
                }}
            if (quizEntity.stars <= MAX_PERCENT) ratingBar.rating =
                (quizEntity.stars.toFloat() / 50)
            else ratingBar.rating = (((quizEntity.stars.toFloat() - 100) / 20) + 2)

            mainTitleButton.text = quizEntity.nameQuiz
            mainTitleButton.setOnClickListener {
                listener.onClick(quizEntity.id!!, chbTypeQuiz.isChecked)
            }
            imvGradHardQuiz.setOnClickListener {
                listener.onClick(quizEntity.id!!, chbTypeQuiz.isChecked)
            }
            imvGradLightQuiz.setOnClickListener {
                listener.onClick(quizEntity.id!!, chbTypeQuiz.isChecked)
            }
            imvTranslate.setOnClickListener {
                listener.onClick(quizEntity.id!!, chbTypeQuiz.isChecked)
            }
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