package com.r3.corda.lib.obligation.utils

import com.r3.corda.lib.obligation.types.FxRateResponse
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.transactions.SignedTransaction

@InitiatingFlow
abstract class AbstractGetFxRate : FlowLogic<FxRateResponse>()

@InitiatingFlow
abstract class AbstractGetFxOracleSignature : FlowLogic<TransactionSignature>()

@InitiatingFlow
abstract class AbstractSendToSettlementOracle : FlowLogic<SignedTransaction>()

abstract class AbstractMakeOffLedgerPayment : FlowLogic<SignedTransaction>()