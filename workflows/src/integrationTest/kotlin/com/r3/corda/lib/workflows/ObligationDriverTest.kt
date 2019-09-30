package com.r3.corda.lib.workflows

import com.r3.corda.lib.obligation.commands.ObligationCommands
import com.r3.corda.lib.obligation.states.Obligation
import com.r3.corda.lib.obligation.workflows.*
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.money.XRP
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.testing.core.*
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant
import java.util.concurrent.Future
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObligationDriverTest {

    private val DUE_DATE: Instant = Instant.now().plusSeconds(10000)

    @Test
    fun `create an obligation, cancel it, create another and change the currency on it`() = withDriver {
        val aUser = User("aUser", "testPassword1", permissions = setOf(Permissions.all()))
        val bUser = User("bUser", "testPassword2", permissions = setOf(Permissions.all()))
        val cUser = User("cUser", "testPassword3", permissions = setOf(Permissions.all()))
        val (nodeA, nodeB, nodeC) = listOf(
                startNode(providedName = ALICE_NAME, rpcUsers = listOf(aUser)),
                startNode(providedName = BOB_NAME, rpcUsers = listOf(bUser)),
                startNode(providedName = CHARLIE_NAME, rpcUsers = listOf(cUser))
        ).waitForAll()

        verifyNodesResolve(nodeA, nodeB, nodeC)

        // Create obligation
        val obligationId = nodeA.rpc.startFlowDynamic(CreateAndReturnObligationId::class.java,
            1000 of GBP,
            InitiatorRole.OBLIGOR,
            nodeB.nodeInfo.legalIdentities.first(),
            DUE_DATE,
            false).returnValue.getOrThrow()

        // Cancel it
        nodeA.rpc.startFlow(::CancelObligation, obligationId).returnValue.getOrThrow()

        // Check the obligation state has been cancelled
        val query = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(obligationId), status = Vault.StateStatus.UNCONSUMED)

        // Hack: It takes a moment for the vaults to update...
        Thread.sleep(100)

        // Check the obligation has been cancelled
        assertNull(nodeA.rpc.vaultQueryBy<Obligation<TokenType>>(criteria = query).states.singleOrNull())
        assertNull(nodeB.rpc.vaultQueryBy<Obligation<TokenType>>(criteria = query).states.singleOrNull())

        // Create anonymous obligation
        val anonObligationId = nodeA.rpc.startFlowDynamic(CreateAndReturnObligationId::class.java,
                1000 of GBP,
                InitiatorRole.OBLIGOR,
                nodeB.nodeInfo.legalIdentities.first(),
                DUE_DATE,
                true).returnValue.getOrThrow()

        val novationCommand = ObligationCommands.Novate.UpdateFaceAmountToken(
                oldToken = GBP,
                newToken = XRP,
                oracle = nodeC.nodeInfo.singleIdentity(),
                fxRate = null
        )

        val novatedObligation = nodeA.rpc.startFlow(::NovateAndReturnObligation, anonObligationId, novationCommand).returnValue.getOrThrow()
        assertThat(novatedObligation.faceAmount.token).isEqualTo(XRP)
    }

    // Runs a test inside the Driver DSL, which provides useful functions for starting nodes, etc.
    private fun withDriver(test: DriverDSL.() -> Unit) = driver(
        DriverParameters(
            isDebug = true,
            startNodesInProcess = false,
            cordappsForAllNodes = listOf(
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.money"),
                    TestCordapp.findCordapp("com.r3.corda.lib.ci"),
                    TestCordapp.findCordapp("com.r3.corda.lib.obligation.contracts"),
                    TestCordapp.findCordapp("com.r3.corda.lib.obligation.oracle.flows"),
                    TestCordapp.findCordapp("com.r3.corda.lib.obligation.api"),
                    TestCordapp.findCordapp("com.r3.corda.lib.obligation.workflows")
            )
        )
    ) { test() }

    // Makes an RPC call to retrieve another node's name from the network map.
    private fun NodeHandle.resolveName(name: CordaX500Name) = rpc.wellKnownPartyFromX500Name(name)!!.name

    // Resolves a list of futures to a list of the promised values.
    private fun <T> List<Future<T>>.waitForAll(): List<T> = map { it.getOrThrow() }

    private fun verifyNodesResolve(nodeA: NodeHandle, nodeB: NodeHandle, nodeC: NodeHandle) {
        assertEquals(BOB_NAME, nodeA.resolveName(BOB_NAME))
        assertEquals(CHARLIE_NAME, nodeA.resolveName(CHARLIE_NAME))

        assertEquals(ALICE_NAME, nodeB.resolveName(ALICE_NAME))
        assertEquals(CHARLIE_NAME, nodeB.resolveName(CHARLIE_NAME))

        assertEquals(ALICE_NAME, nodeC.resolveName(ALICE_NAME))
        assertEquals(BOB_NAME, nodeC.resolveName(BOB_NAME))
    }
}

