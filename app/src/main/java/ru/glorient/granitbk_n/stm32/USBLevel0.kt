package ru.glorient.stm32

import org.json.JSONObject

/**
 * Плдсчсет CRC16
 */
open class CCRCC16{
    /**
     * Таблица CRC16
     */
    private val mCRCTable = arrayOf(
            0x00, 0x01189, 0x02312, 0x0329B, 0x04624, 0x057AD, 0x06536, 0x074BF, 0x08C48, 0x09DC1, 0x0AF5A, 0x0BED3, 0x0CA6C, 0x0DBE5, 0x0E97E, 0x0F8F7,
            0x01081, 0x0108, 0x03393, 0x0221A, 0x056A5, 0x0472C, 0x075B7, 0x0643E, 0x09CC9, 0x08D40, 0x0BFDB, 0x0AE52, 0x0DAED, 0x0CB64, 0x0F9FF, 0x0E876,
            0x02102, 0x0308B, 0x0210, 0x01399, 0x06726, 0x076AF, 0x04434, 0x055BD, 0x0AD4A, 0x0BCC3, 0x08E58, 0x09FD1, 0x0EB6E, 0x0FAE7, 0x0C87C, 0x0D9F5,
            0x03183, 0x0200A, 0x01291, 0x0318, 0x077A7, 0x0662E, 0x054B5, 0x0453C, 0x0BDCB, 0x0AC42, 0x09ED9, 0x08F50, 0x0FBEF, 0x0EA66, 0x0D8FD, 0x0C974,
            0x04204, 0x0538D, 0x06116, 0x0709F, 0x0420, 0x015A9, 0x02732, 0x036BB, 0x0CE4C, 0x0DFC5, 0x0ED5E, 0x0FCD7, 0x08868, 0x099E1, 0x0AB7A, 0x0BAF3,
            0x05285, 0x0430C, 0x07197, 0x0601E, 0x014A1, 0x0528, 0x037B3, 0x0263A, 0x0DECD, 0x0CF44, 0x0FDDF, 0x0EC56, 0x098E9, 0x08960, 0x0BBFB, 0x0AA72,
            0x06306, 0x0728F, 0x04014, 0x0519D, 0x02522, 0x034AB, 0x0630, 0x017B9, 0x0EF4E, 0x0FEC7, 0x0CC5C, 0x0DDD5, 0x0A96A, 0x0B8E3, 0x08A78, 0x09BF1,
            0x07387, 0x0620E, 0x05095, 0x0411C, 0x035A3, 0x0242A, 0x016B1, 0x0738, 0x0FFCF, 0x0EE46, 0x0DCDD, 0x0CD54, 0x0B9EB, 0x0A862, 0x09AF9, 0x08B70,
            0x08408, 0x09581, 0x0A71A, 0x0B693, 0x0C22C, 0x0D3A5, 0x0E13E, 0x0F0B7, 0x0840, 0x019C9, 0x02B52, 0x03ADB, 0x04E64, 0x05FED, 0x06D76, 0x07CFF,
            0x09489, 0x08500, 0x0B79B, 0x0A612, 0x0D2AD, 0x0C324, 0x0F1BF, 0x0E036, 0x018C1, 0x0948, 0x03BD3, 0x02A5A, 0x05EE5, 0x04F6C, 0x07DF7, 0x06C7E,
            0x0A50A, 0x0B483, 0x08618, 0x09791, 0x0E32E, 0x0F2A7, 0x0C03C, 0x0D1B5, 0x02942, 0x038CB, 0x0A50, 0x01BD9, 0x06F66, 0x07EEF, 0x04C74, 0x05DFD,
            0x0B58B, 0x0A402, 0x09699, 0x08710, 0x0F3AF, 0x0E226, 0x0D0BD, 0x0C134, 0x039C3, 0x0284A, 0x01AD1, 0x0B58, 0x07FE7, 0x06E6E, 0x05CF5, 0x04D7C,
            0x0C60C, 0x0D785, 0x0E51E, 0x0F497, 0x08028, 0x091A1, 0x0A33A, 0x0B2B3, 0x04A44, 0x05BCD, 0x06956, 0x078DF, 0x0C60, 0x01DE9, 0x02F72, 0x03EFB,
            0x0D68D, 0x0C704, 0x0F59F, 0x0E416, 0x090A9, 0x08120, 0x0B3BB, 0x0A232, 0x05AC5, 0x04B4C, 0x079D7, 0x0685E, 0x01CE1, 0x0D68, 0x03FF3, 0x02E7A,
            0x0E70E, 0x0F687, 0x0C41C, 0x0D595, 0x0A12A, 0x0B0A3, 0x08238, 0x093B1, 0x06B46, 0x07ACF, 0x04854, 0x059DD, 0x02D62, 0x03CEB, 0x0E70, 0x01FF9,
            0x0F78F, 0x0E606, 0x0D49D, 0x0C514, 0x0B1AB, 0x0A022, 0x092B9, 0x08330, 0x07BC7, 0x06A4E, 0x058D5, 0x0495C, 0x03DE3, 0x02C6A, 0x01EF1, 0x0F78
    )

