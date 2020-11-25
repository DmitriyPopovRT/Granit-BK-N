package ru.glorient.egts

fun UByteArray.toHexString() = asByteArray().asUByteArray().joinToString("") { it.toString(16).padStart(2, '0') }

class EGTSException(message: String?): Throwable(message) {}

interface EGTSPacket{
    val size: Int
    fun copyData(data: UByteArray, offset: Int = 0 ): Int
    fun toString(spaces: Int): String

    fun convert(low: UByte, hi: UByte): UShort{
        return (low.toUShort().toInt()+(hi.toUShort().toInt() shl 8)).toUShort()
    }

    fun convert(x1: UByte, x2: UByte, x3: UByte, x4: UByte): UInt{
        return (x1.toUInt().toInt()+(x2.toUInt().toInt() shl 8)+(x3.toUInt().toInt() shl 16)+(x4.toUInt().toInt() shl 24)).toUInt()
    }

    fun getData(): UByteArray {
        val res = UByteArray(size)
        copyData(res)
        return res
    }

    fun toBinaryString(): String{
        return "${size}:${getData().toHexString()}"
    }
}

class EGTSSignature : EGTSPacket{
    var SIGD: UByteArray? = null
        private set

    val SIGL: UShort
        get(){
            return SIGD?.size?.toUShort() ?: 0u
        }

    constructor(sigd: UByteArray){
        SIGD = sigd
    }

    constructor(data: UByteArray, offset: Int ){
        var srl = 0
        if((data.size - offset) > 2)
        {
            srl = convert(data[offset],data[offset+1]).toInt()
        }else{
            throw EGTSException("EGTSSignature parsing failed")
        }
        if(srl > 0) {
            if (srl >= (data.size - offset - 2)) {
                SIGD = data.copyOfRange(offset+2, srl)
            } else {
                throw EGTSException("EGTSSignature parsing failed")
            }
        }
    }

    override val size: Int
        get(){
            val srd = SIGD
            return if(srd != null)srd.size+2 else 2
        }

    override fun copyData(data: UByteArray, offset: Int): Int{
        if((data.size-offset) >= size){
            data[offset]=SIGL.toUByte()
            data[offset+1]=(SIGL.toInt() ushr 8).toUByte()
            SIGD?.copyInto(data,offset+2)
        }else{
            throw EGTSException("EGTSSignature copping failed")
        }
        return offset+size
    }

    override fun toString(): String {
        var str = "(Signature(${SIGL})"
        if(SIGL > 0u){
            str += ":"+SIGD?.toHexString()
        }
        return str
    }

    override fun toString(spaces: Int): String {
        var str = " ".repeat(spaces) + toString() +"\n"
        return str
    }
}

class EGTSResponse : EGTSPacket{
    var RPID: UShort = 0u
    var PR: UByte = 0u

    constructor(rpip: UShort,
                pr: UByte = 0u){
        RPID = rpip
        PR = pr
    }

    constructor(data: UByteArray, offset: Int = 0 ){
        if((data.size - offset) >= 3){
            RPID = convert(data[offset],data[offset+1])
            PR = data[offset+2]
        }else{
            throw EGTSException("EGTSResponse parsing failed")
        }
    }

    override val size: Int
        get(){
            return 3
        }

    override fun copyData(data: UByteArray, offset: Int): Int{
        if((data.size-offset) >= size){
            data[offset]=RPID.toUByte()
            data[offset+1]=(RPID.toInt() ushr 8).toUByte()
            data[offset+2]=PR
        }else{
            throw EGTSException("EGTSResponse copping failed")
        }
        return offset+size
    }

    override fun toString(): String {
        var str = "Response:RPID=${RPID},PR=${PR}"
        return str
    }

    override fun toString(spaces: Int): String {
        var str = " ".repeat(spaces) + toString() +"\n"
        return str
    }
}

class EGTSRecord : EGTSPacket{
    enum class SERVICE(val type: UByte){
        EGTS_AUTH_SERVICE(1u),
        EGTS_TELEDATA_SERVICE(2u),
        EGTS_COMMANDS_SERVICE(4u),
        EGTS_FIRMWARE_SERVICE(9u),
        EGTS_ECALL_SERVICE(10u);

