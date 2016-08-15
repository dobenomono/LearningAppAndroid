/*
 * Copyright (c) 2016, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.marketingcloud.android.demoapp.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.annotation.NonNull;
import android.support.annotation.Size;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.exacttarget.etpushsdk.ETAnalytics;
import com.exacttarget.etpushsdk.ETException;
import com.exacttarget.etpushsdk.ETPush;
import com.exacttarget.etpushsdk.data.Attribute;
import com.salesforce.marketingcloud.android.demoapp.LearningAppApplication;
import com.salesforce.marketingcloud.android.demoapp.R;
import com.salesforce.marketingcloud.android.demoapp.utils.Utils;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import hugo.weaving.DebugLog;

/**
 * SettingsFragment handles settings that would normally be included within your customer facing app.
 * These settings that are sent to the Marketing Cloud will take up to 15 minutes to take effect.
 * So, after setting or changing these settings, you should wait at least 15 minutes before sending
 * a message from the Marketing Cloud.
 * <p>
 * Your app design may be different (for example, you may set notifications on by default in your
 * Application class if you assume permission was given by the user due to the permission settings
 * set within the Google Play definition.
 * <p/>
 * Settings:
 * <ol>
 * <li>
 * <b>Subscriber Key</b>
 * <br/>
 * This attribute provides a primary key for the Contact record stored in the Salesforce Marketing Cloud.
 * </li>
 * <li>
 * <b>Tags</b>
 * <br/>
 * The Tags section show examples of using Tags to allow your customers to select which type
 * of notification they are interested in receiving, and create new tags.
 * The tags are sent to the Marketing Cloud and can be used to select customers to send the notification to.
 * </li>
 * </ol>
 *
 * @author Salesforce &reg; 2015.
 */
@DebugLog
public class SettingsFragment extends PreferenceFragment implements LearningAppApplication.EtPushListener {
    private static final String TAG = "~#SettingsFragment";

    // KEYS
    private static final String KEY_PREF_NEW_TAG = "pref_new_tag";
    private static final String KEY_PREF_LASTNAME_ATTRIBUTE = "pref_lastname_attribute";
    private static final String KEY_PREF_FIRSTNAME_ATTRIBUTE = "pref_firstname_attribute";
    private static final String KEY_PREF_SUBSCRIBER_KEY = "pref_subscriber_key";
    private static final Handler mainThread = new Handler(Looper.getMainLooper());
    private final Handler progressDialogHandler = new Handler();
    private final DialogRunnable dialogRunnable = new DialogRunnable();
    private final Set<String> tags = new HashSet<>();
    // Local Storage
    private SharedPreferences sharedPreferences;
    private PreferenceScreen preferenceScreen;
    // Error/Exception Handling and Progress Indicator
    private ProgressDialog dialog;
    // Elements we'll use with the ETPush SDK
    private Set<Attribute> attributes = new HashSet<>();
    private String subscriberKey;
    private String lastnameAttribute;
    private String firstnameAttribute;

