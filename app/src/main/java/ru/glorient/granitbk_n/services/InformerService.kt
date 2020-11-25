package ru.glorient.services

import android.content.*
import android.util.Log
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import ru.glorient.informer.AutoDirMode
import ru.glorient.informer.InformerModel
import ru.glorient.informer.RouteDir
import java.io.File
import java.lang.Exception

/**
 * Класс сервиса информатора.
 */
class InformerService : GPSService()  {
    /**
     * Модель информатора.
     */
    @ExperimentalCoroutinesApi
    var mInformerModel: InformerModel? = null

    /**
     * Создание сервиса.
     */
    @ExperimentalCoroutinesApi
    override fun onCreate() {
        super.onCreate()

        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                val mCurrentLocation = locationResult.lastLocation
                mInformerModel?.changeLocation(mCurrentLocation)
            }
        }
    }

    /**
     * Запуск сервиса.
     * https://developer.android.com/reference/android/app/Service#onStartCommand(android.content.Intent,%20int,%20int)
     *
     * @param intent интент (параметры запуска)
     * @param flags флаги запуска
     * @param startId параметр для остановки сервиса
     */
    @ExperimentalCoroutinesApi
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        start()
        sendMessage("""{"state":"connecting"}""")
        mMainJob = GlobalScope.launch{
            val options = intent.getStringExtra("connection")
            if(options != null){
                try {
                    val cn = JSONObject(options)
                    val fileName = if(cn.has("file")) cn.getString("file") else ""
                    try{
                        mInformerModel = InformerModel(
                                file = File(fileName),
                                context = this@InformerService,
                                onText = { s: String, list: List<Int> ->
                                    val x = JSONObject()
                                    x.put("text",s)
                                    x.put("id",JSONArray(list))
                                    val root = JSONObject()
                                    root.put("display",x)
                                    sendMessage(root.toString())
                                },
                                onChangeStation = {
                                    sendMessage("""{"station":$it}""")
                                },
                                onChangeDirrection = {
                                    sendMessage(mInformerModel?.getRoute().toString())
                                },
                                onError = {
                                    throw Exception(it.toString(4))
                                }
                        )

                        sendMessage("""{"state":"run","gpsenable":${mInformerModel!!.mGPSTriggerEnable}}""")
                        sendMessage(mInformerModel?.getAutoDir().toString())
                        sendMessage(mInformerModel?.getRoute().toString())
                        sendMessage(mInformerModel?.getScripts().toString())

                        val str = intent.getStringExtra("payload")
                        if(str != null) mChannelRx.send(str)
                    }catch (e: Exception){
                        sendMessage("""{"error":"parse route"}""")
                        mChannelRx.send("""{"cmd":"stop"}""")
                        Log.e(TAG,e.toString())
                    }
                }catch (e: JSONException){
                    sendMessage("""{"error":"the connection string is not json"}""")
                    mChannelRx.send("""{"cmd":"stop"}""")
                }
            }else{
                sendMessage("""{"error":"the connection string is absent"}""")
                mChannelRx.send("""{"cmd":"stop"}""")
            }
            while(isActive){
                val str=mChannelRx.receive()
                Log.d(TAG, str)
                try {
                    val msg = JSONObject(str)
                    if(msg.has("cmd")) {
                        if (msg.getString("cmd") == "stop") {
                            break
                        }
                    }

                    dirrectionToggle(msg)
                    gpsToggle(msg)
                    autoDir(msg)
                    setGPS(msg)
                    setPlay(msg)
                }catch (e: JSONException){
                    sendMessage("""{"warning":"the message string is not json)"}""")
                    Log.w(TAG,e.toString())
                }
            }
            stopLocationUpdates()
            stopSelf(startId)
        }
        return START_STICKY
    }

    /**
     * Изменение режима автоопределения напрвления движения
     *
     * @param msg json команда
     */
    @ExperimentalCoroutinesApi
    private fun autoDir(msg: JSONObject) {
        if (msg.has("autodir")) {
            when(msg.getString("autodir")){
                "soft" -> mInformerModel?.mAutoDirrection = AutoDirMode.SOFT
                "hard" -> mInformerModel?.mAutoDirrection = AutoDirMode.HARD
                else -> mInformerModel?.mAutoDirrection = AutoDirMode.NONE
            }
            sendMessage(mInformerModel?.getAutoDir().toString())
        }
    }

    /**
     * Окончание работы
     */
    @ObsoleteCoroutinesApi
    @ExperimentalCoroutinesApi
    override fun onDestroy() {
        runBlocking { mInformerModel?.deInit() }
        super.onDestroy()
    }

    /**
     * Запуск скриптов вручную
     *
     * @param msg json команда
     */
    @ExperimentalCoroutinesApi
    private suspend fun setPlay(msg: JSONObject) {
        if (msg.has("play")) {
            val list = msg.getJSONArray("play")
            for(i in (0 until list.length())){
                val  id = list.getInt(i)
                mInformerModel?.playScript(id)
            }
        }
    }

    /**
     * Изменение вектора направления движения
     *
     * @param msg json команда
     */
    @ExperimentalCoroutinesApi
    private fun dirrectionToggle(msg: JSONObject) {
        if (msg.has("dirrection")) {
            when(msg.getString("dirrection")){
                "forward" -> {
                    if(mInformerModel?.mDirrection != RouteDir.FORWARD){
                        changeDirrection()
                    }
                }
                "backward" -> {
                    if(mInformerModel?.mDirrection != RouteDir.BACKWARD){
                        changeDirrection()
                    }
                }
                else -> changeDirrection()
            }
        }
    }

    /**
     * Переключение режима работы триггеров GPS
     *
     * @param msg json команда
     */
    @ExperimentalCoroutinesApi
    private fun gpsToggle(msg: JSONObject) {
        if (msg.has("gpstoggle")) {
            when(msg.getString("gpstoggle")){
                "on" -> {
                    if(!mInformerModel!!.mGPSTriggerEnable){
                        toggleGPS()
                    }
                }
                "off" -> {
                    if(mInformerModel!!.mGPSTriggerEnable){
                        toggleGPS()
                    }
                }
                else -> toggleGPS()
            }
        }
    }

    /**
     * Переключение режима работы триггеров GPS
     */
    @ExperimentalCoroutinesApi
    private fun toggleGPS() {
        mInformerModel!!.mGPSTriggerEnable = !mInformerModel!!.mGPSTriggerEnable
        sendMessage("""{"gpsenable":${mInformerModel!!.mGPSTriggerEnable}}""")
    }

    /**
     * Изменение вектора направления движения
     */
    @ExperimentalCoroutinesApi
    private fun changeDirrection() {
        if(mInformerModel?.mDirrection == RouteDir.FORWARD){
            mInformerModel?.changeDirrection(RouteDir.BACKWARD)
        }else{
            mInformerModel?.changeDirrection(RouteDir.FORWARD)
        }
    }

    /**
     * Имя широковещательного канала на передачу.
     */
    override fun getOuputName(): String = toOutName
    /**
     * Имя широковещательного канала на прием.
     */
    override fun getInputName(): String = toInName

    companion object {
        /**
         * Строка для отладки.
         */
        private val TAG = InformerService::class.java.simpleName
        /**
         * Имя широковещательного канала на передачу.
         */
        const val toOutName = "fromInformer"
        /**
         * Имя широковещательного канала на прием.
         */
        const val toInName = "toInformer"
    }
}