        companion object {
            fun fromUByte(value: UByte): SERVICE {
                val res = SERVICE.values().firstOrNull() { it.type == value }
                if(res == null) throw EGTSException("EGTSRecord parsing SERVICE failed")
                return res!!
            }
        }
    }

    var RN: UShort = 0u
    var RFL: UByte = 0u
    var OID: UInt? = null
    var EVID: UInt? = null
    var TM: UInt? = null
    var SST: SERVICE = SERVICE.EGTS_AUTH_SERVICE
    var RST: SERVICE = SERVICE.EGTS_AUTH_SERVICE

    val RL: UShort
        get(){
            return RD.sumOf{ it.size }.toUShort()
        }

    val RD = mutableListOf<EGTSSubPacket>()

    constructor(rn: UShort,
                rfl: UByte = 0u,
                oid: UInt? = null,
                evid: UInt? = null,
                tm: UInt? = null,
                sst: SERVICE = SERVICE.EGTS_AUTH_SERVICE,
                rst: SERVICE = SERVICE.EGTS_AUTH_SERVICE){
        RN = rn
        RFL = rfl
        OID = oid
        EVID = evid
        TM = tm
        SST = sst
        RST = rst

        RFL = (RFL.toInt() and 0xf8).toUByte()
        if(OID != null) RFL = (RFL.toInt() or 0x01).toUByte()
        if(EVID != null) RFL = (RFL.toInt() or 0x02).toUByte()
        if(TM != null) RFL = (RFL.toInt() or 0x04).toUByte()
    }

    constructor(data: UByteArray, offset: Int = 0 ){
        var rl = 0
        if((data.size - offset) >= 7){
            rl = convert(data[offset],data[offset+1]).toInt()
            RN = convert(data[offset+2],data[offset+3])
            RFL = data[offset+4]
            var ind = offset+5
            if( (RFL.toInt() and 0x01) != 0){
                if((data.size - ind) < 4) throw EGTSException("EGTSRecord parsing failed")
                OID = convert(data[ind],data[ind+1],data[ind+2],data[ind+3])
                ind += 4
            }
            if( (RFL.toInt() and 0x02) != 0){
                if((data.size - ind) < 4)throw EGTSException("EGTSRecord parsing failed")
                EVID = convert(data[ind],data[ind+1],data[ind+2],data[ind+3])
                ind += 4
            }
            if( (RFL.toInt() and 0x04) != 0){
                if((data.size - ind) < 4)throw EGTSException("EGTSRecord parsing failed")
                TM = convert(data[ind],data[ind+1],data[ind+2],data[ind+3])
                ind += 4
            }
            if((data.size - ind) < 2)throw EGTSException("EGTSRecord parsing failed")
            SST = SERVICE.fromUByte(data[ind])
            RST = SERVICE.fromUByte(data[ind+1])
            ind += 2
            if(rl > 0) {
                if ((data.size - ind) < rl) throw EGTSException("EGTSRecord parsing failed")
                val sz = ind+rl
                while( ind < sz){
                    val sr=EGTSSubPacket.getSR(data, ind)
                    ind += sr.size
                    RD.add(sr)
                }
            }
        }else{
            throw EGTSException("EGTSRecord parsing failed")
        }
    }

    override val size: Int
        get(){
            var res = 7
            if(OID != null) res+=4
            if(EVID != null) res+=4
            if(TM != null) res+=4
            RD.forEach(){ res += it.size }
            return res
        }

