package org.fenrirs.relay.core.nip.nip01

import org.fenrirs.relay.models.FiltersX

object VerifyFilterX {

    private fun FiltersX.isValidIds(): ValidationResult {
        ids.forEach { id ->
            val isAllZeros = id.isNotEmpty() && id.all { it == '0' }
            if (id.length != 64 && !isAllZeros) {
                return ValidationResult.invalid("invalid: id '$id' should be 64 characters long or all zeros")
            }
        }
        return ValidationResult.Valid
    }

    private fun FiltersX.isValidAuthors(): ValidationResult {
        authors.forEach { author ->
            if (author.length != 64) {
                return ValidationResult.invalid("invalid: author '$author' should be 64 characters long")
            }
        }
        return ValidationResult.Valid
    }

    private fun FiltersX.isValidSinceUntil(): ValidationResult {
        if (since != null && until != null && since > until) {
            return ValidationResult.invalid("invalid: since '$since' should be less than or equal to until '$until'")
        }
        return ValidationResult.Valid
    }

    private fun FiltersX.isValidLimit(): ValidationResult {
        if (limit != null && limit < 0) {
            return ValidationResult.invalid("invalid: limit '$limit' should be a non-negative number")
        }
        return ValidationResult.Valid
    }

    fun FiltersX.validate(): ValidationResult {
        val idsResult = isValidIds()
        if (!idsResult.isValid) return idsResult

        val authorsResult = isValidAuthors()
        if (!authorsResult.isValid) return authorsResult

        val sinceUntilResult = isValidSinceUntil()
        if (!sinceUntilResult.isValid) return sinceUntilResult

        return isValidLimit()
    }
}