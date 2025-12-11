package org.bargsten.model

import org.bargsten.validation.Validated
import org.bargsten.validation.ValidationCode
import org.bargsten.validation.ValidationIssue
import org.bargsten.validation.validateWithResult

@JvmInline
value class Ean18 private constructor(
    val value: String,
) {
    companion object {
        fun fromUnsafe(value: String) = Ean18(value)

        fun parse(value: String): Validated<ValidationIssue, Ean18> =
            validateWithResult {
                ensure(value.length == 18) { ValidationIssue(ValidationCode.VALUE, "EAN18 must be 18 digits long") }
                ensure(value.all { it.isDigit() }) {
                    ValidationIssue(
                        ValidationCode.VALUE,
                        "EAN18 must only contain digits",
                    )
                }

                Ean18(value)
            }
    }
}
