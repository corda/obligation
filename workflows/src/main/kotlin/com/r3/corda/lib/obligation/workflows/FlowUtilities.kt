package com.r3.corda.lib.obligation.workflows

import com.r3.corda.lib.ci.registerKeyToParty
import com.r3.corda.lib.obligation.states.Obligation
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.CordaInternal
import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
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

@CordaInternal
fun ServiceHub.createNewKey() : AnonymousParty {
    val newKey = keyManagementService.freshKey()
    registerKeyToParty(newKey, this.myInfo.legalIdentities.first(), this)
    return AnonymousParty(newKey)
}

