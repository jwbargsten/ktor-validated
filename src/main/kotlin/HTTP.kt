package org.bargsten

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.requestvalidation.RequestValidation

fun Application.configureHTTP() {
    install(DefaultHeaders) {
        header("X-Engine", "Ktor") // will send this header with each response
    }
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("MyCustomHeader")
        anyHost() // @TODO: Don't do this in production if possible. Try to limit it.
    }
    install(RequestValidation)
    install(ContentNegotiation) {
        json()
    }
    install(Compression)
    routing {
        openAPI(path = "openapi")
    }
}
