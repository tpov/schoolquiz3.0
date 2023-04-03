package com.tpov.schoolquiz.presentation.mainactivity

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.QuizEntity
import com.tpov.schoolquiz.databinding.ActivityMainItemBinding
import com.tpov.schoolquiz.presentation.custom.Logcat
import kotlinx.coroutines.InternalCoroutinesApi
import java.io.File
import java.util.*
import kotlin.math.max
import kotlin.math.min


class MainActivityAdapter(
    private val listener: Listener,
    private val context: Context
) :
    ListAdapter<QuizEntity, MainActivityAdapter.ItemHolder>(ItemComparator()) {


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
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition

                // Swap the items in the adapter
                    //Collections.swap(currentList, fromPosition, toPosition)

                // Notify the adapter that the items have moved
                //notifyItemMoved(fromPosition, toPosition)

                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

                log("onAttachedToRecyclerView onSwiped()")
                val itemView = viewHolder.itemView
                val screenWidth = Resources.getSystem().displayMetrics.widthPixels
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

                log("onAttachedToRecyclerView onChildDraw()")
                if (!animate) {
                    animate = true
                    notifyItemChanged(viewHolder.adapterPosition)

                }
                val deleteButton = viewHolder.itemView.findViewById<ImageButton>(R.id.delete_button_swipe)
                val editButton = viewHolder.itemView.findViewById<ImageButton>(R.id.edit_button_swipe)
                val sendButton = viewHolder.itemView.findViewById<ImageButton>(R.id.send_button_swipe)

                deleteButton.setOnClickListener {
                    Log.d("hkfthgl", "4")
                }
                if (-dX > deleteButton.width) {
                    deleteButton.visibility = View.VISIBLE
                    editButton.visibility = View.VISIBLE
                    sendButton.visibility = View.VISIBLE

                } else {
                    deleteButton.visibility = View.GONE
                    editButton.visibility = View.GONE
                    sendButton.visibility = View.GONE

                }
                when (actionState) {
                    ItemTouchHelper.ACTION_STATE_SWIPE -> {


                        var itemView = viewHolder.itemView

                        var position = viewHolder.adapterPosition
                        var textView = itemView.findViewById<ConstraintLayout>(R.id.constraint_layout)
                        var width = textView.width.toFloat()
                        var translationX = max((-width/1), min(0f, dX))
                            textView.translationX = translationX

                        // Обновляем представление элемента

                    }
                }
            }



            override fun isLongPressDragEnabled(): Boolean {
                return false
            }

            override fun isItemViewSwipeEnabled(): Boolean {
                return true
            }
        }
        val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.activity_main_item, parent, false)


        return ItemHolder(view)
    }


    override fun onBindViewHolder(holder: ItemHolder, position: Int) {
        val item = getItem(position)
        holder.setData(item, listener, context)
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

    class ItemHolder(view: View) : RecyclerView.ViewHolder(view) {

        @OptIn(InternalCoroutinesApi::class)
        fun log(msg: String) { Logcat.log(msg, "MainActivityAdapter", Logcat.LOG_ACTIVITY)}
        //val deleteButton: ImageButton = itemView.findViewById(R.id.delete_button_swipe)
        private val binding = ActivityMainItemBinding.bind(view)
            val swipeLayout: LinearLayout = itemView.findViewById(R.id.swipe_layout)
            val linearLayout: LinearLayout = itemView.findViewById(R.id.linearlayout)
        val mainLayout: FrameLayout = itemView.findViewById(R.id.mainLayout)
        val editButton: ImageButton = itemView.findViewById(R.id.edit_button_swipe)
        val deleteButton: ImageButton = itemView.findViewById(R.id.delete_button_swipe)
        val sendButton: ImageButton = itemView.findViewById(R.id.send_button_swipe)

        fun setData(quizEntity: QuizEntity, listener: Listener, context: Context) = with(binding) {
            deleteButton.setOnClickListener {
                log("onBindViewHolder deleteButton")
            }
            try {
                // Определяем размеры битмапа
                val width = 120
                val height = 75

// Создаем битмап с закругленными углами
                val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val rect = RectF(-25f, 0f, width.toFloat() - 25, height.toFloat())


                paint.color = Color.WHITE
                canvas.drawRoundRect(rect, 25f, 25f, paint)

// Создаем битмап из файла
                val file = File(context.cacheDir, "${quizEntity.picture}")
                val bitmap = BitmapFactory.decodeFile(file.path)

// Создаем шейдер с битмапом, вырезанным в форме закругленного прямоугольника
                val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                val matrix = Matrix()

                matrix.setRectToRect(
                    RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
                    rect,
                    Matrix.ScaleToFit.FILL
                )
                shader.setLocalMatrix(matrix)

// Создаем Paint и устанавливаем его шейдер
                paint.shader = shader

// Отображаем битмап с закругленными углами и картинкой внутри
                canvas.drawRoundRect(rect, 25f, 25f, paint)
                val drawable = BitmapDrawable(context.resources, output)
                imageView.setImageDrawable(drawable)

            } catch (e: Exception) {

                log("onBindViewHolder Exception $e")
            }


            var goHardQuiz =
                "${this.root.context.getString(R.string.go_hard_question)} - ${quizEntity.nameQuiz}"

            if (quizEntity.stars == MAX_PERCENT) {
                Toast.makeText(binding.root.context, goHardQuiz, Toast.LENGTH_SHORT).show()
            }

            if (quizEntity.stars >= MAX_PERCENT) {

            } else if (quizEntity.stars == 120) {


            }

            if (quizEntity.stars <= MAX_PERCENT) ratingBar.rating =
                (quizEntity.stars.toFloat() / 50)
            else ratingBar.rating = (((quizEntity.stars.toFloat() - 100) / 20) + 2)

            mainTitleButton.text = quizEntity.nameQuiz
            mainTitleButton.setOnClickListener {
                listener.onClick(quizEntity.id ?: 0, quizEntity.stars)
            }

        }

        companion object {
            const val PERCENT_TWO_STARS = 0.83333
            const val MAX_PERCENT = 100

            fun create(parent: ViewGroup): ItemHolder {
                return ItemHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.activity_main_item, parent, false)
                )
            }
        }
    }

    interface Listener {

        fun deleteItem(id: Int)
        fun onClick(id: Int, stars: Int)
        fun shareItem(id: Int, stars: Int)
        fun sendItem(quizEntity: QuizEntity)
        fun reloadData()
    }

    @OptIn(InternalCoroutinesApi::class)
    fun log(msg: String) { Logcat.log(msg, "MainActivityAdapter", Logcat.LOG_ACTIVITY)}
}