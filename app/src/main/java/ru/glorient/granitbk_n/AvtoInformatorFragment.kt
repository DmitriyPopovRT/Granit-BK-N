package ru.glorient.granitbk_n

import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.bus_stops.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import ru.glorient.granitbk_n.json.FileJsonOriginal
import ru.glorient.granitbk_n.json.ParserJSON
import ru.glorient.granitbk_n.json.Sequences
import java.io.File
import java.util.*

class AvtoInformatorFragment : Fragment(R.layout.bus_stops) {
    private var sequences = ArrayList<Sequences>()
    private var absolutTimeJsonRoute = mutableMapOf<String, Sequences>()
    var fileJsonOriginal = FileJsonOriginal()

    var isStartedHandler = false
    var isServiceTest = true

    var mainHandler = Handler(Looper.getMainLooper())

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Log.d(TAG, "onActivityCreated activity=${hashCode()}")
        isStartedHandler = false

        if (arguments != null) {
            val str = requireArguments().getString(KEY_TEXT)
            fileJsonOriginal = str?.let { ParserJSON().parse(it) }!!

            updateAvtoInformator(fileJsonOriginal)
            timeDeltaRouteTracking()
        }

        EventBus.getDefault().register(this)
    }

    @Subscribe
    fun onMessageEvent(event: MessageEvent?) {
        val test = event?.serviceFlag
        Log.d(TAG, "onMessageEvent AvtoInformatorFragment $test")
        if (test != null) {
            isServiceTest = test
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView activity=${hashCode()}")
        isStartedHandler = true
        // Убиваем поток
        mainHandler.removeCallbacksAndMessages(null)

        // Закрываем EventBus
        EventBus.getDefault().unregister(this);
    }

    // Временная функция для эмуляции работы автоинформатора по дельте времени
    private fun timeDeltaRouteTracking() {
        mainHandler = Handler(Looper.getMainLooper())
        Log.e(TAG, "### NEW mainHandler=${mainHandler.hashCode()}")
        var i = 0
        mainHandler.post(object : Runnable {
            override fun run() {
                if (!isServiceTest) {
                    mainHandler.removeCallbacks(this)
                    return
                }

                val calendar = Calendar.getInstance()
                val hour = calendar[Calendar.HOUR_OF_DAY]
                val minute = calendar[Calendar.MINUTE]
                val second = calendar[Calendar.SECOND]
                val currentTime = String.format(
                    "%02d:%02d:%02d",
                    hour, minute, second
                )

                absolutTimeJsonRoute.keys.forEach {
                    if (currentTime.equals(it)) {
                        Log.e(TAG, "Выполнение задачи =${mainHandler.hashCode()} размер списка=${absolutTimeJsonRoute.size}")
                        val sequences = absolutTimeJsonRoute.get(it)
                        update(i, sequences!!)
                        i++
                    }

                }

                mainHandler.postDelayed(this, 1000)
            }
        })
    }

    // Тоже временная функция. Отрабатываем остановки +5 секунд от текущего времени
    private fun timeCreate(i: Int): String {
        val calendar = Calendar.getInstance()
        val time = Date().time

        when (i) {
            0 -> {
                val newTime = time + 5000L
                calendar.timeInMillis = newTime
                return String.format(
                    "%02d:%02d:%02d",
                    calendar[Calendar.HOUR_OF_DAY],
                    calendar[Calendar.MINUTE],
                    calendar[Calendar.SECOND]
                )
            }
            1 -> {
                val newTime = time + 10000L
                calendar.timeInMillis = newTime
                return String.format(
                    "%02d:%02d:%02d",
                    calendar[Calendar.HOUR_OF_DAY],
                    calendar[Calendar.MINUTE],
                    calendar[Calendar.SECOND]
                )
            }
            2 -> {
                val newTime = time + 15000L
                calendar.timeInMillis = newTime
                return String.format(
                    "%02d:%02d:%02d",
                    calendar[Calendar.HOUR_OF_DAY],
                    calendar[Calendar.MINUTE],
                    calendar[Calendar.SECOND]
                )
            }
            else -> return ""
        }
    }

    // Обрабатываем изменение остановнок(выделяем текущую отсановку черным цветом и запускаем аудио)
    private fun update(i: Int, sequence: Sequences) {
        when (i) {
            0 -> {
                textView2.setTextColor(Color.BLACK)
                textView10.setTextColor(Color.BLACK)
                playAudio(sequence.audio[0].toString())
            }
            1 -> {
                textView2.setTextColor(Color.GRAY)
                textView10.setTextColor(Color.GRAY)
                textView6.setTextColor(Color.BLACK)
                textView11.setTextColor(Color.BLACK)

                playAudio(sequence.audio[0].toString())
            }
            2 -> {
                textView6.setTextColor(Color.GRAY)
                textView11.setTextColor(Color.GRAY)
                textView7.setTextColor(Color.BLACK)
                textView12.setTextColor(Color.BLACK)

                playAudio(sequence.audio[0].toString())
            }
        }
    }

    // Обновление автоинформатора
    private fun updateAvtoInformator(fileJsonOriginal: FileJsonOriginal) {
        sequences = fileJsonOriginal.sequences
//        Log.d(TAG, "version = ${fileJsonOriginal.version}")
        absolutTimeJsonRoute.clear()

        var i = 0
        while (i < sequences.size) {
            val sequence = sequences[i]
            val timeCreate = timeCreate(i)
            absolutTimeJsonRoute.put(timeCreate, sequence)
//            i++

            when (i) {
                0 -> {
                    textView2.text = sequence.name
                    textView10.text = timeCreate
                    i++
                }
                1 -> {
                    textView6.text = sequence.name
                    textView11.text = timeCreate
                    i++
                }
                2 -> {
                    textView7.text = sequence.name
                    textView12.text = timeCreate
                    i++
                }
            }
        }

//        for (sequence in sequences) {
//
//            Log.d(TAG, "id = ${sequence.id}")
//            Log.d(TAG, "name = ${sequence.name}")
//            Log.d(TAG, "absolute = ${sequence.trigger.time.absolute}")
//            Log.d(TAG, "audio = ${sequence.audio}")
//            Log.d(TAG, "video = ${sequence.video}")
//            Log.d(TAG, "text = ${sequence.let { it.texts[0].text }}")
//            Log.d(TAG, "textnext = ${sequence.let { it.texts[1].textnext }}")
//        }
    }

    // Воспроизводим аудио
    private fun playAudio(name: String) {
        val audioJsonFile: File? = File("storage/87CB-16F2/Granit-BK-N/audio/$name")

        val mp = MediaPlayer()
        mp.setDataSource(audioJsonFile?.absolutePath)
        mp.prepare()
        mp.start()
    }

    companion object {
        const val KEY_TEXT = "key_text"
        const val TAG = "AvtoInformatorFragment"

        fun newInstance(str : String): AvtoInformatorFragment {
            return AvtoInformatorFragment().withArguments {
                Log.d(TAG, "newInstance")
                putString(KEY_TEXT, str)
            }
        }
    }
}