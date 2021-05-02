package org.thoughtcrime.securesms;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.AnimRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import org.thoughtcrime.securesms.components.ContactFilterToolbar;
import org.thoughtcrime.securesms.components.ContactFilterToolbar.OnFilterChangedListener;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader.DisplayMode;
import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.groups.SelectionLimits;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.util.DynamicNoActionBarInviteTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.WindowUtil;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture.Listener;
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask;
import org.thoughtcrime.securesms.util.text.AfterTextChanged;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.concurrent.ExecutionException;
import java.util.LinkedList;
import java.util.Iterator;

public class InviteActivity extends PassphraseRequiredActivity implements ContactSelectionListFragment.OnContactSelectedListener {

  private ContactSelectionListFragment contactsFragment;
  private EditText                     noteText;
  private static TextView              note1;
  private ViewGroup                    smsSendFrame;
  private Button                       smsSendButton;
  private Animation                    slideInAnimation;
  private Animation                    slideOutAnimation;
  private DynamicTheme                 dynamicTheme = new DynamicNoActionBarInviteTheme();
  private Toolbar                      primaryToolbar;
  private String                       note;
  private String                       notes;
  private String                       noted;
  private int                          j;
  private boolean                      add = false;
  LinkedList<String> s = new LinkedList<>();
  @Override
  protected void onPreCreate() {
    super.onPreCreate();
    dynamicTheme.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    getIntent().putExtra(ContactSelectionListFragment.DISPLAY_MODE, DisplayMode.FLAG_SMS);
    getIntent().putExtra(ContactSelectionListFragment.SELECTION_LIMITS, SelectionLimits.NO_LIMITS);
    getIntent().putExtra(ContactSelectionListFragment.HIDE_COUNT, true);
    getIntent().putExtra(ContactSelectionListFragment.REFRESHABLE, false);

    setContentView(R.layout.add_notes);

    initializeAppBar();
    initializeResources();
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  private void initializeAppBar() {
    primaryToolbar = findViewById(R.id.toolbar);
    setSupportActionBar(primaryToolbar);

    assert getSupportActionBar() != null;

    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setTitle(R.string.NoteActivity_note);
  }

  private void initializeResources() {
    slideInAnimation  = loadAnimation(R.anim.slide_from_bottom);
    slideOutAnimation = loadAnimation(R.anim.slide_to_bottom);

    View                 noteButton     = findViewById(R.id.note_button);
    Button               saveButton       = findViewById(R.id.save_button);
    Button               smsCancelButton = findViewById(R.id.cancel_sms_button);
    ContactFilterToolbar contactFilter   = findViewById(R.id.contact_filter);

    noteText        = findViewById(R.id.note_text);
    note1             = findViewById(R.id.note1);
    smsSendFrame      = findViewById(R.id.sms_send_frame);
    smsSendButton     = findViewById(R.id.send_sms_button);
    contactsFragment  = (ContactSelectionListFragment)getSupportFragmentManager().findFragmentById(R.id.contact_selection_list_fragment);
    noteText.setText(note);
    noteText.addTextChangedListener(new AfterTextChanged(editable -> {
      boolean isEnabled = editable.length() > 0;
      saveButton.setEnabled(isEnabled);
      saveButton.animate().alpha(isEnabled ? 1f : 0.5f);
       }));

    updateSmsButtonText(contactsFragment.getSelectedContacts().size());

    smsCancelButton.setOnClickListener(new SmsCancelClickListener());
    smsSendButton.setOnClickListener(new SmsSendClickListener());
    contactFilter.setOnFilterChangedListener(new ContactFilterChangedListener());
    contactFilter.setNavigationIcon(R.drawable.ic_search_conversation_24);

    if (Util.isDefaultSmsProvider(this)) {
      noteButton.setOnClickListener(new ShareClickListener());
      saveButton.setOnClickListener(new SmsClickListener());
    } else {
      noteButton.setVisibility(View.GONE);
      saveButton.setOnClickListener(new ShareClickListener());
      saveButton.setOnClickListener(new SmsClickListener());
      saveButton.setText(R.string.NoteActivity_simpan);
    }
  }

  private Animation loadAnimation(@AnimRes int animResId) {
    final Animation animation = AnimationUtils.loadAnimation(this, animResId);
    animation.setInterpolator(new FastOutSlowInInterpolator());
    return animation;
  }

  @Override
  public boolean onBeforeContactSelected(Optional<RecipientId> recipientId, String number) {
    updateSmsButtonText(contactsFragment.getSelectedContacts().size() + 1);
    return true;
  }

  @Override
  public void onContactDeselected(Optional<RecipientId> recipientId, String number) {
    updateSmsButtonText(contactsFragment.getSelectedContacts().size());
  }

  private void sendSmsInvites() {
    new SendSmsInvitesAsyncTask(this, noteText.getText().toString())
            .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                    contactsFragment.getSelectedContacts()
                            .toArray(new SelectedContact[0]));
  }

