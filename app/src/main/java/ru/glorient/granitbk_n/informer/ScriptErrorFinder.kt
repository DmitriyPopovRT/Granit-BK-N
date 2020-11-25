package ru.glorient.informer

import org.json.JSONObject

/**
 * Класс для поиска ошибок в файле скрипта
 */
class ScriptErrorFinder{
    companion object{
        /**
         * Поиск ошибок
         *
         * @param  json скрипты в JSON из файла
         */
        fun findID(json: String): Int{
            try {
                var str = json.substring(json.indexOf('[') + 1, json.lastIndexOf(']') )

                while(true) {
                    val begin = str.indexOf('{')
                    if (begin < 0) return 0
                    var index = begin
                    var counter = 1
                    while (counter != 0) {
                        index = str.indexOfAny(charArrayOf('{', '}'), index + 1)
                        if (index < 0) return -2
                        if (str[index] == '{') counter++
                        else counter--
                    }
                    index++
                    var substr = str.substring(begin, index)

                    try{
                        val x = JSONObject(substr)
                        if(x.toString().indexOf(",null]") >= 0) {
                            //println("parser check , before ]")
                            return try{
                                x.getInt("id")
                            }catch (e: Exception){
                                -4
                            }
                        }
                    } catch (e: Exception) {
                        val i1 = substr.indexOf(""""id":""")
                        if(i1 >= 0){
                            substr=substr.substring(i1+""""id":""".length)
                            val i2 = substr.indexOf(",")
                            substr=substr.substring(0,i2)
                            return try {
                                substr.toInt()
                            }catch (e: Exception){
                                //println("parser $substr")
                                -3
                            }
                        }
                        return -3
                    }

                    if(index+1 < str.length) str = str.substring(index+1)
                    else return 0
                }
            }catch (e: Exception){
                return -1
            }
        }
    }
}