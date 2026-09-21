package com.tripex.pose.data.tiles

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.tripex.pose.data.BuildConfig
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PmTilesBootstrapperTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private lateinit var bootstrapper: PmTilesBootstrapper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Unique file per test method would be ideal; one store instance for the class avoids
        // DataStore's "same file multiple instances" crash.
        val prefsName = "boundaries_prefs_${System.nanoTime()}"
        store = PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(prefsName) },
        )
        bootstrapper = PmTilesBootstrapper(context, store, dispatcher)
        bootstrapper.destinationFile().let { dest ->
            if (dest.exists()) dest.delete()
            File(dest.parentFile, "${PmTilesBootstrapper.FILE_NAME}.tmp").delete()
        }
    }

    @Test
    fun `copies on first start and skips when version matches`() = runTest(dispatcher) {
        val dest = bootstrapper.destinationFile()

        val first = bootstrapper.ensureReady()
        assertTrue(first.isSuccess)
        assertTrue(dest.exists())
        assertTrue(dest.length() > 0)
        val sizeAfterFirst = dest.length()
        val modifiedAfterFirst = dest.lastModified()

        Thread.sleep(20)
        val second = bootstrapper.ensureReady()
        assertTrue(second.isSuccess)
        assertEquals(sizeAfterFirst, dest.length())
        assertEquals(modifiedAfterFirst, dest.lastModified())
    }

    @Test
    fun `copies again after version bump`() = runTest(dispatcher) {
        val dest = bootstrapper.destinationFile()
        assertTrue(bootstrapper.ensureReady().isSuccess)
        assertTrue(dest.exists())

        store.edit { it[PmTilesBootstrapper.VERSION_KEY] = BuildConfig.BOUNDARIES_VERSION - 1 }
        val before = dest.lastModified()
        Thread.sleep(20)
        assertTrue(bootstrapper.ensureReady().isSuccess)
        assertTrue(dest.lastModified() >= before)
        assertTrue(dest.length() > 0)
    }

    @Test
    fun `interrupted copy does not leave destination file`() = runTest(dispatcher) {
        val dest = bootstrapper.destinationFile()
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, "${PmTilesBootstrapper.FILE_NAME}.tmp")
        tmp.writeText("partial")
        if (dest.exists()) dest.delete()

        assertTrue(bootstrapper.ensureReady().isSuccess)
        assertTrue(dest.exists())
        assertFalse(tmp.exists())
    }
}
