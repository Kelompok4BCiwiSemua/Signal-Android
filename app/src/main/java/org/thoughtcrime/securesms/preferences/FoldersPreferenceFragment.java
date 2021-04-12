package org.thoughtcrime.securesms.preferences;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;

import org.greenrobot.eventbus.EventBus;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.devicetransfer.olddevice.OldDeviceTransferActivity;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.util.ConversationUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ThrottledDebouncer;

public class FoldersPreferenceFragment extends ListSummaryPreferenceFragment {

  private final ThrottledDebouncer refreshDebouncer = new ThrottledDebouncer(500);

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
    
    findPreference(TextSecurePreferences.BACKUP).setOnPreferenceClickListener(unused -> {
      goToBackupsPreferenceFragment();
      return true;
    });

  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_folders);
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity)getActivity()).getSupportActionBar().setTitle(R.string.preferences__folders);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    EventBus.getDefault().unregister(this);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  private void goToBackupsPreferenceFragment() {
    ((ApplicationPreferencesActivity) requireActivity()).pushFragment(new BackupsPreferenceFragment());
  }

  public static CharSequence getSummary(Context context) {
    return null;
  }
}
