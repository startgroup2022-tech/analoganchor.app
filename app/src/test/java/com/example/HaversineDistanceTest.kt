package com.analoganchor.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.analoganchor.app.viewmodel.AnchorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HaversineDistanceTest {

    @Test
    fun testHaversineDistanceBetweenCoordinates() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = AnchorViewModel(app)
        
        // Coordinates for Busaiteen Al Hasan Mosque / User Position
        val userLat = 26.2285
        val userLng = 50.5860
        
        // Coordinates for Moza Mosque in Busaiteen
        val mosqueLat = 26.2250
        val mosqueLng = 50.5790
        
        // Calculate geofence distance
        val distance = viewModel.calculateDistance(userLat, userLng, mosqueLat, mosqueLng)
        
        // Verify it yields ~799 meters (within 5 meters tolerance)
        assertEquals(799.37, distance, 5.0)
    }
}