    /**
     * Retrieves the subscriber key and tags preference, listen for changes and propagate them to the SDK.
     *
     * @param savedInstanceState
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        sharedPreferences = getActivity().getPreferences(Context.MODE_PRIVATE);
        preferenceScreen = getPreferenceScreen();

        ETAnalytics.trackPageView("data://SettingsActivity", "Loading Settings activity");

        ETPush etPush = LearningAppApplication.getEtPush(this); // Add ourselves as an LearningAppApplication.EtPushListener
        if (etPush == null) {
            // Display a progress dialog while we wait for ETPush to finish initializing. <-- Seeing this should be exceedingly rare!
            dialog = ProgressDialog.show(getActivity(), "", "Waiting for ETPush to finish initializing ...", true);
            progressDialogHandler.postDelayed(dialogRunnable, 30000);
        } else {
            // Hide the progress dialog & show the Attributes, SubscriberKey and Tags.  If ETPush hasn't
            // finished initializing, the callback will get executed by our LearningAppApplication.
            this.onReadyForPush(etPush);
        }
    }

    @Override
    public void onReadyForPush(@NonNull final ETPush etPush) {
        progressDialogHandler.removeCallbacks(dialogRunnable);
        try {
            for (Attribute attribute : etPush.getAttributes()) {
                if (attribute.getKey().equals("FirstName") || attribute.getKey().equals("LastName")) {
                    attributes.add(attribute);
                }
            }
            attributes.addAll(etPush.getAttributes());
            subscriberKey = etPush.getSubscriberKey();
            tags.addAll(etPush.getTags());
        } catch (ETException e) {
            Log.e(TAG, e.getMessage());
        }

        displaySubscriberKey(subscriberKey, etPush);
        displayAttributes(attributes, etPush);
        displayTags(tags, etPush);

        // Hide the progress dialog
        if (dialog != null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                dialog.hide();
            } else {
                mainThread.post(new Runnable() {
                    @Override
                    public void run() {
                        dialog.hide();
                    }
                });
            }
            dialog.dismiss();
        }
    }

    private void displaySubscriberKey(@NonNull final @Size(min = 1) String subscriberKey, @NonNull final ETPush etPush) {
        if (TextUtils.isEmpty(subscriberKey) || etPush == null) {
            // display message
            return;
        }

        final Preference preference = findPreference(KEY_PREF_SUBSCRIBER_KEY);
        preference.setSummary(!TextUtils.isEmpty(subscriberKey) ? subscriberKey : sharedPreferences.getString(KEY_PREF_SUBSCRIBER_KEY, ""));

        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {

                final EditTextPreference editTextPreference = (EditTextPreference) preferenceScreen.findPreference(KEY_PREF_SUBSCRIBER_KEY);

                final AlertDialog alertDialog = (AlertDialog) editTextPreference.getDialog();
                final EditText editText = editTextPreference.getEditText();

                editText.setText(!TextUtils.isEmpty(subscriberKey) ? subscriberKey : sharedPreferences.getString(KEY_PREF_SUBSCRIBER_KEY, ""));

                Button positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                positiveButton.setOnClickListener(new View.OnClickListener() {

                    @SuppressLint("CommitPrefEdits")
                    @Override
                    public void onClick(android.view.View v) {
                        String newSubscriberKey = editText.getText().toString().trim();
                        if (TextUtils.isEmpty(newSubscriberKey)) {
                            Utils.flashError(editText, getString(R.string.error_cannot_be_blank));
                            return;
                        } else {
                            try {
                                if (etPush.setSubscriberKey(newSubscriberKey)) {
                                    // Save the preference to Shared Preferences only if we don't encounter an error
                                    SharedPreferences.Editor editor = sharedPreferences.edit();
                                    editor.putString(KEY_PREF_SUBSCRIBER_KEY, newSubscriberKey);
                                    editor.commit();
                                    editTextPreference.setSummary(newSubscriberKey);
                                } else {
                                    Utils.flashError(editText, getString(R.string.error_could_not_update));
                                    return;
                                }
                            } catch (ETException e) {
                                Log.e("TAG", e.getMessage(), e);
                                return;
                            }
                        }
                        alertDialog.dismiss();
                        preference.setSummary(newSubscriberKey);
                    }
                });
                ETAnalytics.trackPageView("data://SettingsActivity-SubscriberKeySet", "Subscriber Key Set");
                return true;
            }
        });

    }

    private void displayAttributes(@NonNull final Set<Attribute> attributes, @NonNull final ETPush etPush) {
        if (attributes.isEmpty()) {
            // display message
            return;
        }
        Log.i(TAG, String.format(Locale.ENGLISH, "ATTRIBUTES: %s", attributes));
        //saveAttributesToSharedPreferences(attributes);
        String firstName = null;
        String lastName = null;
        for (Attribute attribute : attributes) {
            if (attribute.getKey().equalsIgnoreCase("firstname")) {
                firstName = attribute.getValue();
            } else if (attribute.getKey().equalsIgnoreCase("lastname")) {
                lastName = attribute.getValue();
            }
        }
        final PreferenceCategory preferenceCategory = (PreferenceCategory) this.preferenceScreen.findPreference("pref_attributes_section");
        // Firstname
        EditTextPreference editTextPreference = new EditTextPreference(getActivity());
        editTextPreference.setDefaultValue(!TextUtils.isEmpty(firstName) ? firstName : "");
        editTextPreference.setDialogMessage(getResources().getString(R.string.pref_attribute_firstname_summ));
        editTextPreference.setKey(KEY_PREF_FIRSTNAME_ATTRIBUTE);
        editTextPreference.setSummary(!TextUtils.isEmpty(firstName) ? firstName : getResources().getString(R.string.pref_attribute_firstname_summ));
        editTextPreference.setTitle(getResources().getString(R.string.pref_attribute_firstname));
        preferenceCategory.addPreference(editTextPreference);
        final Preference firstnamePreference = findPreference(KEY_PREF_FIRSTNAME_ATTRIBUTE);
        firstnamePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                final EditTextPreference editTextPreference = (EditTextPreference) preferenceScreen.findPreference(KEY_PREF_FIRSTNAME_ATTRIBUTE);

                final AlertDialog alertDialog = (AlertDialog) editTextPreference.getDialog();
                final EditText editText = editTextPreference.getEditText();
                editText.setText(sharedPreferences.getString(KEY_PREF_FIRSTNAME_ATTRIBUTE, ""));

                Button positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                positiveButton.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        String value = editText.getText().toString().trim();
                        if (TextUtils.isEmpty(value)) {
                            Utils.flashError(editText, getString(R.string.error_cannot_be_blank));
                            return;
                        } else {
                            try {
                                etPush.addAttribute("FirstName", value);
                                editTextPreference.setSummary(value);
                                sharedPreferences.edit().putString(KEY_PREF_FIRSTNAME_ATTRIBUTE, value).apply();
                            } catch (ETException e) {
                                Log.e(TAG, e.getMessage(), e);
                            }
                            //configureAttributes(etPush);
                        }
                        alertDialog.dismiss();
                    }
                });
                return true;
            }
        });


        // Lastname
        editTextPreference = new EditTextPreference(getActivity());
        editTextPreference.setDefaultValue(!TextUtils.isEmpty(lastName) ? lastName : "");
        editTextPreference.setDialogMessage(getResources().getString(R.string.pref_attribute_lastname_summ));
        editTextPreference.setKey(KEY_PREF_LASTNAME_ATTRIBUTE);
        editTextPreference.setSummary(!TextUtils.isEmpty(lastName) ? lastName : getResources().getString(R.string.pref_attribute_lastname_summ));
        editTextPreference.setTitle(getResources().getString(R.string.pref_attribute_lastname));
        preferenceCategory.addPreference(editTextPreference);
        final Preference lastnamePreference = findPreference(KEY_PREF_LASTNAME_ATTRIBUTE);
        lastnamePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                final EditTextPreference editTextPreference = (EditTextPreference) preferenceScreen.findPreference(KEY_PREF_LASTNAME_ATTRIBUTE);

                final AlertDialog alertDialog = (AlertDialog) editTextPreference.getDialog();
                final EditText editText = editTextPreference.getEditText();
                editText.setText(sharedPreferences.getString(KEY_PREF_LASTNAME_ATTRIBUTE, ""));

                Button positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                positiveButton.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        String value = editText.getText().toString().trim();
                        if (TextUtils.isEmpty(value)) {
                            Utils.flashError(editText, getString(R.string.error_cannot_be_blank));
                            return;
                        } else {
                            try {
                                etPush.addAttribute("LastName", value);
                                editTextPreference.setSummary(value);
                                sharedPreferences.edit().putString(KEY_PREF_LASTNAME_ATTRIBUTE, value).apply();
                            } catch (ETException e) {
                                Log.e(TAG, e.getMessage(), e);
                            }
                        }
                        alertDialog.dismiss();
                    }
                });
                return true;
            }
        });

    }

    private void displayTags(@NonNull final Set<String> tags, @NonNull final ETPush etPush) {
        if (tags.isEmpty() || etPush == null) {
            // display message
            return;
        }
        Log.i(TAG, String.format(Locale.ENGLISH, "TAGS: %s", tags));
        saveTagsToSharedPreferences(tags);

        final PreferenceCategory tagsSection = (PreferenceCategory) this.preferenceScreen.findPreference("pref_tag_section");

        EditTextPreference et = new EditTextPreference(getActivity());
        et.setDefaultValue("");
        et.setDialogMessage(getResources().getString(R.string.pref_new_tag_summ));
        et.setKey(KEY_PREF_NEW_TAG);
        et.setSummary(getResources().getString(R.string.pref_new_tag_summ));
        et.setTitle(getResources().getString(R.string.pref_new_tag));
        tagsSection.addPreference(et);

        /* Create rows from list of tags. */
        for (String tag : this.tags) {
            addTagCheckbox(tagsSection, tag, etPush);
        }

