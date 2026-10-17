package ru.example.guessnumber.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import ru.example.guessnumber.FeedbackLevel
import ru.example.guessnumber.GameViewModel
import ru.example.guessnumber.R
import ru.example.guessnumber.data.PreferencesManager
import ru.example.guessnumber.databinding.FragmentGameBinding

/**
 * GameFragment — игровой экран (экран 2).
 * Ввод числа, термометр, анимированный смайлик, счётчик попыток,
 * история чисел (RecyclerView), кнопка "Сдаться".
 */
class GameFragment : Fragment() {

    private var _binding: FragmentGameBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GameViewModel by activityViewModels()
    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.initPreferences(PreferencesManager(requireContext()))

        // Если игра ещё не начата (например, прямой переход) — стартуем с сохранённым диапазоном
        if (viewModel.maxValue.value == null) {
            viewModel.startNewGame(PreferencesManager(requireContext()).loadRange())
        }

        // --- История введённых чисел (RecyclerView) ------------------------
        historyAdapter = HistoryAdapter()
        binding.rvHistory.apply {
            // ВАЖНО: XML-атрибут android:orientation="horizontal" у RecyclerView
            // влияет на LayoutManager, создаваемый из XML. Явно задаём свой
            // layout-менеджер, чтобы избежать падения при первой раскладке.
            layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
            adapter = historyAdapter
            // Не перерисовываем анимациями по умолчанию — иначе анимации появления
            // конфликтуют с частым notifyDataSetChanged и вызывают вылет
            itemAnimator = null
        }

        // --- Кнопка "Проверить" ---------------------------------------------
        binding.btnCheck.setOnClickListener {
            viewModel.checkGuess(binding.etNumber.text.toString())
            // После проверки очищаем поле для следующего ввода
            if (viewModel.inputError.value == null) {
                binding.etNumber.setText("")
            }
        }

        // Enter на клавиатуре = проверка
        binding.etNumber.setOnEditorActionListener { _, _, _ ->
            binding.btnCheck.performClick()
            true
        }

        // --- Кнопка "Сдаться" -----------------------------------------------
        binding.btnGiveUp.setOnClickListener {
            val secret = viewModel.giveUp() ?: return@setOnClickListener
            showResultDialog(won = false, secret = secret)
        }

        // --- Наблюдатели LiveData (MVVM) ------------------------------------

        // Счётчик попыток
        viewModel.attempts.observe(viewLifecycleOwner) { att ->
            binding.tvAttempts.text = getString(R.string.attempts_label, att)
        }

        // История чисел с анимацией появления нового элемента
        viewModel.history.observe(viewLifecycleOwner) { list ->
            historyAdapter.submit(list)
            // ВАЖНО: smoothScrollToPosition вызывается только при непустом списке,
            // иначе RecyclerView бросает IndexOutOfBoundsException ("Inconsistent
            // thread state detected" / crash при входе на экран игры) и приложение вылетает.
            if (list.isNotEmpty()) {
                binding.rvHistory.smoothScrollToPosition(list.size - 1)
            }
        }

        // Обратная связь по подсказке: текст + fade-анимация + смайлик + вибрация
        viewModel.feedback.observe(viewLifecycleOwner) { result ->
            if (result == null) return@observe
            val text = when (result.level) {
                FeedbackLevel.COLD -> getString(R.string.hint_cold)
                FeedbackLevel.WARM -> getString(R.string.hint_warm)
                FeedbackLevel.HOT -> getString(R.string.hint_hot)
                FeedbackLevel.NEUTRAL -> ""
            }
            binding.tvHint.text = text
            // Fade-in/fade-out анимация подсказки
            binding.tvHint.startAnimation(
                AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in_out)
            )
            updateFace(result.level)
            vibrateForLevel(result.level)
            viewModel.onFeedbackShown()

