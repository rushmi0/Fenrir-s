package org.fenrirs.storage.statement

import jakarta.inject.Singleton

import org.fenrirs.storage.DatabaseFactory.configTask
import org.fenrirs.storage.service.Operator
import org.fenrirs.storage.service.OperatorStore
import org.fenrirs.storage.table.OPERATOR

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

@Singleton
object OperatorStoreImpl : OperatorStore {

    override fun isEmpty(): Boolean = configTask {
        OPERATOR.selectAll().limit(1).empty()
    }

    override fun find(pubkey: String): Operator? = configTask {
        OPERATOR.selectAll().where { OPERATOR.PUBKEY eq pubkey }.firstOrNull()?.toOperator()
    }

    override fun all(): List<Operator> = configTask {
        OPERATOR.selectAll().map { it.toOperator() }
    }

    override fun add(pubkey: String, role: String) {
        configTask {
            OPERATOR.insert {
                it[PUBKEY] = pubkey
                it[ROLE] = role
                it[CREATED_AT] = System.currentTimeMillis() / 1000
            }
        }
    }

    override fun remove(pubkey: String) {
        configTask {
            OPERATOR.deleteWhere { OPERATOR.PUBKEY eq pubkey }
        }
    }

    private fun ResultRow.toOperator() = Operator(
        pubkey = this[OPERATOR.PUBKEY],
        role = this[OPERATOR.ROLE],
        createdAt = this[OPERATOR.CREATED_AT]
    )
}
