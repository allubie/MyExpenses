package org.totschnig.myexpenses.model

import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.DataBaseAccount
import org.totschnig.myexpenses.util.Utils
import java.util.Collections
import java.util.Currency
import java.util.Locale

open class PreferencesCurrencyContext(
    private val prefHandler: PrefHandler,
    private val application: MyApplication
) : CurrencyContext {
    override fun get(currencyCode: String): CurrencyUnit {
        synchronized(this) {
            var currencyUnit = INSTANCES[currencyCode]
            if (currencyUnit != null) {
                return currencyUnit
            }
            val c = Utils.getInstance(currencyCode, prefHandler)
            currencyUnit = if (c != null) {
                CurrencyUnit(currencyCode, getSymbol(c), getFractionDigits(c), c.displayName)
            } else {
                val customSymbol = getCustomSymbol(currencyCode)
                val customFractionDigits = getCustomFractionDigits(currencyCode)
                CurrencyUnit(
                    currencyCode, customSymbol ?: "¤",
                    if (customFractionDigits == -1) DEFAULT_FRACTION_DIGITS else customFractionDigits
                )
            }
            INSTANCES[currencyCode] = currencyUnit
            return currencyUnit
        }
    }

    private fun getCustomSymbol(currencyCode: String) =
        prefHandler.getString(currencyCode + KEY_CUSTOM_CURRENCY_SYMBOL, null)

    private fun getCustomFractionDigits(currencyCode: String) =
        prefHandler.getInt(currencyCode + KEY_CUSTOM_FRACTION_DIGITS, -1)

    private fun getSymbol(currency: Currency) = getCustomSymbol(currency.currencyCode)
        ?: currency.getSymbol(application.userPreferredLocale)

    private fun getFractionDigits(currency: Currency) =
        getCustomFractionDigits(currency.currencyCode).takeIf { it != -1 } ?:
        currency.getDefaultFractionDigits().takeIf { it != -1 } ?: DEFAULT_FRACTION_DIGITS

    override fun storeCustomFractionDigits(currencyCode: String?, fractionDigits: Int) {
        prefHandler.putInt(currencyCode + KEY_CUSTOM_FRACTION_DIGITS, fractionDigits)
        INSTANCES.remove(currencyCode)
    }

    override fun storeCustomSymbol(currencyCode: String?, symbol: String?) {
        val currency = try {
            Currency.getInstance(currencyCode)
        } catch (ignored: Exception) { null }
        val key = currencyCode + KEY_CUSTOM_CURRENCY_SYMBOL
        if (currency != null && currency.symbol == symbol) {
            prefHandler.remove(key)
        } else {
            prefHandler.putString(key, symbol)
        }
        INSTANCES.remove(currencyCode)
    }

    override fun ensureFractionDigitsAreCached(currency: CurrencyUnit?) {
        storeCustomFractionDigits(currency!!.code, currency.fractionDigits)
    }

    override fun invalidateHomeCurrency() {
        INSTANCES.remove(DataBaseAccount.AGGREGATE_HOME_CURRENCY_CODE)
    }

    override val homeCurrencyString: String
        get() = prefHandler.getString(PrefKey.HOME_CURRENCY, null) ?: localCurrency.currencyCode

    override val localCurrency = Utils.getCountryFromTelephonyManager(application)?.let {
        try {
            Currency.getInstance(Locale("", it))
        } catch (ignore: Exception) {
            null
        }
    } ?: Utils.getSaveDefault()

    companion object {
        /**
         * used with currencies where Currency.getDefaultFractionDigits returns -1
         */
        const val DEFAULT_FRACTION_DIGITS = 8
        private const val KEY_CUSTOM_FRACTION_DIGITS = "CustomFractionDigits"
        private const val KEY_CUSTOM_CURRENCY_SYMBOL = "CustomCurrencySymbol"
        private val INSTANCES = Collections.synchronizedMap(HashMap<String?, CurrencyUnit>())
    }
}
