package ru.glorient.granitbk_n.adapters

import android.view.ViewGroup
import com.hannesdorfmann.adapterdelegates4.AbsListItemAdapterDelegate
import ru.glorient.granitbk_n.R
import ru.glorient.granitbk_n.avtoinformer.Stop
import ru.glorient.granitbk_n.accessory.inflate

// AdapterDelegate для дефолтных остановок
class DefaultStopAdapterDelegate(
    private val onItemClick: (position: Int) -> Unit
): AbsListItemAdapterDelegate<Stop.DefaultStop, Stop, DefaultStopHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup): DefaultStopHolder {
        return DefaultStopHolder(
            parent.inflate(R.layout.item_default_stop),
            onItemClick
        )
    }

    override fun isForViewType(item: Stop, items: MutableList<Stop>, position: Int): Boolean {
        return item is Stop.DefaultStop
    }

    override fun onBindViewHolder(
        item: Stop.DefaultStop,
        holder: DefaultStopHolder,
        payloads: MutableList<Any>
    ) {
        holder.bind(item)
    }
}