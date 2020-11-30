package ru.glorient.granitbk_n.avtoinformer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.observe
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import org.greenrobot.eventbus.Subscribe
import org.json.JSONObject
import ru.glorient.granitbk_n.MainActivity
import ru.glorient.granitbk_n.MessageEvent
import ru.glorient.granitbk_n.R
import ru.glorient.granitbk_n.accesory.UpdateListListener
import ru.glorient.granitbk_n.accesory.withArguments
import ru.glorient.granitbk_n.adapters.StopAdapter
import ru.glorient.granitbk_n.databinding.BusStopsBinding
import ru.glorient.services.ServiceManager

class AvtoInformatorFragment : Fragment(R.layout.bus_stops), UpdateListListener {
    private var stopAdapter: StopAdapter? = null
    private val stopListViewModel: AvtoInformatorViewModel by viewModels()

    private var _binding: BusStopsBinding? = null
    private val binding: BusStopsBinding get() = _binding!!
    private lateinit var busStopList: RecyclerView
    private lateinit var textViewStartStop: TextView
    private lateinit var textViewFinishStop: TextView
    // Список остановок маршрута
    var listStop = mutableListOf<Stop>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Байдим вьюху (ViewBinding)
        _binding = BusStopsBinding.bind(view)
        // Находим список
        busStopList = view.findViewById<RecyclerView>(R.id.busStopList)
        // TextView начало маршрута
        textViewStartStop = view.findViewById(R.id.startStop)
        // TextView конец маршрута
        textViewFinishStop = view.findViewById(R.id.finalStop)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Log.d(TAG, "onActivityCreated activity=${hashCode()}")

        // EventBus для прокидывания данных между фрагментами или активити
//        EventBus.getDefault().register(this)
//        if (BuildConfig.DEBUG) {

        // Инициализируем адаптер
        initList()

        // Ловим нажатие на изменение направления движения
        binding.routeBidir.setOnClickListener {
            toggle()

            // Меняем маршрут и запрашиваем данные через пол секунды
            Handler(Looper.getMainLooper()).postAtTime({
                stopListViewModel.requestStops()
            }, SystemClock.uptimeMillis() + 500L)
        }

        // Получаем список остановок
        stopListViewModel.requestStops()
        // Подписываемся через лайвдату на изменение списка
        stopListViewModel.stops
            .observe(viewLifecycleOwner) { newStop: List<Stop> ->
                listStop = mutableListOf<Stop>()
                val listStopScreen = mutableListOf<Stop>()
                newStop.forEachIndexed { ind, it : Stop ->
                    // Сравниваем по индексу и заполняем начало и конец маршрута
                    when (ind) {
                        0 -> {
                            textViewStartStop.text = (it as? Stop.DefaultStop)?.name ?: ((it as? Stop.NextStop)?.name)
                        }
                        newStop.size - 1 -> {
                            textViewFinishStop.text = (it as? Stop.DefaultStop)?.name ?: ((it as? Stop.NextStop)?.name)
                        }
                        else -> {
                            // Заполняем список остановок для адаптера
                            listStopScreen.add(it)
                        }
                    }
                    // Заполняем список остановок
                    this.listStop.add(it)
                }

                // Выводим в адаптер
                stopAdapter?.items = listStopScreen
            }

        // Нажатие на стартовое поле маршрута (при ручном управлении)
        textViewStartStop.setOnClickListener {
            if (!MainActivity.flagSelectedButtonAvto) {
                val id = (listStop[0] as? Stop.DefaultStop)?.id ?:
                ((listStop[0] as? Stop.NextStop)?.id)
                if (id != null) {
                    play(id)
                }
            }
        }

        // Нажатие на финишное поле маршрута (при ручном управлении)
        textViewFinishStop.setOnClickListener {
            if (!MainActivity.flagSelectedButtonAvto) {
                val id = (listStop[listStop.lastIndex] as? Stop.DefaultStop)?.id ?:
                ((listStop[listStop.lastIndex] as? Stop.NextStop)?.id)
                if (id != null) {
                    play(id)
                }
            }
        }
    }

    // Инициализируем адаптер
    private fun initList() {
        stopAdapter = StopAdapter { position ->
            if (!MainActivity.flagSelectedButtonAvto) {
                Log.d(TAG, "initList position = $position ")
                Log.d(TAG, "initList listStop = ${listStop[position]} ")
                val id = (listStop[position + 1] as? Stop.DefaultStop)?.id ?:
                    ((listStop[position + 1] as? Stop.NextStop)?.id)
                if (id != null) {
                    play(id)
                }
            }
        }

        with(busStopList) {
            adapter = stopAdapter
            // Кастомизируем LinearLayoutManager для медленного прокручивания списка
            val customLayoutManager: LinearLayoutManager? = object : LinearLayoutManager(requireContext()) {
                override fun smoothScrollToPosition(
                    recyclerView: RecyclerView,
                    state: RecyclerView.State,
                    position: Int
                ) {
                    val smoothScroller: LinearSmoothScroller = object : LinearSmoothScroller(requireContext()) {
                        private val SPEED = 300f // Change this value (default=25f)
                        override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                            return SPEED / displayMetrics.densityDpi
                        }
                    }
                    smoothScroller.targetPosition = position
                    startSmoothScroll(smoothScroller)
                }
            }

            layoutManager = customLayoutManager
            setHasFixedSize(true)
            // Изменяем анимацию добавления/удаления элементов списка (из библиотеки)
//            itemAnimator = OvershootInLeftAnimator()
        }
    }

    @Subscribe
    fun onMessageEvent(event: MessageEvent?) {
//        val test = event?.serviceFlag
//        if (test != null) {
//            isServiceTest = test
//        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView activity=${hashCode()}")
//        // Закрываем EventBus
//        EventBus.getDefault().unregister(this)

        // Обнуляем байдинг
        _binding = null
    }

    companion object {
        const val KEY_TEXT = "key_text"
        const val TAG = "AvtoInformatorFragment"

        fun newInstance(str: String): AvtoInformatorFragment {
            return AvtoInformatorFragment().withArguments {
//                putString(KEY_TEXT, str)
            }
        }
    }

    // При нажатии на остановку воспроизводим название
    private fun play(id: Int) {
        stopListViewModel.serviceManager?.sendMessage(
            ServiceManager.ServiceType.Informer,
            JSONObject("""{"play":[${id}]}""")
        )
    }

    // Меняем направление маршрута
    private fun toggle() {
        stopListViewModel.serviceManager?.sendMessage(
            ServiceManager.ServiceType.Informer,
            JSONObject("""{"dirrection":"toggle"}""")
        )
    }

    // Через интерфейс получаем событие о том что список изменился
    // Обновляем список и скролим до нужной остановки
    override fun updateList() {
        val ind = stopListViewModel.requestStops()
        busStopList.smoothScrollToPosition(ind)
    }
}