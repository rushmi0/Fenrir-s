package org.fenrirs.relay.core.nip40

import io.micronaut.context.annotation.Bean
import jakarta.inject.Inject
import org.fenrirs.stored.statement.StoredServiceImpl
import org.slf4j.LoggerFactory

@Bean
class ExpirationTimestamp @Inject constructor(private val sqlExec: StoredServiceImpl) {




    companion object {
        private val LOG = LoggerFactory.getLogger(ExpirationTimestamp::class.java)
    }

}


