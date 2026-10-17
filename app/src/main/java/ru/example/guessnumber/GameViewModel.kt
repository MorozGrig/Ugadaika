package ru.example.guessnumber

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import ru.example.guessnumber.data.GameRecord
import ru.example.guessnumber.data.PreferencesManager
import kotlin.random.Random

/**
 * Уровень "температуры" — подсказка близости к загаданному числу.
 */
enum class FeedbackLevel { NEUTRAL, COLD, WARM, HOT }

/**
 * Результат одной попытки проверки числа.
 */
data class GuessResult(
    val number: Int,          // введённое игроком число
    val level: FeedbackLevel, // подсказка (холодно/тепло/горячо)
    val distancePercent: Float, // расстояние до загаданного числа в % от диапазона (0..1)
    val isWin: Boolean        // угадал ли игрок
)

/**
 * GameViewModel — управление состоянием игры (архитектура MVVM).
 * Все данные хранятся в LiveData, поэтому при повороте экрана
 * ViewModel не уничтожается и состояние сохраняется автоматически.
 */
class GameViewModel : ViewModel() {

    private lateinit var prefs: PreferencesManager

    /** Инициализация менеджера предпочтений (вызывается из фрагментов) */
    fun initPreferences(pm: PreferencesManager) {
        if (!this::prefs.isInitialized) prefs = pm
    }

    // --- Состояние игры -----------------------------------------------------

    /** Верхняя граница диапазона (нижняя всегда 1) */
    private val _maxValue = MutableLiveData<Int>()
    val maxValue: LiveData<Int> get() = _maxValue

    /** Загаданное компьютером число */
    private val _secretNumber = MutableLiveData<Int>()
    val secretNumber: LiveData<Int> get() = _secretNumber

    /** Счётчик попыток */
    private val _attempts = MutableLiveData<Int>()
    val attempts: LiveData<Int> get() = _attempts

    /** История введённых чисел */
    private val _history = MutableLiveData<List<Int>>()
    val history: LiveData<List<Int>> get() = _history

    /** Последняя подсказка (для текстового вывода и Snackbar-ошибок — null = нет события) */
    private val _feedback = MutableLiveData<GuessResult?>()
    val feedback: LiveData<GuessResult?> get() = _feedback

    /** Текущий уровень температуры для термометра и смайлика */
    private val _level = MutableLiveData<FeedbackLevel>(FeedbackLevel.NEUTRAL)
    val level: LiveData<FeedbackLevel> get() = _level

    /** Относительное расстояние (0..1) для заполнения шкалы термометра */
    private val _thermoFill = MutableLiveData<Float>(0f)
    val thermoFill: LiveData<Float> get() = _thermoFill

    /** Флаг завершённости игры (победа или сдались) */
    private val _gameFinished = MutableLiveData<Boolean>(false)
    val gameFinished: LiveData<Boolean> get() = _gameFinished

    /** Был ли установлен новый рекорд после последней победы */
    private val _newRecordSet = MutableLiveData<Boolean>(false)
    val newRecordSet: LiveData<Boolean> get() = _newRecordSet

    /** Сообщение об ошибке ввода (одноразовое событие через LiveData) */
    private val _inputError = MutableLiveData<String?>()
    val inputError: LiveData<String?> get() = _inputError

    // --- Методы управления --------------------------------------------------

    /**
     * Начинает новую игру с выбранным диапазоном [max].
     * Генерирует случайное число от 1 до max, сбрасывает счётчики и историю.
     */
    fun startNewGame(max: Int) {
        _maxValue.value = max
        _secretNumber.value = Random.nextInt(1, max + 1)
        _attempts.value = 0
        _history.value = emptyList()
        _feedback.value = null
        _level.value = FeedbackLevel.NEUTRAL
        _thermoFill.value = 0f
        _gameFinished.value = false
        _newRecordSet.value = false
        prefs.saveRange(max)
    }

    /**
     * Проверяет введённую строку [input]:
     * - пустое поле / не число / вне диапазона -> ошибка в _inputError;
     * - иначе вычисляет расстояние и уровень подсказки.
     *
     * Правила подсказки (distance — |секрет - ввод|):
     *   Горячо: distance <= range * 0.1
     *   Тепло:  range * 0.1 < distance <= range * 0.3
     *   Холодно: distance > range * 0.3
     */
    fun checkGuess(input: String) {
        val max = _maxValue.value ?: return
        val secret = _secretNumber.value ?: return

        // Обработка ошибок ввода
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            _inputError.value = "Введите число!"
            return
        }
        val guess = trimmed.toIntOrNull()
        if (guess == null) {
            _inputError.value = "Это не число! Введите целое число."
            return
        }
        if (guess < 1 || guess > max) {
            _inputError.value = "Число должно быть в диапазоне 1–$max!"
            return
        }
        if (_gameFinished.value == true) {
            _inputError.value = "Игра уже завершена. Начните новую!"
            return
        }

        // Подсчёт расстояния и уровня
        val distance = kotlin.math.abs(secret - guess)
        val range = max.toFloat()
        val percent = distance / range // 0..1

        val level = when {
            distance == 0 -> FeedbackLevel.HOT
            percent > 0.3f -> FeedbackLevel.COLD
            percent > 0.1f -> FeedbackLevel.WARM
            else -> FeedbackLevel.HOT
        }

        val isWin = distance == 0
        val result = GuessResult(guess, level, percent, isWin)

        // Обновление состояния
        _attempts.value = (_attempts.value ?: 0) + 1
        _history.value = (_history.value ?: emptyList()) + guess
        _feedback.value = result
        _level.value = level
        // Чем ближе к секрету — тем выше "температура": заполнение инвертировано
        _thermoFill.value = 1f - percent.coerceIn(0f, 1f)

        if (isWin) {
            _gameFinished.value = true
        }
        // Сброс одноразового сообщения об ошибке
        _inputError.value = null
    }

    /**
     * Игрок сдаётся: игра завершается, загаданное число раскрывается.
     * @return загаданное число
     */
    fun giveUp(): Int? {
        _gameFinished.value = true
        return _secretNumber.value
    }

    /**
     * Сохраняет результат победы как рекорд (если он попадает в топ-10).
     * @return true, если это был новый рекорд
     */
    fun saveResultAsRecord(playerName: String): Boolean {
        val att = _attempts.value ?: return false
        val max = _maxValue.value ?: return false
        val record = GameRecord(playerName.ifBlank { "Аноним" }, att, max)
        val isNew = if (this::prefs.isInitialized) prefs.isNewRecord(att) else true
        if (isNew && this::prefs.isInitialized) {
            prefs.addRecord(record)
        }
        _newRecordSet.value = isNew
        return isNew
    }

    /** Проверка, попадёт ли текущий результат в таблицу рекордов */
    fun currentResultIsRecord(): Boolean {
        val att = _attempts.value ?: return false
        return if (this::prefs.isInitialized) prefs.isNewRecord(att) else true
    }

    /** Прочитать список рекордов (для таблицы) */
    fun getRecords(): List<GameRecord> =
        if (this::prefs.isInitialized) prefs.getRecords() else emptyList()

    /** Consumed флаг ошибки ввода (чтобы Snackbar не показывался повторно) */
    fun onInputErrorShown() {
        _inputError.value = null
    }

    /** Consumed флаг обратной связи */
    fun onFeedbackShown() {
        _feedback.value = null
    }
}
