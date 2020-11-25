package ru.glorient.granitbk_n.avtoinformer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import ru.glorient.granitbk_n.MainActivity

class AvtoInformatorViewModel : ViewModel() {

    private val repository = MainActivity.model.mInformer

    private val stopLiveData = MutableLiveData<List<Stop>>()

    val stops: LiveData<List<Stop>>
        get() = stopLiveData

    val serviceManager = repository.mServiceManager

    // Получаем маршрут и возвращаем индекс текущей остановки
    fun requestStops() : Int {
        var ind = 0
        repository.updateList() { index, stopsList ->
            ind = index
            stopLiveData.postValue(stopsList)
        }
        return ind
    }
}