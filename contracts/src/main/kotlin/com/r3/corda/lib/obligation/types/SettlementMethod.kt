package com.r3.corda.lib.obligation.types

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.serialization.CordaSerializable
import java.security.PublicKey

/** All settlement methods require some key or account that a payment must be made to. */
@CordaSerializable
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "_type")
interface SettlementMethod {
    /** The public key, account number or whatever, that payment should be made to. */
    val accountToPay: Any
}

/**
 * Payment can be made whatever token states the obligee requests. Most likely, the payment will be made in the token
 * in which the obligation is denominated. However this might not always be the case. For example, the obligation
 * might be denominated in GBP so the obligee accepts GBP from a number of GBP issuers but not all issuers. On the other
 * hand, the obligation might be denominated GBP but also accepts payments in some other on-ledger currency. As such it
 * might be the case that some currency conversion is required.
 */
data class OnLedgerSettlement(
        /** Payments are always made to public keys on ledger. TODO: Add certificate for AML reasons. */
        override val accountToPay: PublicKey,
        /** The type will eventually be a TokenType. */
        val acceptableTokenTypes: List<TokenType>
) : SettlementMethod