  private void updateSmsButtonText(int count) {
    smsSendButton.setText(getResources().getQuantityString(R.plurals.InviteActivity_send_sms_to_friends,
            count,
            count));
    smsSendButton.setEnabled(count > 0);
  }

  @Override public void onBackPressed() {
    if (smsSendFrame.getVisibility() == View.VISIBLE) {
      cancelSmsSelection();
    } else {
      super.onBackPressed();
    }
  }

  private void cancelSmsSelection() {
    setPrimaryColorsToolbarNormal();
    contactsFragment.reset();
    updateSmsButtonText(contactsFragment.getSelectedContacts().size());
    ViewUtil.animateOut(smsSendFrame, slideOutAnimation, View.GONE);
  }

  private void setPrimaryColorsToolbarNormal() {
    primaryToolbar.setBackgroundColor(0);
    primaryToolbar.getNavigationIcon().setColorFilter(null);
    primaryToolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.signal_text_primary));

    if (Build.VERSION.SDK_INT >= 23) {
      WindowUtil.setStatusBarColor(getWindow(), ThemeUtil.getThemedColor(this, android.R.attr.statusBarColor));
      getWindow().setNavigationBarColor(ThemeUtil.getThemedColor(this, android.R.attr.navigationBarColor));
      WindowUtil.setLightStatusBarFromTheme(this);
    }

    WindowUtil.setLightNavigationBarFromTheme(this);
  }

  private void setPrimaryColorsToolbarForSms() {
    primaryToolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.core_ultramarine));
    primaryToolbar.getNavigationIcon().setColorFilter(ContextCompat.getColor(this, R.color.signal_text_toolbar_subtitle), PorterDuff.Mode.SRC_IN);
    primaryToolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.signal_text_toolbar_title));

    if (Build.VERSION.SDK_INT >= 23) {
      WindowUtil.setStatusBarColor(getWindow(), ContextCompat.getColor(this, R.color.core_ultramarine));
      WindowUtil.clearLightStatusBar(getWindow());
    }

    if (Build.VERSION.SDK_INT >= 27) {
      getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.core_ultramarine));
      WindowUtil.clearLightNavigationBar(getWindow());
    }
  }

  private class ShareClickListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      noteText.setText(noteText.getText().toString());
    }
  }

  private class SmsClickListener implements OnClickListener {

    @Override
    public void onClick(View v) {
      note = noteText.getText().toString();
      s.push("\n" + note);
      j = s.size();
      int k = 0;
      note1.append(note+"\n");
    }
    }


  private class SmsCancelClickListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      cancelSmsSelection();
    }
  }

  private class SmsSendClickListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      new AlertDialog.Builder(InviteActivity.this)
              .setTitle(getResources().getQuantityString(R.plurals.InviteActivity_send_sms_invites,
                      contactsFragment.getSelectedContacts().size(),
                      contactsFragment.getSelectedContacts().size()))
              .setMessage(noteText.getText().toString())
              .setPositiveButton(R.string.yes, (dialog, which) -> sendSmsInvites())
              .setNegativeButton(R.string.no, (dialog, which) -> dialog.dismiss())
              .show();
    }
  }

  private class ContactFilterChangedListener implements OnFilterChangedListener {
    @Override
    public void onFilterChanged(String filter) {
      contactsFragment.setQueryFilter(filter);
    }
  }

  @SuppressLint("StaticFieldLeak")
  private class SendSmsInvitesAsyncTask extends ProgressDialogAsyncTask<SelectedContact,Void,Void> {
    private final String message;

    SendSmsInvitesAsyncTask(Context context, String message) {
      super(context, R.string.InviteActivity_sending, R.string.InviteActivity_sending);
      this.message = message;
    }

    @Override
    protected Void doInBackground(SelectedContact... contacts) {
      final Context context = getContext();
      if (context == null) return null;

      for (SelectedContact contact : contacts) {
        RecipientId recipientId    = contact.getOrCreateRecipientId(context);
        Recipient   recipient      = Recipient.resolved(recipientId);
        int         subscriptionId = recipient.getDefaultSubscriptionId().or(-1);

        MessageSender.send(context, new OutgoingTextMessage(recipient, message, subscriptionId), -1L, true, null);

        if (recipient.getContactUri() != null) {
          DatabaseFactory.getRecipientDatabase(context).setHasSentInvite(recipient.getId());
        }
      }

      return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
      super.onPostExecute(aVoid);
      final Context context = getContext();
      if (context == null) return;

      ViewUtil.animateOut(smsSendFrame, slideOutAnimation, View.GONE).addListener(new Listener<Boolean>() {
        @Override
        public void onSuccess(Boolean result) {
          contactsFragment.reset();
        }

        @Override
        public void onFailure(ExecutionException e) {}
      });
      Toast.makeText(context, R.string.InviteActivity_invitations_sent, Toast.LENGTH_LONG).show();
    }
  }
}
