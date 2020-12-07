package ru.glorient.granitbk_n.accessory

import android.os.Bundle
import androidx.fragment.app.Fragment

// Универсальная функция для прокидывания аргументов во фрагмент
fun <T : Fragment> T.withArguments(action: Bundle.() -> Unit): T {
    return apply {
        val args = Bundle().apply(action)
        arguments = args
    }
}