    override fun copyData(data: UByteArray, offset: Int): Int{
        if((data.size-offset) >= size){
            val rl = RD.sumOf{ it.size }
            data[offset]=rl.toUByte()
            data[offset+1]=(rl ushr 8).toUByte()
            data[offset+2]=RN.toUByte()
            data[offset+3]=(RN.toInt() ushr 8).toUByte()
            data[offset+4]=RFL
            var ind = offset + 5
            if( (RFL.toInt() and 0x01) != 0){
                val x: UInt = OID ?: 0u
                data[ind] = x.toUByte()
                data[ind+1] = (x.toInt() ushr 8).toUByte()
                data[ind+2] = (x.toInt() ushr 16).toUByte()
                data[ind+3] = (x.toInt() ushr 24).toUByte()
                ind += 4
            }
            if( (RFL.toInt() and 0x02) != 0){
                val x: UInt = EVID ?: 0u
                data[ind] = x.toUByte()
                data[ind+1] = (x.toInt() ushr 8).toUByte()
                data[ind+2] = (x.toInt() ushr 16).toUByte()
                data[ind+3] = (x.toInt() ushr 24).toUByte()
                ind += 4
            }
            if( (RFL.toInt() and 0x04) != 0){
                val x: UInt = TM ?: 0u
                data[ind] = x.toUByte()
                data[ind+1] = (x.toInt() ushr 8).toUByte()
                data[ind+2] = (x.toInt() ushr 16).toUByte()
                data[ind+3] = (x.toInt() ushr 24).toUByte()
                ind += 4
            }
            data[ind] = SST.type
            data[ind+1] = RST.type
            ind += 2

            RD.forEach(){
                ind=it.copyData(data,ind)
            }
        }else{
            throw EGTSException("EGTSRecord copping failed")
        }
        return offset+size
    }

    override fun toString(): String {
        var str = "(${RFL.toUByte().toString(2).padStart(8,'0')}):RN=${RN},SST=${SST},RST=${RST}"
        if(OID != null) str += ",OID=${OID}"
        if(EVID != null) str += ",EVID=${EVID}"
        if(TM != null) str += ",TM=${TM}"
        if(!RD.isEmpty()) {
            str += ",Size=${RL}"
        }
        return str
    }

    override fun toString(spaces: Int): String {
        var str = " ".repeat(spaces) + toString()
        if(!RD.isEmpty()){
            str+="\n"
            RD.forEach(){
                str+=it.toString(spaces+2)
            }
        }
        return str
    }
}

class EGTSTransport{
    enum class PACKET_TYPE(val type: UByte){
        EGTS_PT_RESPONSE(0u),
        EGTS_PT_APPDATA(1u),
        EGTS_PT_SIGNED_APPDATA(2u);

        companion object {
            fun fromUByte(value: UByte): PACKET_TYPE {
                val res = PACKET_TYPE.values().firstOrNull() { it.type == value }
                if(res == null) throw EGTSException("EGTSTransport parsing PACKET_TYPE failed")
                return res!!
            }
        }
    }

    var SKID: UByte = 0u
    var FLAGS: UByte = 0u
    var HE: UByte = 0u
    var PID: UShort = 0u
    var PT: PACKET_TYPE = PACKET_TYPE.EGTS_PT_APPDATA

    var PRA: UShort? = null
    var RCA: UShort? = null
    var TTL: UByte? = null

    val SFRD = mutableListOf<EGTSPacket>()

    private var isCorrect = false
    val correct: Boolean
        get() = this.isCorrect

    constructor(){}

    constructor(skid: UByte, flags: UByte, he: UByte, pid: UShort, pt: PACKET_TYPE){
        SKID = skid
        FLAGS = (flags.toInt() and 0x1f).toUByte()
        HE = he
        PID = pid
        PT = pt

        isCorrect = true
    }

