package ru.example.guessnumber.data

import android.content.Context

/**
 * Менеджер работы с SharedPreferences.
 * Хранит таблицу рекордов (топ-10) и последний выбор диапазона игрока.
 */
class PreferencesManager(context: Context) {

    // Используем контекст приложения, чтобы не утекать с Activity/Fragment
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Возвращает отсортированный по попыток (по возрастанию) топ-10 рекордов.
     */
    fun getRecords(): List<GameRecord> {
        val raw = prefs.getString(KEY_RECORDS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(GameRecord.RECORD_SEPARATOR)
            .mapNotNull { GameRecord.deserialize(it) }
            .sortedBy { it.attempts }
            .take(MAX_RECORDS)
    }

    /**
     * Проверяет, является ли результат с данным числом попыток новым рекордом.
     * Новый рекорд, если записей меньше 10 ИЛИ попыток меньше худшего результата.
     */
    fun isNewRecord(attempts: Int): Boolean {
        val records = getRecords()
        return records.size < MAX_RECORDS || attempts < records.last().attempts
    }

    /**
     * Добавляет рекорд в таблицу: сортирует по попыток, оставляет топ-10, сохраняет.
     */
    fun addRecord(record: GameRecord) {
        val updated = (getRecords() + record)
            .sortedBy { it.attempts }
            .take(MAX_RECORDS)
        val serialized = updated.joinToString(GameRecord.RECORD_SEPARATOR) { it.serialize() }
        prefs.edit().putString(KEY_RECORDS, serialized).apply()
    }

    /** Сохраняет выбранный диапазон (верхняя граница) между запусками приложения */
    fun saveRange(maxValue: Int) {
        prefs.edit().putInt(KEY_RANGE, maxValue).apply()
    }

    /** Читает ранее выбранный диапазон (по умолчанию 100) */
    fun loadRange(): Int = prefs.getInt(KEY_RANGE, DEFAULT_RANGE)

    companion object {
        private const val PREFS_NAME = "guess_number_prefs"
        private const val KEY_RECORDS = "records"
        private const val KEY_RANGE = "selected_range"
        private const val DEFAULT_RANGE = 100
        const val MAX_RECORDS = 10 // Топ-10 лучших результатов
    }
}
