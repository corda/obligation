package com.r3.corda.lib.obligation.manual.contract

import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class DummyContract : Contract {
    override fun verify(tx: LedgerTransaction) = Unit
}