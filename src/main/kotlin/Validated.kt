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

    fun <B> mapTo(value: B): Validated<E, B> = map { value }

    fun <F> mapErrors(f: (List<E>) -> List<F>): Validated<F, A> = when (this) {
        is Valid -> this
        is Invalid -> Invalid(f(errors))
    }

    fun <B> flatMap(f: (A) -> Validated<@UnsafeVariance E, B>): Validated<E, B> = when (this) {
        is Valid -> f(value)
        is Invalid -> this
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


class ValidationScope<E>(private val errors: MutableList<E> = mutableListOf()) {
    fun ensure(condition: Boolean, error: () -> E) {
        if (!condition) errors.add(error())
    }

    fun <A> ensureNotNull(value: A?, error: () -> E): A? {
        if (value == null) errors.add(error())
        return value
    }

    fun ensure(validation: Validated<E, *>) {
        if (validation is Validated.Invalid) errors.addAll(validation.errors)
    }


    internal fun build(): Validated<E, Unit> =
        if (errors.isEmpty()) Validated.unit() else Validated.invalid(errors)

    internal fun <A> buildWith(value: A): Validated<E, A> =
        if (errors.isEmpty()) Validated.valid(value) else Validated.invalid(errors)


}

class ValidationException(val errors: List<Any?>) : Exception() {
    override fun fillInStackTrace() = this
}

fun <E> validate(block: ValidationScope<E>.() -> Unit): Validated<E, Unit> =
    try {
        ValidationScope<E>().apply(block).build()
    } catch (e: ValidationException) {
        @Suppress("UNCHECKED_CAST")
        Validated.invalid(e.errors as List<E>)
    }

fun <E, A> validateTo(block: ValidationScope<E>.() -> A): Validated<E, A> {
    val scope = ValidationScope<E>()
    return try {
        scope.buildWith(scope.block())
    } catch (e: ValidationException) {
        @Suppress("UNCHECKED_CAST")
        Validated.invalid(e.errors as List<E>)
    }
}



fun <E> validate(block: ValidationScope<E>.() -> Unit): Validated<E, Unit> =
    ValidationScope<E>().apply(block).build()

fun <E, A> validateWith(block: ValidationScope<E>.() -> A): Validated<E, A> {
    val scope = ValidationScope<E>()
    val result = scope.block()
    return scope.buildWith(result)
}


fun <E> validate(block: ValidationScope<E>.() -> Unit): Validated<E, Unit> =
    try {
        ValidationScope<E>().apply(block).build()
    } catch (e: ValidationException<*>) {
        @Suppress("UNCHECKED_CAST")
        Validated.invalid(e.errors as List<E>)
    }

fun <E, A> validating(block: ValidationScope<E>.() -> A): Validated<E, A> {
    val scope = ValidationScope<E>()
    return try {
        scope.buildWith(scope.block())
    } catch (e: ValidationException<*>) {
        @Suppress("UNCHECKED_CAST")
        Validated.invalid(e.errors as List<E>)
    }
}
