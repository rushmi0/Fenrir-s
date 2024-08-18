package org.fenrirs

import jakarta.inject.Inject
import org.fenrirs.relay.modules.FiltersX
import org.fenrirs.relay.modules.TAG_E
import org.fenrirs.relay.policy.NostrRelayConfig
import org.fenrirs.stored.DatabaseFactory
import org.fenrirs.stored.Environment
import org.fenrirs.stored.statement.StoredServiceImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
