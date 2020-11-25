package ru.glorient.egts

import java.nio.charset.Charset
import java.util.*
import kotlin.properties.Delegates
import kotlin.properties.Delegates.observable
import kotlin.reflect.KProperty

/**
 * Интерфейс подзаписи
 *
 * | **Бит 7-0** | **Тип** | **Тип**** данных **|** Размер, байт** |
 * | --- | --- | --- | --- |
 * | SRT (Subrecord Type) | M | BYTE | 1 |
 * | --- | --- | --- | --- |
 * | SRL (Subrecord Length) | M | USHORT | 2 |
 * | SRD (Subrecord Data) | O | BINARY | 0… 65495 |
 *
 * - SRT – (SubrecordType), тип подзаписи (подтип передаваемых данных в рамках общего набора типов одного Сервиса). Тип 0 – специальный, зарезервирован за подзаписью подтверждения данных для каждого сервиса. Конкретные значения номеров типов подзаписей определяются логикой самого Сервиса. Протокол оговаривает лишь то, что этот номер должен присутствовать, а нулевой идентификатор зарезервирован;
 * - SRL – (SubrecordLength), длина данных в байтах подзаписи в поле SRD;
 * - SRD – (SubrecordData), данные подзаписи. Наполнение данного поля специфично для каждого сочетания идентификатора типа Сервиса и типа подзаписи.
 */
