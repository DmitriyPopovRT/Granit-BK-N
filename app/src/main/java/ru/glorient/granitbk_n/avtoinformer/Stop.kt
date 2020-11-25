package ru.glorient.granitbk_n.avtoinformer

sealed class Stop {
    data class DefaultStop(
        val id: Long,
        val name: String,
        val time: String
    ) : Stop()

    data class NextStop(
        val id: Long,
        val name: String,
        val time: String
    ) : Stop()
}