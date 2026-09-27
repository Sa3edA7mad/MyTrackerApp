package com.example.mytrackerapp.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytrackerapp.domain.UnitPrefs
import com.example.mytrackerapp.domain.WeightUnit
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** MEA-xx in docs/FEATURES.md: body measurements. */
@RunWith(AndroidJUnit4::class)
class MeasurementsE2eTest : E2eTest() {

    private fun openMeasurements() {
        tapDesc("Settings")
        tapText("Body measurements")
        see("DERIVED")
    }

    @Test
    fun mea01_startsEmptyWithTheSevenDefaultMetrics() {
        openMeasurements()
        see("BMI")
        see("LEAN MASS")
        see("WAIST:HIP")
        see("WAIST:HEIGHT")
        see("Log weight, height, waist, hips and body fat to fill these in.")
        listOf(
            "Body weight", "Body fat", "Height", "Chest", "Upper arm (R)", "Waist", "Hips",
            "Thigh (R)", "Resting heart rate"
        ).forEach { see(it) }
        dontSee("Neck")
        see("Edit metrics")
    }

    @Test
    fun mea02_loggingASessionFillsCardsAndBmi() {
        openMeasurements()
        tapDesc("Log measurements")
        see("Log a measuring session")
        seeFieldValue("Date (yyyy-mm-dd)", LocalDate.now().toString())
        typeInto("Body weight (kg)", "80")
        typeInto("Height (cm)", "180")
        field("Resting heart rate") // unitless: no "()" suffix
        tapButton("Save")
        see("80.0 kg")
        see("180 cm")
        see("24.7")
    }

    @Test
    fun mea03_cancelLogsNothing() {
        openMeasurements()
        tapDesc("Log measurements")
        typeInto("Body weight (kg)", "80")
        tapButton("Cancel")
        dontSee("80.0 kg")
    }

    @Test
    fun mea04_editMetricsTogglesAMetricOntoTheList() {
        openMeasurements()
        tapText("Edit metrics")
        see("Edit metrics")
        tapText("Neck")
        tapDesc("Back")
        see("Neck")
    }

    @Test
    fun mea05_aCustomMetricCanBeAddedAndArchived() {
        openMeasurements()
        tapText("Edit metrics")
        tapButton("+ Add custom metric")
        see("New metric")
        typeInto("Name", "Grip strength")
        tapButton("COUNT")
        tapButton("Add metric")
        see("Grip strength")
        tapButton("Archive")
        see("Archive Grip strength?")
        tapInDialog("ARCHIVE")
        see("ARCHIVED")
        tapDesc("Back")
        dontSee("Grip strength")
    }
}

/** MEA-06..07: reading history, edit and the one hard delete. */
@RunWith(AndroidJUnit4::class)
class MeasurementHistoryE2eTest : E2eTest() {

    override suspend fun arrange() {
        val day = { d: Long ->
            LocalDate.now().minusDays(d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        container.measurements.log("bodyweight", 82.0, day(7))
        container.measurements.log("bodyweight", 80.0, day(0))
    }

    @Test
    fun mea06_cardShowsLatestAndDeltas() {
        tapDesc("Settings")
        tapText("Body measurements")
        see("80.0 kg")
        see("vs last: -2.0  ·  vs first: -2.0")
    }

    @Test
    fun mea07_historyEditsAndDeletesAReading() {
        tapDesc("Settings")
        tapText("Body measurements")
        tapText("Body weight")
        see("80.0 kg")
        see("82.0 kg")
        tapDesc("Edit reading")
        see("Edit reading")
        typeInto("Value (kg)", "79.5")
        tapText("SAVE")
        see("79.5 kg")
        tapDesc("Delete reading")
        see("Delete this reading?")
        tapText("DELETE")
        dontSee("79.5 kg")
        see("82.0 kg")
    }
}

/** MEA-08..09: display units and derived stats (regressions: lean mass was always "kg",
 *  waist-to-height was never shown). */
@RunWith(AndroidJUnit4::class)
class MeasurementUnitsE2eTest : E2eTest() {

    override suspend fun arrange() {
        val now = System.currentTimeMillis()
        with(container.measurements) {
            log("bodyweight", 80.0, now)
            log("body_fat", 20.0, now)
            log("height", 180.0, now)
            log("waist", 90.0, now)
            log("hips", 100.0, now)
        }
        applyRules(units = UnitPrefs(weight = WeightUnit.LB))
    }

    @Test
    fun mea08_poundsApplyToCardsAndLeanMass() {
        tapDesc("Settings")
        tapText("Body measurements")
        see("176.4 lb")
        see("141.1 lb")
    }

    @Test
    fun mea09_derivedStatsAreAllFilled() {
        tapDesc("Settings")
        tapText("Body measurements")
        see("24.7") // BMI
        see("0.90") // waist:hip
        see("0.50") // waist:height
        dontSee("Log weight, height, waist, hips and body fat to fill these in.")
    }
}