interface EGTSSubPacket : EGTSPacket {
    companion object {
        /**
         * Фабрика объектов подзаписей
         *
         * @param data Массив данных
         * @param offset Смещение в [data]
         * @return Объект класса взависимости от типа плдзаписи
         */
        fun getSR(data: UByteArray, offset: Int = 0): EGTSSubPacket {
            return when(data[offset]){
                EGTS_SR_RECORD_RESPONSE -> EGTSSubRecordResponse(data, offset)
                EGTS_SR_TERM_IDENTITY -> EGTSSubRecordTermIdentity(data, offset)
                EGTS_SR_RESULT_CODE -> EGTSSubRecordResultCode(data, offset)
                EGTS_SR_POS_DATA -> EGTSSubRecordPosData(data, offset)
                else -> EGTSSubRecord(data, offset)
            }
        }

        /**
         * ПОДЗАПИСЬ EGTS\_SR\_RECORD\_RESPONSE
         *
         * | **Бит 7-0** | **Тип** | **Тип**** данных **|** Размер, байт** |
         * | --- | --- | --- | --- |
         * | CRN (Confirmed Record Number) | M | USHORT | 2 |
         * | RST (Record Status) | M | BYTE | 1 |
         *
         * ```
         * Поля подзаписи EGTS\_SR\_RECORD\_RESPONSE:
         * ```
         *
         * - CRN – (ConfirmedRecordNumber), номер подтверждаемой записи (значение поля RN из обрабатываемой записи);
         * - RST – (RecordStatus), статус обработки записи.
         *
         * ```
         * При получении подтверждения Отправителем, он анализирует поле RST подзаписи EGTS\_SR\_ RECORD\_RESPONSE и, в случае получения статуса об успешной обработке, стирает запись из внутреннего хранилища, иначе, в случае ошибки и в зависимости от причины, производит соответствующие действия.Рекомендуется совмещать подтверждение транспортного уровня (тип пакета EGTS\_PT\_RESPONSE) с подзаписями – подтверждениями уровня поддержки услуг EGTS\_SR\_RECORD\_RESPONSE.
         * ```
         */
        const val EGTS_SR_RECORD_RESPONSE: UByte = 0u
        /**
         * ПОДЗАПИСЬ EGTS\_SR\_TERM\_IDENTITY
         *
         * | **Бит 7-0** | **Тип** | **Тип**** данных **|** Размер, байт** |
         * | --- | --- | --- | --- |
         * | TID (Terminal Identifier) | M | UINT | 4 |
         * | Flags | M | BYTE | 1 |
         * | MNE BSE NIDE SSRA LNGCE IMSIE IMEIE HDIDE | | | |
         * | HDID (Home Dispatcher Identifier) | O | USHORT | 2 |
         * | IMEI (International Mobile Equipment Identity) | O | STRING | 15 |
         * | IMSI (International Mobile Subscriber Identity) | O | STRING | 16 |
         * | LNGC (Language Code) | O | STRING | 3 |
         * | NID (Network Identifier) | O | BINARY | 3 |
         * | BS (Buffer Size) | O | USHORT | 2 |
         * | MSISDN (Mobile Station Integrated Services Digital Network Number) | O | STRING | 15 |
         *
         * ```
         * Поля подзаписи EGTS\_SR\_TERM\_IDENTITY:
         * ```
         *
         * - TID – (TerminalIdentifier), уникальный идентификатор, назначаемый при программировании АС. Наличие значения 0 в данном поле означает, что АС не прошел процедуру конфигурирования, или прошел её не полностью. Данный идентификатор назначается оператором и однозначно определяет набор учетных данных АС. TID назначается при инсталляции АС как дополнительного оборудования и передаче оператору учетных данных АС (IMSI, IMEI, serial\_id). В случае использования АС в качестве штатного устройства, TID сообщается оператору автопроизводителем вместе с учетными данными (VIN, IMSI, IMEI);
         * - HDIDE – (HomeDispatcherIdentifierExists), битовый флаг, который определяет наличие поля HDID в подзаписи (если бит равен 1, то поле передаётся, если 0, то не передаётся);
         * - IMEIE – (InternationalMobileEquipmentIdentityExists), битовый флаг, который определяет наличие поля IMEI в подзаписи (если бит равен 1, то поле передаётся, если 0, то не передаётся);
         * - IMSIE – (InternationalMobileSubscriberIdentityExists), битовый флаг, который определяет наличие поля IMSI в подзаписи (если бит равен 1, то поле передаётся, если 0, то не передаётся);
         * - LNGCE – (LanguageCodeExists), битовый флаг, который определяет наличие поля LNGC в подзаписи (если бит равен 1, то поле передаётся, если 0, то не передаётся);
         * - SSRA – битовый флаг предназначен для определения алгоритма использования Сервисов (если бит равен 1, то используется «простой» алгоритм, если 0, то алгоритм «запросов» на использование Cервисов);
         * - NIDE – (NetworkIdentifierExists), битовый флаг определяет наличие поля NID в подзаписи (если бит равен 1, то поле передаётся, если 0, то не передаётся);
         * - BSE – (BufferSizeExists), битовый флаг, определяющий наличие поля BS в подзаписи (если бит равен 1, то поле передаётся, если 0, то не передаётся);
         * - MNE – (MobileNetworkExists), битовый флаг, определяющий наличие поля MSISDN в подзаписи (если бит равен 1, то поле передаётся, если 0, то не передаётся);
         * - HDID – (HomeDispatcherIdentifier), идентификатор «домашней» ТП (подробная учётная информация о терминале хранится на данной ТП);
         * - IMEI – (InternationalMobileEquipmentIdentity), идентификатор мобильного устройства (модема). При невозможности определения данного параметра, АС должна заполнять данное поле значением 0 во всех 15-ти символах;
         * - IMSI – (International Mobile Subscriber Identity), идентификатормобильногоабонента. При невозможности определения данного параметра, АС должна заполнять данное поле значением 0 во всех 16-ти символах;
         * - LNGC – (LanguageCode), код языка, предпочтительного к использованию на стороне АС, по ISO 639-2, например, «rus» – русский;
         * - NID – (NetworkIdentifier), идентификатор сети оператора, в которой зарегистрирована АС на данный момент. Используются 20 младших бит. Представляет пару кодов MCC-MNC (на основе рекомендаций ITU-TE.212). Таблица 8 иллюстрирует структуру поля NID;
         * - BS – (BufferSize), максимальный размер буфера приёма АС в байтах. Размер каждого пакета информации, передаваемого на АС, не должен превышать данного значения. Значение поля BS может принимать различные значения, например 800, 1000, 1024, 2048, 4096 и т.д., и зависит от реализации аппаратной и программной частей конкретной АС;
         * - MSISDN – (Mobile Station Integrated Services Digital Network Number), телефонныйномермобильногоабонента. При невозможности определения данного параметра, устройство должно заполнять данное поле значением 0 во всех 15-ти символах (формат описан в [6]).
         *
         * ```
         * Передача поля HDID определяется настройками АС и целесообразна при возможности подключении АС к ТП, отличной от «домашней», например, при использовании территориально распределённой сети ТП. При использовании только одной «домашней» ТП, передача HDID не требуется.«Простой» алгоритм использования Сервисов, как было отмечено в подразделе 6.2.1, подразумевает, что для АС (авторизуемой ТП) доступны все Сервисы, и в таком режиме АС разрешено сразу отправлять данные для требуемого сервиса. В зависимости от действующих на авторизующей ТП для данной АС разрешений, в ответ на пакет с данными для Сервиса может быть возвращена запись-подтверждение с соответствующим признаком ошибки. В системах с простым распределением прав на использование Сервисов рекомендуется применять, именно, «Простой» алгоритм. Это сокращает объём передаваемого трафика и время, затрачиваемое АС на авторизацию.Алгоритм «запросов» на использование сервисов подразумевает, что перед тем, как использовать тот или иной тип Сервиса (отправлять данные), АС должна получить от ТП информацию о доступных для использования Сервисов. Запрос на использование сервисов может осуществляется как на этапе авторизации, так и после неё. На этапе авторизации запрос на использование того или иного сервиса производится путём добавления подзаписей типа SR\_SERVICE\_INFO и установка бита 7 поля SRVP в значение 1. После процедуры авторизации запрос на использование сервиса может быть осуществлён также при помощи подзаписей SR\_ SERVICE\_INFO.
         * ```
         */
        const val EGTS_SR_TERM_IDENTITY: UByte = 1u
        const val EGTS_SR_MODULE_DATA: UByte = 2u
        const val EGTS_SR_VEHICLE_DATA: UByte = 3u
        const val EGTS_SR_DISPATCHER_IDENTITY: UByte = 5u
        const val EGTS_SR_AUTH_PARAMS: UByte = 6u
        const val EGTS_SR_AUTH_INFO: UByte = 7u
        const val EGTS_SR_SERVICE_INFO: UByte = 8u

        /**
         * ПОДЗАПИСЬ EGTS\_SR\_RESULT\_CODE.
         *
         * | **Бит 7-0** | **Тип** | **Тип**** данных **|** Размер, байт** |
         * | --- | --- | --- | --- |
         * | RCD (Result Code) | M | BYTE | 1 |
         *
         * Поля подзаписи EGTS\_SR\_SERVICE\_INFO:
         *
         * - RCD – (ResultCode), код, определяющий результат выполнения операции авторизации
         */
        const val EGTS_SR_RESULT_CODE: UByte = 9u

        const val EGTS_SR_POS_DATA: UByte = 16u
        const val EGTS_SR_EXT_POS_DATA: UByte = 17u
        const val EGTS_SR_AD_SENSORS_DATA: UByte = 18u
        const val EGTS_SR_COUNTERS_DATA: UByte = 19u
        const val EGTS_SR_STATE_DATA: UByte = 20u
        const val EGTS_SR_LOOPIN_DATA: UByte = 22u
        const val EGTS_SR_ABS_DIG_SENS_DATA: UByte = 23u
        const val EGTS_SR_ABS_AN_SENS_DATA: UByte = 24u
        const val EGTS_SR_ABS_CNTR_DATA: UByte = 25u
        const val EGTS_SR_ABS_LOOPIN_DATA: UByte = 26u
        const val EGTS_SR_LIQUID_LEVEL_SENSOR: UByte = 27u
        const val EGTS_SR_PASSENGERS_COUNTERS: UByte = 28u
    }
}

