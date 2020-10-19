package ru.glorient.granitbk_n.json

import android.os.Environment
import android.util.Log
import com.beust.klaxon.Klaxon
import java.io.*

class ParserJSON {
    // Файл json
    private var routeJsonFile: File? = File("storage/87CB-16F2/Granit-BK-N/route/route test.json")

    // Считываем файл json с SD карты и переводим в строку
    fun readFileSD() : String? {
        // проверяем доступность SD
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED
        ) {
            Log.d(
                TAG,
                "SD-карта не доступна: " + Environment.getExternalStorageState()
            )
            return ""
        }
        try {
            // открываем поток для чтения
            val br = BufferedReader(FileReader(routeJsonFile))
            var str: String? = ""
            var strResult: String? = ""
            // читаем содержимое
            while (br.readLine().also { str = it } != null) {
                strResult += str
            }
            return strResult
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            return null
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }

    // Парсим строку
    fun parse(str: String) : FileJsonOriginal? {
        return Klaxon().parse<FileJsonOriginal>(str)

//        Log.d(TAG,"version = ${result?.version}")
//        Log.d(TAG,"type = $type")
//        Log.d(TAG,"id = ${sequences?.get(0).let { it?.id }}")
//        Log.d(TAG,"nextstop = ${sequences?.nextstop}")
//        Log.d(TAG,"name = ${sequences?.name}")
//        Log.d(TAG,"priority = ${sequences?.mode?.priority}")
//        Log.d(TAG,"rule = ${sequences?.mode?.rule}")
//        Log.d(TAG,"route = ${sequences?.trigger?.route}")
//        Log.d(TAG,"handle = ${sequences?.trigger?.handle}")
//        Log.d(TAG,"lon = ${sequences?.trigger?.gps?.lon}")
//        Log.d(TAG,"lat = ${sequences?.trigger?.gps?.lat}")
//        Log.d(TAG,"radius = ${sequences?.trigger?.gps?.radius}")
//        Log.d(TAG,"prior = ${sequences?.trigger?.gps?.prior}")
//        Log.d(TAG,"post = ${sequences?.trigger?.gps?.post}")
//        Log.d(TAG,"delay = ${sequences?.trigger?.gps?.delay}")
//        Log.d(TAG,"absolute = ${sequences?.trigger?.time?.absolute}")
//        Log.d(TAG,"begin = ${sequences?.trigger?.time?.periodic?.begin}")
//        Log.d(TAG,"end = ${sequences?.trigger?.time?.periodic?.end}")
//        Log.d(TAG,"period = ${sequences?.trigger?.time?.periodic?.period}")
//        Log.d(TAG,"audio = ${sequences?.audio}")
//        Log.d(TAG,"video = ${sequences?.video}")
//        Log.d(TAG,"text = ${sequences?.get(0).let { it?.texts?.get(0)?.text }}")
//        Log.d(TAG,"delaystop = ${sequences?.texts?.delaystop}")
//        Log.d(TAG,"textnext = ${sequences?.texts?.textnext}")
    }

    companion object {
        const val TAG = "ParserJSON"
    }
}