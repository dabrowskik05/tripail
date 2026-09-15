package com.tripex.pose.data.repository

import com.tripex.pose.data.network.NominatimApi
import com.tripex.pose.data.network.NominatimPlaceDto
import com.tripex.pose.domain.geo.Place
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GeocodingRepositoryImplTest {

    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `empty query fails without calling api`() = runTest(dispatcher) {
        val api = CountingApi()
        val repo = GeocodingRepositoryImpl(api, dispatcher)

        val result = repo.search("   ")

        assertTrue(result.isFailure)
        assertEquals(0, api.calls)
    }

    @Test
    fun `cache hit skips second network call`() = runTest(dispatcher) {
        val api = CountingApi(
            listOf(
                NominatimPlaceDto(
                    lat = "52.23",
                    lon = "21.01",
                    displayName = "Warszawa",
                ),
            ),
        )
        val repo = GeocodingRepositoryImpl(api, dispatcher)

        val first = repo.search("Warszawa").getOrThrow()
        val second = repo.search("warszawa").getOrThrow()

        assertEquals(Place("Warszawa", 52.23, 21.01), first)
        assertEquals(first, second)
        assertEquals(1, api.calls)
    }

    @Test
    fun `second distinct query hits network again`() = runTest(dispatcher) {
        val api = CountingApi(
            resultsByCall = listOf(
                listOf(NominatimPlaceDto("52.23", "21.01", "Warszawa")),
                listOf(NominatimPlaceDto("50.06", "19.94", "Kraków")),
            ),
        )
        val repo = GeocodingRepositoryImpl(api, dispatcher)

        val warsaw = repo.search("Warszawa").getOrThrow()
        val krakow = repo.search("Kraków").getOrThrow()

        assertEquals(2, api.calls)
        assertEquals("Warszawa", warsaw.displayName)
        assertEquals("Kraków", krakow.displayName)
    }

    private class CountingApi(
        private val always: List<NominatimPlaceDto> = emptyList(),
        private val resultsByCall: List<List<NominatimPlaceDto>> = emptyList(),
    ) : NominatimApi {
        var calls: Int = 0
            private set

        override suspend fun search(query: String, format: String, limit: Int): List<NominatimPlaceDto> {
            val index = calls
            calls += 1
            return if (resultsByCall.isNotEmpty()) {
                resultsByCall.getOrElse(index) { emptyList() }
            } else {
                always
            }
        }
    }
}
