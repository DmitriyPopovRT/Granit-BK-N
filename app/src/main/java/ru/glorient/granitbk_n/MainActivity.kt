package ru.glorient.granitbk_n

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
//import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import ru.glorient.granitbk_n.accesory.UpdateListListener
import ru.glorient.granitbk_n.avtoinformer.AvtoInformatorFragment
import ru.glorient.granitbk_n.camera.CameraFragment
import ru.glorient.services.ServiceManager
import java.util.*

class MainActivity : AppCompatActivity(), UpdateListListener {
    var flagService = false
    private lateinit var textView: TextView
    private lateinit var textViewCurrentTime: TextView
    private lateinit var buttonCamera: ImageButton
    private lateinit var buttonAvtoinformator: ImageButton
    private lateinit var containerBus: LinearLayout
    private lateinit var textViewSpeed: TextView

    private val updateListListener: UpdateListListener?
        get() = supportFragmentManager.findFragmentByTag("AvtoInformatorFragment")
            .let { it as? UpdateListListener }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textViewCurrentTime = findViewById(R.id.time)
        buttonCamera = findViewById(R.id.camera)
        buttonAvtoinformator = findViewById(R.id.avtoInformatorButton)
        containerBus = findViewById(R.id.containerBus)
        textViewSpeed = findViewById(R.id.speed)

        // Проверяем все ли даны разрешения
        if (!verifyPermissions()) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_CODE)
            Log.d(TAG, "onCreate запрашиваем разрешения")
        } else {
            Log.d(TAG, "onCreate есть все разрешения")
            onCreateActivity()
        }

        init()

        // При нажатии на значок переходим в активность Камера
        buttonCamera.setOnClickListener {
            val fragmentCamera = supportFragmentManager.findFragmentByTag("CameraFragment")
            val alreadyHasFragment = fragmentCamera != null

            if (!alreadyHasFragment) {
                // Создаем фрагмент камера
                supportFragmentManager.beginTransaction()
                    .replace(
                        R.id.containerBus,
                        CameraFragment(), "CameraFragment"
                    )
                    .addToBackStack("CameraFragment")
                    .commit()
            } else {
                supportFragmentManager.popBackStack("CameraFragment", 0)
            }
        }

        buttonAvtoinformator.setOnClickListener {
            val fragmentAvtoInformator =
                supportFragmentManager.findFragmentByTag("AvtoInformatorFragment")
            val alreadyHasFragment = fragmentAvtoInformator != null

            if (!alreadyHasFragment) {
                // Создаем фрагмент автоинформатор
                supportFragmentManager.beginTransaction()
                    .replace(R.id.containerBus,
                        AvtoInformatorFragment(), "AvtoInformatorFragment")
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

    // Инициализируем ServiceManager
    fun init() {
        model.serviceManager = ServiceManager(
            context = this,
            onStateChange = { serviceType: ServiceManager.ServiceType, serviceState: ServiceManager.ServiceState ->
                val s = model.states[serviceType]!!.copy()
                s.state = serviceState
                model.states[serviceType] = s
                model.logs.add(0, "$serviceType->$serviceState")
                if (model.logs.size > 50) model.logs.removeRange(49, model.logs.size - 1)
            },
            onRecieve = { serviceType: ServiceManager.ServiceType, jsonObject: JSONObject ->
                model.logs.add(0, "$serviceType->$jsonObject")
                if (model.logs.size > 50) model.logs.removeRange(49, model.logs.size - 1)
                Log.d(serviceType.toString(), jsonObject.toString())
            },
            onSTM32Search = {
                if (it) {
                    model.states[ServiceManager.ServiceType.STM32] =
                        Model.ServiceModel(ServiceManager.ServiceState.STOPED)
                } else {
                    model.states.remove(ServiceManager.ServiceType.STM32)
                }
            }
        )

        model.serviceManager.subscribe(model.mInformer)
    }

    // Обрабатываем нажатие на пункты меню
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.startService -> {
                if (!flagService) {

                    model.states.forEach {
                        //                    if (it.value.state == ServiceManager.ServiceState.STOPED) {
                        model.serviceManager.startService(
                            it.key,
                            it.value.connection,
                            it.value.payload
                        )

                        if (it.key.name == "Informer") {
                            containerBus.removeView(textView)

                            val timeDelay = SystemClock.uptimeMillis() + 1000L
                            val mainHandler = Handler(Looper.getMainLooper())
                            mainHandler.postAtTime({
                                supportFragmentManager.beginTransaction()
                                    .replace(
                                        R.id.containerBus,
                                        AvtoInformatorFragment.newInstance(""),
                                        "AvtoInformatorFragment"
                                    )
                                    .addToBackStack("AvtoInformatorFragment")
                                    .commit()
                            }, timeDelay)
                        }

                        item.title = "Остановить службу"
                        flagService = true
                    }
                } else {
                    item.title = "Запустить службу"
                    flagService = false

                    model.states.forEach {
                        model.serviceManager.stopService(it.key)
                    }
                }

//                // Передаем флаг через EventBus
//                val obj = MessageEvent(flagService)
//                EventBus.getDefault().post(obj)

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
    }

    // Раз в секунду обновляем время
    private fun updateTime() {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post(object : Runnable {
            override fun run() {
                textViewCurrentTime.text = getCurrentTime()
                getLocation()
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
        val model: Model = Model()
    }

    // Прокидываем листенер до фрагмента
    override fun updateList() {
        updateListListener?.updateList()
    }

    // Получаем локацию
    private fun getLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        LocationServices.getFusedLocationProviderClient(this)
            .lastLocation
            .addOnSuccessListener {
                it?.let {
                    val speed = (it.speed.toInt()*3600)/1000
                    val str = resources.getString(R.string.speed, speed.toString())
                    // Изменяем скорость
                    textViewSpeed.text = str
                } ?: Toast.makeText(
                    this,
                    R.string.location_absent,
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
            .addOnCanceledListener {
                Toast.makeText(
                    this,
                    R.string.location_request_was_canceled,
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    R.string.location_request_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}

class MessageEvent(val serviceFlag: Boolean)