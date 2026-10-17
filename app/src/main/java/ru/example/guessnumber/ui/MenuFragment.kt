package ru.example.guessnumber.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import ru.example.guessnumber.GameViewModel
import ru.example.guessnumber.R
import ru.example.guessnumber.data.PreferencesManager
import ru.example.guessnumber.databinding.FragmentMenuBinding

/**
 * MenuFragment — главный экран приложения (экран 1).
 * Содержит заголовок с логотипом, кнопки выбора диапазона,
 * кнопку "Новая игра" и переход к таблице рекордов.
 */
class MenuFragment : Fragment() {

    private var _binding: FragmentMenuBinding? = null
    private val binding get() = _binding!!

    // Общая ViewModel на весь процесс игры (activityViewModels)
    private val viewModel: GameViewModel by activityViewModels()

    // Выбранный диапазон по умолчанию (читается из SharedPreferences)
    private var selectedMax = 100

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Инициализация хранилища и восстановление последнего выбранного диапазона
        val prefs = PreferencesManager(requireContext())
        viewModel.initPreferences(prefs)
        selectedMax = prefs.loadRange()
        updateRangeButtonsSelection()

        // Кнопки выбора диапазона: 1-50, 1-100, 1-200
        binding.btnRange50.setOnClickListener { selectRange(50) }
        binding.btnRange100.setOnClickListener { selectRange(100) }
        binding.btnRange200.setOnClickListener { selectRange(200) }

        // "Новая игра" — запускает игру в выбранном диапазоне и переходит на игровой экран
        binding.btnNewGame.setOnClickListener {
            viewModel.startNewGame(selectedMax)
            findNavController().navigate(R.id.action_menu_to_game)
        }

        // "Таблица рекордов" — открывает отдельный фрагмент
        binding.btnRecords.setOnClickListener {
            findNavController().navigate(R.id.action_menu_to_records)
        }
    }

    /** Подсветка активной кнопки диапазона */
    private fun selectRange(max: Int) {
        selectedMax = max
        updateRangeButtonsSelection()
    }

    private fun updateRangeButtonsSelection() {
        val activeColor = requireContext().getColor(R.color.purple_500)
        val inactiveColor = requireContext().getColor(R.color.white)
        val buttons = listOf(
            binding.btnRange50 to 50,
            binding.btnRange100 to 100,
            binding.btnRange200 to 200
        )
        for ((btn, value) in buttons) {
            btn.setBackgroundColor(if (value == selectedMax) activeColor else inactiveColor)
            btn.setTextColor(
                if (value == selectedMax) requireContext().getColor(R.color.white)
                else requireContext().getColor(R.color.black)
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Избегаем утечки памяти
    }
}
