package com.tpov.schoolquiz.presentation.mainactivity

import android.content.Context
import android.graphics.*
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
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
import kotlinx.coroutines.InternalCoroutinesApi
import java.io.File
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


class MainActivityAdapter @OptIn(InternalCoroutinesApi::class) constructor(
    private val listener: Listener,
    private val context: Context,
    private val viewModel: MainActivityViewModel
) :
    ListAdapter<QuizEntity, MainActivityAdapter.ItemHolder>(ItemComparator()) {
    var onDeleteButtonClick: ((RecyclerView.ViewHolder) -> Unit)? = null

    private var swipeEnabled = true
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

        log("fun onAttachedToRecyclerView()")
        var animate = false
        val itemTouchHelperCallback = object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                return makeMovementFlags(0, ItemTouchHelper.LEFT)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                log("onAttachedToRecyclerView onSwiped()")
                val position = viewHolder.adapterPosition
                // Получаем позицию свайпнутого элемента
                notifyItemChanged(position)
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val holder = viewHolder as ItemHolder
                    val width = holder.constraintLayout.width.toFloat()
                    val maxSwipe = -width * 0.9f // 90% от ширины экрана

                    val swipeSlowdownFactor = 0.5f

                    val adjustedDX = dX * swipeSlowdownFactor
                    val translationX = if (adjustedDX < 0) max(maxSwipe, adjustedDX) else min(
                        width * 0.2f,
                        adjustedDX
                    )

                    holder.constraintLayout.translationX = translationX

                    val showButtonsThreshold = -width * 0.4f // 40% от ширины экрана

                    if (!isCurrentlyActive) {
                        if (translationX <= showButtonsThreshold) {
                            if (viewHolder.itemView.delete_button_swipe.visibility != View.VISIBLE) {
                                animateButtonsIn(viewHolder)
                            }
                        } else {
                            if (viewHolder.itemView.delete_button_swipe.visibility != View.GONE) {
                                animateButtonsOut(viewHolder)
                            }
                        }
                    }
                }

                super.onChildDrawOver(
                    c,
                    recyclerView,
                    viewHolder,
                    viewHolder.itemView.constraint_layout.translationX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }

            override fun isLongPressDragEnabled(): Boolean {
                return false
            }

            override fun isItemViewSwipeEnabled(): Boolean {
                log("swipe: $swipeEnabled")
                return swipeEnabled
            }
        }
        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)

    }

    private fun animateButtonsIn(viewHolder: RecyclerView.ViewHolder) {
        val deleteButton = viewHolder.itemView.delete_button_swipe
        val editButton = viewHolder.itemView.edit_button_swipe
        val sendButton = viewHolder.itemView.send_button_swipe

        val buttons = arrayOf(deleteButton, editButton, sendButton)

        for (i in buttons.indices) {
            buttons[i].visibility = View.VISIBLE
            buttons[i].translationX =
                viewHolder.itemView.findViewById<RelativeLayout>(R.id.mainLayout).width.toFloat()

            val delay = 500L * i

            buttons[i].animate()
                .setStartDelay(delay)
                .translationX(0f)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .setDuration(400)
                .start()
        }

        // Отключаем кликабельность элемента
        viewHolder.itemView.isClickable = false
        viewHolder.itemView.isEnabled = false

        viewHolder.itemView.post {
            swipeEnabled = false
        }
    }

    private fun animateButtonsOut(viewHolder: RecyclerView.ViewHolder) {
        val deleteButton = viewHolder.itemView.delete_button_swipe
        val editButton = viewHolder.itemView.edit_button_swipe
        val sendButton = viewHolder.itemView.send_button_swipe

        val buttons = arrayOf(deleteButton, editButton, sendButton)

        for (i in buttons.indices) {
            buttons[i].translationX = 0f

            val delay = 50L * i

            buttons[i].animate()
                .translationX(viewHolder.itemView.findViewById<RelativeLayout>(R.id.mainLayout).width.toFloat())
                .setInterpolator(AccelerateDecelerateInterpolator())
                .setDuration(200)
                .setStartDelay(delay)
                .withEndAction {
                    buttons[i].visibility = View.GONE
                }
                .start()
        }

        // Включаем кликабельность элемента обратно
        viewHolder.itemView.isClickable = true
        viewHolder.itemView.isEnabled = true

        viewHolder.itemView.post {
            swipeEnabled = true
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
        return ItemHolder.create(parent, listener)
    }


    @OptIn(InternalCoroutinesApi::class)
    override fun onBindViewHolder(holder: ItemHolder, position: Int) {
        val item = getItem(position)
        holder.setData(item, listener, context, viewModel)
        holder.deleteButton.setOnClickListener {
            listener.deleteItem(position)

            log("onBindViewHolder deleteItem")
            // Обработка нажатия на кнопку удаления
        }
        holder.editButton.setOnClickListener {

            log("onBindViewHolder editButton")
        }
        holder.sendButton.setOnClickListener {
            log("onBindViewHolder sendButton")
            listener.sendItem(item)
        }
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

        init {

            itemView.setOnTouchListener(object : View.OnTouchListener {
                private val SWIPE_THRESHOLD = 100
                private var downX: Float = 0f
                private var downY: Float = 0f

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.x
                            downY = event.y
                        }

                        MotionEvent.ACTION_UP -> {
                            val deltaX = event.x - downX
                            val deltaY = event.y - downY

                            if (abs(deltaX) > SWIPE_THRESHOLD && abs(deltaY) < SWIPE_THRESHOLD) {
                                if (deltaX > 0) {
                                    // Swipe right
                                } else {
                                    // Swipe left
                                }
                                return true
                            } else {
                                itemView.performClick()
                            }
                        }
                    }
                    return true
                }
            })
        }

        @OptIn(InternalCoroutinesApi::class)
        fun setData(quizEntity: QuizEntity, listener: Listener, context: Context, viewModel: MainActivityViewModel) = with(binding) {
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

            if (quizEntity.stars >= MAX_PERCENT) {
                imvGradLightQuiz.visibility = View.VISIBLE
                imvGradHardQuiz.visibility = View.GONE
                chbTypeQuiz.visibility = View.VISIBLE
            } else if (quizEntity.stars == 120) {
                chbTypeQuiz.visibility = View.VISIBLE
                imvGradLightQuiz.visibility = View.GONE
                imvGradHardQuiz.visibility = View.VISIBLE
            } else {
                chbTypeQuiz.visibility = View.GONE
            }
            imvTranslate.imageAlpha = 128

            var lvlTranslate = 0
            lvlTranslate = viewModel.getLvlTranslateByQuizId(quizEntity.id!!)
            if (adapterPosition == 0) lvlTranslate = 50
            if (adapterPosition == 1) lvlTranslate = 100
            if (adapterPosition == 2) lvlTranslate = 150
            if (adapterPosition == 3) lvlTranslate = 200
            if (adapterPosition == 4) lvlTranslate = 250

            //imvTranslate
            if (lvlTranslate <= 100) imvTranslate.setColorFilter(Color.GRAY)
            else if (lvlTranslate <= 200) imvTranslate.setColorFilter(Color.YELLOW)
            else imvTranslate.setColorFilter(Color.BLUE)

            if (quizEntity.stars <= MAX_PERCENT) ratingBar.rating =
                (quizEntity.stars.toFloat() / 50)
            else ratingBar.rating = (((quizEntity.stars.toFloat() - 100) / 20) + 2)

            mainTitleButton.text = quizEntity.nameQuiz
            mainTitleButton.setOnClickListener {
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
        fun shareItem(id: Int, stars: Int)
        fun sendItem(quizEntity: QuizEntity)
        fun reloadData()
    }

    @OptIn(InternalCoroutinesApi::class)
    fun log(msg: String) {
        Logcat.log(msg, "MainActivityAdapter", Logcat.LOG_ACTIVITY)
    }
}