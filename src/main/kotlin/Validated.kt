package org.bargsten.validation

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Represents a result of validation that can either be valid or invalid.
 * This is the basis for error accumulation, so you can check many fields
 * of e.g. a request and give a list of issues back to the user. This is better
 * than failing at each error, so the user has the annoying:
 *
 * ```
 * call -> error -> fix -> next call -> next error -> next fix -> ...
 * ```
 *
 * loop. For a more in-depth explanation, you can have a look at the
 * [Validation documentation of the Arrow library](https://arrow-kt.io/learn/typed-errors/validation/)
 *
 * Validated is working very similar to how Arrow approaches it, only a bit simpler and more explicit.
 *
 * There are two standard use cases that follow a specific pattern:
 *
 * ## Use case 1 - validating/parsing something
 *
 * A good example is the [org.bargsten.model.Ean18] class. You have the combination of `ensure`
 * (or `demand`) and `validateWithResult`.
 *
 * ## Use case 2 - merging ("attaching") multiple validations.
 *
 * At some point you want to bring all your parsed/validated results together and build something new or accumulate all
 * the validations into one.
 *
 * This requires, besides `ensure/demand`, extra functionality: `attach()` and `get()`. With `attach()` you can
 * "attach" a validation to the current validation scope. Basically, if the validation you attach has errors, these
 * errors are added to the list of errors of the current scope.
 *
 * Later, when you need the value of the validation, you have to call `.get()` on the validation. If the validation
 * is invalid, the scope will short-cut and return an [Invalid] object with the accumulated errors. The flow is then
 * as follows (I also added a `demand` call to see how it can be used):
 *
 * ```
 * validateWithResult {
 *   ensure(a > b) { "$a should be greater than $b" } // accumulate error
 *   // if false, this will short-circuit with 2 errors (the one above and the error of the demand call)
 *   demand($patat == $friet) { "patat and friet should be equal" }
 *   // if demand did pass, we continue with accumulation
 *   ensure(c > d) { "$c should be greater than $d" } // accumulate error
 *   ensure(e > f) { "$e should be greater than $f" } // accumulate error
 *   val ean18 = attach(Ean18.parse(value)) // if parsing fails with errors, add the errors to the current scope/context
 *
 *   // at this point, we accumulated: 3 ensure checks and the ean18 validation. Theoretically, this might result in
 *   // 4 or more errors, depending on how many checks the Ean18.parse function does.
 *
 *   ean18.get()  // if there are errors, we will short-cut and return with the errors accumulated so far.
 * }
 * ```
 *
 * **Just remember this pattern: `ensure`, `ensure`, ..., `attach`, `ensure` and at the end `.get()`**
 *
 * See  [nl.edsn.service.chr.adapters.rest.connectedparties.ConnectedPartiesController.validateUpdateRequest] for
 * an example
 *
 * @param E The type of the validation error(s).
 * @param A The type of the valid value.
 */
sealed class Validated<out E, out A> {
    data class Valid<A>(
        val value: A,
    ) : Validated<Nothing, A>()

    data class Invalid<E>(
        val errors: List<E>,
    ) : Validated<E, Nothing>()

    fun getOrNull(): A? = (this as? Valid)?.value

    fun getOrElse(default: () -> @UnsafeVariance A): A = getOrNull() ?: default()

    fun onValid(action: (A) -> Unit): Validated<E, A> {
        if (this is Valid) action(value)
        return this
    }

    fun onInvalid(action: (List<E>) -> Unit): Validated<E, A> {
        if (this is Invalid) action(errors)
        return this
    }

    fun orElse(default: () -> Validated<@UnsafeVariance E, @UnsafeVariance A>): Validated<E, A> =
        when {
            isValid -> this
            else -> default()
        }

    fun <B> fold(
        onValid: (A) -> B,
        onInvalid: (List<E>) -> B,
    ) = when (this) {
        is Valid -> onValid(value)
        is Invalid -> onInvalid(errors)
    }

    fun <B> map(f: (A) -> B): Validated<E, B> =
        when (this) {
            is Valid -> Valid(f(value))
            is Invalid -> this
        }

    fun <F> mapErrors(f: (List<E>) -> List<F>): Validated<F, A> =
        when (this) {
            is Valid -> this
            is Invalid -> Invalid(f(errors))
        }

    fun <B> flatMap(f: (A) -> Validated<@UnsafeVariance E, B>): Validated<E, B> =
        when (this) {
            is Valid -> f(value)
            is Invalid -> this
        }

    fun recover(f: (List<E>) -> @UnsafeVariance A): Validated<Nothing, A> =
        when (this) {
            is Valid -> this
            is Invalid -> Valid(f(errors))
        }

    val isValid: Boolean get() = this is Valid
    val isInvalid: Boolean get() = this is Invalid

    companion object {
        private val UNIT = Valid(Unit)

        fun unit(): Validated<Nothing, Unit> = UNIT

        fun <A> valid(a: A): Validated<Nothing, A> = Valid(a)

        fun <E> invalidOne(e: E): Validated<E, Nothing> = Invalid(listOf(e))

        fun <E> invalid(errors: List<E>): Validated<E, Nothing> = Invalid(errors)
    }
}

fun <E, A, B, C> Validated<E, A>.zip(
    other: Validated<E, B>,
    f: (A, B) -> C,
): Validated<E, C> =
    when (this) {
        is Validated.Valid ->
            when (other) {
                is Validated.Valid -> Validated.Valid(f(value, other.value))
                is Validated.Invalid -> other
            }

        is Validated.Invalid ->
            when (other) {
                is Validated.Valid -> this
                is Validated.Invalid -> Validated.Invalid(errors + other.errors)
            }
    }

fun <E, A> Validated<E, A>.zip(other: Validated<E, *>): Validated<E, A> = zip(other) { a, _ -> a }

fun <E, A> List<Validated<E, A>>.sequence(): Validated<E, List<A>> =
    fold(Validated.valid(emptyList())) { acc, validated ->
        acc.zip(validated) { list, a -> list + a }
    }

fun <E> validateAll(vararg validations: Validated<E, *>): Validated<E, Unit> {
    val errors = validations.flatMap { (it as? Validated.Invalid)?.errors ?: emptyList() }
    return if (errors.isEmpty()) Validated.unit() else Validated.invalid(errors)
}

class ValidationScope<E>(
    private val _errors: MutableList<E> = mutableListOf(),
) {
    val errors: List<E> get() = _errors

    fun ensure(
        predicate: Boolean,
        error: () -> E,
    ) {
        if (!predicate) _errors.add(error())
    }

    fun ensure(
        predicate: () -> Boolean,
        error: () -> E,
    ) {
        if (!predicate()) _errors.add(error())
    }

    /**
     * Attaches the provided validation result to the current context. If the validation result
     * is invalid, its errors are added to the internal list of errors.
     *
     * @param validation The validation result to attach. This can be a valid or invalid instance of `Validated`.
     * @return The same `Validated` instance provided as input.
     */
    fun <A> attach(validation: Validated<E, A>): Validated<E, A> {
        if (validation is Validated.Invalid) _errors.addAll(validation.errors)
        return validation
    }

    fun <A> attach(validation: () -> Validated<E, A>): Validated<E, A> = attach(validation())

    @JvmName("ensureNotNullFluent")
    fun <A : Any> A?.ensureNotNull(error: () -> E): A? {
        if (this == null) _errors.add(error())
        return this
    }

    fun <A : Any> ensureNotNull(
        value: A?,
        error: () -> E,
    ): A {
        if (value == null) {
            _errors.add(error())
            throw ValidationException()
        }
        return value
    }

    fun <A : Any> ensureNotNull(
        value: () -> A?,
        error: () -> E,
    ): A {
        val v = value()
        if (v == null) {
            _errors.add(error())
            throw ValidationException()
        }
        return v
    }

    fun <A : Any> demandNotNull(
        value: A?,
        error: () -> E,
    ) {
        if (value == null) {
            _errors.add(error())
            throw ValidationException()
        }
    }

    fun <A : Any> demandNotNull(
        valueFn: () -> A?,
        error: () -> E,
    ) {
        if (valueFn() == null) {
            _errors.add(error())
            throw ValidationException()
        }
    }

    @JvmName("attachFluent")
    fun <E, A> Validated<E, A>.attach(): Validated<E, A> {
        if (this is Validated.Invalid) _errors.addAll(_errors)

        return this
    }

    fun <A : Any> A.ensure(
        predicate: (A) -> Boolean,
        error: () -> E,
    ): A {
        if (!predicate(this)) _errors.add(error())
        return this
    }

    @JvmName("ensureNullableFluent")
    fun <A : Any> A?.ensure(
        predicate: (A) -> Boolean,
        error: () -> E,
    ): A? {
        if (this != null && !predicate(this)) _errors.add(error())
        return this
    }

    fun <A> Validated<E, A>.get(): A =
        when (this) {
            is Validated.Valid -> this.value
            is Validated.Invalid -> throw ValidationException()
        }

    @JvmName("demandNotNullFluent")
    @OptIn(ExperimentalContracts::class)
    fun <A : Any> A?.demandNotNull(error: () -> E): A {
        contract { returns() implies (this@demandNotNull != null) }
        if (this == null) {
            _errors.add(error())
            throw ValidationException()
        }
        return this
    }

    fun <A> A.demand(
        predicate: (A) -> Boolean,
        error: () -> E,
    ): A {
        if (!predicate(this)) {
            _errors.add(error())
            throw ValidationException()
        }
        return this
    }

    fun demand(
        predicate: () -> Boolean,
        error: () -> E,
    ) {
        if (!predicate()) {
            _errors.add(error())
            throw ValidationException()
        }
    }

    fun demand(
        predicate: Boolean,
        error: () -> E,
    ) {
        if (!predicate) {
            _errors.add(error())
            throw ValidationException()
        }
    }

    fun <A> ensureValue(validated: Validated<E, A>): A? =
        when (validated) {
            is Validated.Valid -> validated.value
            is Validated.Invalid -> {
                _errors.addAll(validated.errors)
                null
            }
        }

    fun <A> demandValue(validated: Validated<E, A>): A =
        when (validated) {
            is Validated.Valid -> validated.value
            is Validated.Invalid -> {
                _errors.addAll(validated.errors)
                throw ValidationException()
            }
        }

    internal fun build(): Validated<E, Unit> = if (_errors.isEmpty()) Validated.unit() else Validated.invalid(_errors)

    internal fun <A> buildWith(value: A): Validated<E, A> = if (_errors.isEmpty()) Validated.valid(value) else Validated.invalid(_errors)
}

private class ValidationException : Exception() {
    override fun fillInStackTrace() = this
}

fun <E> validate(block: ValidationScope<E>.() -> Unit): Validated<E, Unit> {
    val scope = ValidationScope<E>()
    return try {
        scope.apply(block).build()
    } catch (e: ValidationException) {
        @Suppress("UNCHECKED_CAST")
        Validated.invalid(scope.errors)
    }
}

fun <E, A> validateWithResult(block: ValidationScope<E>.() -> A): Validated<E, A> {
    val scope = ValidationScope<E>()
    return try {
        scope.buildWith(scope.block())
    } catch (_: ValidationException) {
        @Suppress("UNCHECKED_CAST")
        Validated.invalid(scope.errors)
    }
}
