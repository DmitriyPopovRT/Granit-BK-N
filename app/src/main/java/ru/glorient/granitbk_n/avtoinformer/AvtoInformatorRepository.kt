package ru.glorient.granitbk_n.avtoinformer

import android.util.Log
import org.json.JSONObject
import ru.glorient.granitbk_n.accesory.UpdateListListener
import ru.glorient.informer.RouteItem
import ru.glorient.services.IServiceListiner
import ru.glorient.services.ServiceManager
import kotlin.random.Random

class AvtoInformatorRepository : IServiceListiner {

    var isConnected = false
    override var mServiceManager: ServiceManager? = null
    var mRoute = mutableListOf<RouteItem>()
    var mStationID = 0
    private val listStops = mutableListOf<Stop>()
    var indexStop = 0

    // Кастуем Main Activity к интерфейсу
    private val updateListListener: UpdateListListener?
        get() = mServiceManager?.context?.let { it as? UpdateListListener }

    // Оповещаем об ищменении списка
    fun updateList(
        onStopsFetched: (ind: Int, stops: List<Stop>) -> Unit
    ) {
        onStopsFetched(indexStop, listStops)
    }

    // Здесь ловим изменение состояния движения
    override fun onRecieve(tp: ServiceManager.ServiceType, data: JSONObject) {
        if (tp == ServiceManager.ServiceType.Informer) {
//            Log.d(AvtoInformatorFragment.TAG, "AvtoInformatorRepository onReceive")
            if (data.has("route")) {
                mRoute.clear()
                listStops.clear()
                val dt = data.getJSONArray("route")
                for (i in 0..dt.length() - 1) {
                    val item = RouteItem(dt.getJSONObject(i))
                    if (item.mID == mStationID) {
                        item.mNextID = 1
                    }
                    mRoute.add(item)
                    val defaultStop = Stop.DefaultStop(
                        Random.nextLong(),
                        item.mName,
                        ""
                    )
                    listStops.add(defaultStop)
                }
                Log.d(AvtoInformatorFragment.TAG, "AvtoInformatorRepository Получили маршрут")
            }

//            if (data.has("name")) {
//                mName = data.getString("name")
//
//            }
//            if (data.has("forward"))
//                isForward = data.getBoolean("forward")
//            if (data.has("circle"))
//                isCircle = data.getBoolean("circle")
//
//            if(data.has("scripts")){
//                mScripts.clear()
//                val dt = data.getJSONArray("scripts")
//                for(i in 0..dt.length()-1){
//                    mScripts.add(ManualScript(dt.getJSONObject(i)))
//                }
//            }
//
            if (data.has("station")) {
//                Log.d(AvtoInformatorFragment.TAG, "mRoute $mRoute")
                Log.d(AvtoInformatorFragment.TAG, "station $data")
                mStationID = data.getInt("station")

                var r1 = mRoute.firstOrNull { (it.mID != mStationID) && (it.mNextID == 1) }
                if (r1 != null) {
                    val itm = r1.copy()
                    itm.mNextID = 0
                    val index = mRoute.indexOf(r1)
                    mRoute.remove(r1)
                    mRoute.add(index, itm)

                    listStops.removeAt(index)
                    val defaultStop = Stop.DefaultStop(
                        Random.nextLong(),
                        r1.mName,
                        ""
                    )
                    listStops.add(index, defaultStop)
                }

                r1 = mRoute.firstOrNull { (it.mID == mStationID) && (it.mNextID == 0) }
                if (r1 != null) {
                    val itm = r1.copy()
                    itm.mNextID = 1
                    val index = mRoute.indexOf(r1)
                    mRoute.remove(r1)
                    mRoute.add(index, itm)
                    indexStop = index

                    listStops.removeAt(index)
                    val nextStop = Stop.NextStop(
                        Random.nextLong(),
                        r1.mName,
                        ""
                    )
                    listStops.add(index, nextStop)
                }

                updateListListener?.updateList()

                updateList() { _, _ ->
                    indexStop
                    listStops
                }
                Log.d(AvtoInformatorFragment.TAG, "mRoute $mRoute")
            }
//
//            if(data.has("display")) {
//                val display = data.getJSONObject("display")
//                var str = ""
//                if (display.has("id")) {
//                    val ar = display.getJSONArray("id")
//                    if(ar.length() > 0){
//                        str = "["
//                        for(i in 0 until ar.length()-1){
//                            str+=ar.getInt(i).toString()+","
//                        }
//                        str+=ar.getInt(ar.length()-1).toString()+"]:"
//                    }
//                }
//                if (display.has("text")) str += display.getString("text")
//                mText = str
//            }

        }
    }

    override fun onStateChange(tp: ServiceManager.ServiceType, state: ServiceManager.ServiceState) {
        if (tp == ServiceManager.ServiceType.Informer) {
            isConnected = (state == ServiceManager.ServiceState.RUN)
        }
    }
}