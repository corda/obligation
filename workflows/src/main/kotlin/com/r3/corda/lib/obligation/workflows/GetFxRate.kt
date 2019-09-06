package com.r3.corda.lib.obligation.workflows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.obligation.types.FxRateRequest
import com.r3.corda.lib.obligation.types.FxRateResponse
import com.r3.corda.lib.obligation.utils.AbstractGetFxRate
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap

class GetFxRate(private val request: FxRateRequest, private val oracle: Party) : AbstractGetFxRate() {
    @Suspendable
    override fun call(): FxRateResponse {
        val session = initiateFlow(oracle)
        return session.sendAndReceive<FxRateResponse>(request).unwrap { it }
    }
}