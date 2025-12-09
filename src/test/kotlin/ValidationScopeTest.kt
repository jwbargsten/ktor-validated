import io.kotest.core.spec.style.FunSpec
import org.bargsten.Customer
import org.bargsten.CustomerReq

class ValidationScopeTest : FunSpec({

    test("scope") {
        val req = CustomerReq(1, "John", "Doe")
        val result = validated {
            // shortcut/early exit
            demand(req.id > 0) { "Customer ID must be positive" }
            // accumulative
            check(req.firstName.isNotBlank()) { "First name must not be blank" }
            check(req.lastName.isNotBlank()) { "Last name must not be blank" }
        }
        result.fold(
            onValid = { println("valid") },
            onInvalid = { println(it.joinToString()) }
        )
        if (result.isValid) {
            println("everything is fine")
        } else {
            println("error")
        }
    }
})
