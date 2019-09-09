package com.r3.corda.lib.obligation.oracle.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.obligation.oracle.services.FxRateService
import com.r3.corda.lib.obligation.api.AbstractGetFxRate
import com.r3.corda.lib.obligation.types.FxRate
import com.r3.corda.lib.obligation.types.FxRateRequest
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.utilities.unwrap

@InitiatedBy(AbstractGetFxRate::class)
class ReceiveFxRateRequest(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val request = otherSession.receive<FxRateRequest>().unwrap { it }
        val fxRateService = serviceHub.cordaService(FxRateService::class.java)
        // Don't use the real Fx Oracle for testing.
        //val response = fxRateService.getRate(request)
        val response = FxRate(request.baseCurrency, request.counterCurrency, request.time, 2L)
        otherSession.send(response)
    }
}