package ru.glorient.services

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject
import ru.glorient.informer.AutoDirMode
import ru.glorient.informer.ManualScript
import ru.glorient.informer.RouteItem

class InformerDecorator : IServiceListiner {
    override var mServiceManager: ServiceManager? = null
    var isConnected = mutableStateOf(false)

    var mName = mutableStateOf("Маршрут")
    var isForward = mutableStateOf(true)
    var isGPS = mutableStateOf(true)
    var isAutoDir = mutableStateOf(true)
    var isCircle = mutableStateOf(false)
    var mRoute = mutableStateListOf<RouteItem>()
    var mScripts = mutableStateListOf<ManualScript>()

    var mStationID = mutableStateOf(-1)
    var mText = mutableStateOf("")

    override fun onRecieve(tp: ServiceManager.ServiceType, data: JSONObject){
        if(tp == ServiceManager.ServiceType.Informer){
//            Log.d("InformerDecorator",data.toString())
            if(data.has("route")){
                mRoute.clear()
                mStationID.value = -1
                val dt = data.getJSONArray("route")
                for(i in 0 until dt.length()){
                    val item = RouteItem(dt.getJSONObject(i))
                    mRoute.add(item)
                }
            }
            if(data.has("name")) mName.value = data.getString("name")
            if(data.has("forward")) isForward.value = data.getBoolean("forward")
            if(data.has("circle")) isCircle.value = data.getBoolean("circle")

            if(data.has("scripts")){
                mScripts.clear()
                val dt = data.getJSONArray("scripts")
                for(i in 0 until dt.length()){
                    mScripts.add(ManualScript(dt.getJSONObject(i)))
                }
            }

            if(data.has("station")){
                mStationID.value = data.getInt("station")

                var r1 = mRoute.firstOrNull { (it.mID != mStationID.value) && (it.mNextID == 1) }
                if (r1 != null){
                    val itm = r1.copy()
                    itm.mNextID = 0
                    val index = mRoute.indexOf(r1)
                    mRoute.remove(r1)
                    mRoute.add(index, itm)
                }

                r1 = mRoute.firstOrNull { (it.mID == mStationID.value) && (it.mNextID == 0) }
                if (r1 != null){
                    val itm = r1.copy()
                    itm.mNextID = 1
                    val index = mRoute.indexOf(r1)
                    mRoute.remove(r1)
                    mRoute.add(index, itm)
                }
            }

            if(data.has("display")) {
                val display = data.getJSONObject("display")
                var str = ""
                if (display.has("id")) {
                    val ar = display.getJSONArray("id")
                    if(ar.length() > 0){
                        str = "["
                        for(i in 0 until ar.length()-1){
                            str+=ar.getInt(i).toString()+","
                        }
                        str+=ar.getInt(ar.length()-1).toString()+"]:"
                    }
                }
                if (display.has("text")) str += display.getString("text")
                mText.value = str
            }

            if (data.has("gpsenable")) {
                isGPS.value = data.getBoolean("gpsenable")
            }

            if (data.has("autodir")) {
                isAutoDir.value = data.getString("autodir") != "none"
            }
        }
    }

    override fun onStateChange(tp: ServiceManager.ServiceType, state: ServiceManager.ServiceState){
        if(tp == ServiceManager.ServiceType.Informer){
            isConnected.value = (state == ServiceManager.ServiceState.RUN)
        }
    }

    fun play(id: Int){
        mServiceManager?.sendMessage(ServiceManager.ServiceType.Informer,
                JSONObject("""{"play":[${id}]}"""))

    }

    fun toggleDir(){
        mServiceManager?.sendMessage(ServiceManager.ServiceType.Informer,
                JSONObject("""{"dirrection":"toggle"}"""))
        
    }

    fun toggleGPS(){
        mServiceManager?.sendMessage(ServiceManager.ServiceType.Informer,
                JSONObject("""{"gpstoggle":"toggle"}"""))

    }

    fun setAutoDir(dir: AutoDirMode) {
        val str = when(dir){
            AutoDirMode.HARD -> "hard"
            AutoDirMode.SOFT -> "soft"
            else -> "none"
        }
        mServiceManager?.sendMessage(ServiceManager.ServiceType.Informer,
                JSONObject("""{"autodir":"$str"}"""))
    }
}