    private var index = 0
    private var hl = 0
    private var fdl = 0
    private var rd_crc: UShort = 0u
    private var rd: UByteArray? = null
    fun addData(data: UByteArray, offset: Int = 0): Boolean{
        if(isCorrect) throw EGTSException("EGTSTransport add failed")
        var ind = offset
        while(ind != data.size){
            when(index){
                0 -> if(data[ind].toInt() != 1) throw EGTSException("EGTSTransport Protocol Version failed")
                1 -> SKID = data[ind]
                2 -> FLAGS = data[ind]
                3 -> {
                    hl = data[ind].toInt()
                    if((hl != 11) && (hl != 16)) throw EGTSException("EGTSTransport Header Length failed")
                }
                4 -> HE = data[ind]
                5 -> fdl = data[ind].toInt()
                6 -> fdl += data[ind].toInt() shl 8
                7 -> PID = data[ind].toUByte().toUShort()
                8 -> PID = ((data[ind].toInt() shl 8)+PID.toInt()).toUShort()
                9 -> PT = PACKET_TYPE.fromUByte(data[ind])
                else -> {
                    if(hl == 11){
                        when(index){
                            10 -> {
                                if(!checkHeaderCRC(11, data[ind])) throw EGTSException("EGTSTransport Header CRC failed")
                                if(fdl == 0){
                                    isCorrect = true
                                    return true
                                }
                                rd = UByteArray(fdl)
                            }
                            else -> {
                                if(index < (fdl+11)){
                                    rd!![index-11]=data[ind]
                                }else if(index == fdl+11){
                                    rd_crc = data[ind].toUByte().toUShort()
                                }else{
                                    rd_crc = ((data[ind].toInt() shl 8)+rd_crc.toInt()).toUShort()
                                    if(rd_crc != getCRC16(rd!!)) throw EGTSException("EGTSTransport Data CRC failed")
                                    parseFrameData(rd!!)
                                    isCorrect = true
                                    return true
                                }
                            }
                        }
                    }else{
                        when(index){
                            10 -> PRA = data[ind].toUByte().toUShort()
                            11 -> PRA = ((data[ind].toInt() shl 8)+ PRA!!.toInt()).toUShort()
                            12 -> RCA = data[ind].toUByte().toUShort()
                            13 -> RCA = ((data[ind].toInt() shl 8)+ RCA!!.toInt()).toUShort()
                            14 -> TTL = data[ind]
                            15 -> {
                                if(!checkHeaderCRC(16, data[ind])) throw EGTSException("EGTSTransport Header CRC failed")
                                if(fdl == 0){
                                    isCorrect = true
                                    return true
                                }
                                rd = UByteArray(fdl)
                            }
                            else -> {
                                if(index < (fdl+16)){
                                    rd!![index-16]=data[ind]
                                }else if(index == fdl+16){
                                    rd_crc = data[ind].toUByte().toUShort()
                                }else{
                                    rd_crc = ((data[ind].toInt() shl 8)+rd_crc.toInt()).toUShort()
                                    if(rd_crc != getCRC16(rd!!)) throw EGTSException("EGTSTransport Data CRC failed")
                                    parseFrameData(rd!!)
                                    isCorrect = true
                                    return true
                                }
                            }
                        }
                    }
                }
            }
            ind++
            index++
        }
        return isCorrect
    }

    private fun parseFrameData(rd: UByteArray) {
        var ind = 0
        if(PT == PACKET_TYPE.EGTS_PT_RESPONSE){
            val rs = EGTSResponse(rd,ind);
            SFRD.add(rs)
            ind += rs.size
        }else if(PT == PACKET_TYPE.EGTS_PT_SIGNED_APPDATA){
            val sg = EGTSSignature(rd,ind);
            SFRD.add(sg)
            ind += sg.size
        }
        while(ind != rd.size){
            val rec = EGTSRecord(rd,ind);
            SFRD.add(rec)
            ind += rec.size
        }
    }

    private fun checkHeaderCRC(size: Int, crc: UByte): Boolean{
        if(size == 11){
            val data= ubyteArrayOf(1u,SKID,FLAGS,11u,HE,
                    fdl.toUByte(),(fdl.toInt() ushr 8).toUByte(),
                    PID.toUByte(),(PID.toInt() ushr 8).toUByte(),
                    PT.type)
            return getCRC8(data) == crc
        }else{
            val data= ubyteArrayOf(1u,SKID,FLAGS,16u,HE,
                    fdl.toUByte(),(fdl.toInt() ushr 8).toUByte(),
                    PID.toUByte(),(PID.toInt() ushr 8).toUByte(),
                    PT.type,
                    PRA!!.toUByte(),(PRA!!.toInt() ushr 8).toUByte(),
                    RCA!!.toUByte(),(RCA!!.toInt() ushr 8).toUByte(),
                    TTL!!)
            return getCRC8(data) == crc
        }
    }

