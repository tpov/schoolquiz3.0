package com.tpov.schoolquiz.presentation.network.profile

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
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
import com.tpov.schoolquiz.presentation.MainApp
import com.tpov.schoolquiz.presentation.factory.ViewModelFactory
import com.tpov.schoolquiz.presentation.fragment.BaseFragment
import com.tpov.schoolquiz.presentation.network.event.log
import com.wajahatkarim3.easyflipview.EasyFlipView
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

// Flip the view programatically
        flipView.flipTheView()
        try {
           // val player: PlayersEntity = viewModel.getPlayer()
        } catch (e: Exception) {

        }

        val profileGameValues = listOf(
            56,
            85,
            45,
            76,
            56,
            75,
            15
            //player.timeInGamesAllTime,
            //player.timeInGamesInQuiz,
            //player.timeInGamesInChat,
            //player.timeInGamesSmsPoints,
            //player.ratingPlayer,
            //player.ratingAnswer,
            //player.ratingQuiz
        )

        val profileQualificationValues = listOf(
            75,
            25,
            56,
            0,
            36,
            86,
            100
            //player.gamer,
            //player.sponsor,
            //player.translater,
            //player.tester,
            //player.moderator,
            //player.admin,
            //player.developer
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
        radarChart.setExtraOffsets(8f, 8f, 8f, 8f)

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
                viewModel.getTranslate()
            }
        }
        view.findViewById<ImageButton>(R.id.imb_delete).setOnClickListener {
            viewModel.getDeleteAllQuiz()
        }

        viewModel.synth.observe(viewLifecycleOwner) { number ->
            log("fun viewModel.getSynth.observe: $number")
            when (number) { //if (SharedPreferencesManager.canSyncProfile())
                1 -> {
                    if (!isExecuted[0]) { // проверяем, выполнялось ли число 0 ранее
                        CoroutineScope(Dispatchers.IO).launch { viewModel.setProfile() }
                        isExecuted[0] =
                            true // устанавливаем флаг в true, чтобы пометить число 0 как выполненное
                    }
                }

                2 -> {
                    if (!isExecuted[1]) {
                        CoroutineScope(Dispatchers.IO).launch { viewModel.getProfile() }
                        isExecuted[1] = true
                    }
                }

                3 -> {
                    if (!isExecuted[2]) {
                        CoroutineScope(Dispatchers.IO).launch { viewModel.setProfile() }
                        isExecuted[2] = true
                    }
                }

                4 -> {
                    if (!isExecuted[3]) {
                        CoroutineScope(Dispatchers.IO).launch {
                            viewModel.setQuizFB()
                            viewModel.getPlayersList()
                        }
                        isExecuted[3] = true
                    }
                }

                5 -> {
                    if (!isExecuted[4]) {
                        CoroutineScope(Dispatchers.IO).launch { viewModel.setQuestionsFB() }
                        isExecuted[4] = true
                    }
                }

                6 -> {
                    if (!isExecuted[5]) {
                        CoroutineScope(Dispatchers.IO).launch { viewModel.setEventQuiz() }
                        isExecuted[5] = true
                    }
                }
            }
        }

        viewModel.getTpovId()


    }

    @OptIn(InternalCoroutinesApi::class)
    override fun onAttach(context: Context) {
        component.inject(this)
        super.onAttach(context)
    }
}
