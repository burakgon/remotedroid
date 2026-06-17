package com.remotedroid.input

/**
 * Saf imleç matematiği. Göreli delta'ları hassasiyetle ölçekler ve imleci ekran
 * sınırlarına kıstırır. Android/DOM bağımlılığı yok → JVM'de birim test edilebilir.
 */
class Cursor(
    var x: Float,
    var y: Float,
    val width: Int,
    val height: Int,
    var sensitivity: Float = 1.5f,
) {
    fun move(dx: Float, dy: Float) {
        x = (x + dx * sensitivity).coerceIn(0f, (width - 1).toFloat())
        y = (y + dy * sensitivity).coerceIn(0f, (height - 1).toFloat())
    }

    fun center() {
        x = width / 2f
        y = height / 2f
    }
}
