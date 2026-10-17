package ru.example.guessnumber.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputEditText
import ru.example.guessnumber.GameViewModel
import ru.example.guessnumber.R
import ru.example.guessnumber.data.PreferencesManager

/**
 * ResultDialog — диалог результата игры (экран 3).
 * Показывает победу/поражение, загаданное число, количество попыток.
 * При новом рекорде предлагает ввести имя. Есть кнопки "Играть снова",
 * "В меню" и "Поделиться результатом".
 */
class ResultDialog : DialogFragment() {

    private val viewModel: GameViewModel by activityViewModels()

    companion object {
        private const val ARG_WON = "won"
        private const val ARG_SECRET = "secret"

        /** Фабричный метод создания диалога с аргументами */
        fun newInstance(won: Boolean, secret: Int): ResultDialog {
            val dialog = ResultDialog()
            dialog.arguments = Bundle().apply {
                putBoolean(ARG_WON, won)
                putInt(ARG_SECRET, secret)
            }
            return dialog
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        val won = requireArguments().getBoolean(ARG_WON)
        val secret = requireArguments().getInt(ARG_SECRET)
        val attempts = viewModel.attempts.value ?: 0
        val max = viewModel.maxValue.value ?: 100

        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.dialog_result, null)
        val tvMessage = view.findViewById<android.widget.TextView>(R.id.tvResultMessage)
        val etName = view.findViewById<TextInputEditText>(R.id.etPlayerName)
        val nameLayout = view.findViewById<View>(R.id.tilName)

        // Сообщение о победе/поражении + загаданное число + попытки
        tvMessage.text = if (won) {
            getString(R.string.result_win, secret, attempts)
        } else {
            getString(R.string.result_lose, secret, attempts)
        }

        // Если это победа и результат попадает в топ-10 — просим имя для рекорда
        val canBeRecord = won && viewModel.currentResultIsRecord()
        nameLayout.visibility = if (canBeRecord) View.VISIBLE else View.GONE

        val prefs = PreferencesManager(requireContext())

        return AlertDialog.Builder(requireContext())
            .setTitle(if (won) R.string.result_title_win else R.string.result_title_lose)
            .setView(view)
            // "Играть снова": сохраняем рекорд (если нужно) и перезапускаем игру
            .setPositiveButton(R.string.btn_play_again) { _, _ ->
                if (canBeRecord) {
                    val name = etName.text?.toString()?.trim().orEmpty()
                    prefs.addRecord(
                        ru.example.guessnumber.data.GameRecord(
                            playerName = if (name.isBlank()) "Аноним" else name,
                            attempts = attempts,
                            range = max
                        )
                    )
                }
                // Перезапуск той же игры
                (parentFragmentManager.findFragmentById(R.id.nav_host_fragment)
                    ?.childFragmentManager?.fragments?.firstOrNull() as? GameFragment)
                    ?.restart()
            }
            // "В меню": сохраняем рекорд и возвращаемся на главный экран
            .setNegativeButton(R.string.btn_to_menu) { _, _ ->
                if (canBeRecord) {
                    val name = etName.text?.toString()?.trim().orEmpty()
                    prefs.addRecord(
                        ru.example.guessnumber.data.GameRecord(
                            playerName = if (name.isBlank()) "Аноним" else name,
                            attempts = attempts,
                            range = max
                        )
                    )
                }
                findNavController().navigate(R.id.action_game_to_menu)
            }
            // "Поделиться результатом" через Intent
            .setNeutralButton(R.string.btn_share) { _, _ ->
                shareResult(won, secret, attempts, max)
            }
            .create()
    }

    /** Отправка текста результата через стандартный выбор приложений Android */
    private fun shareResult(won: Boolean, secret: Int, attempts: Int, max: Int) {
        val text = if (won) {
            getString(R.string.share_win, attempts, secret, max)
        } else {
            getString(R.string.share_lose, secret, max)
        }
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(intent, getString(R.string.btn_share)))
    }
}
