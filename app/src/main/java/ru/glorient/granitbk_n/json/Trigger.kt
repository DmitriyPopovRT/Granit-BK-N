package ru.glorient.granitbk_n.json

// триггер запуска скрипта
class Trigger {
    // forward|back условие срабатывания для bidir в зависимости от направления движения
    // (По умолчанию forward)
    var route = "forward"

    // on|off разрешение запуска в ручном режиме    (По умолчанию on)
    var handle = "on"

    // триггер от положения GPS
    var gps: Gps = Gps()

    // триггер по времени
    var time = Time()
}