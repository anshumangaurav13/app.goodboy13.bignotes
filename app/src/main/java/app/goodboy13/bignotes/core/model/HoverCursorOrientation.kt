package app.goodboy13.bignotes.core.model

enum class HoverCursorOrientation(
    val degrees: Float,
    val label: String,
    val arrow: String
) {
    UP_RIGHT(45f, "Up-Right", "↗"),
    UP_LEFT(-45f, "Up-Left", "↖"),
    DOWN_LEFT(-135f, "Down-Left", "↙"),
    DOWN_RIGHT(135f, "Down-Right", "↘");

    companion object {
        fun fromString(value: String?): HoverCursorOrientation {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UP_RIGHT
        }
    }
}