    /**
     * Проверка CRC
     *
     * @param data Данные с CRC
     * @return true в случае успеха
     */
    fun checkCRC(data: ByteArray):Boolean{
        var crc = 0xffff
        for(x in data){
            crc= (crc ushr 8) xor (mCRCTable[(x.toInt() xor crc) and 0x00ff])
        }
        return (crc == 0)
    }

    /**
     * Подсчет CRC
     * Добовляет CRC к концу данных
     *
     * @param data Данные с 2-мя байтами в конце под CRC
     */
    fun addCRC(data: ByteArray){
        var crc = 0xffff
        for(i in 0 until (data.count()-2)){
            crc= (crc shr 8) xor (mCRCTable[(data[i].toInt() xor crc) and 0x00ff])
        }
        data[data.count() - 2]=crc.toByte()
        data[data.count() - 1]=(crc ushr 8).toByte()
    }
}

/**
 * Класс форматирования данных для передачи по USB
 */
class USBLevel0 : CCRCC16() {
    /**
     * Синхрослово
     */
    private val mHeader =  byteArrayOf(0xf6.toByte(), 0xb8.toByte(), 0xaa.toByte(), 0x18.toByte())
    /**
     * Флаг обнаружения синхрослова
     */
    private var mStart = false
    /**
     * Размер принимаемых данных
     */
    private var mDataSize: Int = 0
    /**
     * Принимаемые данные
     */
    private var mData: ByteArray = ByteArray(0)
    /**
     * Циклический буфер для прверки синхрослова
     */
    private val mSync = byteArrayOf(0.toByte(), 0.toByte(), 0.toByte(), 0.toByte())
    /**
     * Текущий индекс принимаемых данных пакета
     */
    private var mIndex: Int = 0
    /**
     * Список принятых пакетов
     */
    private val mList = mutableListOf<String>()

    /**
     * Поиск синхрослова
     *
     * @param b принятый байт
     * @return true в случае успеха
     */
    private fun addToSync(b: Byte): Boolean {
        mSync[0] = mSync[1]
        mSync[1] = mSync[2]
        mSync[2] = mSync[3]
        mSync[3] = b
        return mSync.contentEquals(mHeader)
    }

    /**
     * Получить буфер под прием
     *
     * @return буфер
     */
    fun getBuffer(): ByteArray{
        if(!mStart) return ByteArray(4)
        else if(mIndex < 8) return ByteArray(8-mIndex)
        else return ByteArray(mDataSize+10-mIndex)
    }

    /**
     * Обработка принятых данных
     *
     * @param data принятые данные
     * @return true если есть принятые пакеты в списке
     */
    fun addData(data: ByteArray): Boolean
    {
        for (x in data){
            if (!mStart){
                if(addToSync(x)){
                    mStart = true
                    mDataSize = 0
                    mIndex = 4
                }
            }else{
                if(mIndex < 8){
                    mDataSize = (mDataSize ushr 8) or (x.toInt() shl 24)
                }
                else if (mIndex == 8){
                    val dt = ByteArray(mDataSize + 10)
                    mHeader.copyInto(dt, 0)
                    dt[4] = (mDataSize).toByte()
                    dt[5] = (mDataSize ushr 8).toByte()
                    dt[6] = (mDataSize ushr 16).toByte()
                    dt[7] = (mDataSize ushr 24).toByte()
                    dt[8] = x
                    mData=dt
                }
                else if(mIndex < (mDataSize+9)){
                    mData[mIndex] = x
                }
                else
                {
                    mData[mIndex] = x
                    newData(mData)
                    mStart = false
                }
                mIndex++
            }
        }
        return !isEmpty
    }

    /**
     * Провека принятого пакета
     *
     * @param data данные пакета
     */
    private fun newData(data: ByteArray) {
        if (checkCRC(data)) {
            val dt = data.copyOfRange(8, data.count() - 2)
            mList.add(dt.decodeToString())
        }
    }


    /**
     * Получить данные из списка
     *
     * @throws JSONObject
     * @exception JSONException
     * @return json строка или null
     */
    fun nextJSONString(): String? {
        return if (!isEmpty) {
            val res: String = mList.first()
            mList.removeAt(0)
            JSONObject(res).toString(4)
        } else {
            null
        }
    }

    /**
     * Флаг пустого списка принятых пакетов
     */
    val isEmpty: Boolean
        get() = mList.count() == 0

    /**
     * Форматирование данных для передачи
     *
     * @param json строка для передачи
     * @return пакет
     */
    fun encode(json: String): ByteArray {
        return encode(JSONObject(json))
    }

    /**
     * Форматирование данных для передачи
     *
     * @param json строка для передачи
     * @return пакет
     */
    fun encode(json: JSONObject): ByteArray {
//        println(json.toString())//@@@@
        val str: ByteArray = json.toString().toByteArray()
        val res = ByteArray(10 + str.count())
        mHeader.copyInto(res, 0)
        res[4] = str.count().toByte()
        res[5] = (str.count() ushr 8).toByte()
        res[6] = (str.count() ushr 16).toByte()
        res[7] = (str.count() ushr 24).toByte()
        str.copyInto(res, 8)
        addCRC(res)
        return res
    }
}