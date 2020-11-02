package ru.glorient.granitbk_n

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import org.greenrobot.eventbus.EventBus
import ru.glorient.granitbk_n.camera.CameraFragment
import ru.glorient.granitbk_n.json.ParserJSON
import java.util.*

class MainActivity : AppCompatActivity() {
    var flagService = false
    private lateinit var textView: TextView

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
            val fragmentCamera = supportFragmentManager.findFragmentByTag("CameraFragment")
            val alreadyHasFragment = fragmentCamera != null

            if (!alreadyHasFragment) {
                // Создаем фрагмент камера
                supportFragmentManager.beginTransaction()
                    .replace(R.id.containerBus,
                        CameraFragment(), "CameraFragment")
                    .addToBackStack("CameraFragment")
                    .commit()
            } else {
                supportFragmentManager.popBackStack("CameraFragment", 0)
            }
        }

        avtoInformatorButton.setOnClickListener {
            val fragmentAvtoInformator =
                supportFragmentManager.findFragmentByTag("AvtoInformatorFragment")
            val alreadyHasFragment = fragmentAvtoInformator != null

            if (!alreadyHasFragment) {
                // Создаем фрагмент автоинформатор
                supportFragmentManager.beginTransaction()
                    .replace(R.id.containerBus, AvtoInformatorFragment(), "AvtoInformatorFragment")
                    .addToBackStack("AvtoInformatorFragment")
                    .commit()
            } else {
                supportFragmentManager.popBackStack("AvtoInformatorFragment", 0)
            }
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
//                    EventBus.getDefault().register(this)
                    Log.d(
                        AvtoInformatorFragment.TAG,
                        "Остановили службу и передаем флаг $flagService"
                    )
                    // Передаем флаг через EventBus
                    val obj = MessageEvent(flagService)
                    EventBus.getDefault().post(obj)
                } else {
                    // Парсим json
                    val str = ParserJSON().readFileSD()
                    if (str != null) {
                        Log.d(AvtoInformatorFragment.TAG, "Парсим и передаем listener")

                        // Удаляем оповещение о пустом контейнере
                        containerBus.removeView(textView)
//                        val progBar = createProgressBar()
//                        containerBus.addView(progBar)

                        supportFragmentManager.beginTransaction()
                            .replace(
                                R.id.containerBus,
                                AvtoInformatorFragment.newInstance(str),
                                "AvtoInformatorFragment"
                            )
                            .addToBackStack("AvtoInformatorFragment")
                            .commit()

//                        containerBus.removeView(progBar)

                    }

                    item.title = "Остановить службу"
                    flagService = true
                    Toast.makeText(this, "Служба запущена", Toast.LENGTH_SHORT).show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Функция создания прогресс бара программно
    private fun createProgressBar(): ProgressBar {
        // Создаем прогессбар
        val progressBar = ProgressBar(this)
        // Указываем программно ширину textView
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        progressBar.layoutParams = params

        progressBar.foregroundGravity = Gravity.CENTER
        return progressBar
    }


    // Дублируем onCreate если пользователь дал разрешения
    private fun onCreateActivity() {
        updateTime()
        textView = TextView(this)
        // Указываем программно ширину textView
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        textView.layoutParams = params
        textView.text = "Список пуст. Пожалуйста, запустите службу"
        textView.gravity = Gravity.CENTER
        textView.setTextColor(Color.RED)
        containerBus.addView(textView)

//        // Создаем фрагмент автоинформатора
//        supportFragmentManager.beginTransaction()
//            .replace(R.id.containerBus, AvtoInformatorFragment(), "AvtoInformatorFragment")
//            .addToBackStack("AvtoInformatorFragment")
//            .commit()
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

class MessageEvent(val serviceFlag: Boolean)