    val HL: UByte
        get(){
            if(isCorrect) {
                var res = 11
                if ((FLAGS.toInt() and 0x20) != 0) res += 5
                return res.toUByte()
            }else{
                return 0u
            }
        }

    val FDL: UShort
        get(){
            if(SFRD.size == 0) return 0u
            return SFRD.sumOf { it.size }.toUShort()
        }

    val size: Int
        get(){
            if(isCorrect) {
                var res = HL.toInt()
                if (FDL > 0u) res += FDL.toInt() + 2
                return res
            }else{
                return 0
            }
        }

    fun getData(): UByteArray{
        if(!isCorrect) throw EGTSException("EGTSTransport do not init")
        val res = UByteArray(size)
        res[0]=1u
        res[1]=SKID
        res[2]=FLAGS
        res[3]=HL
        res[4]=HE
        res[5]=FDL.toUByte()
        res[6]=(FDL.toInt() ushr 8).toUByte()
        res[7]=PID.toUByte()
        res[8]=(PID.toInt() ushr 8).toUByte()
        res[9]=PT.type
        var ind=10
        if((FLAGS.toInt() and 0x20) != 0)
        {
            val pra: UShort = PRA ?: 0u
            res[ind]=pra.toUByte()
            res[ind+1]=(pra.toInt() ushr 8).toUByte()
            val rca: UShort = RCA ?: 0u
            res[ind+2]=rca.toUByte()
            res[ind+3]=(rca.toInt() ushr 8).toUByte()
            res[ind+4]=TTL ?: 0u
            ind += 5
        }
        res[ind] = getCRC8(res.copyOf(ind))
        ind++
        val end = ind

        SFRD.forEach(){
            ind=it.copyData(res,ind)
        }

        if(FDL > 0u){
            val crc = getCRC16(res.copyOfRange(end,ind))
            res[ind]=crc.toUByte()
            res[ind+1]=(crc.toInt() ushr 8).toUByte()
        }

        return res
    }

