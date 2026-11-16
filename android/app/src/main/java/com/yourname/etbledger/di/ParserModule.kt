package com.yourname.etbledger.di

import com.yourname.etbledger.data.parser.BankSmsParser
import com.yourname.etbledger.data.parser.CbeSmsParser
import com.yourname.etbledger.data.parser.TelebirrSmsParser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class ParserModule {

    @Binds
    @IntoSet
    abstract fun bindTelebirrParser(impl: TelebirrSmsParser): BankSmsParser

    @Binds
    @IntoSet
    abstract fun bindCbeParser(impl: CbeSmsParser): BankSmsParser
}
