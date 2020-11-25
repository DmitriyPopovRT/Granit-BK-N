package ru.glorient.egts

import kotlinx.coroutines.*

/**
 * Класс простой отправки записи
 *
 * Отправляется [rec], ожидается ответ транспорного уровня о доставки в течении 3 сек,
 * затем ожидается сообщение о подверждении зариси в течении 1 сек.
 * Задача осуществляется в отдельной непрерываемой корутине, результат в [onEnd].
 * Можно запускать несколько параллельно.
 *
 * @property  rec Передаваемая запись
 * @property  mParent Транспорный уровень
 * @property  onEnd Событие на окончание, где @param[res] флаг успешности выполнения, @param[msg] подробное сообщение о выполнении, @param[data] null.
 * @property  mLazy Флаг отложенного запуска
 * @constructor Запускает корутину [mCommandJob] если [mLazy] false
 */
class EGTSCommand(
        val rec: EGTSRecord,
        parent: EGTSTransportLevel,
        onEnd: (res: Boolean, msg: String, data: Any?) -> Unit,
        lazy: Boolean = false
) : EGTSCommandJob(parent,onEnd, lazy){
    /**
     * Корутина задачи
     */
    override val mCommandJob: Job = GlobalScope.launch(start = CoroutineStart.LAZY){
        parent.addSubsriber(this@EGTSCommand)
        rec.RN = parent.getRN()
        if(send(3000, listOf(rec)))
        {
            val result = withTimeoutOrNull(1000){
                loop@ while (true){
                    var msg = mChannel.receive()
                    if(msg is EGTSRecord){
                        for(x in (msg as EGTSRecord).RD){
                            if(x is EGTSSubRecordResponse){
                                val sr = x as EGTSSubRecordResponse
                                if(sr.CRN == rec.RN){
                                    mResult = true
                                    if(sr.RST.toInt() == 0)onEnd(true,"success", null)
                                    else onEnd(false,"RST=${sr.RST}", null)
                                    break@loop
                                }
                            }
                        }
                    }
                }
            }
            if(result == null)onEnd(false,"timeout2", null)
        }else{
            onEnd(false,"timeout1", null)
        }

        parent.removeSubsriber(this@EGTSCommand)
    }

    /**
     * Запуск [commandJob]
     */
    init{
        if(!lazy)mCommandJob.start()
    }
}