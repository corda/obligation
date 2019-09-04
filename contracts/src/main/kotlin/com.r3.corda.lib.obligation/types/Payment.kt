package com.r3.corda.lib.obligation.types

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.r3.corda.lib.tokens.money.Money
import net.corda.core.contracts.Amount
import net.corda.core.serialization.CordaSerializable

typealias PaymentReference = String

@CordaSerializable
@JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, include= JsonTypeInfo.As.PROPERTY, property="_type")
interface Payment<T : Money> {
    /** Reference given to off-ledger payment by settlement rail. */
    val paymentReference: PaymentReference
    /** Amount that was paid in the required token type. */
    val amount: Amount<T>
    /** SENT, ACCEPTED or FAILED. */
    var status: PaymentStatus
}

@CordaSerializable
enum class PaymentStatus { SETTLED, SENT, FAILED }