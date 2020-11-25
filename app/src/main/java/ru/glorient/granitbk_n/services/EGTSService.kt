package ru.glorient.services

import ru.glorient.egts.*
import android.content.*
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import org.json.JSONException
import org.json.JSONObject

class EGTSService : GPSService() {

    private var mEgts: EGTSTransportLevel? = null
    private var mID: UInt = 0u


    override fun onCreate() {
        super.onCreate()

        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                val mCurrentLocation = locationResult.lastLocation
                if((mEgts == null) || (!mEgts!!.isRun))return

                val rec = EGTSRecord(1u, 0x81u, mID, null, null, EGTSRecord.SERVICE.EGTS_TELEDATA_SERVICE, EGTSRecord.SERVICE.EGTS_TELEDATA_SERVICE)
                rec.RD.add(EGTSSubRecordPosData(mCurrentLocation!!.time, mCurrentLocation!!.latitude, mCurrentLocation!!.longitude,
                        mCurrentLocation!!.speed, mCurrentLocation!!.bearing, mCurrentLocation!!.altitude))
                EGTSCommand(rec, mEgts!!, { b: Boolean, s: String, o: Any? -> if(!b) sendMessage( """{"warning":"GPS ${s}'}""") })
            }
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        start()
        sendMessage("""{"state":"connecting"}""")
        mMainJob= GlobalScope.launch(){
            val options = intent.getStringExtra("connection")
            if(options != null){
                try {
                    val cn = JSONObject(options)
                    mID = if(cn.has("transport_id")) cn.getInt("transport_id").toUInt() else (Math.random() *10000).toUInt()
                    mEgts = EGTSTransportLevel(
                            if(cn.has("server"))cn.getString("server") else "10.0.2.2",
                            if(cn.has("port"))cn.getInt("port") else 7001,
                            if(cn.has("timeout"))cn.getInt("timeout") else 3000,
                            mID,
                            { msg -> sendMessage("""{"error":"${msg}"}""") },
                            { on ->
                                if(on)sendMessage("""{"state":"run"}""")
                                else sendMessage("""{"state":"connecting"}""")
                            })
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
                            mEgts?.close()
                            break
                        }
                    }

                    setGPS(msg)

                }catch (e: JSONException){
                    sendMessage("""{"warning":"the message string is not json (${e})"}""")
                }
            }
            stopLocationUpdates()
            stopSelf(startId);
        }
        return START_STICKY
    }

    override fun getOuputName(): String = toOutName
    override fun getInputName(): String = toInName

    companion object {
        private val TAG = EGTSService::class.java.simpleName
        val toOutName = "fromEGTS"
        val toInName = "toEGTS"
    }

}