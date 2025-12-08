sealed class Validated<out E, out A> {
    data class Valid<A>(val value: A) : Validated<Nothing, A>()
    data class Invalid<E>(val errors: List<E>) : Validated<E, Nothing>()

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

    fun orElse(default: () -> Validated<@UnsafeVariance E, @UnsafeVariance A>): Validated<E, A> = when {
        isValid -> this
        else -> default()
    }

    fun <B> fold(onValid: (A) -> B, onInvalid: (List<E>) -> B) = when (this) {
        is Valid -> onValid(value)
        is Invalid -> onInvalid(errors)
    }

    fun <B> map(f: (A) -> B): Validated<E, B> = when (this) {
        is Valid -> Valid(f(value))
        is Invalid -> this
    }

    fun <F> mapErrors(f: (List<E>) -> List<F>): Validated<F, A> = when (this) {
        is Valid -> this
        is Invalid -> Invalid(f(errors))
    }

    fun <B> flatMap(f: (A) -> Validated<@UnsafeVariance E, B>): Validated<E, B> = when (this) {
        is Valid -> f(value)
        is Invalid -> this
    }

    fun recover(f: (List<E>) -> @UnsafeVariance A): Validated<Nothing, A> = when (this) {
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
    f: (A, B) -> C
): Validated<E, C> = when (this) {
    is Validated.Valid -> when (other) {
        is Validated.Valid -> Validated.Valid(f(value, other.value))
        is Validated.Invalid -> other
    }

    is Validated.Invalid -> when (other) {
        is Validated.Valid -> this
        is Validated.Invalid -> Validated.Invalid(errors + other.errors)
    }
}

fun <E, A> Validated<E, A>.zip(other: Validated<E, *>): Validated<E, A> =
    zip(other) { a, _ -> a }

fun <E, A> List<Validated<E, A>>.sequence(): Validated<E, List<A>> =
    fold(Validated.valid(emptyList())) { acc, validated ->
        acc.zip(validated) { list, a -> list + a }
    }

fun <E> validateAll(vararg validations: Validated<E, *>): Validated<E, Unit> {
    val errors = validations.flatMap { (it as? Validated.Invalid)?.errors ?: emptyList() }
    return if (errors.isEmpty()) Validated.unit() else Validated.invalid(errors)
}


class ValidationScope<E>(private val _errors: MutableList<E> = mutableListOf()) {
    val errors: List<E> get() = _errors

    fun check(condition: Boolean, error: () -> E) {
        if (!condition) _errors.add(error())
    }

    fun check(condition: () -> Boolean, error: () -> E) {
        if (!condition()) _errors.add(error())
    }

    fun check(validation: Validated<E, *>) {
        if (validation is Validated.Invalid) _errors.addAll(validation.errors)
    }


    fun <A> A?.checkNotNull(error: () -> E): A? {
        if (this == null) _errors.add(error())
        return this
    }

    fun <A> A.check(condition: (A) -> Boolean, error: () -> E): A {
        if (!condition(this)) _errors.add(error())
        return this
    }

    @JvmName("checkNullable")
    fun <A> A?.check(condition: (A) -> Boolean, error: () -> E): A? {
        if (this != null && !condition(this)) _errors.add(error())
        return this
    }


    fun <A> A?.demandNotNull(error: () -> E): A {
        if (this == null) {
            _errors.add(error())
            throw ValidationException()
        }
        return this
    }

    fun <A> A.demand(condition: (A) -> Boolean, error: () -> E): A {
        if (!condition(this)) {
            _errors.add(error())
            throw ValidationException()
        }
        return this
    }

    fun demand(condition: () -> Boolean, error: () -> E) {
        if (!condition()) {
            _errors.add(error())
            throw ValidationException()
        }
    }

    fun demand(condition: Boolean, error: () -> E) {
        if (!condition) {
            _errors.add(error())
            throw ValidationException()
        }
    }


    fun <A> checkValue(validated: Validated<E, A>): A? = when (validated) {
        is Validated.Valid -> validated.value
        is Validated.Invalid -> {
            _errors.addAll(validated.errors)
            null
        }
    }

    fun <A> demandValue(validated: Validated<E, A>): A = when (validated) {
        is Validated.Valid -> validated.value
        is Validated.Invalid -> {
            _errors.addAll(validated.errors)
            throw ValidationException()
        }
    }


    internal fun build(): Validated<E, Unit> =
        if (_errors.isEmpty()) Validated.unit() else Validated.invalid(_errors)

    internal fun <A> buildWith(value: A): Validated<E, A> =
        if (_errors.isEmpty()) Validated.valid(value) else Validated.invalid(_errors)
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

fun <E, A> validated(block: ValidationScope<E>.() -> A): Validated<E, A> {
    val scope = ValidationScope<E>()
    return try {
        scope.buildWith(scope.block())
    } catch (_: ValidationException) {
        @Suppress("UNCHECKED_CAST")
        Validated.invalid(scope.errors)
    }
}
