package ru.glorient.informer

import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Класс шага скрипта отображения текста
 *
 * @property  mText текст для отображения
 * @property  mDelay минимальное время отображения текста в секундах
 * @property  mGPS признак выполнения в зависимости от режима триггера GPS
 * @property  mRoute признак выполнения в зависимости от вектора движения
 */
data class TextStep(
        var mText: String = "",
        var mDelay: Int = 0,
        var mGPS: GPSTriggerMode = GPSTriggerMode.NONE,
        var mRoute: RouteDir = RouteDir.ANY
){
    /**
     * Номера дисплеев для отображения текста
     */
    val mID = mutableSetOf<Int>()

    /**
     * Конструктор из JSON
     *
     * @param  json Описание данных в JSON
     * @param  dir Вектор напровления остановки
     */
    constructor(json: JSONObject, dir: RouteDir? = null) : this() {
        if(json.has("text")){
            mText = json.getString("text")
        }
        if(json.has("delay")){
            mDelay = json.getInt("delay")
        }
        if(json.has("gps")){
            mGPS = when(json.getString("gps")){
                "prior" -> GPSTriggerMode.PRIOR
                "post" -> GPSTriggerMode.POST
                else -> GPSTriggerMode.NONE
            }
        }
        if((dir==null) || (dir == RouteDir.ANY)){
            if(json.has("route")){
                mRoute = when(json.getString("route")){
                    "forward" -> RouteDir.FORWARD
                    "back" -> RouteDir.BACKWARD
                    "backward" -> RouteDir.BACKWARD
                    else -> RouteDir.ANY
                }
            }
        }else mRoute=dir
        if(json.has("id")){
            val x = json.getJSONArray("id")
            for(i in 0 until x.length()){
                mID.add(x.getInt(i))
            }
        }
    }
}

/**
 * Класс задачи скрипта отображения текста
 *
 * @property  mID id скрипта
 * @property  mTaskPriority приоретет скрипта
 * @param  json JSON массив шагов скрипта
 * @param  dir Вектор напровления остановки
 */
class TextTask(var mID: Int, val mTaskPriority: TaskPriority, json: JSONArray? = null, dir: RouteDir? = null) {
    var mScriptList = mutableListOf<TextStep>()
    init{
        if(json != null) {
            for (index in 0 until json.length()) {
                val script = json.getJSONObject(index)
                mScriptList.add(TextStep(script, dir))
            }
        }
    }

    /**
     * Получить задачу с фильтрацией по вектору направления
     *
     * @param dir вектор направления
     * @return копия задачи с отфильтрованными шагами
     */
    fun getTaskCopy(dir: RouteDir): TextTask{
        val res = TextTask(mID, mTaskPriority)
        res.mScriptList = mScriptList
                .filter { (it.mRoute == RouteDir.ANY) || (it.mRoute == dir) }
                .toMutableList()
        return res
    }

    /**
     * Получить задачу с фильтрацией по режиму GPS
     *
     * @param gps режим сраьатывания GPS
     * @return копия задачи с отфильтрованными шагами
     */
    fun getTaskCopy(gps: GPSTriggerMode): TextTask{
        val res = TextTask(mID, mTaskPriority)
        res.mScriptList = mScriptList
                .filter { (it.mGPS == gps) }
                .toMutableList()
        return res
    }

    /**
     * Соединить шаги задач
     *
     * @param task задача с присоединяемыми шагами
     * @return копия задачи с шагами 2-х задач
     */
    fun addScriptList(task: TextTask): TextTask{
        val res = TextTask(mID, mTaskPriority)
        res.mScriptList = mScriptList.toMutableList()
        res.mScriptList.addAll(task.mScriptList)
        return res
    }

    /**
     * Строковое отображение объекта
     *
     * @return строка для отладки
     */
    override fun toString(): String {
        var res = "$mTaskPriority|"
        mScriptList.forEach{
            res += "$it|"
        }
        return res
    }
}

/**
 * Класс очереди задач отображения текста
 *
 * @property  onText функция на событие отображение текста, где s сам текст, а l список номеров дисплеев.
 */
open class TextTaskQueue(val onText: (s: String, l: List<Int>) -> Unit = { _: String, _: List<Int> -> }) : TaskQueue(100) {
    /**
     * Номер очереди текущего скрипта для определения порядка выполнения скриптов с одинаковым приоритетом
     */
    private var mScriptID = 0
    /**
     * Класс описывающий скрипт
     *
     * @property  mTextTask набор шагов скрипта
     * @property  mPushID номер занесения скрипта в очередь (из [mScriptID])
     * @property  mStep текущий шаг скрипта
     */
    data class Script(
            val mTextTask: TextTask,
            val mPushID: Int = 0,
            var mStep: Int = 0
    )

    /**
     * Карта задач в очереди
     */
    private var mScriptMap = mutableMapOf<Int,Script>()
    /**
     * метка для ожидания окончания задержки вывода текста
     */
    private var mWaitID = 0L
    /**
     * Корутина для ожидания окончания задержки вывода текста
     */
    private var mWaitJob: Job? = null
    /**
     * id выполняющегося скрипта
     */
    var mCurrentID = 0

