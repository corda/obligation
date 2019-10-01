package com.r3.corda.lib.obligation.workflows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.obligation.api.AbstractGetFxOracleSignature
import com.r3.corda.lib.obligation.contracts.commands.ObligationCommands
import net.corda.core.contracts.Command
import net.corda.core.crypto.TransactionSignature
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap
import java.util.function.Predicate

/** Sends only a filtered transaction to the Oracle. */
class GetFxRateOracleSignature(private val ptx: SignedTransaction, val oracle: Party) : AbstractGetFxOracleSignature() {
    @Suspendable
    override fun call(): TransactionSignature {
        val ftx = ptx.buildFilteredTransaction(Predicate {
            when (it) {
                is Command<*> ->
                    oracle.owningKey in it.signers && it.value is ObligationCommands.Novate.UpdateFaceAmountToken<*, *>
                else -> false
            }
        })
        val session = initiateFlow(oracle)
        return session.sendAndReceive<TransactionSignature>(ftx).unwrap { it }
    }
}