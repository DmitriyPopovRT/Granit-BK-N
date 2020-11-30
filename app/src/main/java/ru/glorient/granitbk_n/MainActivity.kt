package ru.glorient.granitbk_n

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import org.json.JSONObject
import ru.glorient.granitbk_n.accesory.DialogFragmentSetting
import ru.glorient.granitbk_n.accesory.OpenFileDialog
import ru.glorient.granitbk_n.accesory.SettingsParse
import ru.glorient.granitbk_n.accesory.UpdateListListener
import ru.glorient.granitbk_n.avtoinformer.AvtoInformatorFragment
import ru.glorient.granitbk_n.camera.CameraFragment
import ru.glorient.granitbk_n.databinding.ActivityMainBinding
import ru.glorient.services.ServiceManager
import java.util.*

class MainActivity : AppCompatActivity(), UpdateListListener {
    // Флаг запущен ли сервис
    var flagService = false
    // Начальное textView показывающее что маршрут не подгружен
    private lateinit var textViewNoList: TextView
    private lateinit var binding: ActivityMainBinding

    private val updateListListener: UpdateListListener?
        get() = supportFragmentManager.findFragmentByTag("AvtoInformatorFragment")
            .let { it as? UpdateListListener }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // Проверяем все ли даны разрешения
        if (!verifyPermissions()) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_CODE)
            Log.d(TAG, "onCreate запрашиваем разрешения")
        } else {
            Log.d(TAG, "onCreate есть все разрешения")
            onCreateActivity()
        }

        // При нажатии на значок загружаем фрагмент Камера
        binding.camera.setOnClickListener {
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

        // При нажатии на значок загружаем фрагмент Автоинформатор
        binding.avtoInformatorButton.setOnClickListener {
            val fragmentAvtoInformator =
                supportFragmentManager.findFragmentByTag("AvtoInformatorFragment")
            val alreadyHasFragment = fragmentAvtoInformator != null

            if (!alreadyHasFragment) {
                // Создаем фрагмент автоинформатор
                supportFragmentManager.beginTransaction()
                    .replace(
                        R.id.containerBus,
                        AvtoInformatorFragment(), "AvtoInformatorFragment"
                    )
                    .addToBackStack("AvtoInformatorFragment")
                    .commit()
            } else {
                supportFragmentManager.popBackStack("AvtoInformatorFragment", 0)
            }
        }

        // Выбираем маршрут из файлов на устройстве
        binding.buttonRoute.setOnClickListener {
            val fileDialog = OpenFileDialog(this)
            fileDialog.setOpenDialogListener { fileName ->
                Log.d(AvtoInformatorFragment.TAG, "Путь к файлу $fileName")
                filePath = fileName
                init()
            }
            fileDialog.show()
        }

        // Настройки
        binding.buttonSetting.setOnClickListener {
//            val file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path + File.separator.toString() + "settings.jsonc"
            DialogFragmentSetting()
                .show(supportFragmentManager, "DialogFragmentSetting")
        }

        // Автоматический/ручной режим
        binding.buttonAvto.setOnClickListener {
            if(flagSelectedButtonAvto) {
                binding.buttonAvto.setBackgroundResource(R.drawable.button_square)
            }
            else {
                binding.buttonAvto.setBackgroundResource(R.drawable.button_square_select)
            }

            flagSelectedButtonAvto = !flagSelectedButtonAvto

            model.mInformer.toggleGPS()
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
        model = Model()
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
                if (filePath.isNotEmpty()) {
                    if (!flagService) {
                        model.states.forEach {
                            //                    if (it.value.state == ServiceManager.ServiceState.STOPED) {
                            model.serviceManager.startService(
                                it.key,
                                it.value.connection,
                                it.value.payload
                            )

                            if (it.key.name == "Informer") {
                                binding.containerBus.removeView(textViewNoList)

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
                } else {
                    Toast.makeText(this, "Выберите файл маршрута", Toast.LENGTH_SHORT).show()
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
        textViewNoList = TextView(this)
        // Указываем программно ширину textView
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        textViewNoList.layoutParams = params
        textViewNoList.text = "Список пуст. Пожалуйста, запустите службу"
        textViewNoList.gravity = Gravity.CENTER
        textViewNoList.setTextColor(Color.RED)
        binding.containerBus.addView(textViewNoList)

        // Инициализируем и считываем настройки с json
        settingsParse = SettingsParse(this)
    }

    // Раз в секунду обновляем время
    private fun updateTime() {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post(object : Runnable {
            override fun run() {
                binding.time.text = getCurrentTime()
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
        lateinit var model: Model
        lateinit var settingsParse: SettingsParse
        var filePath: String = ""
        var flagSelectedButtonAvto = true
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
                    val speed = (it.speed.toInt() * 3600) / 1000
                    val str = resources.getString(R.string.speed, speed.toString())
                    // Изменяем скорость
                    binding.speed.text = str
                } ?: Log.d(TAG, resources.getString(R.string.location_absent))
            }
            .addOnCanceledListener {
                Log.d(TAG, resources.getString(R.string.location_request_was_canceled))
            }
            .addOnFailureListener {
                Log.d(TAG, resources.getString(R.string.location_request_failed))
            }
    }
}

class MessageEvent(val serviceFlag: Boolean)