package org.fenrirs.relay.core.nip01

import jakarta.inject.Singleton
import io.micronaut.context.annotation.Factory
import jakarta.inject.Inject

import org.fenrirs.relay.core.nip09.EventDeletion
import org.fenrirs.relay.core.nip13.ProofOfWork

import org.fenrirs.storage.statement.StoredServiceImpl
import org.fenrirs.storage.Environment


@Factory
class ProtocolFlowFactory @Inject constructor(
    private val sqlExec: StoredServiceImpl,
    private val nip09: EventDeletion,
    private val nip13: ProofOfWork,
    private val env: Environment
) {

    /**
     * ฟังก์ชันนี้ใช้สำหรับสร้างอินสแตนซ์ของ BasicProtocolFlow โดยอิงจากบริการที่มีอยู่
     * ซึ่งจะมีการสร้างเพียงอินสแตนซ์เดียว (Singleton) ของ BasicProtocolFlow
     * และจะถูกใช้ร่วมกันในแอปพลิเคชัน
     *
     * @return อินสแตนซ์ของ BasicProtocolFlow ที่ถูกสร้างด้วยบริการที่จำเป็น
     */
    @Singleton
    fun launchProtocol(): BasicProtocolFlow {
        return BasicProtocolFlow(sqlExec, nip09, nip13, env)
    }

}
