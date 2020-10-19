package ru.glorient.granitbk_n

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.bus_stops.*
import ru.glorient.granitbk_n.json.FileJsonOriginal
import ru.glorient.granitbk_n.json.ParserJSON
import ru.glorient.granitbk_n.json.Sequences
import java.io.*
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {
    private var fileJsonOriginal = FileJsonOriginal()
    private var sequences = ArrayList<Sequences>()
    private var flagService = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Проверяем все ли даны разрешения
        if (!verifyPermissions()) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_CODE)
            Log.d(TAG, "onCreate запрашиваем разрешения")
        } else {
            Log.d(TAG, "onCreate есть все разрешения")
            onCreateActivity()
        }

        // При нажатии на значок переходим в активность Камера
        camera.setOnClickListener {
            val cameraActivityIntent = Intent(this, CameraActivity::class.java)
            startActivity(cameraActivityIntent)
        }
    }

    // Подключаем меню
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_main, menu)
        return true
    }

    // Обрабатываем нажатие на пункты меню
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.startService -> {
                if (flagService) {
                    item.title = "Запустить службу"
                    flagService = false
                    Toast.makeText(this, "Служба остановлена", Toast.LENGTH_SHORT).show()
                } else {
                    // Парсим json
                    val str = ParserJSON().readFileSD()
                    if (str != null) {
                        fileJsonOriginal = ParserJSON().parse(str)!!
                    }

                    updateAvtoInformator()

                    item.title = "Остановить службу"
                    flagService = true
                    Toast.makeText(this, "Служба запущена", Toast.LENGTH_SHORT).show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Дублируем onCreate если пользователь дал разрешения
    private fun onCreateActivity() {
        updateTime()

        // Отрисовываем View с остановками
        val view = layoutInflater.inflate(R.layout.bus_stops, containerBus, false)
//        val view = layoutInflater.inflate(R.layout.activity_camera, containerBus, false)
        view.apply {
//            textView.text = textToAdd
//            deleteButton.setOnClickListener {
//                container.removeView(this)
//            }
        }

        containerBus.addView(view)
    }

    // Обновление автоинформатора
    private fun updateAvtoInformator() {
        sequences = fileJsonOriginal.sequences
        Log.d(TAG, "version = ${fileJsonOriginal.version}")

        var i = 0
        while (i < sequences.size) {
            val sequence = sequences[i]

            when (i) {
                0 -> {
                    textView5.text = sequence.name
                    textView5.setTextColor(Color.BLACK)
                    textView10.text = sequence.trigger.time.absolute
                    textView10.setTextColor(Color.BLACK)
                    i++
                    playAudio(sequence.audio[0].toString())
                }
                1 -> {
                    textView6.text = sequence.name
                    textView11.text = sequence.trigger.time.absolute
//                    playAudio(sequence.audio[0].toString())
                    i++
                }
                2 -> {
                    textView7.text = sequence.name
                    textView12.text = sequence.trigger.time.absolute
//                    playAudio(sequence.audio[0].toString())
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

    // Раз в секунду обновляем время
    private fun updateTime() {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post(object : Runnable {
            override fun run() {
                time.text = getCurrentTime()
                mainHandler.postDelayed(this, 1000)
            }
        })
    }

    // Воспроизводим аудио
    private fun playAudio(name: String) {
        val audioJsonFile: File? = File("storage/87CB-16F2/Granit-BK-N/audio/$name")

        val mp = MediaPlayer()
        mp.setDataSource(audioJsonFile?.absolutePath)
        mp.prepare()
        mp.start()
    }

    // Отслеживаем принял ли пользователь все разрешения
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        Log.d(TAG, "onRequestPermissionsResult отслеживаем разрешения")
        // проверка по запрашиваемому коду и разрешениям
        if (requestCode == REQUEST_CODE && verifyPermissions()) {
            Log.d(TAG, "onRequestPermissionsResult есть все разрешения")
            onCreateActivity()
        } else {
            // Не все разрешения приняты, запрашиваем еще раз
            ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_CODE)
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    // Проверка все ли приняты разрешения
    private fun verifyPermissions(): Boolean {
        // Проверяем, есть ли у нас разрешение камеру
        val isCameraPermissionGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        // Проверяем, есть ли у нас разрешение на запись
        val isWritePermissionGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        // Проверяем, есть ли у нас разрешение на чтение
        val isReadPermissionGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        // Проверяем, есть ли у нас разрешение на получение координат
        val isGeoPermissionGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // Проверяем, есть ли у нас разрешение на запись аудио
        val isRecordAudioPermissionGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        return isCameraPermissionGranted &&
                isWritePermissionGranted &&
                isReadPermissionGranted &&
                isGeoPermissionGranted &&
                isRecordAudioPermissionGranted
    }

    // Получаем время
    fun getCurrentTime(): String? {
        val calendar = Calendar.getInstance()
        val hour = calendar[Calendar.HOUR_OF_DAY]
        val minute = calendar[Calendar.MINUTE]
        val second = calendar[Calendar.SECOND]
        val day = calendar[Calendar.DATE]
        val month = calendar[Calendar.MONTH]
        val year = calendar[Calendar.YEAR]
        return String.format(
            "%02d.%02d.%02d  %02d:%02d:%02d",
            day, month + 1, year, hour, minute, second
        ) // ДД.ММ.ГГГГ ЧЧ:ММ:СС - формат времени
    }

    companion object {
        private const val REQUEST_CODE = 123
        private val PERMISSIONS = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        private const val TAG = "MainActivity"
    }
}