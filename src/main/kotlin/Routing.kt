package com.example

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class Movie(val id: Int, val title: String, val director: String, val releaseYear: Int)

@Serializable
data class MovieRequest(val title: String, val director: String, val releaseYear: Int)

@Serializable
data class Review(val id: Int, val movieId: Int, val reviewerName: String, val rating: Int, val comment: String)

@Serializable
data class ReviewRequest(val reviewerName: String, val rating: Int, val comment: String)

@Serializable
data class MovieWithAverageRating(
    val id: Int,
    val title: String,
    val director: String,
    val releaseYear: Int,
    val averageRating: Double
)

// ========== Repositories ==========
object MovieRepository {
    private val movies = mutableListOf<Movie>()
    private var nextId = 1

    fun getAll() = movies
    fun getById(id: Int) = movies.find { it.id == id }
    fun search(query: String) = movies.filter {
        it.title.contains(query, ignoreCase = true) || it.director.contains(query, ignoreCase = true)
    }
    fun add(movie: Movie) { movies.add(movie) }
    fun update(id: Int, updated: Movie): Boolean {
        val index = movies.indexOfFirst { it.id == id }
        return if (index != -1) {
            movies[index] = updated
            true
        } else false
    }
    fun delete(id: Int) = movies.removeIf { it.id == id }
    fun getNextId() = nextId++
}

object ReviewRepository {
    private val reviews = mutableListOf<Review>()
    private var nextId = 1

    fun getByMovieId(movieId: Int) = reviews.filter { it.movieId == movieId }
    fun add(review: Review) { reviews.add(review) }
    fun delete(id: Int) = reviews.removeIf { it.id == id }
    fun getNextId() = nextId++
    fun averageRating(movieId: Int): Double {
        val ratings = getByMovieId(movieId).map { it.rating }
        return if (ratings.isNotEmpty()) ratings.average() else 0.0
    }
}

// ========== Routing ==========
fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("🎬 Welcome to Movie Library API!")
        }

        route("/movies") {
            get {
                val movies = MovieRepository.getAll().map {
                    MovieWithAverageRating(
                        it.id, it.title, it.director, it.releaseYear,
                        ReviewRepository.averageRating(it.id)
                    )
                }
                call.respond(movies)
            }

            get("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                val movie = id?.let { MovieRepository.getById(it) }
                if (movie == null) call.respond(HttpStatusCode.NotFound)
                else call.respond(movie)
            }

            get("/{id}/average-rating") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest)
                } else {
                    val avg = ReviewRepository.averageRating(id)
                    call.respond(mapOf("averageRating" to avg))
                }
            }

            get("/search") {
                val query = call.request.queryParameters["q"]
                if (query.isNullOrEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, "Missing query parameter")
                } else {
                    call.respond(MovieRepository.search(query))
                }
            }

            post {
                val req = call.receive<MovieRequest>()
                val movie = Movie(MovieRepository.getNextId(), req.title, req.director, req.releaseYear)
                MovieRepository.add(movie)
                call.respond(HttpStatusCode.Created, movie)
            }

            put("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@put
                }
                val req = call.receive<MovieRequest>()
                val updated = Movie(id, req.title, req.director, req.releaseYear)
                if (MovieRepository.update(id, updated)) call.respond(updated)
                else call.respond(HttpStatusCode.NotFound)
            }

            delete("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null || !MovieRepository.delete(id)) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(HttpStatusCode.NoContent)
                }
            }

            // Reviews under movie (RESTful)
            route("/{id}/reviews") {
                get {
                    val id = call.parameters["id"]?.toIntOrNull()
                    if (id == null) call.respond(HttpStatusCode.BadRequest)
                    else call.respond(ReviewRepository.getByMovieId(id))
                }

                post {
                    val id = call.parameters["id"]?.toIntOrNull()
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@post
                    }

                    val req = call.receive<ReviewRequest>()
                    if (req.rating !in 1..5) {
                        call.respond(HttpStatusCode.BadRequest, "Rating must be between 1 and 5")
                        return@post
                    }
                    val movie = MovieRepository.getById(id)
                    if (movie == null) {
                        call.respond(HttpStatusCode.BadRequest, "Movie not found")
                        return@post
                    }

                    val review = Review(ReviewRepository.getNextId(), id, req.reviewerName, req.rating, req.comment)
                    ReviewRepository.add(review)
                    call.respond(HttpStatusCode.Created, review)
                }
            }
        }

        // delete review by id
        delete("/reviews/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null || !ReviewRepository.delete(id)) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
