package org.totschnig.myexpenses.fragment.preferences

import android.os.Bundle
import android.text.TextUtils
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference.OnPreferenceChangeListener
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.preference.PrefKey
import java.time.LocalDate
import java.time.LocalTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

class PreferencesOcrFragment : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_ocr, rootKey)
        unsetIconSpaceReservedRecursive(preferenceScreen)

        val locale = Locale.getDefault()
        if ("" == prefHandler.getString(PrefKey.OCR_TOTAL_INDICATORS, "")) {
            requirePreference<EditTextPreference>(PrefKey.OCR_TOTAL_INDICATORS).text =
                getString(R.string.pref_ocr_total_indicators_default)
        }

        val ocrDatePref = requirePreference<EditTextPreference>(PrefKey.OCR_DATE_FORMATS)
        ocrDatePref.onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
            if (TextUtils.isEmpty(newValue as String)) true
            else {
                try {
                    for (line in newValue.lines()) {
                        LocalDate.now().format(DateTimeFormatter.ofPattern(line))
                    }
                    true
                } catch (e: Exception) {
                    preferenceActivity.showSnackBar(R.string.date_format_illegal)
                    false
                }
            }
        }
        if ("" == prefHandler.getString(PrefKey.OCR_DATE_FORMATS, "")) {
            val shortFormat = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                FormatStyle.SHORT,
                null,
                IsoChronology.INSTANCE,
                locale
            )
            val mediumFormat = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                FormatStyle.MEDIUM,
                null,
                IsoChronology.INSTANCE,
                locale
            )
            ocrDatePref.text = shortFormat + "\n" + mediumFormat
        }

        val ocrTimePref = requirePreference<EditTextPreference>(PrefKey.OCR_TIME_FORMATS)
        ocrTimePref.onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
            if (TextUtils.isEmpty(newValue as String)) true
            else try {
                for (line in newValue.lines()) {
                    LocalTime.now().format(DateTimeFormatter.ofPattern(line))
                }
                true
            } catch (e: Exception) {
                preferenceActivity.showSnackBar(R.string.date_format_illegal)
                false
            }
        }

        if ("" == prefHandler.getString(PrefKey.OCR_TIME_FORMATS, "")) {
            val shortFormat = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                null,
                FormatStyle.SHORT,
                IsoChronology.INSTANCE,
                locale
            )
            val mediumFormat = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                null,
                FormatStyle.MEDIUM,
                IsoChronology.INSTANCE,
                locale
            )
            ocrTimePref.text = shortFormat + "\n" + mediumFormat
        }

        this.requirePreference<ListPreference>(PrefKey.OCR_ENGINE).isVisible =
            preferenceActivity.ocrViewModel.shouldShowEngineSelection()
        configureOcrEnginePrefs()
    }

    fun configureOcrEnginePrefs() {
        val tesseract = findPreference<ListPreference>(PrefKey.TESSERACT_LANGUAGE)
        val mlkit = findPreference<ListPreference>(PrefKey.MLKIT_SCRIPT)
        if (tesseract != null && mlkit != null) {
            preferenceActivity.ocrViewModel.configureOcrEnginePrefs(tesseract, mlkit)
        }
    }
}