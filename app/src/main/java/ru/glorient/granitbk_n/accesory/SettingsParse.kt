package ru.glorient.granitbk_n.accesory

import android.content.Context
import android.widget.Toast
import org.json.JSONObject
import ru.glorient.granitbk_n.MainActivity
import java.io.File
import java.io.FileNotFoundException

// Парсинг файла настроек
class SettingsParse(val context: Context) {

    private val filePath = "/storage/emulated/0/Granit BK-N/settings.jsonc"

    var server = ""
    var port = 0
    var login = ""
    var password = ""
    var transportId = 0
    var timeout = 0
    var ssl = false

    init {
        try {
            val file = File(filePath)
            val str = StringBuilder()
            file.forEachLine {
                str.append(it.substringBeforeLast("//").trim())
            }

            val settings = JSONObject(str.toString())
            if (settings.has("server")) server = settings.getString("server")
            if (settings.has("port")) port = settings.getInt("port")
            if (settings.has("transport_id")) transportId = settings.getInt("transport_id")
            if (settings.has("user")) login = settings.getString("user")
            if (settings.has("password")) password = settings.getString("password")
            if (settings.has("timeout")) timeout = settings.getInt("timeout")
            if (settings.has("ssl")) ssl = settings.getBoolean("ssl")
        } catch (e: FileNotFoundException) {
            Toast.makeText(
                context,
                "Файл настроек не найден",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}