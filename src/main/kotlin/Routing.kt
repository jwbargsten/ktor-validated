package org.bargsten

import Validated.Companion.validWhen
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import validateAll

@Serializable
data class CustomerReq(val id: Int, val firstName: String, val lastName: String)


fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
        post("/abc") {

            val body = call.receive<CustomerReq>()
            validateAll(
                validWhen<String>({ body.id > 0 }, { "ERROR" })
            )


        }
    }
}