        final Preference preference = findPreference(KEY_PREF_NEW_TAG);
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final EditTextPreference editTextPreference = (EditTextPreference) preferenceScreen.findPreference(KEY_PREF_NEW_TAG);

                final AlertDialog alertDialog = (AlertDialog) editTextPreference.getDialog();
                final EditText editText = editTextPreference.getEditText();
                editText.setText(sharedPreferences.getString(KEY_PREF_NEW_TAG, ""));

                Button positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                positiveButton.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        String value = editText.getText().toString().trim();
                        if (TextUtils.isEmpty(value)) {
                            Utils.flashError(editText, getString(R.string.error_cannot_be_blank));
                            return;
                        } else {
                            try {
                                addTag(value, etPush);
                            } catch (ETException e) {
                                Log.e(TAG, e.getMessage(), e);
                            }
                            addTagCheckbox(tagsSection, value, etPush);
                        }
                        alertDialog.dismiss();
                    }
                });
                return true;
            }
        });
    }

    /**
     * Receives a Set of tags and adds them to the Set of tags in Shared Preferences.
     *
     * @param pSet tags to be stored.
     */
    @SuppressLint("CommitPrefEdits")
    private void saveTagsToSharedPreferences(Set<String> pSet) {
        /* Retrieves the tags stored in Shared preferences */
        //Set<String> setToLoad = sharedPreferences.getStringSet("tags", null) == null ? new HashSet<String>() : sharedPreferences.getStringSet("tags", null);
        Set<String> setToLoad = sharedPreferences.getStringSet("tags", new HashSet<String>());
        /* Adds the tags from the Set passed as parameter */
        for (String t : pSet) {
            setToLoad.add(t);
        }
        /* Stores the tags in Shared Preferences */
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Set<String> setToSave = new HashSet<>();
        setToSave.addAll(setToLoad);
        editor.putStringSet("tags", setToSave);
        editor.commit();
        tags.clear();
        tags.addAll(setToLoad);
    }

    /**
     * Receives a Tag to store in Shared preferences
     *
     * @param tag a new Tag to be added.
     */
    private void addTag(@NonNull final String tag, @NonNull final ETPush etPush) throws ETException {
        Set<String> tempSet = new HashSet<>();
        tempSet.add(tag);
        etPush.addTag(tag);
        saveTagsToSharedPreferences(tempSet);
    }

    /**
     * Creates a row from the tag passed in as parameter to be displayed.
     *
     * @param preferenceCategory the section where the Tag will be displayed.
     * @param tag                the Tag to be displayed on the screen.
     */
    private void addTagCheckbox(PreferenceCategory preferenceCategory, final String tag, @NonNull final ETPush etPush) {
        /* Creates a new row if is not already created for the Tag. */
        CheckBoxPreference checkBoxPreference = (CheckBoxPreference) this.preferenceScreen.findPreference(tag);
        if (checkBoxPreference == null) {
            checkBoxPreference = new CheckBoxPreference(getActivity());
            checkBoxPreference.setKey(tag);
            checkBoxPreference.setTitle(tag);
            checkBoxPreference.setSummary("Receive notifications for " + tag);
            checkBoxPreference.setDefaultValue(Boolean.TRUE);
            checkBoxPreference.setChecked(true);

            checkBoxPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

                @Override
                public boolean onPreferenceChange(Preference pref, Object newValue) {
                    /* Add the Tag to the ETPush instance if checked, else remove it. */
                    Boolean enabled = (Boolean) newValue;
                    try {
                        if (enabled) {
                            etPush.addTag(tag);
                        } else {
                            etPush.removeTag(tag);
                        }
                    } catch (ETException e) {
                        if (ETPush.getLogLevel() <= Log.ERROR) {
                            Log.e("TAG", e.getMessage(), e);
                        }
                    }
                    return true;
                }
            });
            /* Add row to section. */
            preferenceCategory.addPreference(checkBoxPreference);
        }
    }

    private class DialogRunnable implements Runnable {
        @Override
        public void run() {
            dialog.hide();
            // Display error message
            dialog = ProgressDialog.show(getActivity(), "ERROR", "Error initializing ETPush.", false, true); // quick & dirty, replace
        }
    }

}
