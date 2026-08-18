package com.youneko.rate.domain.usecase

enum class RatingScale(val max: Int, val label: String) {
    FIVE_STARS(5, "5 sao"),
    TEN_POINT(10, "10 điểm"),
    HUNDRED_POINT(100, "100 điểm");

    fun fromStars(stars: Double?): Double? = stars?.times(max.toDouble() / FIVE_STARS.max)
    fun toStars(value: Double?): Double? = value?.times(FIVE_STARS.max.toDouble() / max)

    companion object {
        fun parse(raw: String): RatingScale = entries.firstOrNull { it.name == raw } ?: FIVE_STARS
    }
}