/**
 * Базовый класс подзаписи
 */
open class EGTSSubRecord : EGTSSubPacket {
    /**
     * SubrecordType, тип подзаписи (подтип передаваемых данных в рамках общего набора типов одного Сервиса).
     *
     * Тип 0 – специальный, зарезервирован за подзаписью подтверждения данных для каждого сервиса.
     * Конкретные значения номеров типов подзаписей определяются логикой самого Сервиса.
     * Протокол оговаривает лишь то, что этот номер должен присутствовать, а нулевой идентификатор зарезервирован.
     */
    var SRT: UByte = 0u
        private set
    /**
     * SubrecordData, данные подзаписи. Наполнение данного поля специфично для каждого сочетания идентификатора типа Сервиса и типа подзаписи.
     */
    var SRD: UByteArray? = null
        protected set

    /**
     * SubrecordLength, длина данных в байтах подзаписи в поле SRD.
     */
    val SRL: UShort
        get() {
            return SRD?.size?.toUShort() ?: 0u
        }

    /**
     * Конструктор для формирования подзаписи на передачу.
     *
     * @param srt SubrecordType
     * @param srd SubrecordData
     */
    constructor(srt: UByte, srd: UByteArray? = null) {
        SRT = srt
        SRD = srd
    }

    /**
     * Конструктор для формирования подзаписи из полученных данных.
     *
     * @param data Массив данных
     * @param offset Смещение в [data]
     */
    constructor(data: UByteArray, offset: Int = 0) {
        var srl = 0
        if ((data.size - offset) > 2) {
            SRT = data[offset]
            srl = convert(data[offset + 1], data[offset + 2]).toInt()

        } else {
            throw EGTSException("EGTSSubRecord parsing failed")
        }
        if (srl > 0) {
            if (srl <= (data.size - offset - 3)) {
                SRD = data.copyOfRange(offset + 3, srl + offset + 3)
            } else {
                throw EGTSException("EGTSSubRecord parsing failed")
            }
        }
    }

