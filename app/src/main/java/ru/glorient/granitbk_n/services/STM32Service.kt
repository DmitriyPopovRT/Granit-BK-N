package ru.glorient.services

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.*
import org.json.JSONException
import org.json.JSONObject
import ru.glorient.stm32.USBLevel0
import java.lang.Exception
import java.util.HashMap
import java.util.concurrent.Executors


class STM32Service : ParentService() {
    val mEncoder = USBLevel0()
    private lateinit var mUsbManager: UsbManager
    private var mConnection: UsbDeviceConnection? = null
    private var mPort: UsbSerialPort? = null

    class SerialListiner(val parent: STM32Service) : SerialInputOutputManager.Listener{
        override fun onNewData(data: ByteArray?) {
//            Log.d(TAG,"!!! ${data?.size}")
            if(parent.mEncoder.addData(data!!))
            {
               while(!parent.mEncoder.isEmpty)parent.sendMessage( """{"read":${parent.mEncoder.nextJSONString()}}""")
            }
        }

        override fun onRunError(e: Exception?) {
            Log.d(TAG, e.toString())
        }
    }
    private val sl = SerialListiner(this)

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        start()
        sendMessage("""{"state":"connecting"}""")
        mMainJob= GlobalScope.launch{
            val options = intent.getStringExtra("connection")
            if(options != null){
                try {
                    val cn = JSONObject(options)
                    val deviceVendorId = if(cn.has("vendorid")) cn.getInt("vendorid") else 1155
                    val deviceProductId = if(cn.has("productid")) cn.getInt("productid") else 22336
                    mUsbManager = getSystemService(AppCompatActivity.USB_SERVICE) as UsbManager
                    val usbDevices: HashMap<String, UsbDevice>? = mUsbManager.deviceList
                    var success = false
                    var device: UsbDevice? = null
                    if (usbDevices != null) {
                        for((key,value) in usbDevices){
                            if ((deviceVendorId == value.vendorId) && (deviceProductId == value.productId)) {
                                device = value
                                success = true
                                break
                            }
                        }
                    }

                    if(success) {
                        val customTable = ProbeTable()
                        customTable.addProduct(
                            deviceVendorId,
                            deviceProductId,
                            CdcAcmSerialDriver::class.java
                        )
                        val prober =  UsbSerialProber(customTable)
                        val driver = prober.probeDevice(device)

                        mPort = driver.ports.get(0)
                        mConnection = mUsbManager.openDevice(device)
                        mPort?.open(mConnection);
                        mPort?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

                        val usbIoManager = SerialInputOutputManager(mPort,sl)
                        Executors.newSingleThreadExecutor().submit(usbIoManager)

                        sendMessage("""{"state":"run"}""")
                        val str = intent.getStringExtra("payload")
                        if (str != null) mChannelRx.send(str)
                    } else {
                        sendMessage("""{"error":"usb is not found"}""")
                        mChannelRx.send("""{"cmd":"stop"}""")
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
//                Log.d(TAG, str)
                try {
                    val msg = JSONObject(str)
                    if(msg.has("cmd")) {
                        if (msg.getString("cmd") == "stop") {
                            break
                        }
                    }

                    sendRaw(msg)

                }catch (e: JSONException){
                    sendMessage("""{"warning":"the message string is not json"}""")
                }
            }
            mPort?.close()
            mConnection?.close()
            stopSelf(startId);
        }
        return START_STICKY
    }

    protected fun sendRaw(msg: JSONObject) {
        if (msg.has("send")) {
            val cmd = msg.getJSONObject("send")
            mPort?.write(mEncoder.encode(cmd),1000)
         }
    }

    override fun getOuputName(): String = toOutName
    override fun getInputName(): String = toInName

    companion object {
        private val TAG = STM32Service::class.java.simpleName
        val toOutName = "fromSTM32"
        val toInName = "toSTM32"
    }
}