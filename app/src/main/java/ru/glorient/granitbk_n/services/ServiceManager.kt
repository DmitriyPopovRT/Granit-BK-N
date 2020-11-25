package ru.glorient.services

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.json.JSONException
import org.json.JSONObject
import java.util.*

interface IServiceListiner{
    var mServiceManager: ServiceManager?

    fun onRecieve(tp: ServiceManager.ServiceType, data: JSONObject)
    fun onStateChange(tp: ServiceManager.ServiceType, state: ServiceManager.ServiceState)
}

class ServiceManager(
        val context: AppCompatActivity,
        val onStateChange: (serviceType: ServiceType, serviceState: ServiceState) -> Unit = { _: ServiceType, _: ServiceState -> },
        val onRecieve: (serviceType: ServiceType, s: JSONObject) -> Unit = { _: ServiceType, _: JSONObject -> },
        val onSTM32Search: (enable: Boolean) -> Unit = {_: Boolean ->}
) {
    enum class ServiceType{
        EGTS,
        MQTT,
        STM32,
        Informer,
        Recorder
    }
    enum class ServiceState {
        STARTING,
        CONNECTING,
        RUN,
        STOPPING,
        STOPED
    }

    private var mListiners = mutableListOf<IServiceListiner>()
    fun subscribe(ls: IServiceListiner){
        if(ls.mServiceManager == null) {
            ls.mServiceManager = this
            mListiners.add(ls)
        }
    }
    fun unsubscribe(ls: IServiceListiner){
        if(mListiners.remove(ls)) {
            ls.mServiceManager = null
        }
    }

    private fun stateChange(tp: ServiceType, state: ServiceState){
        onStateChange(tp, state)
        mListiners.forEach {
            it.onStateChange(tp, state)
        }
    }
    private fun recieve(tp: ServiceType, data: JSONObject){
        onRecieve(tp, data)
        mListiners.forEach {
            it.onRecieve(tp, data)
        }
    }
    fun subsribeListiner(lt: IServiceListiner){
        lt.mServiceManager = this
        mListiners.add(lt)
    }

    private val usbManager: UsbManager =  context.getSystemService(AppCompatActivity.USB_SERVICE) as UsbManager
    var mSTM32Enable = false
        private set

    private val usbBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action!! == "permission") {
                val granted: Boolean = intent.extras!!.getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED)
                if (granted) {
                    mSTM32Enable = true
                    onSTM32Search(mSTM32Enable)
                } else {
                    Log.w(TAG, "USB permission not granted")
                }
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                if(!mSTM32Enable)checkUSB()
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                if(mSTM32Enable)checkUSB()
            }
        }
    }

    // Register the permissions callback, which handles the user's response to the
    // system permissions dialog. Save the return value, an instance of
    // ActivityResultLauncher. You can use either a val, as shown in this snippet,
    // or a lateinit var in your onAttach() or onCreate() method.
    private val requestPermissionLauncher =
            context.registerForActivityResult(ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    // Permission is granted. Continue the action or workflow in your
                    // app.
                } else {
                    // Explain to the user that the feature is unavailable because the
                    // features requires a permission that the user has denied. At the
                    // same time, respect the user's decision. Don't link to system
                    // settings in an effort to convince the user to change their
                    // decision.
                }
            }

    init{
        val filter = IntentFilter()
        filter.addAction("permission")
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        context.registerReceiver(usbBroadcastReceiver, filter)
        checkUSB()

        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                // You can use the API that requires the permission.
            }
            shouldShowRequestPermissionRationale(context, Manifest.permission.ACCESS_FINE_LOCATION) -> {
            }
            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                // You can use the API that requires the permission.
            }
            shouldShowRequestPermissionRationale(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) -> {
            }
            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED -> {
                // You can use the API that requires the permission.
            }
            shouldShowRequestPermissionRationale(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
            }
            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private data class Service(var state: ServiceState, val messageReceiver: BroadcastReceiver, var connection: String = "")

    private val stateMap = mutableMapOf<ServiceType,Service>(
            ServiceType.EGTS to Service(ServiceState.STOPED, object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent) {
                    val str = intent.getStringExtra("payload")
                    if (str != null) {
                        try {
                            val msg = JSONObject(str)
                            if (msg.has("state")) {
                                when (msg.getString("state")) {
                                    "connecting" -> setState(ServiceType.EGTS, ServiceState.CONNECTING)
                                    "run" -> setState(ServiceType.EGTS, ServiceState.RUN)
                                    else -> setState(ServiceType.EGTS, ServiceState.STOPED)
                                }
                                msg.remove("state")
                                if (msg.length() == 0) return
                            }

                            recieve(ServiceType.EGTS, msg)

                        } catch (e: JSONException) {
                            recieve(ServiceType.EGTS, JSONObject("""{"warning":"the message string is not json"}"""))
                            Log.w(TAG, e.toString())
                        }
                    }
                }
            }),
            ServiceType.MQTT to Service(ServiceState.STOPED, object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent) {
                    val str = intent.getStringExtra("payload")
                    if (str != null) {
                        try {
                            val msg = JSONObject(str)
                            if (msg.has("state")) {
                                when (msg.getString("state")) {
                                    "connecting" -> setState(ServiceType.MQTT, ServiceState.CONNECTING)
                                    "run" -> {
                                        setState(ServiceType.MQTT, ServiceState.RUN)
                                        sendState()
                                    }
                                    else -> setState(ServiceType.MQTT, ServiceState.STOPED)
                                }
                                msg.remove("state")
                                if (msg.length() == 0) return
                            }
                            if (msg.has("service")) {
                                val srv = msg.getJSONObject("service")
                                if (srv.has("EGTS")) {
                                    val srv2 = srv.getJSONObject("EGTS")
                                    if(srv2.has("run")) {
                                        if (srv2.getString("run") == "on") {
                                            val con = if(srv2.has("connection")) srv2.getJSONObject("connection").toString() else ""
                                            val dt = if(srv2.has("payload")) srv2.getJSONObject("payload").toString() else ""
                                            startService(ServiceType.EGTS, con, dt)
                                        } else {
                                            stopService(ServiceType.EGTS)
                                        }
                                    }
                                }
                                if (srv.has("Informer")) {
                                    val srv2 = srv.getJSONObject("Informer")
                                    if(srv2.has("run")) {
                                        if (srv2.getString("run") == "on") {
                                            val con = if(srv2.has("connection")) srv2.getJSONObject("connection").toString() else ""
                                            val dt = if(srv2.has("payload")) srv2.getJSONObject("payload").toString() else ""
                                            startService(ServiceType.Informer, con, dt)
                                        } else {
                                            stopService(ServiceType.Informer)
                                        }
                                    }
                                }
                                if (srv.has("STM32")) {
                                    val srv2 = srv.getJSONObject("STM32")
                                    if(srv2.has("run")) {
                                        if (srv2.getString("run") == "on") {
                                            val con = if(srv2.has("connection")) srv2.getJSONObject("connection").toString() else ""
                                            val dt = if(srv2.has("payload")) srv2.getJSONObject("payload").toString() else ""
                                            startService(ServiceType.STM32, con, dt)
                                        } else {
                                            stopService(ServiceType.STM32)
                                        }
                                    }
                                }
                                if (srv.has("MQTT")) {
                                    val srv2 = srv.getJSONObject("MQTT")
                                    if(srv2.has("run")) {
                                        if (srv2.getString("run") == "off") {
                                            stopService(ServiceType.MQTT)
                                        }
                                    }
                                }
                                msg.remove("service")
                                if (msg.length() == 0) return
                            }

                            recieve(ServiceType.MQTT, msg)

                        } catch (e: JSONException) {
                            recieve(ServiceType.MQTT, JSONObject("""{"warning":"the message string is not json"}"""))
                            Log.w(TAG, e.toString())
                        }
                    }
                }
            }),
            ServiceType.STM32 to Service(ServiceState.STOPED, object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent) {
                    val str = intent.getStringExtra("payload")
                    if (str != null) {
                        try {
                            val msg = JSONObject(str)
                            if (msg.has("state")) {
                                when (msg.getString("state")) {
                                    "connecting" -> setState(ServiceType.STM32, ServiceState.CONNECTING)
                                    "run" -> setState(ServiceType.STM32, ServiceState.RUN)
                                    else -> setState(ServiceType.STM32, ServiceState.STOPED)
                                }
                                msg.remove("state")
                                if (msg.length() == 0) return
                            }

                            recieve(ServiceType.STM32, msg)

                        } catch (e: JSONException) {
                            recieve(ServiceType.STM32, JSONObject("""{"warning":"the message string is not json"}"""))
                            Log.w(TAG, e.toString())
                        }
                    }
                }
            }),
            ServiceType.Informer to Service(ServiceState.STOPED, object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent) {
                    val str = intent.getStringExtra("payload")
                    if (str != null) {
                        try {
                            val msg = JSONObject(str)
                            if (msg.has("state")) {
                                when (msg.getString("state")) {
                                    "connecting" -> setState(ServiceType.Informer, ServiceState.CONNECTING)
                                    "run" -> setState(ServiceType.Informer, ServiceState.RUN)
                                    else -> setState(ServiceType.Informer, ServiceState.STOPED)
                                }
                                msg.remove("state")
                                if (msg.length() == 0) return
                            }

                            recieve(ServiceType.Informer, msg)

                        } catch (e: JSONException) {
                            recieve(ServiceType.Informer, JSONObject("""{"warning":"the message string is not json"}"""))
                            Log.w(TAG, e.toString())
                        }
                    }
                }
            })
    )

    fun log(s: String) {
        sendMessage(ServiceType.MQTT, MQTTService.formatLog(s))
    }

    private fun sendState() {
        stateMap.forEach{
            sendServiceState(it.key, it.value)
        }
    }

    private fun clearState() {
        stateMap.forEach{
            sendMessage(ServiceType.MQTT, JSONObject("""{"publish":{"topic":"services/${it.key}","retained":true}}"""))
        }
    }

    private fun sendServiceState(type: ServiceType, service: Service) {
        val data = JSONObject("""{"state":"${service.state}"}""")
        if ((service.state != ServiceState.STOPED) && (service.connection != ""))
            data.put("connection", JSONObject(service.connection))
        val msg = MQTTService.formatMQTTMsg("services/${type}", data,1, true)
        sendMessage(ServiceType.MQTT, msg)
    }

    fun getState(service: ServiceType) = stateMap[service]?.state
    private fun setState(service: ServiceType, state: ServiceState){
        stateMap[service]?.state = state
        if(service != ServiceType.MQTT){
            sendServiceState(service, stateMap[service]!!)
        }
        if(state == ServiceState.STARTING) {
            when(service) {
                ServiceType.EGTS -> LocalBroadcastManager.getInstance(context).registerReceiver(stateMap[service]!!.messageReceiver,
                        IntentFilter(EGTSService.toOutName))
                ServiceType.MQTT -> LocalBroadcastManager.getInstance(context).registerReceiver(stateMap[service]!!.messageReceiver,
                        IntentFilter(MQTTService.toOutName))
                ServiceType.STM32 -> LocalBroadcastManager.getInstance(context).registerReceiver(stateMap[service]!!.messageReceiver,
                        IntentFilter(STM32Service.toOutName))
                ServiceType.Informer -> LocalBroadcastManager.getInstance(context).registerReceiver(stateMap[service]!!.messageReceiver,
                        IntentFilter(InformerService.toOutName))
            }
        }else if(state == ServiceState.STOPED){
            LocalBroadcastManager.getInstance(context).unregisterReceiver(stateMap[service]!!.messageReceiver)
       }
       stateChange(service, state)
    }

    fun startService(service: ServiceType, connection: String = "", payload: String = ""){
        stopService(service)
        while(getState(service) != ServiceState.STOPED){}
        stateMap[service]?.connection = connection
        when(service) {
            ServiceType.EGTS -> {
                setState(service, ServiceState.STARTING)
                Intent(context, EGTSService::class.java).also {
                    if (connection != "") it.putExtra("connection", connection)
                    if (payload != "") it.putExtra("payload", payload)
                    context.startService(it)
                }
            }
            ServiceType.MQTT -> {
                setState(service, ServiceState.STARTING)
                Intent(context, MQTTService::class.java).also {
                    if (connection != "") it.putExtra("connection", connection)
                    if (payload != "") it.putExtra("payload", payload)
                    context.startService(it)
                }
            }
            ServiceType.STM32 -> {
                if(mSTM32Enable) {
                    setState(service, ServiceState.STARTING)
                    Intent(context, STM32Service::class.java).also {
                        it.putExtra("connection", STM32CONNECTION)
                        context.startService(it)
                    }
                }
            }
            ServiceType.Informer -> {
                setState(service, ServiceState.STARTING)
                Intent(context, InformerService::class.java).also {
                    if (connection != "") it.putExtra("connection", connection)
                    if (payload != "") it.putExtra("payload", payload)
                    context.startService(it)
                }
            }
        }
    }

    fun sendMessage(service: ServiceType, msg: JSONObject): Boolean {
        if(getState(service) == ServiceState.STOPED) return false
        var intent: Intent? = null
        when(service){
            ServiceType.EGTS -> intent = Intent(EGTSService.toInName)
            ServiceType.MQTT -> intent = Intent(MQTTService.toInName)
            ServiceType.STM32 -> intent = Intent(STM32Service.toInName)
            ServiceType.Informer -> intent = Intent(InformerService.toInName)
            else -> return false
        }
        intent?.putExtra("payload", msg.toString())
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        return true
    }

    fun stopService(service: ServiceType){
        while(getState(service) == ServiceState.STARTING){}
        if((getState(service) != ServiceState.STOPPING) && (getState(service) != ServiceState.STOPED)){
            when(service) {
                ServiceType.EGTS -> {
                    setState(service, ServiceState.STOPPING)
                    Intent(context, EGTSService::class.java).also({ context.stopService(it) })
                }
                ServiceType.MQTT -> {
                    clearState()
                    setState(service, ServiceState.STOPPING)
                    Intent(context, MQTTService::class.java).also({ context.stopService(it) })
                }
                ServiceType.STM32 -> {
                    setState(service, ServiceState.STOPPING)
                    Intent(context, STM32Service::class.java).also({ context.stopService(it) })
                }
                ServiceType.Informer -> {
                    setState(service, ServiceState.STOPPING)
                    Intent(context, InformerService::class.java).also({ context.stopService(it) })
                }
            }
        }
    }

    private fun checkUSB() {
        val usbDevices: HashMap<String, UsbDevice>? = usbManager.deviceList
        if (!usbDevices?.isEmpty()!!) {
            var keep = true
            usbDevices.forEach{ entry ->
                val deviceVendorId: Int? = entry.value?.vendorId
                val deviceProductId: Int? = entry.value?.productId
                if ((deviceVendorId == STM32_VENDOR_ID) && (deviceProductId == STM32_PRODUCT_ID)) {
                    val intent: PendingIntent = PendingIntent.getBroadcast(context, 0, Intent("permission"), 0)
                    usbManager.requestPermission(entry.value, intent)
                    keep = false
                    Log.i(TAG, "USB: $deviceVendorId:$deviceProductId STM32")
                } else {
                    Log.i(TAG, "USB: $deviceVendorId:$deviceProductId")
                }
            }
            if (keep) {
                if(mSTM32Enable){
                    mSTM32Enable = false
                    onSTM32Search(mSTM32Enable)
                }
            }
        } else {
            Log.i(TAG, "no usb device connected")
            if(mSTM32Enable){
                mSTM32Enable = false
                onSTM32Search(mSTM32Enable)
            }
        }
    }

    companion object{
        private const val STM32_VENDOR_ID = 1155
        private const val STM32_PRODUCT_ID = 22336
        private const val STM32CONNECTION = """{"vendorid":$STM32_VENDOR_ID,"productid":$STM32_PRODUCT_ID}"""
        private val TAG = ServiceManager::class.java.simpleName
    }

}