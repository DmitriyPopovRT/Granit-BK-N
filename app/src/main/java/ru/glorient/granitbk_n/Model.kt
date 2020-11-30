package ru.glorient.granitbk_n

import android.os.Environment
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import ru.glorient.granitbk_n.avtoinformer.AvtoInformatorFragment
import ru.glorient.granitbk_n.avtoinformer.AvtoInformatorRepository
import ru.glorient.services.ServiceManager
import java.io.File

class Model{
    data class ServiceModel(var state: ServiceManager.ServiceState, val connection: String = "{}", val payload: String = "")

    var states = mutableStateMapOf<ServiceManager.ServiceType, ServiceModel>()
    var logs = mutableStateListOf<String>()

    var mInformer = AvtoInformatorRepository()
    private val settingsParse = MainActivity.settingsParse

    init {
        states[ServiceManager.ServiceType.EGTS] = ServiceModel(ServiceManager.ServiceState.STOPED,
//                "{'server':'10.0.2.2','port':7001,'transport_id':6080}",
            """{"server":"10.20.9.3","port":7001,"timeout":3000,"transport_id":6080}""",
            """{"GPS":{"run":"on","interval":10000,"fastestInterval":1000}}"""
        )
        states[ServiceManager.ServiceType.MQTT] = ServiceModel(ServiceManager.ServiceState.STOPED,
//            """{"server":"romasty.duckdns.org","port":8883,"transport_id":6082,"ssl":true,"user":"glorient","password":"raQNI6dpZZrd44xoXFPz","timeout":3000}""",
            """{"server":"${settingsParse.server}","port":${settingsParse.port},"transport_id":${settingsParse.transportId},"ssl":${settingsParse.ssl},"user":"${settingsParse.login}","password":"${settingsParse.password}","timeout":${settingsParse.timeout}}""",
            """{"GPS":{"run":"on","interval":10000,"fastestInterval":1000},"log":"on"}"""
        )
        val file = MainActivity.filePath
//        val file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path + File.separator.toString() + "398t.jsonc"

        // "autodir":"none|soft|hard" - алгоритм определения вектора движения отключен|без определения точности(для эмулятора)|с определением точности(по умолчанию)
        states[ServiceManager.ServiceType.Informer] = ServiceModel(ServiceManager.ServiceState.STOPED,
            """{"file":"$file"}""",
//                """{"GPS":{"run":"on","interval":10000,"fastestInterval":1500},"play":[10,11,1],"dirrection":"toggle"}"""
            """{"GPS":{"run":"on","interval":10000,"fastestInterval":1000},"autodir":"soft"}"""
//                """{"GPS":{"run":"on","interval":10000,"fastestInterval":1000},"play":[11]}"""
        )
    }

    lateinit var serviceManager: ServiceManager
}