package net.arkellyga.smarthome;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.annotation.Nullable;
import android.util.Log;

import yuku.ambilwarna.widget.AmbilWarnaPreference;

public class Preferences extends PreferenceFragment {
    CheckBoxPreference mCbSingleColor;
    PreferenceCategory mCatHallBlue;
    PreferenceCategory mCatHallWork;
    PreferenceCategory mCatKitchenLight;
    PreferenceCategory mCatKitchenKettle;
    // add new category if using common color for each btn
    PreferenceCategory mCatCommonColor;
    AmbilWarnaPreference mPrefCommonColor;
    AmbilWarnaPreference mPrefCommonColorOff;
    PreferenceScreen mPrefScreenDesign;
    // color preferences of all buttons
    AmbilWarnaPreference mPrefHallBlueColor;
    AmbilWarnaPreference mPrefHallBlueColorOff;
    AmbilWarnaPreference mPrefHallWorkColor;
    AmbilWarnaPreference mPrefHallWorkColorOff;
    AmbilWarnaPreference mPrefKitchenLightColor;
    AmbilWarnaPreference mPrefKitchenLightColorOff;
    AmbilWarnaPreference mPrefKitchenKettleColor;
    AmbilWarnaPreference mPrefKitchenKettleColorOff;
    boolean mIsChangedDesign = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        setupPrefs();
        mIsChangedDesign = mCbSingleColor.isChecked();
        setCategoryState();
        mCbSingleColor.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                setCategoryState();
                return false;
            }
        });
    }

    private void setCategoryState() {
        if (mCbSingleColor.isChecked()) {
            mCatHallBlue.removePreference(mPrefHallBlueColor);
            mCatHallBlue.removePreference(mPrefHallBlueColorOff);
            mCatHallWork.removePreference(mPrefHallWorkColor);
            mCatHallWork.removePreference(mPrefHallWorkColorOff);
            mCatKitchenLight.removePreference(mPrefKitchenLightColor);
            mCatKitchenLight.removePreference(mPrefKitchenLightColorOff);
            mCatKitchenKettle.removePreference(mPrefKitchenKettleColor);
            mCatKitchenKettle.removePreference(mPrefKitchenKettleColorOff);

            mCatCommonColor = new PreferenceCategory(getActivity());
            mCatCommonColor.setTitle(R.string.button_common_color);
            mCatCommonColor.setOrder(0);

            mPrefCommonColor = new AmbilWarnaPreference(getActivity(), null);
            mPrefCommonColor.setKey("button_hall_blue_color");
            mPrefCommonColor.setTitle(R.string.button_color);
            mPrefCommonColorOff = new AmbilWarnaPreference(getActivity(), null);
            mPrefCommonColorOff.setKey("button_hall_blue_color_off");
            mPrefCommonColorOff.setTitle(R.string.button_color_off);

            mPrefScreenDesign.addPreference(mCatCommonColor);
            mCatCommonColor.addPreference(mPrefCommonColor);
            mCatCommonColor.addPreference(mPrefCommonColorOff);
            mIsChangedDesign = true;
        } else {
            if (mIsChangedDesign) {
                Log.d("Pref", "isChangedDesign, add ind");
                mCatHallBlue.addPreference(mPrefHallBlueColor);
                mCatHallBlue.addPreference(mPrefHallBlueColorOff);
                mCatHallWork.addPreference(mPrefHallWorkColor);
                mCatHallWork.addPreference(mPrefHallWorkColorOff);
                mCatKitchenLight.addPreference(mPrefKitchenLightColor);
                mCatKitchenLight.addPreference(mPrefKitchenLightColorOff);
                mCatKitchenKettle.addPreference(mPrefKitchenKettleColor);
                mCatKitchenKettle.addPreference(mPrefKitchenKettleColorOff);
                mPrefScreenDesign.removePreference(mCatCommonColor);
                mIsChangedDesign = false;
            }
        }
    }

    private void setupPrefs() {
        mCbSingleColor = (CheckBoxPreference) findPreference("button_single_color");
        mCatHallBlue = (PreferenceCategory) findPreference("category_hall_blue");
        mCatHallWork = (PreferenceCategory) findPreference("category_hall_work");
        mCatKitchenLight = (PreferenceCategory) findPreference("category_kitchen_light");
        mCatKitchenKettle = (PreferenceCategory) findPreference("category_kitchen_kettle");
        mPrefScreenDesign = (PreferenceScreen) findPreference("pref_screen_design");

        mPrefHallBlueColor = (AmbilWarnaPreference) findPreference("button_hall_blue_color");
        mPrefHallBlueColorOff = (AmbilWarnaPreference) findPreference("button_hall_blue_color_off");
        mPrefHallWorkColor = (AmbilWarnaPreference) findPreference("button_hall_work_color");
        mPrefHallWorkColorOff = (AmbilWarnaPreference) findPreference("button_hall_work_color_off");
        mPrefKitchenLightColor = (AmbilWarnaPreference) findPreference("button_kitchen_light_color");
        mPrefKitchenLightColorOff = (AmbilWarnaPreference) findPreference("button_kitchen_light_color_off");
        mPrefKitchenKettleColor = (AmbilWarnaPreference) findPreference("button_kitchen_kettle_color");
        mPrefKitchenKettleColorOff = (AmbilWarnaPreference) findPreference("button_kitchen_kettle_color_off");
    }
}
