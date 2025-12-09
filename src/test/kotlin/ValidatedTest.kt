import Validated.Companion.invalid
import Validated.Companion.invalidOne
import Validated.Companion.valid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

data class Pokemon(val id: Int, val name: String, val level: Int)

class ValidatedTest : FunSpec({

    val validPokemon: Validated<String, Pokemon> = valid(Pokemon(25, "Pikachu", 50))
    val invalidPokemon: Validated<String, Pokemon> = invalidOne("Pokemon fainted")

    context("Validated.Valid/Invalid") {
        test("getOrNull returns value") {
            validPokemon.getOrNull() shouldBe Pokemon(25, "Pikachu", 50)
            invalidPokemon.getOrNull() shouldBe null
        }

        test("getOrElse returns value") {
            validPokemon.getOrElse { Pokemon(0, "MissingNo", 0) } shouldBe Pokemon(25, "Pikachu", 50)
            invalidPokemon.getOrElse { Pokemon(132, "Ditto", 10) } shouldBe Pokemon(132, "Ditto", 10)
        }

        test("isValid returns true") {
            validPokemon.isValid shouldBe true
            invalidPokemon.isValid shouldBe false
        }

        test("isInvalid returns false") {
            validPokemon.isInvalid shouldBe false
            invalidPokemon.isInvalid shouldBe true
        }

        test("onValid executes action") {
            var captured: Pokemon? = null
            validPokemon.onValid { captured = it }
            captured shouldBe Pokemon(25, "Pikachu", 50)
        }

        test("onInvalid does not execute action") {
            var called = false
            validPokemon.onInvalid { called = true }
            called shouldBe false
        }

        test("orElse returns this") {
            validPokemon.orElse { valid(Pokemon(151, "Mew", 50)) } shouldBe validPokemon
            invalidPokemon.orElse { valid(Pokemon(151, "Mew", 50)) } shouldBe valid(Pokemon(151, "Mew", 50))
        }

        test("fold calls onValid") {
            valid(Pokemon(143, "Snorlax", 30)).fold(
                onValid = { it.name },
                onInvalid = { "Error" }
            ) shouldBe "Snorlax"
        }

        test("map transforms value") {
            valid(Pokemon(25, "Pikachu", 50)).map { it.name } shouldBe valid("Pikachu")
        }

        test("mapErrors returns this") {
            val valid = valid(Pokemon(94, "Gengar", 45))
            valid.mapErrors { it.map { e -> "mapped: $e" } } shouldBe valid
        }

        test("flatMap chains validations") {
            val result = validPokemon.flatMap { pokemon ->
                if (pokemon.level >= 116) valid(Pokemon(5, "Charmeleon", pokemon.level))
                else invalidOne("Level too low to evolve")
            }
            result shouldBe invalidOne("Level too low to evolve")
        }

        test("flatMap with successful chain") {
            val result = validPokemon.flatMap { pokemon ->
                valid(Pokemon(5, "Charmeleon", pokemon.level))
            }
            result shouldBe valid(Pokemon(5, "Charmeleon", 50))
        }

        test("recover returns this") {
            validPokemon.recover { Pokemon(0, "Ditto", 1) } shouldBe validPokemon
        }
    }

    context("Validated.Invalid") {

        test("onValid does not execute action") {
            var called = false
            Validated.invalidOne<String>("Error").onValid { called = true }
            called shouldBe false
        }

        test("onInvalid executes action") {
            var captured: List<String>? = null
            Validated.invalid(listOf("HP is zero", "Status: Fainted")).onInvalid { captured = it }
            captured shouldBe listOf("HP is zero", "Status: Fainted")
        }

        test("orElse returns default") {
            val invalid: Validated<String, Pokemon> = Validated.invalidOne("Not found")
            invalid.orElse { valid(Pokemon(132, "Ditto", 10)) } shouldBe valid(Pokemon(132, "Ditto", 10))
        }

        test("fold calls onInvalid") {
            Validated.invalid(listOf("Error1", "Error2")).fold(
                onValid = { "Success" },
                onInvalid = { it.joinToString() }
            ) shouldBe "Error1, Error2"
        }

        test("map returns this") {
            val invalid: Validated<String, Pokemon> = Validated.invalidOne("Pokemon escaped")
            invalid.map { it.name }.shouldBeInstanceOf<Validated.Invalid<String>>()
        }

        test("mapErrors transforms errors") {
            Validated.invalid(listOf("low hp", "poisoned")).mapErrors { errors ->
                errors.map { it.uppercase() }
            } shouldBe Validated.invalid(listOf("LOW HP", "POISONED"))
        }

        test("flatMap returns this") {
            val invalid: Validated<String, Pokemon> = Validated.invalidOne("Wild Pokemon fled")
            invalid.flatMap { valid(it.name) }.shouldBeInstanceOf<Validated.Invalid<String>>()
        }

        test("recover transforms to valid") {
            val invalid: Validated<String, Pokemon> = Validated.invalid(listOf("Error1", "Error2"))
            invalid.recover { errors -> Pokemon(0, "Recovered from ${errors.size} errors", 1) } shouldBe
                valid(Pokemon(0, "Recovered from 2 errors", 1))
        }
    }

    context("Validated companion") {
        test("unit returns valid unit") {
            Validated.unit() shouldBe Validated.Valid(Unit)
        }

        test("valid wraps value") {
            valid(Pokemon(149, "Dragonite", 55)) shouldBe Validated.Valid(Pokemon(149, "Dragonite", 55))
        }

        test("invalidOne creates single error list") {
            invalidOne("Pikachu refused to enter Pokeball") shouldBe Validated.Invalid(listOf("Pikachu refused to enter Pokeball"))
        }

        test("invalid wraps error list") {
            Validated.invalid(listOf("Error1", "Error2")) shouldBe Validated.Invalid(listOf("Error1", "Error2"))
        }
    }

    context("zip extension") {
        test("zip two valids with function") {
            val pokemon1 = valid(Pokemon(25, "Pikachu", 50))
            val pokemon2 = valid(Pokemon(26, "Raichu", 60))
            pokemon1.zip(pokemon2) { p1, p2 -> "${p1.name} evolves to ${p2.name}" } shouldBe
                valid("Pikachu evolves to Raichu")
        }

        test("zip valid with invalid accumulates error") {
            val valid = valid(Pokemon(25, "Pikachu", 50))
            val invalid: Validated<String, Pokemon> = Validated.invalidOne("Raichu not found")
            valid.zip(invalid) { p1, p2 -> "${p1.name}-${p2.name}" } shouldBe Validated.invalidOne("Raichu not found")
        }

        test("zip invalid with valid returns invalid") {
            val invalid: Validated<String, Pokemon> = Validated.invalidOne("Pikachu fainted")
            val valid = valid(Pokemon(26, "Raichu", 60))
            invalid.zip(valid) { p1, p2 -> "${p1.name}-${p2.name}" } shouldBe Validated.invalidOne("Pikachu fainted")
        }

        test("zip two invalids accumulates all errors") {
            val invalid1: Validated<String, Pokemon> = Validated.invalid(listOf("Error1", "Error2"))
            val invalid2: Validated<String, Pokemon> = Validated.invalid(listOf("Error3"))
            invalid1.zip(invalid2) { p1, p2 -> "${p1.name}-${p2.name}" } shouldBe
                Validated.invalid(listOf("Error1", "Error2", "Error3"))
        }

        test("zip without function keeps first value") {
            val pokemon1 = valid(Pokemon(25, "Pikachu", 50))
            val pokemon2 = valid(Pokemon(26, "Raichu", 60))
            pokemon1.zip(pokemon2) shouldBe valid(Pokemon(25, "Pikachu", 50))
        }
    }

    context("sequence extension") {
        test("sequence all valids") {
            val pokemons = listOf(
                valid(Pokemon(1, "Bulbasaur", 5)),
                valid(Pokemon(4, "Charmander", 5)),
                valid(Pokemon(7, "Squirtle", 5))
            )
            pokemons.sequence() shouldBe valid(listOf(
                Pokemon(1, "Bulbasaur", 5),
                Pokemon(4, "Charmander", 5),
                Pokemon(7, "Squirtle", 5)
            ))
        }

        test("sequence with invalid accumulates errors") {
            val validations: List<Validated<String, Pokemon>> = listOf(
                valid(Pokemon(1, "Bulbasaur", 5)),
                invalidOne("Charmander escaped"),
                invalidOne("Squirtle not found")
            )
            validations.sequence() shouldBe Validated.invalid(listOf("Charmander escaped", "Squirtle not found"))
        }

        test("sequence empty list") {
            emptyList<Validated<String, Pokemon>>().sequence() shouldBe valid(emptyList())
        }
    }

    context("validateAll") {
        test("all valid returns unit") {
            validateAll(
                valid(Pokemon(25, "Pikachu", 50)),
                valid(Pokemon(26, "Raichu", 60)),
                Validated.unit()
            ) shouldBe Validated.unit()
        }

        test("mixed validations accumulates errors") {
            validateAll(
                valid(Pokemon(25, "Pikachu", 50)),
                invalidOne("Raichu fainted"),
                invalid(listOf("Not enough badges", "No HM"))
            ) shouldBe Validated.invalid(listOf("Raichu fainted", "Not enough badges", "No HM"))
        }
    }

    context("ValidationScope check") {
        test("check with true condition adds no errors") {
            val result = validate<String> {
                ensure(true) { "Should not appear" }
            }
            result shouldBe Validated.unit()
        }

        test("check with false condition adds error") {
            val result = validate<String> {
                ensure(false) { "Pikachu's level is too low" }
            }
            result shouldBe Validated.invalidOne("Pikachu's level is too low")
        }

        test("check with lambda condition") {
            val result = validate<String> {
                ensure({ 25 > 10 }) { "Level check failed" }
            }
            result shouldBe Validated.unit()
        }

        test("check with Validated") {
            val result = validate<String> {
                ensure(Validated.invalidOne("Existing error"))
            }
            result shouldBe Validated.invalidOne("Existing error")
        }

        test("multiple checks accumulate errors") {
            val result = validate {
                ensure(false) { "Error 1: HP too low" }
                ensure(true) { "Error 2: Should not appear" }
                ensure(false) { "Error 3: PP depleted" }
            }
            result shouldBe invalid(listOf("Error 1: HP too low", "Error 3: PP depleted"))
        }
    }

    context("ValidationScope checkNotNull") {
        test("checkNotNull with non-null returns value") {
            val result = validateWithResult<String, Pokemon> {
                val pokemon: Pokemon? = Pokemon(25, "Pikachu", 50)
                val checked = pokemon.ensureNotNull { "Pokemon is null" }
                checked!!
            }
            result shouldBe valid(Pokemon(25, "Pikachu", 50))
        }

        test("checkNotNull with null adds error") {
            val result = validateWithResult<String, Pokemon?> {
                val pokemon: Pokemon? = null
                pokemon.ensureNotNull { "No Pokemon found in party" }
            }
            result shouldBe Validated.invalid(listOf("No Pokemon found in party"))
        }
    }

    context("ValidationScope value check extension") {
        test("check on value with passing condition") {
            val result = validateWithResult<String, Pokemon> {
                Pokemon(25, "Pikachu", 50).ensure({ it.level > 10 }) { "Level too low" }
            }
            result shouldBe valid(Pokemon(25, "Pikachu", 50))
        }

        test("check on value with failing condition") {
            val result = validateWithResult<String, Pokemon> {
                Pokemon(25, "Pikachu", 5).ensure({ it.level > 10 }) { "Level too low to evolve" }
            }
            result shouldBe Validated.invalid(listOf("Level too low to evolve"))
        }

        test("check on nullable with null skips check") {
            val result = validateWithResult<String, Pokemon?> {
                val pokemon: Pokemon? = null
                pokemon.ensure({ it.level > 10 }) { "Level too low" }
            }
            result shouldBe valid(null)
        }

        test("check on nullable with non-null checks condition") {
            val result = validateWithResult<String, Pokemon?> {
                val pokemon: Pokemon? = Pokemon(25, "Pikachu", 5)
                pokemon.ensure({ it.level > 10 }) { "Level too low" }
            }
            result shouldBe Validated.invalid(listOf("Level too low"))
        }
    }

    context("ValidationScope demand") {
        test("demand with true condition continues") {
            val result = validateWithResult<String, String> {
                demand(true) { "Should not fail" }
                "Pikachu caught!"
            }
            result shouldBe valid("Pikachu caught!")
        }

        test("demand with false condition short-circuits") {
            var reached = false
            val result = validateWithResult<String, String> {
                demand(false) { "Pokeball missed!" }
                reached = true
                "Should not reach"
            }
            result shouldBe Validated.invalidOne("Pokeball missed!")
            reached shouldBe false
        }

        test("demand with lambda condition") {
            val result = validateWithResult<String, String> {
                demand({ false }) { "Lambda failed" }
                "Unreachable"
            }
            result shouldBe Validated.invalidOne("Lambda failed")
        }

        test("demand on value with passing condition") {
            val result = validateWithResult<String, Pokemon> {
                Pokemon(25, "Pikachu", 50).demand({ it.level >= 50 }) { "Not ready for Elite Four" }
            }
            result shouldBe valid(Pokemon(25, "Pikachu", 50))
        }

        test("demand on value with failing condition short-circuits") {
            var reached = false
            val result = validateWithResult<String, Pokemon> {
                Pokemon(25, "Pikachu", 10).demand({ it.level >= 50 }) { "Not ready for Elite Four" }
                reached = true
                Pokemon(25, "Pikachu", 10)
            }
            result shouldBe Validated.invalidOne("Not ready for Elite Four")
            reached shouldBe false
        }
    }

    context("ValidationScope demandNotNull") {
        test("demandNotNull with non-null continues") {
            val result = validateWithResult<String, Pokemon> {
                val pokemon: Pokemon? = Pokemon(151, "Mew", 100)
                pokemon.demandNotNull { "Mew not found" }
            }
            result shouldBe valid(Pokemon(151, "Mew", 100))
        }

        test("demandNotNull with null short-circuits") {
            var reached = false
            val result = validateWithResult<String, Pokemon> {
                val pokemon: Pokemon? = null
                val mew = pokemon.demandNotNull { "Mew not found" }
                reached = true
                mew
            }
            result shouldBe Validated.invalidOne("Mew not found")
            reached shouldBe false
        }
    }

    context("ValidationScope checkValue") {
        test("checkValue with valid returns value") {
            val result = validateWithResult<String, Pokemon?> {
                ensureValue(valid(Pokemon(25, "Pikachu", 50)))
            }
            result shouldBe valid(Pokemon(25, "Pikachu", 50))
        }

        test("checkValue with invalid adds errors and returns null") {
            val result = validateWithResult<String, Pokemon?> {
                ensureValue(Validated.invalid<String>(listOf("Error1", "Error2")))
            }
            result shouldBe Validated.invalid(listOf("Error1", "Error2"))
        }
    }

    context("ValidationScope demandValue") {
        test("demandValue with valid returns value") {
            val result = validateWithResult<String, Pokemon> {
                demandValue(valid(Pokemon(143, "Snorlax", 30)))
            }
            result shouldBe valid(Pokemon(143, "Snorlax", 30))
        }

        test("demandValue with invalid short-circuits") {
            var reached = false
            val result = validateWithResult<String, Pokemon> {
                val pokemon = demandValue<Pokemon>(Validated.invalidOne("Snorlax is blocking the way"))
                reached = true
                pokemon
            }
            result shouldBe Validated.invalidOne("Snorlax is blocking the way")
            reached shouldBe false
        }
    }

    context("validate function") {
        test("validate with no errors returns unit") {
            val result = validate<String> {
                ensure(true) { "No error" }
            }
            result shouldBe Validated.unit()
        }

        test("validate accumulates all errors") {
            val result = validate<String> {
                ensure(false) { "Badge 1 missing" }
                ensure(false) { "Badge 2 missing" }
                ensure(true) { "Badge 3 present" }
            }
            result shouldBe Validated.invalid(listOf("Badge 1 missing", "Badge 2 missing"))
        }
    }

    context("validated function") {
        test("validated returns wrapped value on success") {
            val result = validateWithResult<String, Pokemon> {
                ensure(true) { "No error" }
                Pokemon(94, "Gengar", 45)
            }
            result shouldBe valid(Pokemon(94, "Gengar", 45))
        }

        test("validated returns errors on failure") {
            val result = validateWithResult<String, Pokemon> {
                ensure(false) { "Ghost type needed" }
                ensure(false) { "Level 25 required" }
                Pokemon(94, "Gengar", 45)
            }
            result shouldBe Validated.invalid(listOf("Ghost type needed", "Level 25 required"))
        }

        test("validated short-circuits on demand failure") {
            val result = validateWithResult<String, Pokemon> {
                ensure(false) { "First error collected" }
                demand(false) { "Demand failed - stops here" }
                ensure(false) { "Never reached" }
                Pokemon(94, "Gengar", 45)
            }
            result shouldBe Validated.invalid(listOf("First error collected", "Demand failed - stops here"))
        }
    }

    context("errors property") {
        test("errors returns accumulated errors") {
            val result = validate<String> {
                ensure(false) { "Pikachu fainted" }
                ensure(false) { "Charmander fainted" }
                errors shouldBe listOf("Pikachu fainted", "Charmander fainted")
            }
            result shouldBe Validated.invalid(listOf("Pikachu fainted", "Charmander fainted"))
        }
    }
})
