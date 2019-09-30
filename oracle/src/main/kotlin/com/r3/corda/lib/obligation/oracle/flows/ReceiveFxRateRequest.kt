package com.r3.corda.lib.obligation.oracle.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.obligation.api.AbstractGetFxRate
import com.r3.corda.lib.obligation.api.FxRate
import com.r3.corda.lib.obligation.api.FxRateRequest
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.utilities.unwrap
import java.math.BigDecimal

@InitiatedBy(AbstractGetFxRate::class)
class ReceiveFxRateRequest(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val request = otherSession.receive<FxRateRequest>().unwrap { it }
        val response = FxRate(request.baseCurrency, request.counterCurrency, request.time, BigDecimal(2.0))
        otherSession.send(response)
    }
}