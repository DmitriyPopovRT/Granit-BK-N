package ru.glorient.granitbk_n.adapters

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.extensions.LayoutContainer
import ru.glorient.granitbk_n.R
import ru.glorient.granitbk_n.avtoinformer.Stop

// Холдер для текущих остановок
class NextStopHolder(
    override val containerView: View,
    private val onItemClick: (position: Int) -> Unit
) : RecyclerView.ViewHolder(containerView), LayoutContainer {

    private val stopTextView = itemView.findViewById<TextView>(R.id.stopTextView)
    private val timeStopTextView = itemView.findViewById<TextView>(R.id.timeStopTextView)

    init {
        containerView.setOnClickListener {
            onItemClick(adapterPosition)
        }
    }

    fun bind(stop : Stop.NextStop) {
        stopTextView.text = stop.name
        timeStopTextView.text = stop.time
    }
}