package ru.glorient.egts

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Сообщение для очереди задач.
 *
 * @property mJob Корутина для получения сообщения об успешной доствке пакета
 * @property mRecords список записей для отпраки.
 */
data class EGTSJobMessageOut(val mJob: EGTSCommandJob?, val mRecords: List<EGTSPacket>)

/**
 * Родительский класс алгоритма команды, состоящий из нескольких отправок и проверок результата.
 *
 * Отправляется [rec], ожидается ответ транспорного уровня о доставки в течении 3 сек,
 * затем ожидается сообщение о подверждении зариси в течении 1 сек.
 * Задача осуществляется в отдельной непрерываемой корутине, результат в [onEnd].
 * Можно запускать несколько параллельно.
 *
 * @property  mParent Транспорный уровень
 * @property  onEnd Событие на окончание, где @param[res] флаг успешности выполнения, @param[msg] подробное сообщение о выполнении, @param[msg] подробное сообщение о выполнении, @param[data] результат при  необходимости.
 * @property  mLazy Флаг отложенного запуска
 * @constructor Инициализирует переменные, за запуск [mCommandJob] ответсвенен потомок.
 */
abstract class EGTSCommandJob(
        val mParent: EGTSTransportLevel,
        val onEnd: (res: Boolean, msg: String, data: Any?) -> Unit,
        val mLazy: Boolean = false
){
    /**
     * Входной канал для корутины
     */
    val mChannel = Channel<EGTSPacket>(100)
    /**
     * Флаг ожидания ответа доставки транспортного уровня
     */
    val mWaitFlag: AtomicBoolean = AtomicBoolean(false)
    /**
     * Корутина задачи
     */
    abstract val mCommandJob: Job
    /**
     * Результат выполнения
     */
    var mResult: Boolean = false
        protected set

    /**
     * Отправка пакета пакета транспортного уровня с ожиданием подтверждения доставки.
     *
     * @param[timeout] время таймаута в мсек
     * @param[records] список записей
     * @return true в случае успеха
     */
    protected suspend fun send(timeout: Long, records: List<EGTSPacket>): Boolean{
        mWaitFlag.set(true)
        mParent.sendCommand(EGTSJobMessageOut(this@EGTSCommandJob, records))
        val res = withTimeoutOrNull(timeout){
            while (mWaitFlag.get()){
                yield()
            }
        }
        return res != null
    }

    /**
     * Отправка пакета пакета транспортного уровня без ожидания подтверждения доставки.
     *
     * @param[records] список записей
     */
    protected suspend fun send(records: List<EGTSPacket>) {
        mParent.sendCommand(EGTSJobMessageOut(null, records))
    }

    /**
     * Отложенный запуск.
     */
    fun start(){
        if(mLazy)mCommandJob.start()
    }
}

/**
 * Реализация траспортного уровня EGTS.
 *
 */
