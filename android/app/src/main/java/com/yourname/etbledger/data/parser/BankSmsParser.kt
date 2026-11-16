package com.yourname.etbledger.data.parser

import com.yourname.etbledger.domain.model.ParsedTransaction

interface BankSmsParser {
    val bankName: String
    fun canHandle(sender: String, body: String): Boolean
    fun parse(sender: String, body: String, smsTimestampMillis: Long): ParsedTransaction?
}