    /**
     * Флаг необходимости заново сформировать [SRD].
     */
    protected var needRefresh = false
    /**
     * Виртуальный метод сформирования [SRD].
     */
    protected open fun refreshSRD(){needRefresh = false}

    /**
     * Размер данных под подзапись.
     */
    override val size: Int
        get() {
            var res= 3
            if(needRefresh)refreshSRD()
            val srd = SRD
            return if (srd != null) srd.size + 3 else 3
        }

    /**
     * Скопировать данные подзаписи в массив [data].
     *
     * @param data Массив данных
     * @param offset Смещение в [data]
     * @return Новое смещение в [data]
     */
    override fun copyData(data: UByteArray, offset: Int): Int {
        if ((data.size - offset) >= size) {
            data[offset] = SRT
            val srd = SRD
            if (srd != null) {
                data[offset + 1] = srd.size.toUByte()
                data[offset + 2] = (srd.size ushr 8).toUByte()
                srd.copyInto(data, offset + 3)
            } else {
                data[offset + 1] = 0u
                data[offset + 2] = 0u
            }
        } else {
            throw EGTSException("EGTSSubRecord copping failed")
        }
        return offset + size
    }

    /**
     * Подзапись в виде строки для отладки.
     *
     * @return frendly data
     */
    override fun toString(): String {
        var str = "0x${SRT.toString(16)}"
        if (SRL > 0u) {
            str += "(Size=${SRL}):" + SRD?.toHexString()
        }
        return str
    }

    /**
     * Подзапись в виде строки для отладки с отступами.
     *
     * @param spaces Количество пробелов
     * @return frendly data
     */
    override fun toString(spaces: Int): String {
        var str = " ".repeat(spaces) + toString() + "\n"
        return str
    }
}

