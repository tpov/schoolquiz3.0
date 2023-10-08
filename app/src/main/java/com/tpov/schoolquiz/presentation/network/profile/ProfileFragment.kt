package com.tpov.schoolquiz.presentation.network.profile

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.RadarChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.data.RadarData
import com.github.mikephil.charting.data.RadarDataSet
import com.github.mikephil.charting.data.RadarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.IRadarDataSet
import com.tpov.schoolquiz.R
import com.tpov.schoolquiz.data.database.entities.PlayersEntity
import com.tpov.schoolquiz.presentation.MainApp
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager
import com.tpov.schoolquiz.presentation.core.SharedPreferencesManager.getTpovId
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import com.tpov.schoolquiz.presentation.fragment.BaseFragment
import com.tpov.schoolquiz.presentation.network.event.log
import com.wajahatkarim3.easyflipview.EasyFlipView
import kotlinx.android.synthetic.main.profile_item.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject

class ProfileFragment : BaseFragment() {

    companion object {
        fun newInstance() = ProfileFragment()
    }

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @OptIn(InternalCoroutinesApi::class)
    private val component by lazy {
        (requireActivity().application as MainApp).component
    }

    @OptIn(InternalCoroutinesApi::class)
    private lateinit var viewModel: ProfileViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(InternalCoroutinesApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this, viewModelFactory)[ProfileViewModel::class.java]

        val radarChart = view.findViewById<RadarChart>(R.id.chart_profile)
        val flipView = view.findViewById<EasyFlipView>(R.id.efv_card)
        val tvSkill = view.findViewById<TextView>(R.id.tv_rating)

        flipView.flipTheView()

        val player: PlayersEntity = try {
            viewModel.getPlayer()
        } catch (e: Exception) {
            PlayersEntity(
                0, 0, 0, 0, 0, 0, 0,
                -1, 0, 0, 0, 0, 0
            )
        }

        tvSkill.text = if (player.skill == -1) {
            Toast.makeText(
                context,
                "Нужно скачать данные профиля нажав на \"download\"",
                Toast.LENGTH_LONG
            ).show()
            "no data"
        } else "Rating: ${player.skill}%"

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
        radarChart.setExtraOffsets(0f, 10f, 0f, 8f)

        val dataSets = ArrayList<RadarDataSet>()
        dataSets.add(radarDataSet1)
        dataSets.add(radarDataSet2)
        radarChart.description.isEnabled = false
        radarChart.legend.isEnabled = false
        radarChart.yAxis.isEnabled = false
        val yAxis = radarChart.yAxis
        val xAxis = radarChart.xAxis
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

        radarDataSet1.color = Color.RED
        radarDataSet1.valueTextColor = Color.MAGENTA
        radarDataSet1.setDrawFilled(true)
        radarDataSet1.fillColor = Color.RED

        radarDataSet2.color = Color.GREEN
        radarDataSet2.valueTextColor = Color.YELLOW
        radarDataSet2.setDrawFilled(true)
        radarDataSet2.fillColor = Color.GREEN

        val data = RadarData(dataSets as List<IRadarDataSet>)
        radarChart.data = data
        radarChart.invalidate()

        viewModel.addQuestion.observe(viewLifecycleOwner) {
            Toast.makeText(activity, "added question", Toast.LENGTH_SHORT).show()
        }
        viewModel.addInfoQuestion.observe(viewLifecycleOwner) {
            Toast.makeText(activity, "added question info", Toast.LENGTH_SHORT).show()
        }
        viewModel.addQuiz.observe(viewLifecycleOwner) {
            Toast.makeText(activity, "added quiz", Toast.LENGTH_SHORT).show()
        }

        val isExecuted = BooleanArray(8) // создаем массив флагов для каждого числа
        view.findViewById<ImageButton>(R.id.imb_download).setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                viewModel.getQuizzFB()
                viewModel.getPlayersList()
            }
        }

        view.findViewById<ImageButton>(R.id.imb_delete).setOnClickListener {
            viewModel.getDeleteAllQuiz()
        }

        log("fun viewModel.getSynth getTpovId: ${getTpovId()}")
        if (SharedPreferencesManager.canSyncProfile() || getTpovId() == 0)
            viewModel.synth.observe(viewLifecycleOwner) { number ->
                log("fun viewModel.getSynth.observe: $number")

                when (number) {
                    1 -> {
                        if (!isExecuted[0]) { // проверяем, выполнялось ли число 0 ранее
                            CoroutineScope(Dispatchers.IO).launch { viewModel.setProfile() }
                            log("fun viewModel.getSynth start: $number")
                            isExecuted[0] =
                                true // устанавливаем флаг в true, чтобы пометить число 0 как выполненное
                        }
                    }

                    2 -> {
                        if (!isExecuted[1]) {
                            CoroutineScope(Dispatchers.IO).launch { viewModel.getSynthProfile() }
                            log("fun viewModel.getSynth start: $number")
                            isExecuted[1] = true
                        }
                    }

                    3 -> {
                        if (!isExecuted[2]) {
                            CoroutineScope(Dispatchers.IO).launch { viewModel.setProfile() }
                            log("fun viewModel.getSynth start: $number")
                            isExecuted[2] = true
                        }
                    }

                    4 -> {
                        if (!isExecuted[3]) {
                            CoroutineScope(Dispatchers.IO).launch {
                                viewModel.setQuizFB()
                                viewModel.getPlayersList()
                            }
                            log("fun viewModel.getSynth start: $number")
                            isExecuted[3] = true
                        }
                    }
                }
            }
        log("tpovIdfsefsef ${getTpovId()}")
        viewModel.getTpovId()
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onAttach(context: Context) {
        component.inject(this)
        super.onAttach(context)
    }
}
