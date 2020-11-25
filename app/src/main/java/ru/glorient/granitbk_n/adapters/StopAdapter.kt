package ru.glorient.granitbk_n.adapters

import androidx.recyclerview.widget.DiffUtil
import com.hannesdorfmann.adapterdelegates4.AsyncListDifferDelegationAdapter
import ru.glorient.granitbk_n.avtoinformer.Stop

// Адаптер для объектов списка
class StopAdapter(
    private val onItemClick: (position: Int) -> Unit
) : AsyncListDifferDelegationAdapter<Stop>(StopDiffUtilCallback()) {

    init {
        delegatesManager.addDelegate(DefaultStopAdapterDelegate(onItemClick))
            .addDelegate(NextStopAdapterDelegate(onItemClick))
    }

    class StopDiffUtilCallback: DiffUtil.ItemCallback<Stop>() {
        override fun areItemsTheSame(oldItem: Stop, newItem: Stop): Boolean {
            return when {
                oldItem is Stop.DefaultStop && newItem is Stop.DefaultStop -> oldItem.id == newItem.id
                oldItem is Stop.NextStop && newItem is Stop.NextStop -> oldItem.id == newItem.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: Stop, newItem: Stop): Boolean {
            return  oldItem == newItem
        }
    }
}