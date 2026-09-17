package com.example.mytrackerapp.domain

/**
 * Display units only. Load is always stored in kilograms and length in centimetres
 * (see [ProgramRules] and the `completions`/`measurements` tables) — these enums and the
 * conversion functions below exist purely so the UI can show a number in the unit the user
 * prefers without ever touching what's persisted.
 */
enum class WeightUnit(val label: String) { KG("kg"), LB("lb") }
enum class LengthUnit(val label: String) { CM("cm"), IN("in") }

data class UnitPrefs(
    val weight: WeightUnit = WeightUnit.KG,
    val length: LengthUnit = LengthUnit.CM
)

object Units {
    private const val LB_PER_KG = 2.20462262
    private const val CM_PER_IN = 2.54

    fun kgToDisplay(kg: Double, unit: WeightUnit): Double =
        if (unit == WeightUnit.KG) kg else kg * LB_PER_KG

    fun displayToKg(value: Double, unit: WeightUnit): Double =
        if (unit == WeightUnit.KG) value else value / LB_PER_KG

    fun cmToDisplay(cm: Double, unit: LengthUnit): Double =
        if (unit == LengthUnit.CM) cm else cm / CM_PER_IN

    fun displayToCm(value: Double, unit: LengthUnit): Double =
        if (unit == LengthUnit.CM) value else value * CM_PER_IN

    fun format(value: Double, decimals: Int): String = "%.${decimals}f".format(value)
}
