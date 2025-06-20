package org.totschnig.myexpenses.viewmodel

import android.app.Application
import androidx.core.os.BundleCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import app.cash.copper.flow.observeQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.db2.deletePrice
import org.totschnig.myexpenses.db2.savePrice
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_COMMODITY
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_DATE
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_MAX_VALUE
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_SOURCE
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_VALUE
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.getLocalDate
import org.totschnig.myexpenses.provider.mapToListWithExtra
import org.totschnig.myexpenses.retrofit.ExchangeRateApi
import org.totschnig.myexpenses.retrofit.ExchangeRateSource
import org.totschnig.myexpenses.util.ExchangeRateHandler
import org.totschnig.myexpenses.util.calculateRealExchangeRate
import org.totschnig.myexpenses.util.safeMessage
import org.totschnig.myexpenses.viewmodel.data.Price
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

private const val SHOW_BATCH_DOWNLOAD_KEY = "batchDownload"

class PriceHistoryViewModel(application: Application, val savedStateHandle: SavedStateHandle) :
    ContentResolvingAndroidViewModel(application) {

    val _batchDownloadResult: MutableStateFlow<String?> = MutableStateFlow(null)
    val batchDownloadResult: Flow<String> = _batchDownloadResult.asStateFlow().filterNotNull()

    fun messageShown() {
        _batchDownloadResult.update { null }
    }

    @Inject
    lateinit var exchangeRateHandler: ExchangeRateHandler

    val commodity: String
        get() = savedStateHandle.get<String>(KEY_COMMODITY)!!

    private val inverseRatePreferenceKey = booleanPreferencesKey("inverseRate_$commodity")

    val inverseRate: Flow<Boolean> by lazy {
        dataStore.data.map { preferences ->
            preferences[inverseRatePreferenceKey] == true
        }
    }

    suspend fun persistInverseRate(inverseRate: Boolean) {
        dataStore.edit { preference ->
            preference[inverseRatePreferenceKey] = inverseRate
        }
    }

    val homeCurrency
        get() = currencyContext.homeCurrencyString

    val relevantSources: List<ExchangeRateApi> by lazy {
        exchangeRateHandler.relevantSources(commodity).also {
            if (it.size > 1) {
                userSelectedSource = it[0]
            }
        }
    }

    var userSelectedSource: ExchangeRateApi? = null

    val effectiveSource: ExchangeRateApi?
        get() = when (relevantSources.size) {
            0 -> null
            1 -> relevantSources.first()
            else -> userSelectedSource
        }

    val pricesWithMissingDates by lazy {
        contentResolver.observeQuery(
            uri = TransactionProvider.PRICES_URI
                .buildUpon()
                .appendQueryParameter(KEY_COMMODITY, commodity)
                .build(),
            projection = arrayOf(KEY_DATE, KEY_SOURCE, KEY_VALUE),
            notifyForDescendants = true
        ).mapToListWithExtra {
            Price(
                date = it.getLocalDate(0),
                source = ExchangeRateSource.getByName(it.getString(1)),
                value = calculateRealExchangeRate(
                    it.getDouble(2),
                    currencyContext[commodity],
                    currencyContext.homeCurrencyUnit
                )
            )
        }
            .map {
                it.second.fillInMissingDates(
                    end = BundleCompat.getSerializable(
                        it.first,
                        KEY_MAX_VALUE,
                        LocalDate::class.java
                    )
                )
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())
    }


    fun List<Price>.fillInMissingDates(
        start: LocalDate? = null,
        end: LocalDate? = null,
    ): Map<LocalDate, Price?> {
        val seed = start ?: LocalDate.now()

        val maxDate = listOfNotNull(end, minOfOrNull { it.date }).minOrNull() ?: seed
        val allDates = generateSequence(seed) { it.minusDays(1) }
            .takeWhile { it >= maxDate }
            .toList()

        return allDates.associateWithTo(LinkedHashMap()) { date ->
            find { it.date == date }
        }
    }

    fun deletePrice(price: Price): LiveData<Boolean> =
        liveData(context = coroutineContext()) {
            emit(
                repository.deletePrice(price.date, price.source, homeCurrency, commodity) == 1
            )
        }


    fun savePrice(date: LocalDate, value: BigDecimal): LiveData<Int> =
        liveData(context = coroutineContext()) {
            emit(
                repository.savePrice(
                    currencyContext.homeCurrencyUnit,
                    currencyContext[commodity],
                    date,
                    ExchangeRateSource.User,
                    value
                )
            )
        }

    suspend fun loadFromNetwork(
        source: ExchangeRateApi,
        date: LocalDate
    ) = exchangeRateHandler.loadFromNetwork(source, date, commodity, currencyContext.homeCurrencyString)

    suspend fun loadTimeSeries(
        source: ExchangeRateApi,
        start: LocalDate,
        end: LocalDate
    ) {
        val (count, exception) = exchangeRateHandler.loadTimeSeries(
            source,
            start,
            end,
            commodity,
            currencyContext.homeCurrencyString
        )
        _batchDownloadResult.update {
            getString(R.string.batch_download_result, count) + (exception?.let {
                " " + it.safeMessage
            } ?: "")
        }
    }
}