package ru.glorient.granitbk_n

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.View.OnSystemUiVisibilityChangeListener
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import org.json.JSONObject
import ru.glorient.granitbk_n.accessory.*
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
    private lateinit var progressBar: ProgressBar

    private var handler: Handler? = null
    private var runnable: Runnable = object : Runnable {
        override fun run() {
            Log.i(AvtoInformatorFragment.TAG, "postDelayed")
            binding.satellite.text = "0"
        }
    }

    // Менеджер для работы с местоположением
    private var locationManager: LocationManager? = null

    private val locationListener: LocationListener =
        object : LocationListener {
            // обязательные функции LocationListener
            override fun onLocationChanged(location: Location) {}
            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onStatusChanged(
                provider: String,
                status: Int,
                extras: Bundle
            ) {
            }
        }

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
            Accessory().screenBlock(true, window)
            val fragmentCamera = supportFragmentManager.findFragmentByTag("CameraFragment")
            val alreadyHasFragment = fragmentCamera != null

            if (!alreadyHasFragment) {
                binding.containerBus.removeAllViews()
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
            Accessory().screenBlock(false, window)
        }

        // При нажатии на значок загружаем фрагмент Автоинформатор
        binding.avtoInformatorButton.setOnClickListener {
            if (filePath.isNotEmpty()) {
                Accessory().screenBlock(true, window)
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
                Accessory().screenBlock(false, window)
            } else {
                Toast.makeText(this, "Выберите файл маршрута", Toast.LENGTH_SHORT).show()
            }
        }

        // Выбираем маршрут из файлов на устройстве
        binding.buttonRoute.setOnClickListener {
            val fileDialog = OpenFileDialog(this)
            fileDialog.setFilterJson(".jsonc")
            val drawFolder = resources.getDrawable(R.drawable.ic_folder)
            val drawRoute = resources.getDrawable(R.drawable.ic_route)
            fileDialog.setFolderIcon(drawFolder)
            fileDialog.setFileIcon(drawRoute)
            fileDialog.setOpenDialogListener { fileName ->
                Accessory().screenBlock(true, window)

                progressBar = createProgressBar()
                binding.containerBus.removeAllViews()
//                binding.containerBus.removeView(textViewNoList)
                binding.containerBus.addView(progressBar)

                if (flagService) {
                    model.states.forEach {
                        Log.d(
                            AvtoInformatorFragment.TAG,
                            "stopService ${it.key.name} ${it.value.state != ServiceManager.ServiceState.STOPED}"
                        )
                        if (it.value.state != ServiceManager.ServiceState.STOPED) {
                            model.serviceManager.stopService(it.key)
                            model.serviceManager.unsubscribe(model.mInformer)
                        }
                    }

                    Handler(Looper.getMainLooper()).postAtTime({
                        Log.d(AvtoInformatorFragment.TAG, "Путь к файлу $fileName")
                        filePath = fileName
                        init()
                    }, SystemClock.uptimeMillis() + 500L)
                }

                Handler(Looper.getMainLooper()).postAtTime({
                    startService(fileName)
                    flagService = true
                }, SystemClock.uptimeMillis() + 1000L)
            }

            val dialog = fileDialog.show()
            dialog.window?.decorView?.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }

        // Настройки
        binding.buttonSetting.setOnClickListener {
            DialogFragmentSetting().showDialog(this)
        }

        // Автоматический/ручной режим
        binding.buttonAvto.setOnClickListener {
            if (flagSelectedButtonAvto) {
                binding.buttonAvto.setBackgroundResource(R.drawable.button_square)
            } else {
                binding.buttonAvto.setBackgroundResource(R.drawable.button_square_select)
            }

            flagSelectedButtonAvto = !flagSelectedButtonAvto

            model.mInformer.toggleGPS()
        }

        // Кнопка SOS
        binding.buttonSos.setOnClickListener {
            val dialog: AlertDialog = AlertDialog.Builder(this)
                .setMessage("Вы действительно хотите отправить команду SOS?")
                .setPositiveButton("Да") { _, _ ->
                    binding.iconSos.visibility = View.VISIBLE
                }
                .setNegativeButton("Отмена") { _, _ ->
                    binding.iconSos.visibility = View.INVISIBLE
                }
                .create()

            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            dialog.show()
            dialog.window?.decorView?.systemUiVisibility = this.window.decorView.systemUiVisibility
            dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
    }

    private fun setFullScreen() {
        // прячем панель навигации и строку состояния
        val decorView = window.decorView
        val uiOptions = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        decorView.systemUiVisibility = uiOptions
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        setFullScreen()
    }

    private fun startService(fileName: String) {
        if (!flagService) {
            Log.d(AvtoInformatorFragment.TAG, "Путь к файлу $fileName")
            filePath = fileName
            init()
        }

        model.states.forEach {
            Log.d(
                AvtoInformatorFragment.TAG,
                "startService ${it.key.name} ${it.value.state == ServiceManager.ServiceState.STOPED}"
            )
            if (it.value.state == ServiceManager.ServiceState.STOPED) {
                model.serviceManager.startService(
                    it.key,
                    it.value.connection,
                    it.value.payload
                )

                if (it.key.name == "Informer") {

                    val timeDelay = SystemClock.uptimeMillis() + 1000L
                    val mainHandler = Handler(Looper.getMainLooper())
                    mainHandler.postAtTime({
                        binding.containerBus.removeView(progressBar)
                        supportFragmentManager.beginTransaction()
                            .replace(
                                R.id.containerBus,
                                AvtoInformatorFragment.newInstance(""),
                                "AvtoInformatorFragment"
                            )
                            .addToBackStack("AvtoInformatorFragment")
                            .commit()

                        Accessory().screenBlock(false, window)
                    }, timeDelay)
                }
            }
        }
    }

    /*// Подключаем меню
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_main, menu)
        return true
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
    }*/

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

    // Функция создания прогресс бара программно
    private fun createProgressBar(): ProgressBar {
        // Создаем прогессбар
        val progressBar = ProgressBar(this)
        // Указываем программно ширину textView
        val params = LinearLayout.LayoutParams(
            200,
            200
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
        textViewNoList.text = "Список пуст. Пожалуйста, выберите маршрут"
        textViewNoList.gravity = Gravity.CENTER
        textViewNoList.setTextColor(Color.RED)
        binding.containerBus.addView(textViewNoList)

        // Инициализируем и считываем настройки с json
        settingsParse = SettingsParse(this)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        // получаем местоположения устройства
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

        locationManager!!.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            0, 0f, locationListener
        )
        locationManager!!.addGpsStatusListener { event ->
            var satellites = 0
            var satellitesInFix = 0
            val timetofix = locationManager!!.getGpsStatus(null)!!.timeToFirstFix
//            Log.i(AvtoInformatorFragment.TAG, "Time to first fix = $timetofix")
            for (sat in locationManager!!.getGpsStatus(null)!!.satellites) {
                if (sat.usedInFix()) {
                    satellitesInFix++
                }
                satellites++
            }
//            Log.i(
//                AvtoInformatorFragment.TAG,
//                "$satellites Used In Last Fix ($satellitesInFix)"
//            )
            binding.satellite.text = satellitesInFix.toString()

            if(handler != null) {
                handler!!.removeCallbacks(runnable)
            }

            handler = Handler(Looper.getMainLooper())
            handler!!.postAtTime(runnable, SystemClock.uptimeMillis() + 2000L)
        }
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

    fun showImmersiveDialog(mDialog: Dialog, mActivity: Activity) {
        //Set the dialog to not focusable
        mDialog.getWindow()?.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        mDialog.getWindow()?.getDecorView()?.setSystemUiVisibility(setSystemUiVisibility())
        mDialog.setOnShowListener {
            @Override
            fun onShow(dialog: DialogInterface?) {
                //Clear the not focusable flag from the window
                mDialog.getWindow()?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

                //Update the WindowManager with the new attributes
                val wm: WindowManager =
                    mActivity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.updateViewLayout(
                    mDialog.getWindow()?.getDecorView(),
                    mDialog.getWindow()?.getAttributes()
                )
            }
        }
        mDialog.getWindow()?.getDecorView()
            ?.setOnSystemUiVisibilityChangeListener(OnSystemUiVisibilityChangeListener { visibility ->
                if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                    mDialog.getWindow()!!.getDecorView()
                        .setSystemUiVisibility(setSystemUiVisibility())
                }
            })
    }

    fun setSystemUiVisibility(): Int {
        return (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }
}

class MessageEvent(val serviceFlag: Boolean)