class EGTSSubRecordPosData : EGTSSubRecord {
    var NTM: UInt = 0u
        set(value) {
            needRefresh = true
            field =  value
        }
    var LAT: UInt = 0u
        set(value) {
            needRefresh = true
            field =  value
        }
    var LONG: UInt = 0u
        set(value) {
            needRefresh = true
            field =  value
        }
    var FLG: UByte = 0u
        set(value) {
            needRefresh = true
            field =  value
        }
    var SPD: UShort = 0u
        set(value) {
            needRefresh = true
            field =  value
        }
    var DIR: UByte  = 0u
        set(value) {
            needRefresh = true
            field =  value
        }
    var ODM: UByteArray = UByteArray(3)
        set(value) {
            needRefresh = true
            field =  value
        }
    var DIN: UByte = 0u
        set(value) {
            needRefresh = true
            field =  value
        }
    var SRC: UByte = 0u
        set(value) {
            needRefresh = true
            field =  value
        }
    var ALT: UByteArray? = null
        set(value) {
            needRefresh = true
            if(value == null) FLG = FLG and 0x7fu
            else FLG = FLG or 0x80u
            field =  value
        }
    var SRCD: UShort? = null
        set(value) {
            needRefresh = true
            field =  value
        }

    override fun refreshSRD(){
        var size = 21
        if(ALT != null) size += 3
        if(SRCD != null) size += 2
        SRD = UByteArray(size)

        SRD!![0] = NTM.toUByte()
        SRD!![1] = (NTM.toLong() ushr 8).toUByte()
        SRD!![2] = (NTM.toLong() ushr 16).toUByte()
        SRD!![3] = (NTM.toLong() ushr 24).toUByte()
        SRD!![4] = LAT.toUByte()
        SRD!![5] = (LAT.toLong() ushr 8).toUByte()
        SRD!![6] = (LAT.toLong() ushr 16).toUByte()
        SRD!![7] = (LAT.toLong() ushr 24).toUByte()
        SRD!![8] = LONG.toUByte()
        SRD!![9] = (LONG.toLong() ushr 8).toUByte()
        SRD!![10] = (LONG.toLong() ushr 16).toUByte()
        SRD!![11] = (LONG.toLong() ushr 24).toUByte()
        SRD!![12] = FLG
        SRD!![13] = SPD.toUByte()
        SRD!![14] = (SPD.toInt() ushr 8).toUByte()
        SRD!![15] = DIR
        ODM.copyInto(SRD!!,16)
        SRD!![19] = DIN
        SRD!![20] = SRC
        var ind = 21

        if(ALT != null){
            ALT!!.toByteArray().toUByteArray().copyInto(SRD!!,ind)
            ind += +3
        }
        if(SRCD != null){
            SRD!![ind] = SRCD!!.toUByte()
            SRD!![ind+1] = (SRCD!!.toInt() ushr 8).toUByte()
           ind += 2
        }
        needRefresh = false
    }

    constructor(ntm: UInt, lat: UInt, long: UInt,
                flg: UByte = 0u,
                spd: UShort = 0u,
                dir: UByte = 0u,
                odm: UByteArray = UByteArray(3),
                din: UByte = 0u,
                src: UByte = 0u,
                alt: UByteArray? = null,
                secd: UShort? = null) : super (EGTSSubPacket.EGTS_SR_POS_DATA, null) {
        NTM = ntm
        LAT = lat
        LONG = long
        FLG = flg
        SPD = spd
        DIR = dir
        ODM = odm
        DIN = din
        SRC = src
        ALT = alt
        SRCD = secd
    }

    constructor(time: Long, lat: Double, long: Double,
                spd: Float, dir: Float,
                alt: Double? = null
                ) : super (EGTSSubPacket.EGTS_SR_POS_DATA, null) {
        val c = Calendar.getInstance()
        c.time = Date(time)
        c.add(Calendar.YEAR, -30)
        NTM = (c.timeInMillis/1000).toUInt()
        LAT = ((Math.abs(lat) * 0xffffffff)/90.0).toUInt()
        LONG = ((Math.abs(long) * 0xffffffff)/180.0).toUInt()
        FLG = 1u
        if(lat < 0) FLG = FLG or 0x20u
        if(long < 0) FLG = FLG or 0x40u
        SPD = (spd * 10).toUInt().toUShort()
        val dir1 = dir.toUInt().toUShort()
        if(dir1 > 255u) SPD = SPD or 0x8000u
        DIR = dir1.toUByte()
        ODM = ubyteArrayOf(0u,0u,0u)
        if(alt != null) {
            if (alt < 0) SPD = SPD or 0x4000u
            val alt1 = Math.abs(alt).toUInt()
            ALT = ubyteArrayOf(alt1.toUByte(), (alt1.toInt() ushr 8).toUByte(), (alt1.toInt() ushr 16).toUByte())
         }
    }

