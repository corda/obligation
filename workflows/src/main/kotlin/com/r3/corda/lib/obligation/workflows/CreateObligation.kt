package com.r3.corda.lib.obligation.workflows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.ci.RequestKeyFlow
import com.r3.corda.lib.ci.RequestKeyResponder
import com.r3.corda.lib.obligation.commands.ObligationCommands
import com.r3.corda.lib.obligation.contracts.ObligationContract
import com.r3.corda.lib.obligation.states.Obligation
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.seconds
import net.corda.core.utilities.unwrap
import java.security.PublicKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@CordaSerializable
enum class InitiatorRole {
    OBLIGOR,
    OBLIGEE
}

@InitiatingFlow
@StartableByRPC
class CreateObligationInitiator<T : TokenType>(
        private val amount: Amount<T>,
        private val role: InitiatorRole,
        private val counterparty: Party,
        private val dueBy: Instant? = null,
        private val anonymous: Boolean = true
) : FlowLogic<WireTransaction>() {

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
    private fun createAnonymousObligation(ourSession: FlowSession, lenderFlow: FlowSession): Pair<Obligation<T>, PublicKey> {
        val anonymousObligor = subFlow(RequestKeyFlow(lenderFlow))
        val anonymousMe = subFlow(RequestKeyFlow(ourSession))
        return createObligation(us = anonymousMe, them = anonymousObligor)
    }

    private fun createObligation(us: AbstractParty, them: AbstractParty): Pair<Obligation<T>, PublicKey> {
        check(us != them) { "You cannot create an obligation to yourself" }
        val obligation = when (role) {
            InitiatorRole.OBLIGEE -> Obligation(amount, them, us, dueBy)
            InitiatorRole.OBLIGOR -> Obligation(amount, us, them, dueBy)
        }
        return Pair(obligation, us.owningKey)
    }

    @Suspendable
    override fun call(): WireTransaction {
        // Step 1. Initialisation.
        progressTracker.currentStep = INITIALISING
        val ourSession = initiateFlow(ourIdentity)
        val lenderFlow = initiateFlow(counterparty)
        val (obligation, signingKey) = if (anonymous) {
            createAnonymousObligation(ourSession = ourSession, lenderFlow = lenderFlow)
        } else {
            createObligation(us = ourIdentity, them = counterparty)
        }
        lenderFlow.send(anonymous)

        // Step 2. Check parameters.
        if (dueBy != null) {
            val todayUTC = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC)
            require(dueBy > todayUTC) {
                "Due by date must be in the future."
            }
        }

        // Step 3. Building.
        progressTracker.currentStep = BUILDING
        val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
                ?: throw FlowException("No available notary.")
        val utx = TransactionBuilder(notary = notary).apply {
            addOutputState(obligation, ObligationContract.CONTRACT_REF)
            val signers = obligation.participants.map { it.owningKey }
            addCommand(ObligationCommands.Create(), signers)
            setTimeWindow(serviceHub.clock.instant(), 30.seconds)
        }

        // Step 4. Sign the transaction.
        progressTracker.currentStep = SIGNING
        val ptx = serviceHub.signInitialTransaction(utx, signingKey)

        // Step 5. Get the counterparty signature.
        progressTracker.currentStep = COLLECTING
        val stx = subFlow(CollectSignaturesFlow(
                partiallySignedTx = ptx,
                sessionsToCollectFrom = setOf(lenderFlow),
                myOptionalKeys = listOf(signingKey),
                progressTracker = COLLECTING.childProgressTracker())
        )

        // Step 6. Finalise and return the transaction.
        progressTracker.currentStep = FINALISING
        val ntx = subFlow(FinalityFlow(stx, setOf(lenderFlow), FINALISING.childProgressTracker()))
        return ntx.tx
    }
}

@InitiatedBy(CreateObligationInitiator::class)
class CreateObligationResponder(val otherFlow: FlowSession) : FlowLogic<WireTransaction>() {
    @Suspendable
    override fun call(): WireTransaction {
        val isAnonymous = otherFlow.receive<Boolean>().unwrap { it }
        if (isAnonymous) subFlow(RequestKeyResponder(otherSession = otherFlow))
        val flow = object : SignTransactionFlow(otherFlow) {
            @Suspendable
            override fun checkTransaction(btx: SignedTransaction) {
                // TODO: Do some basic checking here.
                // Reach out to human operator when HCI is available.
            }
        }
        val stx = subFlow(flow)
        // Suspend this flow until the transaction is committed.
        return subFlow(ReceiveFinalityFlow(otherFlow, stx.id)).tx
    }
}