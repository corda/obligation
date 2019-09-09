package com.r3.corda.lib.obligation.workflows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.ci.RequestKeyFlow
import com.r3.corda.lib.ci.RequestKeyResponder
import com.r3.corda.lib.obligation.states.Obligation
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import java.security.PublicKey
import java.time.Instant

class CreateAnonymousObligation<T : TokenType>(
        private val lenderSession: FlowSession,
        private val amount: Amount<T>,
        private val role: InitiatorRole,
        private val dueBy: Instant? = null) : FlowLogic<Pair<Obligation<T>, PublicKey>>() {

    @Suspendable
    override fun call(): Pair<Obligation<T>, PublicKey> {
        val anonymousObligee = createNewKey(serviceHub)
        val anonymousObligor = subFlow(RequestKeyFlow(lenderSession))
        return createObligation(us = anonymousObligee, them = anonymousObligor, amount = amount, role = role, dueBy = dueBy)
    }
}

class CreateAnonymousObligationResponder(val otherFlow: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(RequestKeyResponder(otherSession = otherFlow))
    }
}