    private constructor(srt: UByte, srd: UByteArray? = null) : super(srt, srd)

    constructor(data: UByteArray, offset: Int = 0) : super(data, offset) {
        if (SRT != EGTSSubPacket.EGTS_SR_POS_DATA) throw EGTSException("EGTSSubRecordPosData parsing failed")
        try {
            NTM = convert(SRD!![0],SRD!![1],SRD!![2],SRD!![3])
            LAT = convert(SRD!![4],SRD!![5],SRD!![6],SRD!![7])
            LONG = convert(SRD!![8],SRD!![9],SRD!![10],SRD!![11])
            FLG = SRD!![12]
            SPD = convert(SRD!![13],SRD!![14])
            DIR = SRD!![15]
            ODM = SRD!!.copyOfRange(16,19)
            DIN = SRD!![19]
            SRC = SRD!![20]
            var ind = 21
            if((FLG.toInt() and 0x80) != 0 ){
                ALT = SRD!!.copyOfRange(ind,ind+3)
                ind += 3
            }
        }catch(e: Exception){
            throw EGTSException("EGTSSubRecordPosData parsing failed")
        }
    }

    override fun toString(): String {
        var str = "EGTS_SR_POS_DATA(${FLG.toString(2).padStart(8,'0')}):"
        if((FLG.toInt() and 0x01) == 0)str += "invalid"
        else{
            val c = Calendar.getInstance()
            c.time = Date(NTM.toLong()*1000)
            c.add(Calendar.YEAR, 30)
            str += (c.time).toString()
            str += " "+"%.6f".format(((LONG.toDouble()*180.0)/0xffffffff))
            if((FLG.toInt() and 0x40) == 0)str += "E"
            else str += "W"
            str += " "+"%.6f".format(((LAT.toDouble()*90.0)/0xffffffff))
            if((FLG.toInt() and 0x20) == 0)str += "N"
            else str += "S"
            str += ";SPD="+"%.1f".format((SPD.toInt() and 0x3fff).toDouble()/10)
            val dir = if((SPD.toInt() and 0x8000) == 0) DIR.toInt() else  DIR.toInt() + 0x100
            str += ";DIR=${dir}"
            str += ";ODM="+"%.1f".format(convert(ODM!![0],ODM!![1],ODM!![2],0u).toDouble()/10)
            str += ";DIN=${DIN.toString(2).padStart(8,'0')}"
            str += ";SRC=${SRC}"
            if(ALT != null) {
                if((SPD.toInt() and 0x4000) != 0)str += ";ALT=-${convert(ALT!![0], ALT!![1], ALT!![2], 0u)}"
                else str += ";ALT=${convert(ALT!![0], ALT!![1], ALT!![2], 0u)}"
            }
            if(SRCD != null) str += ";SRCD=${SRCD!!}"
        }
        return str
    }
}

class EGTSSubRecordResponse : EGTSSubRecord {
    var CRN: UShort = 0u
        set(value) {
            needRefresh = true
            field =  value
        }
    var RST: UByte = 0u
        private set(value) {
            needRefresh = true
            field =  value
        }

    override fun refreshSRD(){
        SRD = UByteArray(3)

        SRD!![0] = CRN.toUByte()
        SRD!![1] = (CRN.toInt() ushr 8).toUByte()
        SRD!![2] = RST

        needRefresh = false
    }

    constructor(crn: UShort, rst: UByte = 0u) : super (EGTSSubPacket.EGTS_SR_RECORD_RESPONSE, null) {
        CRN = crn
        RST = rst
    }

    private constructor(srt: UByte, srd: UByteArray? = null) : super(srt, srd)

