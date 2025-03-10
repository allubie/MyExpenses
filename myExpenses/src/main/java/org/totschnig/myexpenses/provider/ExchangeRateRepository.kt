package org.totschnig.myexpenses.provider

import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.retrofit.ExchangeRateService
import org.totschnig.myexpenses.retrofit.ExchangeRateSource
import org.totschnig.myexpenses.room.ExchangeRate
import org.totschnig.myexpenses.room.ExchangeRateDao
import java.io.IOException
import java.time.LocalDate

class ExchangeRateRepository(
    private val dao: ExchangeRateDao,
    val prefHandler: PrefHandler,
    val service: ExchangeRateService
) {
    @Throws(IOException::class)
    suspend fun loadExchangeRate(other: String, base: String, date: LocalDate, source: ExchangeRateSource): Double {
        /*        for (rate in dao.getAllRates()) {
                    Timber.d(rate.toString())
                }*/
        val apiKey = (source as? ExchangeRateSource.SourceWithApiKey)?.requireApiKey(prefHandler)
        return if (date == LocalDate.now()) {
            loadFromNetwork(source, apiKey, date, other, base)
        } else dao.getRate(base, other, date, source.id)
            ?: loadFromNetwork(source, apiKey, date, other, base)
    }

    private suspend fun loadFromNetwork(
        source: ExchangeRateSource,
        apiKey: String?,
        date: LocalDate,
        other: String,
        base: String
    ) = service.getRate(source, apiKey, date, other, base).also {
        dao.insert(ExchangeRate(base, other, it.first, it.second, source.id))
    }.second

    suspend fun deleteAll() = dao.deleteALL()
}