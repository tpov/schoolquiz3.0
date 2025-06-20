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
import com.tpov.common.R
import com.tpov.common.data.model.local.StructureDataLocal
import com.tpov.common.databinding.ActivityQuizItemBinding
import com.tpov.common.presentation.model.PathStructure
import com.tpov.common.presentation.utils.ResizeAndCrop
import com.tpov.log_api.logger.Logger
import kotlinx.coroutines.InternalCoroutinesApi
import java.io.File

@Logger
class QuizActivityAdapter @OptIn(InternalCoroutinesApi::class) constructor(
    private val listener: Listener,
    private val context: Context,
    private val viewModel: QuizActivityViewModel
) :
    ListAdapter<StructureDataLocal, QuizActivityAdapter.ItemHolder>(ItemComparator()) {
    var onDeleteButtonClick: ((RecyclerView.ViewHolder) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder {
        return ItemHolder.create(parent, listener)
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onBindViewHolder(holder: ItemHolder, position: Int) {
        val item = getItem(position)
        holder.setData(item, listener, context, viewModel)
    }


    class ItemComparator : DiffUtil.ItemCallback<StructureDataLocal>() {
        override fun areItemsTheSame(oldItem: StructureDataLocal, newItem: StructureDataLocal): Boolean {
            return oldItem.nameItem == newItem.nameItem
        }

        override fun areContentsTheSame(oldItem: StructureDataLocal, newItem: StructureDataLocal): Boolean {
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
            viewModel: QuizActivityViewModel
        ) {
            val alertDialog = AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.send_to_arena_title))
                .setMessage(context.getString(R.string.send_to_arena_text))
                .setPositiveButton("(-) $nolics nolics") { _, _ ->
                    listener.sendItem(viewModel.pathStructure!!)
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
            quizViewModel: QuizActivityViewModel
        ) {
            val popupMenu = PopupMenu(view.context, view)
            popupMenu.inflate(R.menu.popup_menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_send -> {
                        showDialog(context, 200, quizViewModel)
                        true
                    }

                    R.id.menu_delete -> {
                        listener.deleteItem(quizViewModel.pathStructure!!)
                        true
                    }

                    R.id.menu_edit -> {
                        listener.editItem(quizViewModel.pathStructure!!)
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }

        @OptIn(InternalCoroutinesApi::class)
        fun setData(
            quizEntity: StructureDataLocal,
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
                    .load(file as File)
                    .apply(
                        RequestOptions()
                            .override(widthInPx, heightInPx)
                            .transform(
                                ResizeAndCrop(widthInPx, heightInPx),
                                GranularRoundedCorners(0f, radinPx.toFloat(), radinPx.toFloat(), 0f)
                            )
                    ).into(imageView)

            } catch (e: Exception) {}

            val goHardQuiz = "${context.getString(R.string.go_hard_question)} - ${quizEntity.nameItem}"
            initView(quizEntity, goHardQuiz, mainViewModel, listener)
        }

        @OptIn(InternalCoroutinesApi::class)
        private fun ActivityQuizItemBinding.initView(
            quizEntity: StructureDataLocal,
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

            mainTitleButton.text = quizEntity.nameItem
            mainTitleButton.setOnClickListener {
                listener.onClick(quizEntity, chbTypeQuiz.isChecked)
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
        fun deleteItem(pathStructure: PathStructure)
        fun onClick(pathStructure: StructureDataLocal?, type: Boolean)
        fun editItem(pathStructure: PathStructure)
        fun sendItem(pathStructure: PathStructure)
        fun reloadData()
    }

    companion object {
        const val POPUP_TRANSLATE = 1
        const val POPUP_STARS = 2
        const val POPUP_LIFE = 3
        const val POPUP_LIFE_GOLD = 4

    }
}
