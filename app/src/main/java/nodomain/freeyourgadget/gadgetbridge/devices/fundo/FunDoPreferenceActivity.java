package nodomain.freeyourgadget.gadgetbridge.devices.fundo;

import android.os.Bundle;
import android.preference.Preference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractSettingsActivity;
import nodomain.freeyourgadget.gadgetbridge.service.devices.fundo.FunDoDeviceSupport;

public class FunDoPreferenceActivity extends AbstractSettingsActivity {
    Logger LOG = LoggerFactory.getLogger(FunDoDeviceSupport.class.getSimpleName());

    /* access modifiers changed from: protected */
    @Override
    // nodomain.freeyourgadget.gadgetbridge.activities.AppCompatPreferenceActivity, nodomain.freeyourgadget.gadgetbridge.activities.AbstractSettingsActivity
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.fundo_preferences);
        GBApplication.deviceService().onReadConfiguration("start");

        addPreferenceHandlerFor(FunDoConstants.PREF_HANDMOVE_DISPLAY);

        addPreferenceHandlerFor(FunDoConstants.PREF_DO_NOT_DISTURB);
        addPreferenceHandlerFor(FunDoConstants.PREF_DO_NOT_DISTURB_START);
        addPreferenceHandlerFor(FunDoConstants.PREF_DO_NOT_DISTURB_END);

        addPreferenceHandlerFor(FunDoConstants.PREF_INACTIVITY_ENABLE);
        addPreferenceHandlerFor(FunDoConstants.PREF_INACTIVITY_START);
        addPreferenceHandlerFor(FunDoConstants.PREF_INACTIVITY_END);
        addPreferenceHandlerFor(FunDoConstants.PREF_INACTIVITY_THRESHOLD);
        addPreferenceHandlerFor(FunDoConstants.PREF_INACTIVITY_MO);
        addPreferenceHandlerFor(FunDoConstants.PREF_INACTIVITY_TU);
        addPreferenceHandlerFor(FunDoConstants.PREF_INACTIVITY_WE);
        addPreferenceHandlerFor(FunDoConstants.PREF_INACTIVITY_TH);
        addPreferenceHandlerFor(FunDoConstants.PREF_INACTIVITY_FR);
        addPreferenceHandlerFor(FunDoConstants.PREF_INACTIVITY_SA);
        addPreferenceHandlerFor(FunDoConstants.PREF_INACTIVITY_SU);

        addPreferenceHandlerFor(FunDoConstants.PREF_WATER_ENABLE);
        addPreferenceHandlerFor(FunDoConstants.PREF_WATER_START);
        addPreferenceHandlerFor(FunDoConstants.PREF_WATER_END);
        addPreferenceHandlerFor(FunDoConstants.PREF_WATER_THRESHOLD);
        addPreferenceHandlerFor(FunDoConstants.PREF_WATER_MO);
        addPreferenceHandlerFor(FunDoConstants.PREF_WATER_TU);
        addPreferenceHandlerFor(FunDoConstants.PREF_WATER_WE);
        addPreferenceHandlerFor(FunDoConstants.PREF_WATER_TH);
        addPreferenceHandlerFor(FunDoConstants.PREF_WATER_FR);
        addPreferenceHandlerFor(FunDoConstants.PREF_WATER_SA);
        addPreferenceHandlerFor(FunDoConstants.PREF_WATER_SU);

        addPreferenceHandlerFor(FunDoConstants.PREF_FUNDO_HEARTRATE_INTERVAL);
        addPreferenceHandlerFor(FunDoConstants.PREF_FUNDO_HEARTRATE_TIME_START);
        addPreferenceHandlerFor(FunDoConstants.PREF_FUNDO_HEARTRATE_TIME_STOP);
        //addPreferenceHandlerFor("mi_fitness_goal");
    }

    private void addPreferenceHandlerFor(final String preferenceKey) {
        try {
            Preference pref = findPreference(preferenceKey);
            pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newVal) {
                    GBApplication.deviceService().onSendConfiguration(preferenceKey);
                    return true;
                }
            });
        } catch (Exception e) {
            this.LOG.warn(String.format("%s : %s", preferenceKey, e.getMessage()));
        }
    }

}
