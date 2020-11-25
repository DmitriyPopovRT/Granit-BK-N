package ru.glorient.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONException
import org.json.JSONObject
import java.lang.Math.random
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class MQTTService : GPSService() {

    private var mqttClient: MqttAndroidClient? = null
    private var id: Int = 0
    private var rootTopic = ""
    private val mqttOptions = MqttConnectOptions()
    private val mqttStopping: AtomicBoolean = AtomicBoolean(false)
    private var log = false

    override fun onCreate() {
        super.onCreate()

        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                val mCurrentLocation = locationResult.lastLocation
                val res = JSONObject("""{"time":"${Date(mCurrentLocation!!.time).toString()}"}""")
                val position = JSONObject("""{"latitude":${mCurrentLocation!!.latitude},"longitude":${mCurrentLocation!!.longitude}}""")
                if(mCurrentLocation.hasAccuracy()){
                    position.put("accuracy",mCurrentLocation.accuracy)
                }
                res.put("position",position)
                if(mCurrentLocation.hasSpeed()){
                    val speed = JSONObject("""{"value":${mCurrentLocation!!.speed}}""")
                    if(mCurrentLocation.hasSpeedAccuracy()){
                        speed.put("accuracy",mCurrentLocation.speedAccuracyMetersPerSecond)
                    }
                    res.put("speed",speed)
                }
                if(mCurrentLocation.hasBearing()){
                    val bearing = JSONObject("""{"value":${mCurrentLocation!!.bearing}}""")
                    if(mCurrentLocation.hasBearingAccuracy()){
                        bearing.put("accuracy",mCurrentLocation.bearingAccuracyDegrees)
                    }
                    res.put("bearing",bearing)
                }
                if(mCurrentLocation.hasAltitude()){
                    val altitude = JSONObject("""{"value":${mCurrentLocation!!.altitude}}""")
                    if(mCurrentLocation.hasVerticalAccuracy()){
                        altitude.put("accuracy",mCurrentLocation.verticalAccuracyMeters)
                    }
                    res.put("altitude",altitude)
                }
                publish("GPS", res.toString(),0)
            }
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        start()
        sendMessage("""{"state":"connecting"}""")
        val myself=this
        mqttStopping.set(false)
        mMainJob = GlobalScope.launch{
            val options = intent.getStringExtra("connection")
            if(options != null){
                try {
                    val cn = JSONObject(options)
                    val server = if(cn.has("server")) cn.getString("server") else "romasty.duckdns.org"
                    val port = if(cn.has("port")) cn.getInt("port") else 8883
                    id = if(cn.has("transport_id")) cn.getInt("transport_id") else (random()*10000).toInt()
                    rootTopic = "Android-$id"
                    val ssl = if(cn.has("ssl")) cn.getBoolean("ssl") else true
                    if(cn.has("timeout")) mqttOptions.connectionTimeout = cn.getInt("timeout")
                    mqttOptions.userName = if(cn.has("user")) cn.getString("user") else "glorient"
                    mqttOptions.password  = (if(cn.has("password")) cn.getString("password") else "raQNI6dpZZrd44xoXFPz").toCharArray()
                    mqttOptions.setWill("$rootTopic/status","""{"client":"offline"}""".toByteArray(),1,true)
                    mqttOptions.keepAliveInterval = 30
                    val serverURI = (if(ssl) "ssl://" else "tcp://") + "$server:$port"
                    mqttClient = MqttAndroidClient(this@MQTTService, serverURI, rootTopic)
                    mqttClient?.registerResources(this@MQTTService)
                    mqttClient!!.setCallback(object : MqttCallback {
                        override fun messageArrived(topic: String?, message: MqttMessage?) {
                            try {
                                val msg = JSONObject(message?.toString())
                                msg.put("topic",topic)
                                if(topic!!.endsWith("EGTS")) sendToEGTS(msg.toString())
                                else if(topic!!.endsWith("MQTT")) sendToMQTT(msg.toString())
                                else if(topic!!.endsWith("STM32")) sendToSTM32(msg.toString())
                                else if(topic!!.endsWith("Informer")) sendToInformer(msg.toString())
                                else sendMessage(msg.toString())
                            }catch (e: JSONException){
                                sendMessage("""{"warning":"the message string is not json"}""")
                            }
//                            Log.d(TAG, "Receive message: ${message.toString()} from topic: $topic")
                        }
                        override fun connectionLost(cause: Throwable?) {
                            if(!mqttStopping.get()) {
                                sendMessage("""{"state":"connecting"}""")
                                sendMessage("""{"warning":"Connection lost ${cause.toString()}"}""")
                                connect()
                            }
                        }
                        override fun deliveryComplete(token: IMqttDeliveryToken?) {
                        }
                    })

                    if(ssl) {
                        /////SSL ENABLED////
                        val caKeyStore = KeyStore.getInstance(KeyStore.getDefaultType())
                        caKeyStore.load(null, null)
                        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                        tmf.init(caKeyStore)
                        val context = SSLContext.getInstance("TLS")
                        context.init(null, tmf.trustManagers, null)
                        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                            override fun getAcceptedIssuers(): Array<X509Certificate> {
                                return emptyArray()
                            }

                            @Throws(CertificateException::class)
                            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
                            }

                            @Throws(CertificateException::class)
                            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
                            }
                        })
                        val sslContext = SSLContext.getInstance("SSL")
                        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                        val sslSocketFactory = sslContext.socketFactory
                        mqttOptions.socketFactory = sslSocketFactory
                        /////SSL ENABLED////
                    }
                    connect()

                    val str = intent.getStringExtra("payload")
                    if(str != null) mChannelRx.send(str)
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
//                Log.d(TAG, str)
                try {
                    val msg = JSONObject(str)
                    if(msg.has("cmd")) {
                        if (msg.getString("cmd") == "stop") {
                            try {
                                publish("status","",1,true)
                                mqttStopping.set(true)
                                if(log){
                                    log = false
                                    LocalBroadcastManager.getInstance(this@MQTTService).unregisterReceiver(logBroadcastReceiver)
                                }
                                delay(1000)
                                mqttClient?.disconnect(null, object : IMqttActionListener {
                                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                                        Log.d(TAG, "Disconnected")
                                        mqttClient?.close()
                                    }
                                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                                        sendMessage("""{"warning":"Failed to disconnect"}""")
//                                        Log.d(TAG, "Failed to disconnect")
                                    }
                                })
                            } catch (e: MqttException) {
                                e.printStackTrace()
                            }
                            break
                        }
                    }

                    setGPS(msg)
                    publishMessage(msg)
                    changeLog(msg)

                }catch (e: JSONException){
                    sendMessage("""{"warning":"the message string is not json"}""")
                }
            }
            stopLocationUpdates()
            stopSelf(startId);
        }
        return START_STICKY
    }

    private fun sendToSTM32(payload: String) {
        val intent = Intent(STM32Service.toInName)
        intent.putExtra("payload", payload)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendToMQTT(payload: String) {
        GlobalScope.launch{mChannelRx.send(payload)}
    }

    private fun sendToEGTS(payload: String) {
        val intent = Intent(EGTSService.toInName)
        intent.putExtra("payload", payload)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendToInformer(payload: String) {
        val intent = Intent(InformerService.toInName)
        intent.putExtra("payload", payload)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private val logBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val str = intent?.getStringExtra("payload")
            if (str != null) {
//                Log.d(TAG,"${intent?.action}:$str")
                when (intent?.action) {
                    EGTSService.toOutName -> {
                        publish("EGTS",str,0)
                    }
                    STM32Service.toOutName -> {
                        publish("STM32",str,0)
                    }
                    InformerService.toOutName -> {
                        publish("Informer",str,0)
                    }
                }
            }
        }
    }

    private fun changeLog(msg: JSONObject) {
        if(msg.has("log")) {
            if(msg.getString("log") == "on"){
                if(!log){
                    val filter = IntentFilter()
                    filter.addAction(EGTSService.toOutName)
                    filter.addAction(STM32Service.toOutName)
                    filter.addAction(InformerService.toOutName)
                    LocalBroadcastManager.getInstance(this).registerReceiver(logBroadcastReceiver, filter)
                    log = true
                }
            } else {
                if(log){
                    log = false
                    LocalBroadcastManager.getInstance(this).unregisterReceiver(logBroadcastReceiver)
                }
            }
        }
    }

    private fun publishMessage(msg: JSONObject) {
        if(msg.has("publish")) {
            val dt = msg.getJSONObject("publish")
            if(dt != null) {
                val topic = if(dt.has("topic")) dt.getString("topic") else "out"
                val qos = if(dt.has("qos")) dt.getInt("qos") else 1
                val retain = if(dt.has("retained")) dt.getBoolean("retained") else false
                if(dt.has("data")) {
                    val data =  dt.getJSONObject("data")
                    if(data != null)publish(topic,data.toString(),qos,retain)
                } else {
                    publish(topic,"",qos,retain)
                }
            }
        }
    }

    private fun connect() {
        try {
            mqttClient?.connect(mqttOptions, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    if(!mqttStopping.get()) {
                        sendMessage("""{"state":"run"}""")
                        subscribe("cmd/#")
                        publish("status","""{"client":"online"}""",1,true)
                    }
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    sendMessage("""{"error":"Connection failure"}""")
                    runBlocking {mChannelRx.send("""{"cmd":"stop"}""") }
                }
            })
        } catch (e: MqttException) {
            Log.d(TAG, e.toString())
        }
    }

    private fun publish(topic: String, msg: String, qos: Int = 1, retained: Boolean = false) {
        try {
            if((mqttClient == null) || (!mqttClient!!.isConnected) || mqttStopping.get()){
//                sendMessage("{'warning':'Failed to publish'")
                return
            }
            val message = MqttMessage()
            message.payload = msg.toByteArray()
            message.qos = qos
            message.isRetained = retained
            mqttClient?.publish("$rootTopic/$topic", message, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    //Log.d(TAG, "$msg published to $topic")
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    sendMessage("""{"warning":"Failed to publish"}""")
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    private fun subscribe(topic: String, qos: Int = 1) {
        try {
            mqttClient?.subscribe("$rootTopic/$topic", qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Subscribed to $topic")
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    sendMessage("""{"error":"Failed to subscribe $topic"}""")
                    Log.e(TAG, "Failed to subscribe $topic")
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    protected override fun sendMessage(str: String) {
        super.sendMessage(str)
        if(log){
            publish("MQTT",str,0)
        }
    }

    override fun getOuputName(): String = toOutName
    override fun getInputName(): String = toInName

    companion object {
        fun formatMQTTMsg(topic: String, msg: JSONObject, qos: Int = 1, retained: Boolean = false): JSONObject{
            var res = JSONObject("""{"publish":{"topic":"$topic","qos":$qos,"retained":$retained,"data":${msg.toString()}}}""")
            return res
        }

        fun formatLog(str: String): JSONObject{
            val current = LocalDateTime.now()
            var res = JSONObject("""{"publish":{"topic":"log","qos":0,"data":{"time":"$current"}}}""")
            val data = res.getJSONObject("publish").getJSONObject("data")
            data.put("msg",str)
            return res
        }

        private val TAG = MQTTService::class.java.simpleName
        val toOutName = "fromMQTT"
        val toInName = "toMQTT"
    }
}