package ru.glorient.informer

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject
import org.quartz.CronExpression
import java.time.*
import java.util.*

/**
 * Тип маршрута
 */
enum class RouteType{
    /**
     * Линейный маршрут
     */
    BIDIR,
    /**
     * Кольцевой маршрут
     */
    CIRCLE
}

/**
 * Правило вытеснения работающего скрипта
 */
enum class TaskRule{
    /**
     * Убрать из очереди
     */
    STOP,
    /**
     * Оставить в очереди и начать с начала
     */
    BEGIN,
    /**
     * Оставить в очереди и начать с места остановки
     */
    RESUME
}

/**
 * Вектор движения
 */
enum class RouteDir{
    /**
     * Любой
     */
    ANY,
    /**
     * Вперёд
     */
    FORWARD,
    /**
     * Назад
     */
    BACKWARD
}

/**
 * Тип автоопределения вектора движения
 */
enum class AutoDirMode{
    /**
     * Нет автоопределения вектора движения
     */
    NONE,
    /**
     * Автоопределения вектора движения без учета точности курса
     */
    SOFT,
    /**
     * Автоопределения вектора движения
     */
    HARD
}

/**
 * Режим включения тригера GPS
 */
enum class GPSTriggerMode{
    /**
     * Выполнение в самой точке
     */
    NONE,
    /**
     * Выполнение при подъезде к точке
     */
    PRIOR,
    /**
     * Выполнение при отъезде от точки
     */
    POST
}

/**
 * Класс указателя на скрипт остоновки
 *
 * @property  mID id скрипта
 * @property  mName Название скрипта (остановки)
 * @property  mNextID id следующей останоки (последняя 0)
 * @property  mEnable флаг вызова скрипта в ручную
 */
data class RouteItem(var mID: Int = 0, var mName: String = "", var mNextID: Int = 0, var mEnable: Boolean = false){
    /**
     * Конструктор из JSON
     *
     * @param  json Описание данных в JSON
     * @param  nextId id следующей останоки (последняя 0)
     */
    constructor(json: JSONObject, nextId: Int = 0):this(){
        if(json.has("id")) mID = json.getInt("id")
        if(json.has("name")) mName = json.getString("name")
        if(json.has("enable")) mEnable = json.getBoolean("enable")
        mNextID = nextId
    }

    /**
     * JSON для передачи из модуля
     *
     * @return JSON для передачи из модуля
     */
    fun getJSON(): JSONObject {
        return JSONObject("""{"id":$mID,"name":"$mName","enable":$mEnable}""")
    }
}

/**
 * Класс указателя на скрипт для запуски в ручную
 *
 * @property  mID id скрипта
 * @property  mName Название скрипта (остановки)
 */
data class ManualScript(var mID: Int = 0, var mName: String = ""){
    /**
     * Конструктор из JSON
     *
     * @param  json Описание данных в JSON
     */
    constructor(json: JSONObject):this(){
        if(json.has("id")) mID = json.getInt("id")
        if(json.has("name")) mName = json.getString("name")
    }

    /**
     * JSON для передачи из модуля
     *
     * @return JSON для передачи из модуля
     */
    fun getJSON(): JSONObject {
        return JSONObject("""{"id":$mID,"name":"$mName"}""")
    }
}

/**
 * Класс триггера от GPS
 *
 * @property  mLongitude долгота
 * @property  mLatitude широта
 * @property  mRadius радиус определения точки в метрах
 * @property  mPrior расстояние в метрах для срабатывания до точки. Если 0, то отключено.
 * @property  mPost расстояние в метрах для срабатывания после точки. Если 0, то отключено.
 * @property  mDelay задержка в секундах при срабатывании в точке.
 * @property  mBearing курс в направлении вперед для автоопределения вектора (в [mPrior])
 * @property  mIn флаг текущего положения (0 - вне точки, 1 - между [mPrior] и точкой, 2 - между точкой и [mPost].
 * @property  mDir флаг включения в зависимости от направления.
 */
data class GPSTrigger(
        var mLongitude: Double = 0.0,
        var mLatitude: Double = 0.0,
        var mRadius: Float = 25f,
        var mPrior: Float = 0f,
        var mPost: Float = 0f,
        var mDelay: Int = 0,
        var mBearing: Float? = null,
        var mIn: Int = 0,
        var mDir: RouteDir = RouteDir.ANY
){
    /**
     * Конструктор из JSON
     *
     * @param  json Описание данных в JSON
     * @param  dir Включение в зависимости от направления
     */
    constructor(json: JSONObject, dir: RouteDir? = null) : this() {
        if(json.has("lon")){
            mLongitude = json.getDouble("lon")
        }
        if(json.has("lat")){
            mLatitude = json.getDouble("lat")
        }
        if(json.has("radius")){
            mRadius = json.getDouble("radius").toFloat()
        }
        if(json.has("prior")){
            mPrior = json.getDouble("prior").toFloat()
        }
        if(json.has("post")){
            mPost = json.getDouble("post").toFloat()
        }
        if(json.has("delay")){
            mDelay = json.getInt("delay")
        }
        if(json.has("bearing")){
            mBearing = json.getDouble("bearing").toFloat()
        }
        if(dir!= null)mDir=dir
    }
}

