package ru.example.guessnumber.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.example.guessnumber.GameViewModel
import ru.example.guessnumber.R
import ru.example.guessnumber.data.GameRecord
import ru.example.guessnumber.data.PreferencesManager
import ru.example.guessnumber.databinding.FragmentRecordsBinding

/**
 * RecordsFragment — экран таблицы рекордов (топ-10).
 * Рекорды хранятся в SharedPreferences и сортируются по количеству попыток.
 */
class RecordsFragment : Fragment() {

    private var _binding: FragmentRecordsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GameViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = PreferencesManager(requireContext())
        viewModel.initPreferences(prefs)
        val records = prefs.getRecords()

        // Пустая таблица — показываем заглушку
        if (records.isEmpty()) {
            binding.rvRecords.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.rvRecords.layoutManager = LinearLayoutManager(requireContext())
            binding.rvRecords.adapter = RecordsAdapter(records)
        }
    }

    /** Простой адаптер строк таблицы рекордов */
    private class RecordsAdapter(
        private val records: List<GameRecord>
    ) : RecyclerView.Adapter<RecordsAdapter.VH>() {

        class VH(val tv: android.widget.TextView) : RecyclerView.ViewHolder(tv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_record, parent, false) as android.widget.TextView
            return VH(tv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = records[position]
            // Строка вида: "1. Иван — 5 попыток (1–100)"
            holder.tv.text = holder.itemView.context.getString(
                R.string.record_row, position + 1, r.playerName, r.attempts, r.range
            )
        }

        override fun getItemCount(): Int = records.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
