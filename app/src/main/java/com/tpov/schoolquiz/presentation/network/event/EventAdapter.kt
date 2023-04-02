package com.tpov.schoolquiz.presentation.network.event

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.ChatEntity
import com.tpov.schoolquiz.data.database.entities.QuestionEntity
import com.tpov.schoolquiz.data.database.entities.QuizEntity

class EventAdapter(
    private val quiz2List: List<QuizEntity>,
    private val quiz3List: List<QuizEntity>,
    private val quiz4List: List<QuizEntity>,
    private val translate1EventList: List<QuestionEntity>,
    private val translate2EventList: List<QuestionEntity>,
    private val translateEditQuestionList: List<QuestionEntity>,
    private val moderatorEventList: List<ChatEntity>,
    private val adminEventList: List<ChatEntity>,
    private val developerEventList: List<ChatEntity>,
    private val listener: ListenerEvent
) : RecyclerView.Adapter<EventAdapter.MyViewHolder>() {

    private val viewTypes = arrayOf(
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

    private val size1 = quiz2List.size
    private val size2 = quiz3List.size + size1
    private val size3 = quiz4List.size + size2
    private val size4 = translate1EventList.size + size3
    private val size5 = translate2EventList.size + size4
    private val size6 = translateEditQuestionList.size + size5
    private val size7 = moderatorEventList.size + size6
    private val size8 = adminEventList.size + size7
    private val size9 = developerEventList.size + size8

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

    override fun getItemViewType(position: Int): Int {
        return if (position == 1 ||
            position == size1 + 1 ||
            position == size2 + 1||
            position == size3 + 1||
            position == size4 + 1||
            position == size5 + 1||
            position == size6 + 1||
            position == size7 + 1||
            position == size8 + 1
        ) HEADER_VIEW
        else if (position <= size1 + 1) QUIZ2_LIST
        else if (position <= size2 + 2) QUIZ3_LIST
        else if (position <= size3 + 3) QUIZ4_LIST
        else if (position <= size4 + 4) TRANSLATE1_EVENT_LIST
        else if (position <= size5 + 5) TRANSLATE2_EVENT_LIST
        else if (position <= size6 + 6) TRANSLATE_EDIT_QUESTION_LIST
        else if (position <= size7 + 7) MODERATOR_EVENT_LIST
        else if (position <= size8 + 8) ADMIN_EVENT_LIST
        else if (position <= size9 + 9) DEVELOPER_EVENT_LIST
        else -1
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        log("fun onBindViewHolder(), quiz2List: $quiz2List")

        log(
            "fun onBindViewHolder(), position: $position, type: ${getItemViewType(position)}"
        )
        log("onBindViewHolder(), viewTypes: ${viewTypes[1]}}")
        log("onBindViewHolder(), viewTypes: ${viewTypes[2]}}")
        log("onBindViewHolder(), viewTypes: ${viewTypes[3]}}")
        log("onBindViewHolder(), viewTypes: ${viewTypes[4]}}")
        log("onBindViewHolder(), viewTypes: ${viewTypes[5]}}")
        log("onBindViewHolder(), viewTypes: ${viewTypes[6]}}")
        log("onBindViewHolder(), viewTypes: ${viewTypes[7]}}")
        log("onBindViewHolder(), viewTypes: ${viewTypes[0]}}")
        when (getItemViewType(position)) {
            QUIZ2_LIST -> {
                val quiz = quiz2List[position - 1]
                holder.bindQuiz2(quiz)
            }

            QUIZ3_LIST -> {
                val quiz = quiz3List[position - size2 - 2]
                holder.bindQuiz3(quiz)
            }

            QUIZ4_LIST -> {
                val quiz = quiz4List[position - size3 - 3]
                holder.bindQuiz4(quiz)
            }

            TRANSLATE1_EVENT_LIST -> {
                val question =
                    translate1EventList[position - size4 - 4]
                holder.bindTranslate1Event(question)
            }

            TRANSLATE2_EVENT_LIST -> {
                val question = translate2EventList[position - size5 - 5]
                holder.bindTranslate2Event(question)
            }

            TRANSLATE_EDIT_QUESTION_LIST -> {
                val question = translateEditQuestionList[position - size6 - 6]
                holder.bindTranslateEditQuestion(question)
            }

            MODERATOR_EVENT_LIST -> {
                val question = moderatorEventList[position - size7 - 7]
                holder.bindModeratorEvent(question)
            }

            ADMIN_EVENT_LIST -> {
                val quiz = adminEventList[position - size8 - 8]
                holder.bindAdminEvent(quiz)
            }

            DEVELOPER_EVENT_LIST -> {
                val quiz = developerEventList[position - size9 - 9]
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
        var count = headers.size
        viewTypes.forEach { count += it.second.size }
        return count
    }

    private fun getViewTypeIndex(viewType: Int): Int {
        var index = 0
        viewTypes.forEachIndexed { i, pair ->
            if (pair.first == viewType) {
                return index
            }
            index += pair.second.size + 1
        }
        throw IllegalArgumentException("Invalid view type")
    }

    inner class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bindQuiz2(quiz: QuizEntity) {
            itemView.findViewById<Button>(R.id.main_title_button).text = quiz.nameQuiz
            itemView.findViewById<ImageView>(R.id.imv_gradient_light_quiz).visibility = View.VISIBLE
            itemView.findViewById<ImageView>(R.id.imv_grafient_hard_quiz).visibility = View.GONE
            itemView.findViewById<TextView>(R.id.tvNumHardQuiz).visibility = View.GONE
            itemView.findViewById<RatingBar>(R.id.ratingBar).visibility = View.GONE
            itemView.findViewById<Switch>(R.id.s_hardQuiz).visibility = View.GONE
            itemView.findViewById<TextView>(R.id.tvNumQuestion).text = quiz.numQ.toString()
            itemView.findViewById<LinearLayout>(R.id.infoQuiz).visibility = View.VISIBLE
            itemView.findViewById<TextView>(R.id.tvNumHardQuiz).visibility = View.GONE
            itemView.findViewById<TextView>(R.id.tvNumQuestion).visibility = View.VISIBLE
            itemView.findViewById<TextView>(R.id.tvName).text = quiz.userName
            itemView.findViewById<TextView>(R.id.tvTime).text = quiz.data
            val imageQuiz = itemView.findViewById<ImageView>(R.id.imageView)
            if (quiz.picture != "") Picasso.get().load(quiz.picture).into(imageQuiz)

            itemView.setOnClickListener {
                listener.onQuiz2Clicked(quiz.id!!)
            }
        }

        fun bindQuiz3(quiz: QuizEntity) {
            itemView.findViewById<Button>(R.id.main_title_button).text = quiz.nameQuiz
            itemView.findViewById<ImageView>(R.id.imv_gradient_light_quiz).visibility = View.GONE
            itemView.findViewById<ImageView>(R.id.imv_grafient_hard_quiz).visibility = View.VISIBLE
            itemView.findViewById<TextView>(R.id.tvNumHardQuiz).visibility = View.VISIBLE
            itemView.findViewById<RatingBar>(R.id.ratingBar).visibility = View.GONE
            itemView.findViewById<Switch>(R.id.s_hardQuiz).visibility = View.GONE
            itemView.findViewById<TextView>(R.id.tvNumQuestion).visibility = View.GONE
            itemView.findViewById<LinearLayout>(R.id.infoQuiz).visibility = View.VISIBLE
            itemView.findViewById<TextView>(R.id.tvNumHardQuiz).text = quiz.numHQ.toString()
            itemView.findViewById<TextView>(R.id.tvName).text = quiz.userName
            itemView.findViewById<TextView>(R.id.tvTime).text = quiz.data
            val imageQuiz = itemView.findViewById<ImageView>(R.id.imageView)
            if (quiz.picture != "") Picasso.get().load(quiz.picture).into(imageQuiz)

            itemView.setOnClickListener {
                listener.onQuiz3Clicked(quiz.id!!)
            }
        }

        fun bindQuiz4(quiz: QuizEntity) {
            itemView.findViewById<Button>(R.id.main_title_button).text = quiz.nameQuiz
            itemView.findViewById<TextView>(R.id.tvNumHardQuiz).visibility = View.VISIBLE
            itemView.findViewById<TextView>(R.id.tvNumQuestion).visibility = View.VISIBLE
            itemView.findViewById<RatingBar>(R.id.ratingBar).visibility = View.GONE
            itemView.findViewById<Switch>(R.id.s_hardQuiz).visibility = View.GONE
            itemView.findViewById<LinearLayout>(R.id.infoQuiz).visibility = View.VISIBLE
            itemView.findViewById<TextView>(R.id.tvNumHardQuiz).text = quiz.numHQ.toString()
            itemView.findViewById<TextView>(R.id.tvNumQuestion).text = quiz.numQ.toString()
            itemView.findViewById<TextView>(R.id.tvName).text = quiz.userName
            itemView.findViewById<TextView>(R.id.tvTime).text = quiz.data
            val imageQuiz = itemView.findViewById<ImageView>(R.id.imageView)
            if (quiz.picture != "") Picasso.get().load(quiz.picture).into(imageQuiz)

            itemView.setOnClickListener {
                listener.onQuiz4Clicked(quiz.id!!)
            }
        }

        fun bindTranslate1Event(question: QuestionEntity) {
            itemView.findViewById<Button>(R.id.main_title_button).text =
                "${question.language} - lvl:${question.lvlTranslate} \"${question.nameQuestion}\""
            itemView.findViewById<ImageView>(R.id.imv_gradient_translate_quiz).visibility =
                View.VISIBLE
            itemView.findViewById<RatingBar>(R.id.ratingBar).visibility = View.GONE
            itemView.findViewById<Switch>(R.id.s_hardQuiz).visibility = View.GONE

            itemView.setOnClickListener {
                listener.onTranslate1EventClicked(question.id!!)
            }
        }

        fun bindTranslate2Event(question: QuestionEntity) {
            itemView.findViewById<Button>(R.id.main_title_button).text =
                "${question.language} - lvl:${question.lvlTranslate} \"${question.nameQuestion}\""
            itemView.findViewById<ImageView>(R.id.imv_gradient_translate_quiz).visibility =
                View.VISIBLE
            itemView.findViewById<RatingBar>(R.id.ratingBar).visibility = View.GONE
            itemView.findViewById<Switch>(R.id.s_hardQuiz).visibility = View.GONE

            itemView.setOnClickListener {
                listener.onTranslate2EventClicked(question.id!!)
            }
        }

        fun bindTranslateEditQuestion(question: QuestionEntity) {
            itemView.findViewById<Button>(R.id.main_title_button).text =
                "${question.language} - lvl:${question.lvlTranslate} \"${question.nameQuestion}\""
            itemView.findViewById<ImageView>(R.id.imv_gradient_translate_quiz).visibility =
                View.VISIBLE
            itemView.findViewById<RatingBar>(R.id.ratingBar).visibility = View.GONE
            itemView.findViewById<Switch>(R.id.s_hardQuiz).visibility = View.GONE

            itemView.setOnClickListener {
                listener.onTranslateEditQuestionClicked(question.id!!)
            }
        }

        fun bindModeratorEvent(chat: ChatEntity) {
            itemView.findViewById<Button>(R.id.main_title_button).text = "${chat.msg}"
            itemView.findViewById<ImageView>(R.id.imv_gradient_translate_quiz).visibility =
                View.VISIBLE
            itemView.findViewById<RatingBar>(R.id.ratingBar).visibility = View.GONE
            itemView.findViewById<Switch>(R.id.s_hardQuiz).visibility = View.GONE

            itemView.setOnClickListener {
                listener.onModeratorEventClicked(it.id)
            }
        }

        fun bindAdminEvent(chat: ChatEntity) {
            itemView.findViewById<Button>(R.id.main_title_button).text = "${chat.msg}"
            itemView.findViewById<ImageView>(R.id.imv_gradient_translate_quiz).visibility =
                View.VISIBLE
            itemView.findViewById<RatingBar>(R.id.ratingBar).visibility = View.GONE
            itemView.findViewById<Switch>(R.id.s_hardQuiz).visibility = View.GONE

            itemView.setOnClickListener {
                listener.onAdminEventClicked(it.id)
            }
        }

        fun bindDeveloperEvent(chat: ChatEntity) {
            itemView.findViewById<Button>(R.id.main_title_button).text = "${chat.msg}"
            itemView.findViewById<ImageView>(R.id.imv_gradient_translate_quiz).visibility =
                View.VISIBLE
            itemView.findViewById<RatingBar>(R.id.ratingBar).visibility = View.GONE
            itemView.findViewById<Switch>(R.id.s_hardQuiz).visibility = View.GONE

            itemView.setOnClickListener {
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
        fun onTranslate2EventClicked(quizId: Int)
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