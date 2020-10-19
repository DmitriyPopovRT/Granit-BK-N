package ru.glorient.granitbk_n.json

// Набор скриптов
class Sequences {
    // id скрипта, должен быть уникальным и больше 0.
    // Используется при вызове скрипта в ручном режиме
    var id = 1

    // id скрипта, привязанный к следующей остановке, если 0 то либо последний в списке,
    // либо не остановка. (По умолчанию 0)
    var nextstop = 0

    // название скрипта для вывода. (По умолчанию "stop_[id]")
    var name = "Остановка 1"

    // Поведения скрипта при конфликте
    var mode = Mode()

    // триггер запуска скрипта
    val trigger = Trigger()

    // список аудиофайлов
    var audio: List<Any> = arrayOf("file1.wav", "file2.wav").toList()

    // список видеофайлов
    var video: List<Any> = arrayOf("file1.mp4", "file2.mp4").toList()

    // список текстовых сообщений
    var texts = arrayListOf<Texts>()
}