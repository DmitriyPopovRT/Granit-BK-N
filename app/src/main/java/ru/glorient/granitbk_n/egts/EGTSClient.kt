package ru.glorient.egts

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException

/**
 * Состояние клиента
 */
enum class ClientState{
    /**
     * Режим процесса подключения
     */
    CONNECTING,
    /**
     * Режим работы
     */
    RUNING,
    /**
     * Режим процесса остановки
     */
    STOPING,
    /**
     * Отключено
     */
    STOPED,
    /**
     * Неопределено
     */
    UNKNOWN
}

/**
 * Тип сообщения
 */
enum class MessageType{
    /**
     * Команда на включение
     */
    START,
    /**
     * Команда на остановку
     */
    STOP,
    /**
     * Команда на отсылку данных
     */
    SEND,
    /**
     * Команда на обраьотку принятых данных
     */
    RECIEVE,
    /**
     * Сообщение о смене состояния клиента
     */
    STATE
}

/**
 * Класс сообщения
 *
 * @property  type Тип сообщения
 * @property  state Состояние клиента
 * @property  record Данные
 */
data class EGTSMessage(val type: MessageType, val state: ClientState? = null, val record: EGTSTransport? = null)

/**
 * Класс клиента
 *
 * Постояно работает только основная корутина [mMainJob].
 * Для устанвки соединения запускается корутина установки соединения [mStartJob] и после установки соединения запускает [mReadJob] и прекращает работу.
 * При аварийном обрыве связи [mReadJob] прикращает работу по исключению и снова щапускается [mStartJob].
 * При выключении останавливаются [mReadJob] или [mStartJob] по исключению.
 *
 * @property  mServerIP Адрес сервера
 * @property  mServerPort Порт серевера
 * @property  mTimeOut Таймаут соединения в исек
 * @property  mChannelOut Канал выходных данных
 * @property  onError Событие на ошибку, где @param[msg] ее описание.
 * @constructor Запускает основную корутину [mMainJob] с чтением приемного канала комманд
 */
class EGTSClient (
        private val mServerIP: String,
        private val mServerPort: Int,
        private val mTimeOut: Int,
        private var mChannelOut: Channel<Any>?,
        private val onError: (msg: String) -> Unit = {}
){
    /**
     * Входной канал для основной корутины
     */
    private val mChannelIn = Channel<EGTSMessage>(100)
    /**
     * Текущее состояние клиента
     */
    private var mState: ClientState = ClientState.STOPED
    /**
     * Socket для установки TCP соединения
     */
    private var mConnection: Socket = Socket()
    /**
     * Корутина установки соединения
     */
    private var mStartJob: Job? = null
    /**
     * Корутина чтения данных из сокета
     */
    private var mReadJob: Job? = null
    /**
     * Mutex для защиты ресурсов
     */
    private val mMutex = Mutex()

    /**
     * Основная корутина
     */
    private val mMainJob: Job = GlobalScope.launch{
        while (isActive) {
            val msg = mChannelIn.receive()
            when (msg.type) {
                MessageType.START -> start()
                MessageType.STOP -> stop()
                MessageType.SEND -> send(msg.record)
                MessageType.RECIEVE -> recieve(msg.record)
            }
        }
    }

    /**
     * Изменение состояния
     *
     * @param st Состояние
     */
    private suspend fun changeState(st: ClientState) {
        if(mState != st){
            mState = st
            mChannelOut?.send(EGTSMessage(MessageType.STATE, mState))
        }
    }

    /**
     * Запуск клиента
     */
    private suspend fun start(){
        mMutex.lock()
        if (mState == ClientState.STOPED) {
            changeState(ClientState.CONNECTING)
            mMutex.unlock()
        }else{
            mMutex.unlock()
            return
        }

        println("EGTS connect to $mServerIP:$mServerPort (timeout=${mTimeOut}ms)")//@@@@

        mStartJob=GlobalScope.launch {
            while (isActive) {
                try {
                    mMutex.lock()
                    if (mState == ClientState.CONNECTING) {
                        mMutex.unlock()
                        val sockAdr =  InetSocketAddress(mServerIP, mServerPort)
                        mConnection.connect(sockAdr,mTimeOut)
                        mMutex.lock()
                        if (mState == ClientState.CONNECTING){
                            mReadJob = GlobalScope.launch { readTask() }
                            changeState(ClientState.RUNING)
                        }else{
                            changeState(ClientState.STOPED)
                        }
                    }else{
                        changeState(ClientState.STOPED)
                    }
                    mMutex.unlock()
                    break
                } catch (e: Exception) {
                    onError("$e")
                    mConnection = Socket()
                    delay(3000)
                    continue
                }
            }
        }
    }

    /**
     * Остановка клиента
     */
    private suspend fun stop(){
        mMutex.lock()
        if ((mState != ClientState.STOPED) && (mState != ClientState.STOPING)) {
            changeState(ClientState.STOPING)
        }else{
            mMutex.unlock()
            return
        }
        mMutex.unlock()

        mStartJob?.join()

        mMutex.lock()
        if (mState != ClientState.STOPED) {
            mConnection.close()
            mReadJob?.join()
            changeState(ClientState.STOPED)
        }
        mMutex.unlock()
    }

    /**
     * Посылка пакета серверу через входной канал
     *
     * @param msg Пакет транспорного уровня
     */
    suspend fun sendEGTS(msg: EGTSTransport){
        val rec = EGTSMessage(MessageType.SEND, record = msg)
        mChannelIn.send(rec)
    }

    /**
     * Посылка пакета в сокет
     *
     * @param msg Пакет транспорного уровня
     */
    private fun send(msg: EGTSTransport?){
        mConnection.outputStream?.write(msg?.getData()?.toByteArray())
    }

    /**
     * Посылка пакета от сервера в выходной канал
     *
     * @param msg Пакет транспорного уровня
     */
    private suspend fun recieve(msg: EGTSTransport?){
        mChannelOut?.send(EGTSMessage(MessageType.RECIEVE, record = msg))
    }

    /**
     * Команда на установку соединения
     */
    suspend fun connect() {
        mChannelIn.send(EGTSMessage(MessageType.START))
    }

    /**
     * Команда на остановку работы
     */
    suspend fun close(){
        mChannelIn.send(EGTSMessage(MessageType.STOP))
    }

    /**
     * Функция корутины чтения из сокета
     */
    private suspend fun readTask() {
        val din = DataInputStream(mConnection.getInputStream())
        val data = ByteArray(1024)
        var packet = EGTSTransport()
        try {
            while(true)
            {
                val size=din.read(data)
                if(size < 0){
                    mMutex.lock()
                    changeState(ClientState.STOPED)
                    mMutex.unlock()
                    onError("Server closed connection")
                    break
                }
                var ind = 0
                try {
                    while (ind < size) {
                        if (!packet.addData(data.copyOfRange(0, size).toUByteArray())) break
                        ind += packet.size
                        recieve(packet)
                        packet = EGTSTransport()
                    }
                }catch (e: EGTSException){
                    onError(e.toString())
                    packet = EGTSTransport()
                }
            }
        }catch (e: SocketException){}
    }
}