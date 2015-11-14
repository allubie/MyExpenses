package org.totschnig.myexpenses.preference;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceDialogFragmentCompat;
import android.text.format.DateFormat;
import android.widget.TimePicker;

/**
 * Created by privat on 14.11.15.
 */
public class TimePreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat {

  public static final int DEFAULT_VALUE = 500;

  private TimePicker mTimePicker;

  @Override
  protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
    final TimePreference preference = (TimePreference) getPreference();
    Context context = getContext();
    mTimePicker = new TimePicker(context);
    mTimePicker.setId(1);
    mTimePicker.setIs24HourView(DateFormat.is24HourFormat(context));
    mTimePicker.setCurrentHour(preference.getHour());
    mTimePicker.setCurrentMinute(preference.getMinute());
    builder.setView(mTimePicker);
  }

  @Override
  public void onDialogClosed(boolean positiveResult) {
    final TimePreference preference = (TimePreference) getPreference();

    if (!positiveResult) {
      return;
    }
    mTimePicker.clearFocus();
    preference.setValue(100*mTimePicker.getCurrentHour()+mTimePicker.getCurrentMinute());
  }

  public static TimePreferenceDialogFragmentCompat newInstance(Preference preference) {
    TimePreferenceDialogFragmentCompat fragment = new TimePreferenceDialogFragmentCompat();
    Bundle bundle = new Bundle(1);
    bundle.putString("key", preference.getKey());
    fragment.setArguments(bundle);
    return fragment;
  }
}
