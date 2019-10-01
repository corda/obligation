package com.r3.corda.lib.obligation.workflows

import com.r3.corda.lib.obligation.contracts.commands.ObligationCommands
import com.r3.corda.lib.obligation.contracts.states.Obligation
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.singleOutput
import net.corda.core.CordaInternal
import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.CordaSerializable
import java.security.PublicKey
import java.time.Instant

/** Gets a linear state by unique identifier. */
inline fun <reified T : LinearState> getLinearStateById(linearId: UniqueIdentifier, services: ServiceHub): StateAndRef<T>? {
    return services.vaultService.queryBy<T>(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))).states.singleOrNull()
}

/** Lambda for resolving an [AbstractParty] to a [Party]. */
val FlowLogic<*>.resolver get() = { abstractParty: AbstractParty ->
    serviceHub.identityService.requireWellKnownPartyFromAnonymous(abstractParty)
}

@CordaSerializable
enum class InitiatorRole {
    OBLIGOR,
    OBLIGEE
}

@CordaInternal
fun <T : TokenType>createObligation(us: AbstractParty, them: AbstractParty, amount: Amount<T>, role: InitiatorRole, dueBy: Instant?): Pair<Obligation<T>, PublicKey> {
        check(us != them) { "You cannot create an obligation to yourself" }
        val obligation = when (role) {
            InitiatorRole.OBLIGEE -> Obligation(amount, them, us, dueBy)
            InitiatorRole.OBLIGOR -> Obligation(amount, us, them, dueBy)
        }
        return Pair(obligation, us.owningKey)
}

/**
 * Helper flow for node driver test where we do not have access to [ServiceHub].
 */
@StartableByRPC
@InitiatingFlow
class CreateAndReturnObligationId(private val amount: Amount<TokenType>,
                                  private val role: InitiatorRole,
                                  private val counterparty: Party,
                                  private val dueBy: Instant? = null,
                                  private val anonymous: Boolean = true
) : FlowLogic<UniqueIdentifier>() {
    override fun call(): UniqueIdentifier {
        val wireTx = subFlow(CreateObligation(amount, role, counterparty, dueBy, anonymous))
        return wireTx.toLedgerTransaction(serviceHub).singleOutput<Obligation<TokenType>>().linearId
    }
}

/**
 * Responder flow.
 */
@InitiatedBy(CreateAndReturnObligationId::class)
class CreateObligationResonder(private val otherSide: FlowSession) : FlowLogic<Unit>() {
    override fun call() {
        subFlow(CreateObligationResponder(otherSide))
    }
}

/**
 * Helper flow for node driver test where we do not have access to [ServiceHub].
 */
@StartableByRPC
@InitiatingFlow
class NovateAndReturnObligation( private val linearId: UniqueIdentifier,
                                 private val novationCommand: ObligationCommands.Novate) : FlowLogic<Obligation<TokenType>>() {
    override fun call(): Obligation<TokenType> {
        val result = subFlow(NovateObligation(linearId, novationCommand))
        return result.toLedgerTransaction(serviceHub).singleOutput()
    }
}

@InitiatedBy(NovateAndReturnObligation::class)
class NovateResponder(private val otherSide: FlowSession) : FlowLogic<Unit>() {
    override fun call() {
        subFlow(NovateObligationResponder(otherSide))
    }

}
