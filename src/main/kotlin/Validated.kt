sealed class Validated<out E, out A> {
    data class Valid<A>(val value: A) : Validated<Nothing, A>()
    data class Invalid<E>(val errors: List<E>) : Validated<E, Nothing>()


    fun getOrNull(): A? = (this as? Valid)?.value

    fun getOrElse(default: () -> @UnsafeVariance A): A = getOrNull() ?: default()

    fun getOrDefault(default: @UnsafeVariance A): A = getOrNull() ?: default

    fun errorsOrEmpty(): List<E> = (this as? Invalid)?.errors ?: emptyList()

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

    val isValid: Boolean get() = this is Valid
    val isInvalid: Boolean get() = this is Invalid

    companion object {
        fun <A> valid(a: A): Validated<Nothing, A> = Valid(a)
        fun validUnit(): Validated<Nothing, Unit> = Valid(Unit)
        fun <E> invalidOne(e: E): Validated<E, Nothing> = Invalid(listOf(e))
        fun <E> invalid(errors: List<E>): Validated<E, Nothing> = Invalid(errors)

        fun <E, A> fromNullable(value: A?, error: () -> E): Validated<E, A> =
            if (value != null) Valid(value) else invalidOne(error())

        fun <E, A> catch(error: (Throwable) -> E, block: () -> A): Validated<E, A> =
            try {
                Valid(block())
            } catch (e: Throwable) {
                invalidOne(error(e))
            }

        fun <E> validWhen(fn: () -> Boolean, errFn: () -> E): Validated<E, Unit> = if (fn()) Valid(Unit) else Invalid(listOf(errFn()))
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

fun <E, A, B, C, D> zip3(
    v1: Validated<E, A>,
    v2: Validated<E, B>,
    v3: Validated<E, C>,
    f: (A, B, C) -> D
): Validated<E, D> = v1.zip(v2) { a, b -> a to b }.zip(v3) { (a, b), c -> f(a, b, c) }

fun <E, A> List<Validated<E, A>>.sequence(): Validated<E, List<A>> =
    fold(Validated.valid(emptyList())) { acc, validated ->
        acc.zip(validated) { list, a -> list + a }
    }

fun <E, A, B> List<A>.traverse(f: (A) -> Validated<E, B>): Validated<E, List<B>> =
    map(f).sequence()

fun <E> validateAll(vararg validations: Validated<E, *>): Validated<E, Unit> {
    val errors = validations.flatMap { it.errorsOrEmpty() }
    return if (errors.isEmpty()) Validated.valid(Unit) else Validated.invalid(errors)
}
