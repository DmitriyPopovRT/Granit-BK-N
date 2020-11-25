package ru.glorient.granitbk_n.accesory

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes

// Инфлэйт вьюхи в делегатах
fun ViewGroup.inflate(@LayoutRes layoutRes: Int, attachToRoot: Boolean = false) : View {
    return LayoutInflater.from(context).inflate(layoutRes, this, attachToRoot)
}