package com.r3.corda.lib.obligation.api

import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.serialization.CordaSerializable
import java.math.BigDecimal
import java.time.Instant

@CordaSerializable
data class FxRateRequest(val baseCurrency: TokenType, val counterCurrency: TokenType, val time: Instant)

@CordaSerializable
data class FxRate(val baseCurrency: TokenType, val counterCurrency: TokenType, val time: Instant, val rate: BigDecimal)

typealias FxRateResponse = FxRate