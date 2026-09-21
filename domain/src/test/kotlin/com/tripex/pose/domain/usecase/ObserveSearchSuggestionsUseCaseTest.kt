package com.tripex.pose.domain.usecase

import app.cash.turbine.test
import com.tripex.pose.domain.geo.Place
import com.tripex.pose.domain.repository.GeocodingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveSearchSuggestionsUseCaseTest {

    private fun place(name: String) = Place(displayName = name, latitude = 0.0, longitude = 0.0, id = name)

    @Test
    fun `typing a word costs one request, not one per keystroke`() = runTest {
        val repo = RecordingGeocoder()
        val queries = MutableStateFlow("")
        val useCase = ObserveSearchSuggestionsUseCase(repo)

        useCase(queries).test {
            assertEquals(emptyList<Place>(), awaitItem().getOrThrow())

            // Eight keystrokes, well inside the debounce window.
            for (prefix in listOf("W", "Wa", "War", "Wars", "Warsz", "Warsza", "Warszaw", "Warszawa")) {
                queries.value = prefix
            }
            advanceTimeBy(ObserveSearchSuggestionsUseCase.DEBOUNCE_MS + 50)

            assertEquals(listOf(place("Warszawa")), awaitItem().getOrThrow())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals("only the settled query may be looked up", listOf("Warszawa"), repo.queries)
    }

    @Test
    fun `a query shorter than the minimum never reaches the network`() = runTest {
        val repo = RecordingGeocoder()
        val queries = MutableStateFlow("W")
        val useCase = ObserveSearchSuggestionsUseCase(repo)

        useCase(queries).test {
            advanceTimeBy(ObserveSearchSuggestionsUseCase.DEBOUNCE_MS + 50)
            assertEquals(emptyList<Place>(), awaitItem().getOrThrow())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(emptyList<String>(), repo.queries)
    }

    @Test
    fun `retyping the same query does not repeat the lookup`() = runTest {
        val repo = RecordingGeocoder()
        val queries = MutableStateFlow("Kraków")
        val useCase = ObserveSearchSuggestionsUseCase(repo)

        useCase(queries).test {
            advanceTimeBy(ObserveSearchSuggestionsUseCase.DEBOUNCE_MS + 50)
            awaitItem()
            // Same text, differently spaced — the same intention.
            queries.value = "  Kraków  "
            advanceTimeBy(ObserveSearchSuggestionsUseCase.DEBOUNCE_MS + 50)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf("Kraków"), repo.queries)
    }

    private class RecordingGeocoder : GeocodingRepository {
        val queries = mutableListOf<String>()

        override suspend fun suggest(query: String): Result<List<Place>> {
            queries += query
            return Result.success(
                listOf(Place(displayName = query, latitude = 0.0, longitude = 0.0, id = query)),
            )
        }

        override suspend fun reverseGeocode(lat: Double, lng: Double): Result<Place?> =
            Result.success(null)

        override suspend fun search(query: String): Result<Place> =
            suggest(query).map { it.first() }
    }
}