class EGTSTransportLevel(
        serverIP: String,
        serverPort: Int,
        timeout: Int,
        val mID: UInt,
        val onError: (msg: String) -> Unit = {},
        val onConnect: (on: Boolean) -> Unit = {},
) {
    private var pid: UShort = 1u
    private val chn = Channel<Any>(100)
    private val client = EGTSClient(serverIP, serverPort, timeout, chn, onError)
    private val stopFlag: AtomicBoolean = AtomicBoolean(false)
    private val runFlag: AtomicBoolean = AtomicBoolean(false)
    private var authStep = 0
    private var authRN: UShort = 0u
    private var timeoutJob: Job? = null

    private var rn: UShort = 1u
    private val mutex2 = Mutex()
    suspend fun getRN(): UShort{
        mutex2.lock()
        val res = rn
        rn++
        mutex2.unlock()
        return res
    }

    private val mutex = Mutex()
    private val subscribers = mutableListOf<EGTSCommandJob>()
    suspend fun addSubsriber(sub: EGTSCommandJob){
        mutex.lock()
        subscribers.add(sub)
        mutex.unlock()
    }
    suspend fun removeSubsriber(sub: EGTSCommandJob){
        mutex.lock()
        subscribers.remove(sub)
        mutex.unlock()
    }
    suspend fun sendCommand(cmd: EGTSJobMessageOut){
        chn.send(cmd)
    }
    private val waitMap = mutableMapOf<UShort, EGTSCommandJob>()

    val isRun: Boolean
        get() = runFlag.get()

    init{
        GlobalScope.launch {
            while (true) {
                var ms = chn.receive()
//                println(ms)//@@@@
                if(ms is EGTSJobMessageOut) analize(ms)
                if(ms !is EGTSMessage) continue
                var msg = ms
                if (msg.state == ClientState.STOPED) {
                    timeoutJob?.cancel()
                    if(runFlag.get()){
                        runFlag.set(false)
                        onConnect(false)
                    }
                    if(stopFlag.get()) break
                    launch{client.connect()}
                } else if (msg.state == ClientState.STOPING) {
                    timeoutJob?.cancel()
                    if(runFlag.get()){
                        runFlag.set(false)
                        onConnect(false)
                    }
                } else if (msg.state == ClientState.RUNING) {
                    authStep = 1
                    authRN = getRN()
                    client.sendEGTS(EGTSTransport(0u, 0u, 0u, pid, EGTSTransport.PACKET_TYPE.EGTS_PT_APPDATA)
                            .also { it.SFRD.add(EGTSRecord(authRN, 0x81u, mID, null, null, EGTSRecord.SERVICE.EGTS_AUTH_SERVICE, EGTSRecord.SERVICE.EGTS_AUTH_SERVICE)
                                    .also { it.RD.add(EGTSSubRecordTermIdentity(mID)) })
                            })
                    pid++
                    timeoutJob = launch {
                        delay(5_000)
                        onError("auth timeout")
                        client.close()
                    }
                } else if (msg.type == MessageType.RECIEVE) {
                    val pack = msg.record!!
                    if (pack.PT == EGTSTransport.PACKET_TYPE.EGTS_PT_APPDATA) {
                        client.sendEGTS(EGTSTransport(0u, 0u, 0u, pid, EGTSTransport.PACKET_TYPE.EGTS_PT_RESPONSE)
                                .also { it.SFRD.add(EGTSResponse(pack.PID)) })
                        pid++
                        if(!runFlag.get())pack.SFRD.forEach({auth(it)})
                        else pack.SFRD.forEach({recieve(it)})
                    } else if(pack.PT == EGTSTransport.PACKET_TYPE.EGTS_PT_RESPONSE) {
                        if(!runFlag.get()){
                            if(authStep == 3){
                                pack.SFRD.forEach(){
                                    if(it is EGTSResponse){
                                        if(it.RPID == authRN){
                                            authStep = 0
                                            runFlag.set(true)
                                            timeoutJob?.cancel()
//                                            println("auth")//@@@@
                                            onConnect(true)
                                        }
                                    }
                                }
                            }
                        }else{
                            pack.SFRD.forEach(){
                                if(it is EGTSResponse){
                                    val id = it.RPID
                                    if(waitMap.containsKey(id)){
                                        waitMap[id]!!.mWaitFlag.set(false)
                                        waitMap.remove(id)
                                    }
                                }
                            }
                        }
                    }
                }
             }
        }

        GlobalScope.launch{client.connect()}
    }

    private suspend fun analize(msg: EGTSJobMessageOut) {
        if(isRun){
            val packet = EGTSTransport(0u, 0u, 0u, pid, EGTSTransport.PACKET_TYPE.EGTS_PT_APPDATA)
            msg.mRecords.forEach(){
                packet.SFRD.add(it)
                if(msg.mJob != null)waitMap[pid]=msg.mJob
            }
            client.sendEGTS(packet)
            pid++
        }
    }

    private suspend fun recieve(rec: EGTSPacket) {
        mutex.lock()
        subscribers.forEach(){
            it.mChannel.send(rec)
        }
        mutex.unlock()
    }

    private suspend fun auth(rec: EGTSPacket) {
        if(rec is EGTSRecord){
            val rc = rec
            if((rc.SST == EGTSRecord.SERVICE.EGTS_AUTH_SERVICE) && (rc.RST == EGTSRecord.SERVICE.EGTS_AUTH_SERVICE))
            {
                rc.RD.forEach(){
                    when (authStep){
                        1 -> {
                            if(it is EGTSSubRecordResultCode){
                                if(it.RCD.toInt() == 0) authStep++
                            }
                        }
                        2 -> {
                            if(it is EGTSSubRecordResponse){
                                if((it.CRN == authRN) && (it.RST.toInt() == 0)){
                                    authStep++
                                    authRN = pid
                                    client.sendEGTS(EGTSTransport(0u, 0u, 0u, pid, EGTSTransport.PACKET_TYPE.EGTS_PT_APPDATA)
                                            .also { it.SFRD.add(EGTSRecord(getRN(), 0x81u, mID, null, null, EGTSRecord.SERVICE.EGTS_AUTH_SERVICE, EGTSRecord.SERVICE.EGTS_AUTH_SERVICE)
                                                    .also { it.RD.add(EGTSSubRecordResponse(rec.RN)) })
                                            })
                                    pid++
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun close(){
        stopFlag.set(true)
        client.close()
    }
}