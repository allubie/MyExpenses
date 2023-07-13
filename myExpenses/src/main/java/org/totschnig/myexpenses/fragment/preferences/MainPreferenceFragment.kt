package org.totschnig.myexpenses.fragment.preferences

import android.content.Context
import android.os.Bundle
import android.view.ContextThemeWrapper
import androidx.core.content.res.ResourcesCompat
import androidx.preference.*
import com.evernote.android.state.State
import com.evernote.android.state.StateSaver
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.preference.PrefKey

class MainPreferenceFragment : BasePreferenceFragment() {

    lateinit var adapter: PreferenceGroupAdapter

    var isSlideable: Boolean = true
        set(value) {
            field = value
            val preferenceAdapterPosition = adapter.getPreferenceAdapterPosition(highlightedKey)
            if (!value) {
                view?.setBackgroundColor(
                    ResourcesCompat.getColor(
                        resources,
                        R.color.settings_two_pane_background_color,
                        null
                    )
                )

                adapter.notifyItemChanged(preferenceAdapterPosition)
            }
        }

    @State
    lateinit var highlightedKey: String

    fun onLoadPreference(key: String) {
        val oldPosition = adapter.getPreferenceAdapterPosition(highlightedKey)
        val newPosition = adapter.getPreferenceAdapterPosition(key)
        highlightedKey = key
        adapter.notifyItemChanged(oldPosition)
        adapter.notifyItemChanged(newPosition)
    }

    override fun overrideTheme(): Int {
        return R.style.MyPreferenceHeaderTheme
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            StateSaver.restoreInstanceState(this, it)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        StateSaver.saveInstanceState(this, outState)
    }

    override val preferencesResId = R.xml.preference_headers

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        requirePreference<Preference>(PrefKey.CATEGORY_BACKUP_EXPORT).title = exportBackupTitle
        highlightedKey = preferenceScreen.getPreference(0).key
    }

    override fun onCreateAdapter(preferenceScreen: PreferenceScreen) =
        object : PreferenceGroupAdapter(preferenceScreen) {

            override fun onBindViewHolder(holder: PreferenceViewHolder, position: Int) {
                super.onBindViewHolder(holder, position)
                if (!isSlideable && getItem(position)?.key == highlightedKey) {
                    holder.itemView.setBackgroundColor(
                        ResourcesCompat.getColor(resources, R.color.activatedBackground, null)
                    )
                }
            }
        }.also {
            adapter = it
        }
}