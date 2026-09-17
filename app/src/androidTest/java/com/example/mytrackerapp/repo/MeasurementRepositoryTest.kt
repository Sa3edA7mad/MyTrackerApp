package com.example.mytrackerapp.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mytrackerapp.data.db.AppDatabase
import com.example.mytrackerapp.data.db.SeedCallback
import com.example.mytrackerapp.domain.LengthUnit
import com.example.mytrackerapp.domain.UnitPrefs
import com.example.mytrackerapp.domain.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeasurementRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: MeasurementRepository
    private lateinit var units: MutableStateFlow<UnitPrefs>

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .addCallback(SeedCallback)
            .build()
        units = MutableStateFlow(UnitPrefs())
        repo = MeasurementRepository(db.metricDao(), db.measurementDao()) { units }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun loggingInPoundsStoresKilograms() = runTest {
        units.value = UnitPrefs(weight = WeightUnit.LB)
        // 220.462262 lb == 100 kg.
        val result = repo.log("bodyweight", 220.462262, System.currentTimeMillis())
        assertTrue(result.isSuccess)

        val stored = repo.observeHistory("bodyweight").first().first()
        assertEquals(100.0, stored.value, 1e-3)
    }

    @Test
    fun switchingTheUnitDoesNotChangeTheStoredValue() = runTest {
        repo.log("bodyweight", 80.0, System.currentTimeMillis())
        val storedKg = repo.observeHistory("bodyweight").first().first().value

        units.value = UnitPrefs(weight = WeightUnit.LB)
        val stillStoredKg = repo.observeHistory("bodyweight").first().first().value

        assertEquals(storedKg, stillStoredKg, 1e-9)
    }

    @Test
    fun deletingAnEntryRemovesIt() = runTest {
        repo.log("bodyweight", 80.0, System.currentTimeMillis())
        val id = repo.observeHistory("bodyweight").first().first().id

        repo.deleteEntry(id)
        assertTrue(repo.observeHistory("bodyweight").first().isEmpty())
    }

    @Test
    fun disablingAMetricLeavesTrendsButKeepsItsRows() = runTest {
        repo.log("neck", 38.0, System.currentTimeMillis())
        repo.setMetricEnabled("neck", true)
        assertTrue(repo.observeTrends().first().any { it.metricId == "neck" })

        repo.setMetricEnabled("neck", false)
        assertTrue(repo.observeTrends().first().none { it.metricId == "neck" })
        assertEquals(1, repo.observeHistory("neck").first().size)
    }

    @Test
    fun derivedBmiAppearsOnlyOnceHeightAndWeightExist() = runTest {
        var derived = repo.observeDerived().first()
        assertNull(derived.bmi)

        repo.log("bodyweight", 80.0, System.currentTimeMillis())
        derived = repo.observeDerived().first()
        assertNull(derived.bmi)

        repo.log("height", 180.0, System.currentTimeMillis())
        derived = repo.observeDerived().first()
        assertEquals(24.7, derived.bmi!!, 0.05)
    }

    @Test
    fun loggingAnInvalidPercentFails() = runTest {
        val result = repo.log("body_fat", 150.0, System.currentTimeMillis())
        assertTrue(result.isFailure)
    }

    @Test
    fun lengthUnitConvertsOnTheWayIn() = runTest {
        units.value = UnitPrefs(length = LengthUnit.IN)
        repo.log("waist", 10.0, System.currentTimeMillis()) // 10 in
        val stored = repo.observeHistory("waist").first().first().value
        assertEquals(25.4, stored, 1e-6)
    }
}
