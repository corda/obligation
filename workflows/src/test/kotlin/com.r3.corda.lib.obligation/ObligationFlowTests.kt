package com.r3.corda.lib.obligation

import com.r3.corda.lib.obligation.commands.ObligationCommands
import com.r3.corda.lib.obligation.states.Obligation
import com.r3.corda.lib.obligation.utils.InitiatorRole
import com.r3.corda.lib.obligation.workflows.CancelObligationInitiator
import com.r3.corda.lib.obligation.workflows.CreateObligation
import com.r3.corda.lib.obligation.workflows.NovateObligation
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.singleOutput
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.money.USD
import com.r3.corda.lib.tokens.money.XRP
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
import org.junit.Assert.assertNull
import org.assertj.core.api.Assertions.assertThat
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
    private val DUE_DATE: Instant = Instant.now().plusSeconds(10000)

    @Before
    fun before() {
        mockNet = MockNetwork(
                MockNetworkParameters(
                        networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                        cordappsForAllNodes = listOf(
                                TestCordapp.findCordapp("com.r3.corda.lib.tokens.money"),
                                TestCordapp.findCordapp("com.r3.corda.lib.ci"),
                                TestCordapp.findCordapp("com.r3.corda.lib.obligation.contracts"),
                                TestCordapp.findCordapp("com.r3.corda.lib.obligation.workflows"),
                                TestCordapp.findCordapp("com.r3.corda.lib.obligation.utils")
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
    fun `create obligation then cancel it`() {
        // Create obligation
        val tx = aliceNode.startFlow(CreateObligation(AMOUNT(10000, GBP), InitiatorRole.OBLIGOR, bob, DUE_DATE)).let {
            it.getOrThrow()
        }
        val obligationId = tx.toLedgerTransaction(aliceNode.services).singleOutput<Obligation<TokenType>>().linearId

        // Cancel it
        aliceNode.startFlow(CancelObligationInitiator(obligationId)).let {
            it.getOrThrow()
        }

        // Check the obligation state has been exited.
        val query = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(obligationId), status = Vault.StateStatus.UNCONSUMED)
        // Hack: It takes a moment for the vaults to update...
        Thread.sleep(100)
        assertNull(aliceNode.transaction { aliceNode.services.vaultService.queryBy<Obligation<TokenType>>(query).states.singleOrNull() })
        assertNull(bobNode.transaction { bobNode.services.vaultService.queryBy<Obligation<TokenType>>(query).states.singleOrNull() })
    }

    @Test
    fun `newly created obligation is stored in vaults of participants`() {
        val tx = aliceNode.startFlow(CreateObligation(AMOUNT(10000, GBP), InitiatorRole.OBLIGOR, bob, DUE_DATE)).let {
            it.getOrThrow()
        }
        val obligationId = tx.toLedgerTransaction(aliceNode.services).singleOutput<Obligation<TokenType>>().linearId

        // Check both parties have the same obligation.
        val aObligation = aliceNode.queryObligationById(obligationId)
        val bObligation = bobNode.queryObligationById(obligationId)
        assertEquals(aObligation, bObligation)
    }

    @Test
    fun `anonymous obligation parties are confidential to the parties involved`() {
        // Create obligation
        val publicTx = aliceNode.startFlow(CreateObligation(AMOUNT(10000, GBP), InitiatorRole.OBLIGOR, bob, DUE_DATE, false)).let {
            it.getOrThrow()
        }
        // Create anonymous obligation
        val anonTx = aliceNode.startFlow(CreateObligation(AMOUNT(10000, GBP), InitiatorRole.OBLIGOR, bob, DUE_DATE)).let {
            it.getOrThrow()
        }
        val publicOblicationId = publicTx.toLedgerTransaction(aliceNode.services).singleOutput<Obligation<TokenType>>().linearId
        val anonOblicationId = anonTx.toLedgerTransaction(aliceNode.services).singleOutput<Obligation<TokenType>>().linearId

        val publicObligation = aliceNode.queryObligationById(publicOblicationId)
        val anonObligation = aliceNode.queryObligationById(anonOblicationId)

        val publicObligee = publicObligation.state.data.obligee
        val publicObligor = publicObligation.state.data.obligor

        val anonObligee = anonObligation.state.data.obligee
        val anonObligor = anonObligation.state.data.obligor

        // Charlie can resolve the parties from the network map cache
        assertThat(charlieNode.services.identityService.wellKnownPartyFromAnonymous(publicObligee)).isEqualTo(bob)
        assertThat(charlieNode.services.identityService.wellKnownPartyFromAnonymous(publicObligor)).isEqualTo(alice)

        // Charlie shouldn't be able to resolve the anonymous parties
        assertNull(charlieNode.services.identityService.wellKnownPartyFromAnonymous(anonObligee))
        assertNull(charlieNode.services.identityService.wellKnownPartyFromAnonymous(anonObligor))

    }

    @Test
    fun `novate obligation currency`() {
        // Create obligation
        val tx = aliceNode.startFlow(CreateObligation(AMOUNT(10000, GBP), InitiatorRole.OBLIGOR, bob, DUE_DATE)).let {
            it.getOrThrow()
        }
        val obligationId = tx.toLedgerTransaction(aliceNode.services).singleOutput<Obligation<TokenType>>().linearId

        val novationCommand = ObligationCommands.Novate.UpdateFaceAmountToken(
                oldToken = USD,
                newToken = XRP,
                oracle = charlieNode.services.myInfo.legalIdentities.first(),
                fxRate = null
        )

        val result = aliceNode.transaction { aliceNode.startFlow(NovateObligation(obligationId, novationCommand)).getOrThrow() }
        val novatedObligation = result.toLedgerTransaction(aliceNode.services).singleOutput<Obligation<TokenType>>()
        assertThat(novatedObligation.faceAmount.token).isEqualTo(XRP)
    }
}