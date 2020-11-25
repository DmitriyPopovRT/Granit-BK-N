package ru.glorient.informer

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Класс шага скрипта проигрования аудиофайла
 *
 * @property  mFile название аудиофайла
 * @property  mGPS признак выполнения в зависимости от режима триггера GPS
 * @property  mRoute признак выполнения в зависимости от вектора движения
 */
data class AudioStep(
        var mFile: String = "",
        var mGPS: GPSTriggerMode = GPSTriggerMode.NONE,
        var mRoute: RouteDir = RouteDir.ANY
){
    /**
     * Конструктор из JSON
     *
     * @param  json Описание данных в JSON
     * @param  path папка с аудиофайлами
     * @param  dir Вектор напровления остановки
     */
    constructor(json: JSONObject, path: String? = null, dir: RouteDir? = null) : this() {
        if(json.has("file")){
            mFile = if(path != null) path + json.getString("file")
            else json.getString("file")
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
    }
}

/**
 * Класс задачи скрипта проигрования аудиофайла
 *
 * @property  mID id скрипта
 * @property  mTaskPriority приоретет скрипта
 * @param  path папка с аудиофайлами
 * @param  json JSON массив шагов скрипта
 * @param  dir Вектор напровления остановки
 */
class AudioTask(var mID: Int, val mTaskPriority: TaskPriority, path: String? = null, json: JSONArray? = null, dir: RouteDir? = null) {
    var mScriptList = mutableListOf<AudioStep>()
    init{
        if(json != null) {
            for (index in 0 until json.length()) {
                val script = json.getJSONObject(index)
                if (script.has("file")) {
                    mScriptList.add(AudioStep(script, path, dir))
                }
            }
        }
    }

    /**
     * Получить задачу с фильтрацией по вектору направления
     *
     * @param dir вектор направления
     * @return копия задачи с отфильтрованными шагами
     */
    fun getTaskCopy(dir: RouteDir): AudioTask{
        val res = AudioTask(mID, mTaskPriority)
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
    fun getTaskCopy(gps: GPSTriggerMode): AudioTask{
        val res = AudioTask(mID, mTaskPriority)
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
    fun addScriptList(task: AudioTask): AudioTask{
        val res = AudioTask(mID, mTaskPriority)
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
 * Класс очереди задач проигрования аудиофайлов
 *
 * @property  mContext контекст для проигрователя.
 */
open class AudioTaskQueue(private val mContext: Context) : TaskQueue(100) {
    /**
     * Номер очереди текущего скрипта для определения порядка выполнения скриптов с одинаковым приоритетом
     */
    private var mScriptID = 0
    /**
     * Класс описывающий скрипт
     *
     * @property  mAudioTask набор шагов скрипта
     * @property  mPushID номер занесения скрипта в очередь (из [mScriptID])
     * @property  mStep текущий шаг скрипта
     */
    data class Script(
            val mAudioTask: AudioTask,
            val mPushID: Int = 0,
            var mStep: Int = 0
    )

    /**
     * Карта задач в очереди
     */
    private var mScriptMap = mutableMapOf<Int,Script>()
    /**
     * метка для ожидания окончания проигрования аудиофайла
     */
    private var mWaitID = 0L
    /**
     * id выполняющегося скрипта
     */
    var mCurrentID = 0
    /**
     * проигрователь
     */
    private var mPlayer: MediaPlayer? = null

    /**
     * Функция на обработку сообщений корутине
     *
     * @param msg сообщение из канала
     */
    override suspend fun analize(msg: Any){
        when (msg) {
            is AudioTask -> {
                addAudioTask(msg)
            }
            is Int -> {
                removeAudioTask(msg)
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
        mPlayer?.stop()
        mPlayer?.release()
        mPlayer = null
    }

    /**
     * Функция обработки окончания проигрования
     *
     * @param tid метка для ожидания окончания проигрования аудиофайла
     */
    private suspend fun endDelay(tid: Long) {
        if( (mCurrentID != 0) && (mWaitID == tid)){
            mPlayer?.release()
            mPlayer = null
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
    private fun removeAudioTask(id: Int) {
        if(mCurrentID != id){
            mScriptMap.remove(id)
        }
    }

    /**
     * Функция добавления скрипта в очередь
     *
     * @param task скрипт
     */
    private suspend fun addAudioTask(task: AudioTask) {
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
            if(task.mTaskPriority.mPriority > mScriptMap[mCurrentID]!!.mAudioTask.mTaskPriority.mPriority){
                stopCurrent()
                mScriptMap[task.mID] = Script(task)
            } else {
                if (task.mTaskPriority.mWait) {
                    mScriptMap[task.mID] = Script(task, mScriptID++)
                } else {
                    if (mScriptMap[mCurrentID]!!.mAudioTask.mTaskPriority.mPriority == task.mTaskPriority.mPriority) {
                        println("$mCurrentID --- ${task.mID}")
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
                .filterValues { it.mAudioTask.mTaskPriority.mPriority != priority }
            .toMutableMap()
    }

    /**
     * Функция остановки выполняющегося скрипта
     */
    private fun stopCurrent() {
        if(mCurrentID != 0){
            mPlayer?.stop()
            val script = mScriptMap[mCurrentID]!!
            if(script.mAudioTask.mTaskPriority.mRule == TaskRule.STOP){
                mScriptMap.remove(mCurrentID)
            }else if(script.mAudioTask.mTaskPriority.mRule == TaskRule.BEGIN){
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
            while(script.mStep < script.mAudioTask.mScriptList.size) {
                val step = script.mAudioTask.mScriptList[script.mStep]
                if (mWaitID == 0L) {
                    mPlayer = MediaPlayer.create(mContext, Uri.fromFile(File(step.mFile)))
                    if(mPlayer != null){
                        mWaitID = (1L..Long.MAX_VALUE).random()
                        val tid=mWaitID
                        mPlayer?.setOnCompletionListener {
                            GlobalScope.launch { mChannel.send(tid) }
                        }
                        mPlayer?.start()
                    }
                    return
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
                if(script.mAudioTask.mTaskPriority.mPriority > prior){
                    tid = id
                    prior = script.mAudioTask.mTaskPriority.mPriority
                    subprior = script.mPushID
                }else if(script.mAudioTask.mTaskPriority.mPriority == prior){
                    if(script.mPushID < subprior){
                        tid = id
                        subprior = script.mPushID
                    }
                }
            }
            mCurrentID = tid
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
    suspend fun add(script: AudioTask){
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