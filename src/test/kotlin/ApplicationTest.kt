package com.example

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.*

class ApplicationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testRootEndpoint() = testApplication {
        application { module() }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("🎬 Welcome to Movie Library API!", response.bodyAsText())
    }

    @Test
    fun testCreateGetUpdateDeleteMovie() = testApplication {
        application { module() }

        // Create movie
        val movieReq = MovieRequest("Inception", "Nolan", 2010)
        val createRes = client.post("/movies") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(MovieRequest.serializer(), movieReq))
        }
        assertEquals(HttpStatusCode.Created, createRes.status)
        val movie = json.decodeFromString<Movie>(createRes.bodyAsText())

        // Get movie by ID
        val getRes = client.get("/movies/${movie.id}")
        assertEquals(HttpStatusCode.OK, getRes.status)
        val fetchedMovie = json.decodeFromString<Movie>(getRes.bodyAsText())
        assertEquals("Inception", fetchedMovie.title)

        // Update movie
        val updatedReq = MovieRequest("Inception Updated", "Christopher Nolan", 2011)
        val updateRes = client.put("/movies/${movie.id}") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(MovieRequest.serializer(), updatedReq))
        }
        assertEquals(HttpStatusCode.OK, updateRes.status)
        val updatedMovie = json.decodeFromString<Movie>(updateRes.bodyAsText())
        assertEquals("Inception Updated", updatedMovie.title)
        assertEquals(2011, updatedMovie.releaseYear)

        // Delete movie
        val deleteRes = client.delete("/movies/${movie.id}")
        assertEquals(HttpStatusCode.NoContent, deleteRes.status)

        // Confirm delete
        val getAfterDelete = client.get("/movies/${movie.id}")
        assertEquals(HttpStatusCode.NotFound, getAfterDelete.status)
    }

    @Test
    fun testAddReviewAndGetAverageRating() = testApplication {
        application { module() }

        // Create movie first
        val movieReq = MovieRequest("Interstellar", "Nolan", 2014)
        val movieRes = client.post("/movies") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(MovieRequest.serializer(), movieReq))
        }
        val movie = json.decodeFromString<Movie>(movieRes.bodyAsText())

        // Add review (valid)
        val reviewReq = ReviewRequest("Alice", 5, "Great movie!")
        val reviewRes = client.post("/movies/${movie.id}/reviews") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ReviewRequest.serializer(), reviewReq))
        }
        assertEquals(HttpStatusCode.Created, reviewRes.status)
        val createdReview = json.decodeFromString<Review>(reviewRes.bodyAsText())
        assertEquals("Alice", createdReview.reviewerName)

        // Add review (invalid rating)
        val badReviewReq = ReviewRequest("Bob", 6, "Too good!")
        val badReviewRes = client.post("/movies/${movie.id}/reviews") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ReviewRequest.serializer(), badReviewReq))
        }
        assertEquals(HttpStatusCode.BadRequest, badReviewRes.status)

        // Get reviews for movie
        val getReviewsRes = client.get("/movies/${movie.id}/reviews")
        assertEquals(HttpStatusCode.OK, getReviewsRes.status)
        val reviews = json.decodeFromString<List<Review>>(getReviewsRes.bodyAsText())
        assertTrue(reviews.any { it.reviewerName == "Alice" })

        // Get average rating
        val avgRes = client.get("/movies/${movie.id}/average-rating")
        assertEquals(HttpStatusCode.OK, avgRes.status)
        val avgMap = json.decodeFromString<Map<String, Double>>(avgRes.bodyAsText())
        assertEquals(5.0, avgMap["averageRating"])
    }

    @Test
    fun testSearchMovies() = testApplication {
        application { module() }

        // Add movies
        val movies = listOf(
            MovieRequest("The Matrix", "Wachowski", 1999),
            MovieRequest("Dunkirk", "Nolan", 2017)
        )
        for (m in movies) {
            client.post("/movies") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(MovieRequest.serializer(), m))
            }
        }

        // Search by title
        val searchRes = client.get("/movies/search?q=matrix")
        assertEquals(HttpStatusCode.OK, searchRes.status)
        val found = json.decodeFromString<List<Movie>>(searchRes.bodyAsText())
        assertTrue(found.any { it.title.contains("Matrix", ignoreCase = true) })

        // Search by director
        val searchRes2 = client.get("/movies/search?q=nolan")
        assertEquals(HttpStatusCode.OK, searchRes2.status)
        val found2 = json.decodeFromString<List<Movie>>(searchRes2.bodyAsText())
        assertTrue(found2.any { it.director.contains("Nolan", ignoreCase = true) })

        // Search with missing query param
        val badSearch = client.get("/movies/search")
        assertEquals(HttpStatusCode.BadRequest, badSearch.status)
    }

    @Test
    fun testDeleteReview() = testApplication {
        application { module() }

        // Create movie
        val movieReq = MovieRequest("Avatar", "Cameron", 2009)
        val movieRes = client.post("/movies") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(MovieRequest.serializer(), movieReq))
        }
        val movie = json.decodeFromString<Movie>(movieRes.bodyAsText())

        // Add review
        val reviewReq = ReviewRequest("John", 4, "Nice movie")
        val reviewRes = client.post("/movies/${movie.id}/reviews") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ReviewRequest.serializer(), reviewReq))
        }
        val review = json.decodeFromString<Review>(reviewRes.bodyAsText())

        // Delete review
        val deleteRes = client.delete("/reviews/${review.id}")
        assertEquals(HttpStatusCode.NoContent, deleteRes.status)

        // Delete again -> NotFound
        val deleteAgain = client.delete("/reviews/${review.id}")
        assertEquals(HttpStatusCode.NotFound, deleteAgain.status)
    }
}
