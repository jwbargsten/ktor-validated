package org.bargsten

import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import validated

@Serializable
data class CustomerReq(val id: Int, val firstName: String, val lastName: String)
data class Customer(val id: Int, val firstName: String, val lastName: String)


fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
        post("/abc") {

            val body = call.receive<CustomerReq>()
            val customer = validated {
                check(body.id > 0) { "error" }

                Customer(body.id, body.firstName, body.lastName)
            }
        }
    }
}