    private val crc8 = arrayOf(
            0x00, 0x31, 0x62, 0x53, 0xC4, 0xF5, 0xA6, 0x97,
            0xB9, 0x88, 0xDB, 0xEA, 0x7D, 0x4C, 0x1F, 0x2E,
            0x43, 0x72, 0x21, 0x10, 0x87, 0xB6, 0xE5, 0xD4,
            0xFA, 0xCB, 0x98, 0xA9, 0x3E, 0x0F, 0x5C, 0x6D,
            0x86, 0xB7, 0xE4, 0xD5, 0x42, 0x73, 0x20, 0x11,
            0x3F, 0x0E, 0x5D, 0x6C, 0xFB, 0xCA, 0x99, 0xA8,
            0xC5, 0xF4, 0xA7, 0x96, 0x01, 0x30, 0x63, 0x52,
            0x7C, 0x4D, 0x1E, 0x2F, 0xB8, 0x89, 0xDA, 0xEB,
            0x3D, 0x0C, 0x5F, 0x6E, 0xF9, 0xC8, 0x9B, 0xAA,
            0x84, 0xB5, 0xE6, 0xD7, 0x40, 0x71, 0x22, 0x13,
            0x7E, 0x4F, 0x1C, 0x2D, 0xBA, 0x8B, 0xD8, 0xE9,
            0xC7, 0xF6, 0xA5, 0x94, 0x03, 0x32, 0x61, 0x50,
            0xBB, 0x8A, 0xD9, 0xE8, 0x7F, 0x4E, 0x1D, 0x2C,
            0x02, 0x33, 0x60, 0x51, 0xC6, 0xF7, 0xA4, 0x95,
            0xF8, 0xC9, 0x9A, 0xAB, 0x3C, 0x0D, 0x5E, 0x6F,
            0x41, 0x70, 0x23, 0x12, 0x85, 0xB4, 0xE7, 0xD6,
            0x7A, 0x4B, 0x18, 0x29, 0xBE, 0x8F, 0xDC, 0xED,
            0xC3, 0xF2, 0xA1, 0x90, 0x07, 0x36, 0x65, 0x54,
            0x39, 0x08, 0x5B, 0x6A, 0xFD, 0xCC, 0x9F, 0xAE,
            0x80, 0xB1, 0xE2, 0xD3, 0x44, 0x75, 0x26, 0x17,
            0xFC, 0xCD, 0x9E, 0xAF, 0x38, 0x09, 0x5A, 0x6B,
            0x45, 0x74, 0x27, 0x16, 0x81, 0xB0, 0xE3, 0xD2,
            0xBF, 0x8E, 0xDD, 0xEC, 0x7B, 0x4A, 0x19, 0x28,
            0x06, 0x37, 0x64, 0x55, 0xC2, 0xF3, 0xA0, 0x91,
            0x47, 0x76, 0x25, 0x14, 0x83, 0xB2, 0xE1, 0xD0,
            0xFE, 0xCF, 0x9C, 0xAD, 0x3A, 0x0B, 0x58, 0x69,
            0x04, 0x35, 0x66, 0x57, 0xC0, 0xF1, 0xA2, 0x93,
            0xBD, 0x8C, 0xDF, 0xEE, 0x79, 0x48, 0x1B, 0x2A,
            0xC1, 0xF0, 0xA3, 0x92, 0x05, 0x34, 0x67, 0x56,
            0x78, 0x49, 0x1A, 0x2B, 0xBC, 0x8D, 0xDE, 0xEF,
            0x82, 0xB3, 0xE0, 0xD1, 0x46, 0x77, 0x24, 0x15,
            0x3B, 0x0A, 0x59, 0x68, 0xFF, 0xCE, 0x9D, 0xAC
    )
    private fun getCRC8(data: UByteArray): UByte{
        var crc = 0xff
        data.forEach { crc = crc8[crc xor it.toInt()] }
        return crc.toUByte()
    }