            // Победа — открываем диалог результата
            if (result.isWin) showResultDialog(won = true, secret = result.number)
        }

        // Ошибки ввода — Snackbar
        viewModel.inputError.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                viewModel.onInputErrorShown()
            }
        }

        // Термометр привязывается к ViewModel напрямую (кастомный view)
        binding.thermometer.bindToViewModel(viewModel, viewLifecycleOwner)
    }

    /**
     * Обновление анимированного смайлика в зависимости от уровня подсказки.
     * Используется frame animation (AnimationDrawable) и пульсация при "горячо".
     */
    private fun updateFace(level: FeedbackLevel) {
        val iv = binding.ivFace
        when (level) {
            FeedbackLevel.NEUTRAL -> {
                iv.setImageResource(R.drawable.face_neutral_anim)
                stopFrameAnimation(iv)
                iv.clearAnimation()
                iv.imageTintList = null
            }
            FeedbackLevel.COLD -> {
                iv.setImageResource(R.drawable.face_cold_anim)
                playFrameAnimation(iv)
                iv.imageTintList = context?.getColorStateList(R.color.cold_tint)
                stopPulse(iv)
            }
            FeedbackLevel.WARM -> {
                iv.setImageResource(R.drawable.face_warm_anim)
                playFrameAnimation(iv)
                iv.imageTintList = context?.getColorStateList(R.color.warm_tint)
                stopPulse(iv)
            }
            FeedbackLevel.HOT -> {
                iv.setImageResource(R.drawable.face_hot_anim)
                playFrameAnimation(iv)
                iv.imageTintList = context?.getColorStateList(R.color.hot_tint)
                startPulse(iv) // Пульсация смайлика при "горячо"
            }
        }
    }

    /** Запуск покадровой анимации AnimationDrawable */
    private fun playFrameAnimation(iv: android.widget.ImageView) {
        (iv.drawable as? android.graphics.drawable.AnimationDrawable)?.start()
    }

    private fun stopFrameAnimation(iv: android.widget.ImageView) {
        (iv.drawable as? android.graphics.drawable.AnimationDrawable)?.stop()
    }

    /** Пульсация (масштаб туда-сюда) для состояния "горячо" */
    private var pulseX: ObjectAnimator? = null
    private var pulseY: ObjectAnimator? = null

    private fun startPulse(iv: View) {
        if (pulseX?.isRunning == true) return
        pulseX = ObjectAnimator.ofFloat(iv, View.SCALE_X, 1f, 1.25f).apply {
            duration = 350
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
        pulseY = ObjectAnimator.ofFloat(iv, View.SCALE_Y, 1f, 1.25f).apply {
            duration = 350
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
    }

    private fun stopPulse(iv: View) {
        pulseX?.cancel(); pulseY?.cancel()
        pulseX = null; pulseY = null
        iv.scaleX = 1f; iv.scaleY = 1f
    }

    /** Лёгкая вибрация как дополнительная подсказка (опционально) */
    private fun vibrateForLevel(level: FeedbackLevel) {
        val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            ?: return
        if (!vibrator.hasVibrator()) return
        val ms = when (level) {
            FeedbackLevel.COLD -> 30L
            FeedbackLevel.WARM -> 60L
            FeedbackLevel.HOT -> 120L
            FeedbackLevel.NEUTRAL -> 0L
        }
        if (ms == 0L) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(ms)
        }
    }

    /** Открывает диалог результата игры */
    private fun showResultDialog(won: Boolean, secret: Int) {
        val dialog = ResultDialog.newInstance(won, secret)
        dialog.show(parentFragmentManager, "result")
    }

    /** Навигация в меню (используется из ResultDialog) */
    fun goHome() {
        findNavController().navigate(R.id.action_game_to_menu)
    }

    /** Повторная игра в том же диапазоне */
    fun restart() {
        viewModel.maxValue.value?.let { viewModel.startNewGame(it) }
        binding.etNumber.setText("")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopPulse(binding.ivFace)
        _binding = null
    }
}
