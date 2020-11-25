package ru.glorient.services

import android.Manifest
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Родительский класс сервиса
 *
 * Управление сервиса через широковещательный канал [mMessageReceiver] json командой в "payload".
 * Результат выдаётся в широковещательный канал.
 * Сихронизация корутины [mMainJob] через канал [mChannelRx]
 *
 * @param length максимальное количество сообщений в очереди [mChannelRx]
 */
abstract class ParentService(length: Int = 100) : Service() {
    /**
     * Канал входящих сообщений из [mMessageReceiver]
     */
    protected val mChannelRx = Channel<String>(length)
    /**
     * Основная корутина
     */
    protected var mMainJob: Job? = null

    /**
     * Приемник широковещательных сообщений
     */
    private val mMessageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val str = intent.getStringExtra("payload")
            if (str != null) runBlocking { mChannelRx.send(str) }
        }
    }

    /**
     * Отослать широковещательное сообщение
     *
     * @param str стока сообщения
     */
    protected open fun sendMessage(str: String) {
        val intent = Intent(getOuputName())
        intent.putExtra("payload", str)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    /**
     * Начало работы
     *
     * Запускать в начале [onStartCommand].
     */
    protected fun start(){
        runBlocking { mMainJob?.cancelAndJoin() }
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(mMessageReceiver,
                        IntentFilter(getInputName()))
    }

    /**
     * Завершение работы сервиса
     */
    @ExperimentalCoroutinesApi
    override fun onDestroy() {
        runBlocking {
            sendMessage("""{"state":"stopped"}""")
            mChannelRx.send("""{"cmd":"stop"}""")
            mMainJob?.join()
            while (!mChannelRx.isEmpty) mChannelRx.receive()
            mMainJob = null
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver)
        super.onDestroy()
    }

    /**
     * Заглушка.
     *
     * Данный механизм не используется.
     */
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    /**
     * Получить имя широковещательного передатчика.
     *
     * @return имя широковещательного передатчика.
     */
    abstract fun getOuputName(): String
    /**
     * Получить имя широковещательного приемника.
     *
     * @return имя широковещательного приемника.
     */
    abstract fun getInputName(): String
}

/**
 * Родительский класс сервиса, использующего данные локации.
 *
 * @param length максимальное количество сообщений в очереди
 */
abstract class GPSService(length: Int = 100) : ParentService(length) {
    /**
     * Provides access to the Fused Location Provider API.
     */
    protected var mFusedLocationClient: FusedLocationProviderClient? = null
    /**
     * Provides access to the Location Settings API.
     */
    private var mSettingsClient: SettingsClient? = null
    /**
     * Stores parameters for requests to the FusedLocationProviderApi.
     */
    private var mLocationRequest: LocationRequest? = null
    /**
     * Callback for Location events.
     */
    protected var mLocationCallback: LocationCallback? = null
    /**
     * Stores the types of location services the client is interested in using. Used for checking
     * settings to determine if the device has optimal location settings.
     */
    private var mLocationSettingsRequest: LocationSettingsRequest? = null

    /**
     * Флаг синхронизации процесса подключения к сервису локаций.
     */
    private val mGpsEnableWait: AtomicBoolean = AtomicBoolean(false)
    /**
     * Флаг разрешения работы.
     */
    protected var mGpsAccess = false
    /**
     * Requests location updates from the FusedLocationApi. Note: we don't call this unless location
     * runtime permission has been granted.
     */
    private fun startLocationUpdates() {
        if(mGpsAccess) {
            mGpsEnableWait.set(true)
            mSettingsClient!!.checkLocationSettings(mLocationSettingsRequest)
                    .addOnSuccessListener {
                        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            sendMessage("""{"error":"GPS is failed"}""")
                            mGpsEnableWait.set(false)
                        } else {
                            mFusedLocationClient!!.requestLocationUpdates(mLocationRequest,
                                    mLocationCallback, Looper.myLooper())
                            mGpsEnableWait.set(false)
                        }
                    }
                    .addOnFailureListener {
                        sendMessage("""{"error":"GPS is failed"}""")
                    }
            while (mGpsEnableWait.get()) {
            }
        }
    }
    /**
     * Removes location updates from the FusedLocationApi.
     */
    protected fun stopLocationUpdates() {
        if(mGpsAccess) mFusedLocationClient!!.removeLocationUpdates(mLocationCallback)
    }

    /**
     * Создание сервиса.
     */
    override fun onCreate() {
        super.onCreate()

        mGpsAccess=(PermissionChecker.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PermissionChecker.PERMISSION_GRANTED)

        if(mGpsAccess) {
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            mSettingsClient = LocationServices.getSettingsClient(this)

            mLocationRequest = LocationRequest()
            mLocationRequest!!.interval = 10000
            mLocationRequest!!.fastestInterval = 5000
            mLocationRequest!!.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

            val builder = LocationSettingsRequest.Builder()
            builder.addLocationRequest(mLocationRequest!!)
            mLocationSettingsRequest = builder.build()
        }
    }

    /**
     * Обработка команды подключения к сервису лакации.
     *
     * @param msg сообщение от широковещательного приемника
     */
    protected fun setGPS(msg: JSONObject) {
        if (msg.has("GPS")) {
            if(mGpsAccess) {
                val gps = msg.getJSONObject("GPS")
                stopLocationUpdates()

                mLocationRequest = LocationRequest()
                if (gps.has("interval")) {
                    mLocationRequest!!.interval = gps.getLong("interval")
                } else mLocationRequest!!.interval = 30000
                if (gps.has("fastestInterval")) {
                    mLocationRequest!!.fastestInterval = gps.getLong("fastestInterval")
                } else mLocationRequest!!.fastestInterval = 2500
                mLocationRequest!!.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

                val builder = LocationSettingsRequest.Builder()
                builder.addLocationRequest(mLocationRequest!!)
                mLocationSettingsRequest = builder.build()

                if (gps.has("run")) {
                    if (gps.getString("run") == "off") return
                }
                startLocationUpdates()
            } else sendMessage("""{"error":"GPS access denied"}""")
        }
    }
}