    constructor(data: UByteArray, offset: Int = 0) : super(data, offset) {
        if (SRT != EGTSSubPacket.EGTS_SR_RECORD_RESPONSE) throw EGTSException("EGTSSubRecordResponse parsing failed")
        try {
            CRN = convert(SRD!![0], SRD!![1])
            RST = SRD!![2]
        }catch(e: Exception){
            throw EGTSException("EGTSSubRecordResponse parsing failed")
        }
   }

    override fun toString(): String {
        var str = "EGTS_SR_RECORD_RESPONSE: CRN=${CRN},RST=${RST}"
        return str
    }
}

class EGTSSubRecordTermIdentity : EGTSSubRecord {
    var TID: UInt =0u
        set(value) {
            needRefresh = true
            field =  value
        }
    var FLAGS: UByte =0x10u
        private set(value) {
            needRefresh = true
            field =  value
        }
    var HDID: UShort? = null
        set(value) {
            needRefresh = true
            if(value == null) FLAGS = FLAGS and 0xfeu
            else FLAGS = FLAGS or 0x01u
            field =  value
        }
    var IMEI: String = ""
        set(value) {
            needRefresh = true
            if(value == "") FLAGS = FLAGS and 0xfdu
            else FLAGS = FLAGS or 0x02u
            field =  value
        }
    var IMSI: String = ""
        set(value) {
            needRefresh = true
            if(value == "") FLAGS = FLAGS and 0xfbu
            else FLAGS = FLAGS or 0x04u
            field =  value
        }
    var LNGC: String = ""
        set(value) {
            needRefresh = true
            if(value == "") FLAGS = FLAGS and 0xf7u
            else FLAGS = FLAGS or 0x08u
            field =  value
        }
    var NID: UByteArray? = null
        set(value) {
            needRefresh = true
            if(value == null) FLAGS = FLAGS and 0xdfu
            else FLAGS = FLAGS or 0x20u
            field =  value
        }
    var BS: UShort? = null
        set(value) {
            needRefresh = true
            if(value == null) FLAGS = FLAGS and 0xbfu
            else FLAGS = FLAGS or 0x40u
            field =  value
        }
    var MSISDN: String = ""
        set(value) {
            needRefresh = true
            if(value == "") FLAGS = FLAGS and 0x7fu
            else FLAGS = FLAGS or 0x80u
            field =  value
        }

    override fun refreshSRD(){
        var size = 5
        if(HDID != null) size += 2
        if(IMEI != "") size += 15
        if(IMSI != "") size += 16
        if(LNGC != "") size += 3
        if(NID != null) size += 3
        if(BS != null) size += 2
        if(MSISDN != "") size += 15
        SRD = UByteArray(size)

        SRD!![0] = TID.toUByte()
        SRD!![1] = (TID.toLong() ushr 8).toUByte()
        SRD!![2] = (TID.toLong() ushr 16).toUByte()
        SRD!![3] = (TID.toLong() ushr 24).toUByte()
        SRD!![4] = FLAGS
        var ind = 5

        if(HDID != null){
            SRD!![ind] = HDID!!.toUByte()
            SRD!![ind+1] = (HDID!!.toInt() ushr 8).toUByte()
            ind += 2
        }
        if(IMEI != ""){
            IMEI.toByteArray().toUByteArray().copyInto(SRD!!,ind)
            ind += 15
        }
        if(IMSI != ""){
            IMSI.toByteArray().toUByteArray().copyInto(SRD!!,ind)
            ind += 16
        }
        if(LNGC != ""){
            LNGC.toByteArray().toUByteArray().copyInto(SRD!!,ind)
            ind += 3
        }
        if(NID != null){
            NID!!.copyInto(SRD!!,ind)
            ind += 3
        }
        if(BS != null){
            SRD!![ind] = BS!!.toUByte()
            SRD!![ind+1] = (BS!!.toInt() ushr 8).toUByte()
            ind += 2
        }
        if(MSISDN != ""){
            MSISDN.toByteArray(Charsets.ISO_8859_1).toUByteArray().copyInto(SRD!!)
            ind += 15
        }

        needRefresh = false
    }

