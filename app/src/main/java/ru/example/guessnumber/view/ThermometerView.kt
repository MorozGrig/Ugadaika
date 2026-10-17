package ru.example.guessnumber.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import ru.example.guessnumber.FeedbackLevel
import ru.example.guessnumber.GameViewModel
import ru.example.guessnumber.R
import kotlin.math.abs

/**
 * Кастомный View "Термометр" — вертикальная шкала, визуально показывающая,
 * насколько близко игрок подошёл к загаданному числу.
 *
 * Возможна привязка к GameViewModel через [bindToViewModel] — тогда уровень и
 * цвет изменяются автоматически с плавной анимацией заполнения.
 *
 * Атрибуты XML (attrs.xml):
 *  - thermometerHeight — высота колбы термометра;
 *  - labelColor        — цвет подписей мин/макс значений;
 *  - showLabels        — показывать ли метки диапазона.
 */
class ThermometerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- Параметры из атрибутов XML ----------------------------------------
    private var bulbRadiusPx: Float = dp(14f)
    private var labelColor: Int = Color.DKGRAY
    private var showLabels: Boolean = true
    private var minLabel: String = ""
    private var maxLabel: String = ""

    init {
        if (attrs != null) {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.ThermometerView)
            bulbRadiusPx = ta.getDimension(
                R.styleable.ThermometerView_thermometerBulbRadius, dp(14f)
            )
            // labelColor может быть задан ссылкой на атрибут темы (?attr/...),
            // поэтому значение извлекается через TypedValue и реolves в контексте темы
            val tv = android.util.TypedValue()
            if (ta.getValue(R.styleable.ThermometerView_labelColor, tv)) {
                labelColor = when (tv.type) {
                    android.util.TypedValue.TYPE_INT_COLOR_ARGB8,
                    android.util.TypedValue.TYPE_INT_COLOR_RGB8,
                    android.util.TypedValue.TYPE_INT_COLOR_ARGB4,
                    android.util.TypedValue.TYPE_INT_COLOR_RGB4 -> tv.data
                    else -> {
                        context.theme.resolveAttribute(tv.resourceId, tv, true)
                        if (tv.data != 0) tv.data else Color.DKGRAY
                    }
                }
            } else {
                labelColor = Color.DKGRAY
            }
            showLabels = ta.getBoolean(R.styleable.ThermometerView_showLabels, true)
            ta.recycle()
        }
    }

    // --- Состояние ----------------------------------------------------------
    /** Текущий уровень заполнения 0..1 (анимируется) */
    private var fillLevel: Float = 0f
    private var targetFillLevel: Float = 0f
    private var currentColor: Int = NEUTRAL_COLOR
    private var targetColor: Int = NEUTRAL_COLOR

    /** Пропорция ширины, занимаемая трубкой (для корректного центрирования) */
    private val tubeWidthRatio = 0.28f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var animator: ValueAnimator? = null

    companion object {
        private val COLD_COLOR = Color.parseColor("#2196F3")   // синий
        private val WARM_COLOR = Color.parseColor("#FFC107")   // жёлтый
        private val HOT_COLOR = Color.parseColor("#F44336")    // красный
        private val NEUTRAL_COLOR = Color.parseColor("#9E9E9E") // серый
        private const val ANIM_DURATION = 500L
    }

    fun setRangeLabels(min: Int, max: Int) {
        minLabel = min.toString()
        maxLabel = max.toString()
        invalidate()
    }

    /**
     * Устанавливает уровень заполнения и цвет по результату подсказки.
     * Плавная анимация изменения уровня и цвета.
     */
    fun update(level: FeedbackLevel, fill: Float) {
        val newColor = when (level) {
            FeedbackLevel.COLD -> COLD_COLOR
            FeedbackLevel.WARM -> WARM_COLOR
            FeedbackLevel.HOT -> HOT_COLOR
            FeedbackLevel.NEUTRAL -> NEUTRAL_COLOR
        }
        animateTo(fill.coerceIn(0f, 1f), newColor)
    }

    /** Анимация перехода к новому уровню/цвету */
    private fun animateTo(newFill: Float, newColor: Int) {
        animator?.cancel()
        val startFill = fillLevel
        val startColor = currentColor
        targetFillLevel = newFill
        targetColor = newColor

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIM_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                fillLevel = startFill + (targetFillLevel - startFill) * t
                currentColor = blendColors(startColor, targetColor, t)
                invalidate()
            }
            start()
        }
    }

    /** Смешивание двух цветов по коэффициенту t */
    private fun blendColors(from: Int, to: Int, t: Float): Int {
        fun ch(mask: (Int) -> Int) =
            (mask(from) + ((mask(to) - mask(from)) * t)).toInt().coerceIn(0, 255)
        return Color.rgb(ch { Color.red(it) }, ch { Color.green(it) }, ch { Color.blue(it) })
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // Гарантируем минимальную высоту термометра, чтобы шкала была читаемой,
        // даже если родитель дал меньше места (wrap_content в карточке)
        val minHeight = dp(180f).toInt()
        val h = measuredHeight.coerceAtLeast(minHeight)
        setMeasuredDimension(measuredWidth, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // Геометрия: вертикальная трубка + колба внизу
        val labelSpace = if (showLabels) dp(26f) else 0f
        val drawableW = w - labelSpace          // ширина области самой "штанги"
        val tubeWidth = drawableW * tubeWidthRatio
        val cx = drawableW / 2f                 // центр трубки (с учётом отведённого места под метки)
        val tubeLeft = cx - tubeWidth / 2f
        val tubeRight = cx + tubeWidth / 2f
        val topPad = dp(6f)
        val bulbCy = h - bulbRadiusPx - dp(4f)
        val tubeBottom = bulbCy - bulbRadiusPx * 0.4f

        // 1. Контур трубки (фон)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#E0E0E0")
        rect.set(tubeLeft, topPad, tubeRight, tubeBottom)
        canvas.drawRoundRect(rect, tubeWidth / 2f, tubeWidth / 2f, paint)
        // Колба
        canvas.drawCircle(cx, bulbCy, bulbRadiusPx, paint)

        // 2. Заполнение ("ртуть") текущего уровня
        val fillHeight = (tubeBottom - topPad) * fillLevel
        val fillTop = tubeBottom - fillHeight
        paint.color = currentColor
        rect.set(tubeLeft + dp(3f), fillTop, tubeRight - dp(3f), tubeBottom)
        canvas.drawRoundRect(rect, (tubeWidth - dp(6f)) / 2f, (tubeWidth - dp(6f)) / 2f, paint)
        // Заполненная колба всегда в цвете уровня
        canvas.drawCircle(cx, bulbCy, bulbRadiusPx - dp(3f), paint)

        // 3. Обводка трубки
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2f)
        paint.color = Color.parseColor("#757575")
        rect.set(tubeLeft, topPad, tubeRight, tubeBottom)
        canvas.drawRoundRect(rect, tubeWidth / 2f, tubeWidth / 2f, paint)
        canvas.drawCircle(cx, bulbCy, bulbRadiusPx, paint)

        // 4. Метки делений (0%..100%)
        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#BDBDBD")
        for (i in 1..4) {
            val y = tubeBottom - (tubeBottom - topPad) * i / 5f
            canvas.drawLine(tubeRight + dp(2f), y, tubeRight + dp(8f), y, paint)
        }

        // 5. Подписи мин/макс значений диапазона
        if (showLabels && minLabel.isNotEmpty()) {
            paint.style = Paint.Style.FILL
            paint.color = labelColor
            paint.textSize = sp(12f)
            canvas.drawText(maxLabel, tubeRight + dp(12f), topPad + paint.textSize, paint)
            canvas.drawText(minLabel, tubeRight + dp(12f), tubeBottom, paint)
        }
    }

    // --- Привязка к ViewModel (MVVM) ----------------------------------------

    /**
     * Наблюдает за LiveData ViewModel: уровень и заполнение термометра
     * обновляются автоматически при каждой попытке игрока.
     * Используется combine(), чтобы термометр не рассинхронизировался,
     * если колбэк срабатывает раньше обновления другой LiveData.
     */
    fun bindToViewModel(viewModel: GameViewModel, lifecycleOwner: LifecycleOwner) {
        androidx.lifecycle.LiveData.combine(
            viewModel.level, viewModel.thermoFill,
            androidx.lifecycle.Observer { level, fill -> Pair(level, fill) }
        ).observe(lifecycleOwner, Observer { (level, fill) ->
            update(level, fill)
        })
        viewModel.maxValue.observe(lifecycleOwner, Observer { max ->
            setRangeLabels(1, max)
        })
    }

    // --- Утилиты ------------------------------------------------------------
    private fun dp(value: Float) = value * resources.displayMetrics.density
    private fun sp(value: Float) = value * resources.displayMetrics.scaledDensity
}
