package com.r3.corda.lib.obligation.workflow.api

import net.corda.core.flows.FlowLogic
import net.corda.core.transactions.SignedTransaction

abstract class AbstractMakeOffLedgerPayment : FlowLogic<SignedTransaction>()