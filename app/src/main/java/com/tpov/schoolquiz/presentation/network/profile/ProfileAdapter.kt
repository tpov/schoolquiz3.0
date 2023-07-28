package com.tpov.schoolquiz.presentation.network.profile

import android.app.AlertDialog
import android.graphics.Color
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.RadarChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.data.RadarData
import com.github.mikephil.charting.data.RadarDataSet
import com.github.mikephil.charting.data.RadarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.IRadarDataSet
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.PlayersEntity
import com.tpov.schoolquiz.data.fierbase.Chat
import com.tpov.shoppinglist.utils.TimeManager

class ProfileAdapter(
    private val players: List<PlayersEntity>,
    private val playersEntity: List<PlayersEntity>
) : RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder>() {

    inner class ProfileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val radarChart = view.findViewById<RadarChart>(R.id.chart_profile)
        val tvSkill = view.findViewById<TextView>(R.id.tv_rating)
        val cardViewProfile = view.findViewById<CardView>(R.id.cv_profile)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.flash_card_layout_back, parent, false)
        return ProfileViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {

        val player = players[position]

        holder.tvSkill.text = "${player.userName}: ${player.skill}"

        val profileGameValues = listOf(
            player.sponsor,
            player.tester,
            player.translater,
            player.moderator,
            player.admin,
            player.developer
        )

        val profileQualificationValues = listOf(
            player.ratingCountQuestions,
            player.ratingCountTrueQuestion,
            player.ratingTimeInQuiz,
            player.ratingTimeInChat,
            player.ratingSmsPoints,
            player.ratingQuiz,
        )

        val radarDataSet1 = RadarDataSet(profileGameValues.indices.map {
            RadarEntry(
                profileGameValues[it].toFloat(),
                it.toFloat()
            )
        }, "Games")
        val radarDataSet2 = RadarDataSet(profileQualificationValues.indices.map {
            RadarEntry(
                profileQualificationValues[it].toFloat(),
                it.toFloat()
            )
        }, "Qualifications")
        holder.radarChart.setExtraOffsets(0f, 10f, 0f, 8f)

        val dataSets = ArrayList<RadarDataSet>()
        dataSets.add(radarDataSet1)
        dataSets.add(radarDataSet2)
        holder.radarChart.description.isEnabled = false
        holder.radarChart.legend.isEnabled = false
        holder.radarChart.yAxis.isEnabled = false
        val yAxis = holder.radarChart.yAxis
        val xAxis = holder.radarChart.xAxis
        xAxis.textSize = 8f
        yAxis.textColor = Color.YELLOW
        xAxis.textColor = Color.WHITE

        yAxis.axisMinimum = 0f
        yAxis.axisMaximum = 60f
        yAxis.labelCount = 4

        yAxis.valueFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                return "${value.toInt()}%"
            }
        }

        holder.cardViewProfile.setOnClickListener {
            val builder = AlertDialog.Builder(it.context)
            val inflater = LayoutInflater.from(it.context)

            // Настраиваем диалоговое окно
            builder.setTitle("Отправить данные")

            // Создаем LinearLayout
            val linearLayout = LinearLayout(it.context)
            linearLayout.orientation = LinearLayout.VERTICAL
            linearLayout.setPadding(50, 40, 50, 10)

            // Создаем необходимые EditText
            val messageEditText = EditText(it.context)
            messageEditText.hint = "Сообщение"
            linearLayout.addView(messageEditText)

            val addTrophy = EditText(it.context)
            addTrophy.hint = "Начисление трофеев"
            linearLayout.addView(addTrophy)

            val addNolic = EditText(it.context)
            addNolic.inputType = InputType.TYPE_CLASS_NUMBER
            addNolic.hint = "Начисление ноликов"
            linearLayout.addView(addNolic)

            val addGold = EditText(it.context)
            addGold.inputType = InputType.TYPE_CLASS_NUMBER
            addGold.hint = "Начисление золота"
            linearLayout.addView(addGold)

            val addSkill = EditText(it.context)
            addSkill.inputType = InputType.TYPE_CLASS_NUMBER
            addSkill.hint = "Начисление опыта"
            linearLayout.addView(addSkill)

            val addBox = EditText(it.context)
            addSkill.inputType = InputType.TYPE_CLASS_NUMBER
            addSkill.hint = "Начисление коробок"
            linearLayout.addView(addSkill)

            builder.setView(linearLayout)

            // Настраиваем кнопки
            builder.setPositiveButton("OK") { dialog, _ ->

                val massage = messageEditText.text.toString()
                val trophy = addTrophy.text.toString()
                val nolic = addNolic.text.toString().toInt()
                val gold = addGold.text.toString().toInt()
                val skill = addSkill.text.toString().toInt()
                val box = addBox.text.toString().toInt()

                sendData(massage, trophy, nolic, gold, skill, box, holder, player.id ?: 0)

                dialog.dismiss()
            }
            builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

            val dialog = builder.create()
            dialog.window?.setBackgroundDrawableResource(R.color.grey)
            dialog.show()
        }

        radarDataSet1.color = Color.RED
        radarDataSet1.valueTextColor = Color.MAGENTA
        radarDataSet1.setDrawFilled(true)
        radarDataSet1.fillColor = Color.RED

        radarDataSet2.color = Color.GREEN
        radarDataSet2.valueTextColor = Color.YELLOW
        radarDataSet2.setDrawFilled(true)
        radarDataSet2.fillColor = Color.GREEN

        val data = RadarData(dataSets as List<IRadarDataSet>)
        holder.radarChart.data = data
        holder.radarChart.invalidate()
    }

    private fun sendMassage(massage: String, holder: ProfileViewHolder, userTpovId: Int) {

    }

    private fun sendData(
        massage: String,
        trophy: String,
        nolic: Int,
        gold: Int,
        skill: Int,
        box: Int,
        holder: ProfileViewHolder,
        userTpovId: Int,
    ) {
        if (massage != "") sendMassage(massage, holder, userTpovId)
        if (trophy != "") sendTrophy(trophy, holder, userTpovId)
        if (nolic != 0) sendNolic(nolic, holder, userTpovId)
        if (gold != 0) sendGold(gold, holder, userTpovId)
        if (skill != 0) sendSkill(skill, holder, userTpovId)
        if (box != 0) sendBox(box, holder, userTpovId)
    }

    private fun sendNolic(nolic: Int, holder: ProfileViewHolder, userTpovId: Int) {

        val database = Firebase.database
        val myRef = database.getReference("Profiles/$userTpovId/addPoints/addMassage")
        myRef.setValue("$nolic").addOnFailureListener {
            Toast.makeText(holder.itemView.context, "send massage", Toast.LENGTH_SHORT).show()
        }

    }

    private fun sendTrophy(trophy: String, holder: ProfileViewHolder, userTpovId: Int) {

        val db = Firebase.firestore

        val message = Chat(
            TimeManager.getCurrentTime(),
            "TPOV",
            trophy,
            10,
            userTpovId,
            "",
            0,
            0
        )

        db.collection("PersonalMassage/addNolic")
            .add(message)
            .addOnSuccessListener { documentReference ->
                Toast.makeText(holder.itemView.context, "send", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(holder.itemView.context, "error", Toast.LENGTH_LONG).show()
            }


    }

    private fun sendGold(gold: Int, holder: ProfileViewHolder, userTpovId: Int) {


    }

    private fun sendSkill(skill: Int, holder: ProfileViewHolder, userTpovId: Int) {


    }

    private fun sendBox(box: Int, holder: ProfileViewHolder, userTpovId: Int) {


    }

    override fun getItemCount(): Int {
        return players.size
    }
}
