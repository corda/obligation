package com.r3.corda.lib.obligation.flows

import com.r3.corda.lib.obligation.states.Obligation
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.testing.node.StartedMockNode

fun StartedMockNode.queryObligationById(linearId: UniqueIdentifier): StateAndRef<Obligation<TokenType>> {
    return transaction {
        val query = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
        services.vaultService.queryBy<Obligation<TokenType>>(query).states.single()
    }
}