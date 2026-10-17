package ru.example.guessnumber.data

/**
 * Data class для записи таблицы рекордов.
 * Сохраняется в SharedPreferences в сериализованном виде (разделители "|" и ";").
 *
 * @property playerName имя игрока
 * @property attempts   количество попыток (меньше = лучше)
 * @property range      верхняя граница диапазона (50, 100 или 200)
 */
data class GameRecord(
    val playerName: String,
    val attempts: Int,
    val range: Int
) {
    /** Сериализация одной записи в строку для SharedPreferences */
    fun serialize(): String = "$playerName$FIELD_SEPARATOR$attempts$FIELD_SEPARATOR$range"

    companion object {
        const val FIELD_SEPARATOR = "|"   // разделитель полей внутри записи
        const val RECORD_SEPARATOR = ";"  // разделитель записей друг от друга

        /** Десериализация строки обратно в GameRecord; null при повреждённых данных */
        fun deserialize(raw: String): GameRecord? {
            val parts = raw.split(FIELD_SEPARATOR)
            if (parts.size != 3) return null
            val attempts = parts[1].toIntOrNull() ?: return null
            val range = parts[2].toIntOrNull() ?: return null
            return GameRecord(parts[0], attempts, range)
        }
    }
}
