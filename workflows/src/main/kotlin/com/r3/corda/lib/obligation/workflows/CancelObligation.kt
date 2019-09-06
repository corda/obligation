package com.r3.corda.lib.obligation.workflows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.obligation.utils.getLinearStateById
import com.r3.corda.lib.obligation.utils.resolver
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker


@InitiatingFlow
@StartableByRPC
class CancelObligationInitiator(val linearId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {

    companion object {
        object INITIALISING : ProgressTracker.Step("Performing initial steps.")
        object BUILDING : ProgressTracker.Step("Building and verifying transaction.")
        object SIGNING : ProgressTracker.Step("signing transaction.")

        object COLLECTING : ProgressTracker.Step("Collecting counterparty signature.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object FINALISING : ProgressTracker.Step("Finalising transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(INITIALISING, BUILDING, SIGNING, COLLECTING, FINALISING)
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        // Get the obligation from our vault.
        progressTracker.currentStep = INITIALISING
        val obligationStateAndRef = getLinearStateById<com.r3.corda.lib.obligation.states.Obligation<TokenType>>(linearId, serviceHub)
                ?: throw IllegalArgumentException("LinearId not recognised.")
        val obligation = obligationStateAndRef.state.data
        val obligationWithWellKnownParties = obligation.withWellKnownIdentities(resolver)
        // Generate output and required signers list based based upon supplied command.

        // Create the new transaction.
        progressTracker.currentStep = BUILDING
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val signers = obligation.participants.map { it.owningKey }
        val utx = TransactionBuilder(notary = notary).apply {
            addInputState(obligationStateAndRef)
            addCommand(com.r3.corda.lib.obligation.commands.ObligationCommands.Cancel(obligation.linearId), signers)
        }

        // Get the counterparty and our signing key.
        val (us, counterparty) = if (obligationWithWellKnownParties.obligor == ourIdentity) {
            Pair(obligation.obligor, obligationWithWellKnownParties.obligee)
        } else {
            Pair(obligation.obligee, obligationWithWellKnownParties.obligor)
        }

        // Sign it.
        progressTracker.currentStep = SIGNING
        val ptx = serviceHub.signInitialTransaction(utx, us.owningKey)

        // Get the counterparty's signature.
        progressTracker.currentStep = COLLECTING
        val counterpartyFlow = initiateFlow(counterparty as Party)
        val stx = subFlow(CollectSignaturesFlow(
                partiallySignedTx = ptx,
                sessionsToCollectFrom = setOf(counterpartyFlow),
                myOptionalKeys = listOf(us.owningKey),
                progressTracker = COLLECTING.childProgressTracker()
        ))

        progressTracker.currentStep = FINALISING
        return subFlow(FinalityFlow(stx, setOf(counterpartyFlow), FINALISING.childProgressTracker()))
    }
}

@InitiatedBy(CancelObligationInitiator::class)
class CancelObligationResponder(val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val flow = object : SignTransactionFlow(otherSession) {
            @Suspendable
            override fun checkTransaction(stx: SignedTransaction) {
                // TODO: Do some basic checking here.
                // Reach out to human operator when HCI is available.
            }
        }
        val stx = subFlow(flow)
        return subFlow(ReceiveFinalityFlow(otherSession, stx.id))
    }
}