    constructor(tid: UInt, ssra: Boolean = true,
                hdid: UShort? = null,
                imei: String = "",
                imsi: String = "",
                lngc: String = "",
                nid: UByteArray? = null,
                bs: UShort? = null,
                msudn: String = "") : super (EGTSSubPacket.EGTS_SR_TERM_IDENTITY, null) {
        TID = tid
        FLAGS = if(ssra) 0x10u else 0x0u
        HDID = hdid
        IMEI = imei
        IMSI = imsi
        LNGC = lngc
        NID = nid
        BS = bs
        MSISDN = msudn
    }

    private constructor(srt: UByte, srd: UByteArray? = null) : super(srt, srd)

    constructor(data: UByteArray, offset: Int = 0) : super(data, offset) {
        if (SRT != EGTSSubPacket.EGTS_SR_TERM_IDENTITY) throw EGTSException("EGTSSubRecordTermIdentity parsing failed")
        try {
            TID = convert(SRD!![0],SRD!![1],SRD!![2],SRD!![3])
            FLAGS = SRD!![4]
            var ind = 5
            if((FLAGS.toInt() and 0x01) != 0 ){
                HDID = convert(data[ind],data[ind+1])
                ind += 2
            }
            if((FLAGS.toInt() and 0x02) != 0 ){
                IMEI = SRD!!.toByteArray().decodeToString(ind,ind+15)
                ind += 15
            }
            if((FLAGS.toInt() and 0x04) != 0 ){
                IMSI = SRD!!.toByteArray().decodeToString(ind,ind+16)
                ind += 16
            }
            if((FLAGS.toInt() and 0x08) != 0 ){
                LNGC = SRD!!.toByteArray().decodeToString(ind,ind+3)
                ind += 3
            }
            if((FLAGS.toInt() and 0x20) != 0 ){
                NID = SRD!!.copyOfRange(ind,ind+3)
                ind += 3
            }
            if((FLAGS.toInt() and 0x40) != 0 ){
                BS = convert(data[ind],data[ind+1])
                ind += 2
            }
            if((FLAGS.toInt() and 0x80) != 0 ){
                MSISDN = SRD!!.toByteArray().decodeToString(ind,ind+15)
                ind += 15
            }
        }catch(e: Exception){
            throw EGTSException("EGTSSubRecordTermIdentity parsing failed")
        }
    }

    override fun toString(): String {
        var str = "EGTS_SR_TERM_IDENTITY(${FLAGS.toString(2).padStart(8,'0')}): TID=${TID}"
        if(HDID != null) str += ",HDID=${HDID}"
        if(IMEI != "") str += ",IMEI=${IMEI}"
        if(IMSI != "") str += ",IMSI=${IMSI}"
        if((FLAGS.toInt() and 0x10) != 0 ) str += ",SSRA"
        if(NID != null) str += ",NID=${NID?.toHexString()}"
        if(BS != null) str += ",BS=${BS}"
        if(MSISDN != "") str += ",MSISDN=${MSISDN}"
        return str
    }
}

class EGTSSubRecordResultCode : EGTSSubRecord {
    var RCD: UByte = 0u
        private set(value) {
            needRefresh = true
            field =  value
        }

    override fun refreshSRD(){
        SRD = UByteArray(1)

        SRD!![0] = RCD

        needRefresh = false
    }

    constructor(rcd: UByte) : super (EGTSSubPacket.EGTS_SR_RESULT_CODE, null) {
        RCD = rcd
    }

    private constructor(srt: UByte, srd: UByteArray? = null) : super(srt, srd)

    constructor(data: UByteArray, offset: Int = 0) : super(data, offset) {
        if (SRT != EGTSSubPacket.EGTS_SR_RESULT_CODE) throw EGTSException("EGTSSubRecordResultCode parsing failed")
        try {
            RCD = SRD!![0]
        }catch(e: Exception){
            throw EGTSException("EGTSSubRecordResultCode parsing failed")
        }
    }

    override fun toString(): String {
        var str = "EGTS_SR_RESULT_CODE: RCD=${RCD}"
        return str
    }
}
