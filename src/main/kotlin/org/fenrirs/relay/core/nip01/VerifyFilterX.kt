package org.fenrirs.relay.core.nip01

import org.fenrirs.relay.policy.FiltersX

object VerifyFilterX {

    private fun FiltersX.isValidIds(): Pair<Boolean, String> {
        ids.forEach { id ->
            if (id.length != 64 && !id.matches(Regex("0+"))) {
                return false to "invalid: id '$id' should be 64 characters long or all zeros"
            }
        }
        return true to ""
    }

    private fun FiltersX.isValidAuthors(): Pair<Boolean, String> {
        authors.forEach { author ->
            if (author.length != 64) {
                return false to "invalid: author '$author' should be 64 characters long"
            }
        }
        return true to ""
    }

    private fun FiltersX.isValidKinds(): Pair<Boolean, String> {
        kinds.forEach { kind ->
            if (kind !is Long) {
                return false to "invalid: kind '$kind' should be a number"
            }
        }
        return true to ""
    }


//    private fun FiltersX.isValidSinceUntil(): Pair<Boolean, String> {
//        if (since != null && until != null && since >= until) {
//            return false to "invalid: since '$since' should be less than until '$until'"
//        }
//        return true to ""
//    }

    private fun FiltersX.isValidLimit(): Pair<Boolean, String> {
        if (limit != null && limit < 0) {
            return false to "invalid: limit '$limit' should be a non-negative number"
        }
        return true to ""
    }

    fun FiltersX.validate(): Pair<Boolean, String> {
        return when {
            !isValidIds().first -> isValidIds()
            !isValidAuthors().first -> isValidAuthors()
            !isValidKinds().first -> isValidKinds()
            //!isValidSinceUntil().first -> isValidSinceUntil()
            !isValidLimit().first -> isValidLimit()
            else -> true to ""
        }
    }
}
