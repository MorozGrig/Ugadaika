package ru.example.guessnumber.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import ru.example.guessnumber.R

/**
 * Адаптер RecyclerView для истории введённых чисел.
 * Каждый элемент — "чип" с числом; новые элементы появляются с анимацией.
 */
class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.VH>() {

    private val items = mutableListOf<Int>()

    /** Обновление списка и перерисовка */
    fun submit(newItems: List<Int>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class VH(val tv: android.widget.TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_number, parent, false) as android.widget.TextView
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tv.text = items[position].toString()
        // Анимация появления элемента истории (только при прокрутке/добавлении)
        holder.tv.startAnimation(
            AnimationUtils.loadAnimation(holder.itemView.context, R.anim.slide_in_bottom)
        )
    }

    override fun getItemCount(): Int = items.size
}
