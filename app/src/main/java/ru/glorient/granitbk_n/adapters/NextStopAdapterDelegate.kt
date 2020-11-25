package ru.glorient.granitbk_n.adapters

import android.view.ViewGroup
import com.hannesdorfmann.adapterdelegates4.AbsListItemAdapterDelegate
import ru.glorient.granitbk_n.R
import ru.glorient.granitbk_n.avtoinformer.Stop
import ru.glorient.granitbk_n.accesory.inflate

// AdapterDelegate для текущих остановок
class NextStopAdapterDelegate(
    private val onItemClick: (position: Int) -> Unit
): AbsListItemAdapterDelegate<Stop.NextStop, Stop, NextStopHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup): NextStopHolder {
        return NextStopHolder(
            parent.inflate(R.layout.item_next_stop),
            onItemClick
        )
    }

    override fun isForViewType(item: Stop, items: MutableList<Stop>, position: Int): Boolean {
        return item is Stop.NextStop
    }

    override fun onBindViewHolder(
        item: Stop.NextStop,
        holder: NextStopHolder,
        payloads: MutableList<Any>
    ) {
        holder.bind(item)
    }
}