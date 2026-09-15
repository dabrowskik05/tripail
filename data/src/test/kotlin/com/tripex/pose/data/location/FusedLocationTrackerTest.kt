package com.tripex.pose.data.location

import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.tripex.pose.domain.location.LocationConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FusedLocationTrackerTest {

    @Test
    fun `cancelling collection removes location updates`() = runTest {
        val client = mockk<FusedLocationProviderClient>(relaxed = true)
        val callbackSlot = slot<LocationCallback>()
        every {
            client.requestLocationUpdates(
                any<LocationRequest>(),
                capture(callbackSlot),
                any<Looper>(),
            )
        } returns mockk(relaxed = true)

        val tracker = FusedLocationTracker(clientProvider = FusedLocationClientProvider { client })
        val job = launch {
            tracker.locationUpdates(LocationConfig.DEFAULT).collect { }
        }
        testScheduler.runCurrent()
        job.cancel()
        job.join()

        verify(exactly = 1) { client.removeLocationUpdates(callbackSlot.captured) }
    }
}
