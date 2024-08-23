package org.fenrirs

import jakarta.inject.Inject
import org.fenrirs.relay.policy.FiltersX
import org.fenrirs.relay.policy.TAG_E
import org.fenrirs.relay.policy.NostrRelayConfig
import org.fenrirs.storage.DatabaseFactory
import org.fenrirs.storage.Environment
import org.fenrirs.storage.statement.StoredServiceImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FilterSystemTest {

    @Inject
    private lateinit var sqlExec: StoredServiceImpl

    @BeforeEach
    fun setup() {
        // กำหนดค่า ENV ก่อนการใช้งาน DatabaseFactory.initialize()
        DatabaseFactory.ENV = Environment(NostrRelayConfig())

        // เรียกใช้งาน DatabaseFactory.initialize() หลังจากกำหนดค่า ENV
        DatabaseFactory.initialize()

        sqlExec = StoredServiceImpl(DatabaseFactory.ENV)
    }

    // ["REQ","hsZEOtaDsENYkP5H-JIWp",{"kinds": [1],"limit": 2}]
    @Test
    fun `test filterList`() {
        val query = FiltersX(
            kinds = setOf(1),
            limit = 100
        )
        val data = sqlExec.filterList(query)
        data!!.forEach(::println)
    }

    @Test
    fun `test filterList returns Event deleted`() {

        val eventId = "b80aa0a2e69e7ca5801a3848aa33e90a62fb30d27284639e1836c9ede8a9d298"

        val query = FiltersX(
            tags = mapOf(
                TAG_E to setOf(eventId)
            ),
            kinds = setOf(5)
        )

        val data = sqlExec.filterList(query)
            ?.takeIf { it.isNotEmpty() }
            ?.let { event ->
                event[0].tags
                    ?.filter { it.isNotEmpty() && it[0] == "e" }
                    ?.map { it[1] }
                    ?.first()
            }

        assertEquals(eventId, data)
    }

}