/**
 * Класс данных строки cron триггера от времени
 */
class CronDate(){
    /**
     * Объект для определения времени срабатывания
     */
    private var mCronData: CronExpression ? = null

    /**
     * Конструктор из строки
     *
     * @param  s строка в формате "0 0 * ? * * *"
     */
    constructor(s: String) : this(){
        if(CronExpression.isValidExpression(s)) {
            mCronData = CronExpression(s)
        }else throw Exception("Cron data format failed")
    }

    /**
     * Получить следующее время срабатывания
     *
     * @param  time текущще время
     * @return следующее время срабатывания после [time]
     */
    fun nextTime(time: Date):Date? {
        if(mCronData==null) return null
        return mCronData!!.getTimeAfter(time)
    }
}

/**
 * Класс триггера по времени
 *
 * @property  mID id скрипта
 * @property  mNext следующее время срабатывания
 * @property  mCronDate данные расписания
 */
data class TimerTrigger(
        var mID: Int = 0,
        var mNext: Date? = null,
        var mCronDate: CronDate? = null,
){
    /**
     * Конструктор из строки
     *
     * @param  id id скрипта
     * @param  time строка в формате "0 0 * ? * * *"
     */
    constructor(id: Int, time: String) : this() {
        mID = id
        if(time.isBlank()) mNext=Date()
        else mCronDate = CronDate(time)
    }

    /**
     * Конструктор для одноразового выполнения
     *
     * @param  id id скрипта
     * @param  seconds задержка в секундах
     */
    constructor(id: Int, seconds: Long) : this() {
        mID = id
        mNext = Date.from(LocalDateTime.now().plusSeconds(seconds).atZone(ZoneId.systemDefault()).toInstant())
    }

    /**
     * Высчитать следующее время выполнения [mNext]
     *
     * @param time текущще время
     * @return true если возможно выполнение в будущем
     */
    fun findOutNextDate(time: Date): Boolean{
        if((mCronDate != null) && (mNext == null)){
            mNext = mCronDate!!.nextTime(time)
            return mNext != null
        }
        return false
    }
}

/**
 * Класс приоритета скрипта
 *
 * @property  mPriority приоритет
 * @property  mRule режим вытеснения скрипта
 * @property  mWait Если true, то следующий скрипт с таким же приоритетом вытеснит этот скрипт, если он уже исполняется
 * @property  mSelf Если true, то скрипт вытесняет сам себя, если он уже исполняется.
 */
data class TaskPriority(
        var mPriority: Int = 0,
        var mRule: TaskRule = TaskRule.STOP,
        var mWait: Boolean = false,
        var mSelf: Boolean = false
){
    /**
     * Конструктор из JSON
     *
     * @param  json Описание данных в JSON
     */
    constructor(json: JSONObject) : this() {
        if(json.has("wait")){
            mWait = json.getString("wait") == "on"
        }
        if(json.has("self")){
            mSelf = json.getString("self") == "on"
        }
        if(json.has("priority")){
            mPriority = json.getInt("priority")
        }
        if(json.has("rule")){
            mRule = when(json.getString("rule")){
                "begin" -> TaskRule.BEGIN
                "resume" -> TaskRule.RESUME
                else -> TaskRule.STOP
            }
        }
    }
}

/**
 * Родительский класс очереди скриптов
 *
 * @param  lenth размер очереди сообщений
 */
open class TaskQueue(lenth: Int){
    /**
     * Канал для передечи сообщений основной корутине
     */
    protected val mChannel = Channel<Any>(lenth)
    @InternalCoroutinesApi
    /**
     * Основная корутина
     */
    private val mJob = GlobalScope.launch {
        for(msg in mChannel){
            analize(msg)
        }
        closed()
    }

    /**
     * Виртульная функция на выход из корутины
     */
    protected open fun closed(){}

    /**
     * Виртульная функция на обработку сообщений корутине
     *
     * @param msg сообщение из канала
     */
    protected open suspend fun analize(msg: Any){}

    @InternalCoroutinesApi
    /**
     * Остановка корутины
     */
    suspend fun stop(){
        mChannel.close()
        mJob.join()
    }
}