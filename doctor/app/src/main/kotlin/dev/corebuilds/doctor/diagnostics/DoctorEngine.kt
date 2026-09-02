package dev.corebuilds.doctor.diagnostics

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

object DoctorEngine {

    fun run(context: Context, input: DoctorInput): Flow<CheckResult> = flow {
        emit(NetworkChecks.checkDns())
        emit(NetworkChecks.checkVpn(context))

        if (input.addonUrl.isNotBlank()) {
            emit(AddonChecks.checkManifest(input.addonUrl))
            emit(AddonChecks.probeStreams(input.addonUrl))
        }

        if (input.rdKey.isNotBlank()) {
            emit(DebridChecks.checkRealDebrid(input.rdKey))
        }
        if (input.torboxKey.isNotBlank()) {
            emit(DebridChecks.checkTorBox(input.torboxKey))
        }
    }.flowOn(Dispatchers.IO)
}
