package com.r3.corda.lib.obligation

import com.r3.corda.lib.obligation.states.Obligation
import com.r3.corda.lib.obligation.workflows.CancelObligationInitiator
import com.r3.corda.lib.obligation.workflows.CreateObligationInitiator
import com.r3.corda.lib.obligation.workflows.InitiatorRole
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.contracts.utilities.singleOutput
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.money.XRP
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.finance.AMOUNT
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals

class ObligationFlowTests {

    private lateinit var mockNet: MockNetwork
    private lateinit var aliceNode: StartedMockNode
    private lateinit var bobNode: StartedMockNode
    private lateinit var charlieNode: StartedMockNode
    private lateinit var alice: Party
    private lateinit var bob: Party
    private lateinit var charlie: Party
    private lateinit var notary: Party

    @Before
    fun before() {
        mockNet = MockNetwork(
                MockNetworkParameters(
                        networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                        cordappsForAllNodes = listOf(
                                TestCordapp.findCordapp("com.r3.corda.lib.tokens.money"),
                                TestCordapp.findCordapp("com.r3.corda.lib.ci"),
                                TestCordapp.findCordapp("com.r3.corda.lib.obligation.contracts"),
                                TestCordapp.findCordapp("com.r3.corda.lib.obligation.workflows")
                        ),
                        threadPerNode = true
                )
        )
        aliceNode = mockNet.createPartyNode(ALICE_NAME)
        bobNode = mockNet.createPartyNode(BOB_NAME)
        charlieNode = mockNet.createPartyNode(CHARLIE_NAME)
        alice = aliceNode.info.singleIdentity()
        bob = bobNode.info.singleIdentity()
        charlie = charlieNode.info.singleIdentity()
        notary = mockNet.defaultNotaryIdentity

        mockNet.startNodes()
    }

    @After
    fun after() {
        mockNet.stopNodes()
    }

    @Test
    fun `create obligation`() {

    val tx = aliceNode.startFlow(CreateObligationInitiator(AMOUNT(10000, GBP), InitiatorRole.OBLIGOR, bob, Instant.now().plusSeconds(10000))).let {
        it.getOrThrow()
    }

    val obligationId = tx.toLedgerTransaction(aliceNode.services).singleOutput<Obligation<TokenType>>().linearId

    val query = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(obligationId), status = Vault.StateStatus.CONSUMED)

    aliceNode.startFlow(CancelObligationInitiator(obligationId))

    val query2 = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(obligationId), status = Vault.StateStatus.UNCONSUMED)
    }

    @Test
    fun `newly created obligation is stored in vaults of participants`() {

        val tx = aliceNode.startFlow(CreateObligationInitiator(AMOUNT(10000, GBP), InitiatorRole.OBLIGOR, bob, Instant.now().plusSeconds(10000))).let {
            it.getOrThrow()
        }

        val obligationId = tx.toLedgerTransaction(aliceNode.services).singleOutput<Obligation<TokenType>>().linearId

        // Check both parties have the same obligation.
        val aObligation = aliceNode.queryObligationById(obligationId)
        val bObligation = bobNode.queryObligationById(obligationId)
        assertEquals(aObligation, bObligation)
    }

}