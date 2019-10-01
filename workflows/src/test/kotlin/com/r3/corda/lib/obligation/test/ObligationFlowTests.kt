package com.r3.corda.lib.obligation.test

import com.r3.corda.lib.obligation.contracts.commands.ObligationCommands
import com.r3.corda.lib.obligation.contracts.states.Obligation
import com.r3.corda.lib.obligation.workflows.CancelObligation
import com.r3.corda.lib.obligation.workflows.CreateObligation
import com.r3.corda.lib.obligation.workflows.InitiatorRole
import com.r3.corda.lib.obligation.workflows.NovateObligation
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.contracts.utilities.singleOutput
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.money.USD
import com.r3.corda.lib.tokens.money.XRP
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals

class ObligationFlowTests {

    private lateinit var mockNet: MockNetwork
    private lateinit var aliceNode: StartedMockNode
    private lateinit var bobNode: StartedMockNode
    private lateinit var oracleNode: StartedMockNode
    private lateinit var alice: Party
    private lateinit var bob: Party
    private lateinit var oracle: Party
    private lateinit var notary: Party
    private val DUE_DATE: Instant = Instant.now().plusSeconds(10000)

    @Before
    fun before() {
        mockNet = MockNetwork(
                MockNetworkParameters(
                        networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                        cordappsForAllNodes = listOf(
                                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                                TestCordapp.findCordapp("com.r3.corda.lib.tokens.money"),
                                TestCordapp.findCordapp("com.r3.corda.lib.ci"),
                                TestCordapp.findCordapp("com.r3.corda.lib.obligation.contracts"),
                                TestCordapp.findCordapp("com.r3.corda.lib.obligation.api"),
                                TestCordapp.findCordapp("com.r3.corda.lib.obligation.workflows"),
                                TestCordapp.findCordapp("com.r3.corda.lib.obligation.oracle.flows")
                        ),
                        threadPerNode = true
                )
        )
        aliceNode = mockNet.createPartyNode(ALICE_NAME)
        bobNode = mockNet.createPartyNode(BOB_NAME)
        oracleNode = mockNet.createPartyNode(CHARLIE_NAME)
        alice = aliceNode.info.singleIdentity()
        bob = bobNode.info.singleIdentity()
        oracle = oracleNode.info.singleIdentity()
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
        val tx = aliceNode.startFlow(CreateObligation(1000 of GBP, InitiatorRole.OBLIGOR, bob, DUE_DATE)).let {
            it.getOrThrow()
        }
        val obligationId = tx.toLedgerTransaction(aliceNode.services).singleOutput<Obligation<TokenType>>().linearId

        // Cancel it
        aliceNode.startFlow(CancelObligation(obligationId)).let {
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
        val tx = aliceNode.startFlow(CreateObligation(1000 of GBP, InitiatorRole.OBLIGOR, bob, DUE_DATE)).let {
            it.getOrThrow()
        }
        val obligationId = tx.toLedgerTransaction(aliceNode.services).singleOutput<Obligation<TokenType>>().linearId

        // Check both parties have the same obligation.
        Thread.sleep(100)
        val aObligation = aliceNode.queryObligationById(obligationId)
        val bObligation = bobNode.queryObligationById(obligationId)
        assertEquals(aObligation, bObligation)
    }

    @Test
    fun `anonymous obligation parties are confidential to the parties involved`() {
        // Create obligation
        val publicTx = aliceNode.startFlow(CreateObligation(1000 of GBP, InitiatorRole.OBLIGOR, bob, DUE_DATE, false)).let {
            it.getOrThrow()
        }
        // Create anonymous obligation
        val anonTx = aliceNode.startFlow(CreateObligation(1000 of GBP, InitiatorRole.OBLIGOR, bob, DUE_DATE, true)).let {
            it.getOrThrow()
        }
        val publicObligationId = publicTx.toLedgerTransaction(aliceNode.services).singleOutput<Obligation<TokenType>>().linearId
        val anonObligationId = anonTx.toLedgerTransaction(aliceNode.services).singleOutput<Obligation<TokenType>>().linearId

        val publicObligation = aliceNode.queryObligationById(publicObligationId)
        val anonObligation = aliceNode.queryObligationById(anonObligationId)

        val publicObligee = publicObligation.state.data.obligee
        val publicObligor = publicObligation.state.data.obligor

        val anonObligee = anonObligation.state.data.obligee
        val anonObligor = anonObligation.state.data.obligor

        // Oracle can resolve the parties from the network map cache
        assertThat(oracleNode.services.identityService.wellKnownPartyFromAnonymous(publicObligee)).isEqualTo(bob)
        assertThat(oracleNode.services.identityService.wellKnownPartyFromAnonymous(publicObligor)).isEqualTo(alice)

        // Oracle shouldn't be able to resolve the anonymous parties
        assertNull(oracleNode.services.identityService.wellKnownPartyFromAnonymous(anonObligee))
        assertNull(oracleNode.services.identityService.wellKnownPartyFromAnonymous(anonObligor))

    }

    @Test
    fun `novate obligation currency`() {
        // Create obligation
        val tx = aliceNode.startFlow(CreateObligation(1000 of GBP, InitiatorRole.OBLIGOR, bob, DUE_DATE)).let {
            it.getOrThrow()
        }
        val obligationId = tx.toLedgerTransaction(aliceNode.services).singleOutput<Obligation<TokenType>>().linearId

        val novationCommand = ObligationCommands.Novate.UpdateFaceAmountToken(
                oldToken = USD,
                newToken = XRP,
                oracle = oracleNode.services.myInfo.legalIdentities.first(),
                fxRate = null
        )

        val result = aliceNode.transaction { aliceNode.startFlow(NovateObligation(obligationId, novationCommand)).getOrThrow() }
        val novatedObligation = result.toLedgerTransaction(aliceNode.services).singleOutput<Obligation<TokenType>>()
        assertThat(novatedObligation.faceAmount.token).isEqualTo(XRP)
    }
}