    private val crc16 = arrayOf(
            0x0000, 0x1021, 0x2042, 0x3063, 0x4084, 0x50A5, 0x60C6, 0x70E7,
            0x8108, 0x9129, 0xA14A, 0xB16B, 0xC18C, 0xD1AD, 0xE1CE, 0xF1EF,
            0x1231, 0x0210, 0x3273, 0x2252, 0x52B5, 0x4294, 0x72F7, 0x62D6,
            0x9339, 0x8318, 0xB37B, 0xA35A, 0xD3BD, 0xC39C, 0xF3FF, 0xE3DE,
            0x2462, 0x3443, 0x0420, 0x1401, 0x64E6, 0x74C7, 0x44A4, 0x5485,
            0xA56A, 0xB54B, 0x8528, 0x9509, 0xE5EE, 0xF5CF, 0xC5AC, 0xD58D,
            0x3653, 0x2672, 0x1611, 0x0630, 0x76D7, 0x66F6, 0x5695, 0x46B4,
            0xB75B, 0xA77A, 0x9719, 0x8738, 0xF7DF, 0xE7FE, 0xD79D, 0xC7BC,
            0x48C4, 0x58E5, 0x6886, 0x78A7, 0x0840, 0x1861, 0x2802, 0x3823,
            0xC9CC, 0xD9ED, 0xE98E, 0xF9AF, 0x8948, 0x9969, 0xA90A, 0xB92B,
            0x5AF5, 0x4AD4, 0x7AB7, 0x6A96, 0x1A71, 0x0A50, 0x3A33, 0x2A12,
            0xDBFD, 0xCBDC, 0xFBBF, 0xEB9E, 0x9B79, 0x8B58, 0xBB3B, 0xAB1A,
            0x6CA6, 0x7C87, 0x4CE4, 0x5CC5, 0x2C22, 0x3C03, 0x0C60, 0x1C41,
            0xEDAE, 0xFD8F, 0xCDEC, 0xDDCD, 0xAD2A, 0xBD0B, 0x8D68, 0x9D49,
            0x7E97, 0x6EB6, 0x5ED5, 0x4EF4, 0x3E13, 0x2E32, 0x1E51, 0x0E70,
            0xFF9F, 0xEFBE, 0xDFDD, 0xCFFC, 0xBF1B, 0xAF3A, 0x9F59, 0x8F78,
            0x9188, 0x81A9, 0xB1CA, 0xA1EB, 0xD10C, 0xC12D, 0xF14E, 0xE16F,
            0x1080, 0x00A1, 0x30C2, 0x20E3, 0x5004, 0x4025, 0x7046, 0x6067,
            0x83B9, 0x9398, 0xA3FB, 0xB3DA, 0xC33D, 0xD31C, 0xE37F, 0xF35E,
            0x02B1, 0x1290, 0x22F3, 0x32D2, 0x4235, 0x5214, 0x6277, 0x7256,
            0xB5EA, 0xA5CB, 0x95A8, 0x8589, 0xF56E, 0xE54F, 0xD52C, 0xC50D,
            0x34E2, 0x24C3, 0x14A0, 0x0481, 0x7466, 0x6447, 0x5424, 0x4405,
            0xA7DB, 0xB7FA, 0x8799, 0x97B8, 0xE75F, 0xF77E, 0xC71D, 0xD73C,
            0x26D3, 0x36F2, 0x0691, 0x16B0, 0x6657, 0x7676, 0x4615, 0x5634,
            0xD94C, 0xC96D, 0xF90E, 0xE92F, 0x99C8, 0x89E9, 0xB98A, 0xA9AB,
            0x5844, 0x4865, 0x7806, 0x6827, 0x18C0, 0x08E1, 0x3882, 0x28A3,
            0xCB7D, 0xDB5C, 0xEB3F, 0xFB1E, 0x8BF9, 0x9BD8, 0xABBB, 0xBB9A,
            0x4A75, 0x5A54, 0x6A37, 0x7A16, 0x0AF1, 0x1AD0, 0x2AB3, 0x3A92,
            0xFD2E, 0xED0F, 0xDD6C, 0xCD4D, 0xBDAA, 0xAD8B, 0x9DE8, 0x8DC9,
            0x7C26, 0x6C07, 0x5C64, 0x4C45, 0x3CA2, 0x2C83, 0x1CE0, 0x0CC1,
            0xEF1F, 0xFF3E, 0xCF5D, 0xDF7C, 0xAF9B, 0xBFBA, 0x8FD9, 0x9FF8,
            0x6E17, 0x7E36, 0x4E55, 0x5E74, 0x2E93, 0x3EB2, 0x0ED1, 0x1EF0
    )
    private fun getCRC16(data: UByteArray): UShort{
        var crc = 0xFFFF
        data.forEach {
            crc = (crc shl 8) xor crc16[((crc ushr 8) xor it.toInt()) and 0xff]
        }
        return crc.toUShort()
    }

    override fun toString(): String {
        var flag = "PR=${(FLAGS.toInt() and 3)}"
        if( (FLAGS.toInt() and 0x04) != 0 ) flag += ",CMP"
        if( (FLAGS.toInt() and 0x18) != 0 ) flag += ",ENA=${((FLAGS.toInt() ushr 3) and 3)}"
        if( (FLAGS.toInt() and 0x20) != 0 ) flag += ",RTE"

        var str = "${PT}(${flag}):PID=${PID},SKID=${SKID},HE=${HE}"
        if(PRA != null) str += ",PRA=${PRA}"
        if(RCA != null) str += ",RCA=${PRA}"
        if(TTL != null) str += ",TTL=${PRA}"
        if(!SFRD.isEmpty()) {
            str += ",Size=${FDL}\n"
            SFRD.forEach(){
                str += it.toString(2)
            }
        }
        return str
    }

    fun toBinaryString(): String{
        return "${size}:${getData().toHexString()}"
    }
}