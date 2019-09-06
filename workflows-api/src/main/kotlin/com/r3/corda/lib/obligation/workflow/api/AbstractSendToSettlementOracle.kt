package com.r3.corda.lib.obligation.workflow.api

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.transactions.SignedTransaction

@InitiatingFlow
abstract class AbstractSendToSettlementOracle : FlowLogic<SignedTransaction>()

