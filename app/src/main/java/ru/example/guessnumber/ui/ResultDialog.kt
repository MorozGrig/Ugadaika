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

        /** Разрешённые символы имени: латиница, кириллица, пробел, дефис, апостроф */
        private val NAME_REGEX = Regex("^[\\p{L}][\\p{L}\\s'\\-]{0,19}$")

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

        // Валидация поля имени: разрешены буквы (латиница/кириллица), пробелы,
        // дефис и апостроф; только цифры или спецсимволы — ошибка.
        val tilName = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilName)
        etName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateName(etName, tilName)
        }

        val prefs = PreferencesManager(requireContext())

        /** Проверка введённого имени перед сохранением рекорда */
        fun saveRecord(): Boolean {
            if (!canBeRecord) return true // рекорд не требуется — сохранять нечего
            if (!validateName(etName, tilName)) return false
            val name = etName.text?.toString()?.trim().orEmpty()
            prefs.addRecord(
                ru.example.guessnumber.data.GameRecord(
                    playerName = sanitizeName(if (name.isBlank()) "Аноним" else name),
                    attempts = attempts,
                    range = max
                )
            )
            return true
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (won) R.string.result_title_win else R.string.result_title_lose)
            .setView(view)
            .setPositiveButton(R.string.btn_play_again, null)
            .setNegativeButton(R.string.btn_to_menu, null)
            .setNeutralButton(R.string.btn_share) { _, _ ->
                shareResult(won, secret, attempts, max)
            }
            .create()

        // ВАЖНО: переопределяем onClick кнопок, чтобы при некорректном имени
        // диалог НЕ закрывался (стандартный listener всегда скрывает диалог).
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (saveRecord()) {
                    dismiss()
                    // Перезапуск той же игры через общий NavHostFragment
                    (parentFragmentManager.findFragmentById(R.id.nav_host_fragment)
                        ?.childFragmentManager?.fragments?.firstOrNull() as? GameFragment)
                        ?.restart()
                } else {
                    etName.requestFocus() // имя некорректно — остаёмся, поле подсвечено
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                if (saveRecord()) {
                    dismiss()
                    findNavController().navigate(R.id.action_game_to_menu)
                } else {
                    etName.requestFocus()
                }
            }
        }
        return dialog
    }

    /**
     * Валидация поля имени игрока. Пустое поле допустимо (будет "Аноним"),
     * но если что-то введено — только разрешённые символы (латиница/кириллица).
     * @return true, если имя корректно
     */
    private fun validateName(
        etName: TextInputEditText,
        tilName: com.google.android.material.textfield.TextInputLayout
    ): Boolean {
        val name = etName.text?.toString()?.trim().orEmpty()
        return when {
            name.isEmpty() -> {
                tilName.error = null // пустое имя допустимо — будет "Аноним"
                true
            }
            !NAME_REGEX.matches(name) -> {
                tilName.error = getString(R.string.error_invalid_name)
                false
            }
            else -> {
                tilName.error = null
                true
            }
        }
    }

    /** Очистка имени от недопустимых символов перед сохранением */
    private fun sanitizeName(raw: String): String =
        raw.filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '\'' }
            .trim()
            .ifBlank { "Аноним" }

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