    /**
     * Функция на обработку сообщений корутине
     *
     * @param msg сообщение из канала
     */
    override suspend fun analize(msg: Any){
        when (msg) {
            is TextTask -> {
                addTextTask(msg)
            }
            is Int -> {
                removeTextTask(msg)
            }
            is Long -> {
                endDelay(msg)
            }
        }
    }

    /**
     * Функция на выход из корутины
     */
    override fun closed(){
       runBlocking { mWaitJob?.cancelAndJoin() }
    }

    /**
     * Функция обработки окончания задержки
     *
     * @param tid метка для ожидания окончания задержки вывода текста
     */
    private suspend fun endDelay(tid: Long) {
        if( (mCurrentID != 0) && (mWaitID == tid)){
            mWaitID = 0
            mScriptMap[mCurrentID]!!.mStep++
            next()
        }
    }

    /**
     * Функция удаления скрипта из очереди
     *
     * @param id id скрипта
     */
    private fun removeTextTask(id: Int) {
        if(mCurrentID != id){
            mScriptMap.remove(id)
        }
    }

    /**
     * Функция добавления скрипта в очередь
     *
     * @param task скрипт
     */
    private suspend fun addTextTask(task: TextTask) {
        if(task.mID == mCurrentID) {
          if(!task.mTaskPriority.mSelf){
              return
          }else{
              stopCurrent()
          }
        }

        if(!task.mTaskPriority.mSelf) {
            if(mScriptMap.containsKey(task.mID))return
        }

        if(mCurrentID == 0){
            mScriptMap[task.mID] = Script(task)
        }else{
            if(task.mTaskPriority.mPriority > mScriptMap[mCurrentID]!!.mTextTask.mTaskPriority.mPriority){
                stopCurrent()
                mScriptMap[task.mID] = Script(task)
            } else {
                if (task.mTaskPriority.mWait) {
                    mScriptMap[task.mID] = Script(task, mScriptID++)
                } else {
                    if (mScriptMap[mCurrentID]!!.mTextTask.mTaskPriority.mPriority == task.mTaskPriority.mPriority) {
                        if((task.mID < 10000) || ((task.mID % 10000) != (mCurrentID % 10000))) {
                            stopCurrent()
                            removeAllWithPriority(task.mTaskPriority.mPriority)
                        }
                    }
                    mScriptMap[task.mID] = Script(task, mScriptID++)
                }
            }
        }

        next()
    }

    /**
     * Функция удаления из очереди всех скриптов с задданым приоритетом
     *
     * @param priority приоритет
     */
    private fun removeAllWithPriority(priority: Int) {
        mScriptMap = mScriptMap
                .filterValues { it.mTextTask.mTaskPriority.mPriority != priority }
            .toMutableMap()
    }

    /**
     * Функция остановки выполняющегося скрипта
     */
    private fun stopCurrent() {
        if(mCurrentID != 0){
            val script = mScriptMap[mCurrentID]!!
            if(script.mTextTask.mTaskPriority.mRule == TaskRule.STOP){
                mScriptMap.remove(mCurrentID)
            }else if(script.mTextTask.mTaskPriority.mRule == TaskRule.BEGIN){
                script.mStep = 0
            }
            mCurrentID = 0
            mWaitID = 0L
        }
    }

    /**
     * Следующий шаг в очереди скриптов
     */
    private suspend fun next(){
        if(mCurrentID != 0){
            val script = mScriptMap[mCurrentID]!!
            while(script.mStep < script.mTextTask.mScriptList.size) {
                val step = script.mTextTask.mScriptList[script.mStep]
                if (mWaitID == 0L) {
                    onText(step.mText, step.mID.toList())
                    if (step.mDelay != 0) {
                        mWaitJob?.cancelAndJoin()
                        mWaitID = (1L..Long.MAX_VALUE).random()
                        val tid=mWaitID
                        val td = step.mDelay*1000L
                        mWaitJob = GlobalScope.launch {
                            delay(td)
                            mChannel.send(tid)
                        }
                        return
                    } else {
                        script.mStep++
                    }
                }else{
                   return
                }
            }
            mScriptMap.remove(mCurrentID)
            mCurrentID = 0
        }

        if((mCurrentID == 0) && (mScriptMap.isNotEmpty())){
            var prior = -1
            var subprior = Int.MAX_VALUE
            var tid = 0
            mScriptMap.forEach{ (id, script) ->
                if(script.mTextTask.mTaskPriority.mPriority > prior){
                    tid = id
                    prior = script.mTextTask.mTaskPriority.mPriority
                    subprior = script.mPushID
                }else if(script.mTextTask.mTaskPriority.mPriority == prior){
                    if(script.mPushID < subprior){
                        tid = id
                        subprior = script.mPushID
                    }
                }
            }
            mCurrentID = tid
            mWaitID = 0L
            next()
        }

        if(mScriptMap.isEmpty())mScriptID=0
    }

    /**
     * Метод добавления скрипта
     *
     * @param script скрипт
     */
    @ExperimentalCoroutinesApi
    suspend fun add(script: TextTask){
        if(script.mScriptList.isNotEmpty()) {
            if (!mChannel.isClosedForSend) mChannel.send(script)
        }
    }

    /**
     * Метод удавления скрипта
     *
     * @param scriptID id скрипта
     */
    @ExperimentalCoroutinesApi
    suspend fun remove(scriptID: Int){
        if (!mChannel.isClosedForSend) mChannel.send(scriptID)
    }
}