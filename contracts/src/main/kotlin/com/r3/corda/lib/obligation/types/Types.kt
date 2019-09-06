package com.r3.corda.lib.obligation.types

import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import java.time.Instant

@CordaSerializable
sealed class SettlementOracleResult {
    data class Success(val stx: SignedTransaction) : com.r3.corda.lib.obligation.types.SettlementOracleResult()
    data class Failure(val stx: SignedTransaction?, val message: String) : com.r3.corda.lib.obligation.types.SettlementOracleResult()
}

@CordaSerializable
data class FxRateRequest(val baseCurrency: TokenType, val counterCurrency: TokenType, val time: Instant)

typealias FxRateResponse = com.r3.corda.lib.obligation.types.FxRate