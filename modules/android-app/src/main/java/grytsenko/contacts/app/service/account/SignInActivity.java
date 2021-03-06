package grytsenko.contacts.app.service.account;

import static java.text.MessageFormat.format;
import grytsenko.contacts.app.R;
import grytsenko.contacts.app.data.NotAuthorizedException;
import grytsenko.contacts.app.data.NotAvailableException;
import grytsenko.contacts.app.data.RestClient;
import grytsenko.contacts.common.util.StringUtils;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Allows user to enter credentials and create new account.
 * 
 * <p>
 * In this activity we set the default settings for synchronization.
 * 
 * <p>
 * We use {@link RestClient#getMy(String, String)} to check credentials.
 */
public class SignInActivity extends AccountAuthenticatorActivity {

    private static final String TAG = SignInActivity.class.getName();

    private EditText nameInput;
    private EditText passwordInput;

    private String accountType;

    private String username;
    private String password;

    private SignInTask signInTask;
    private Dialog signInDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setDefaultSyncSettings();

        setContentView(R.layout.signin_activity_layout);

        accountType = getString(R.string.accountType);

        signInDialog = new AlertDialog.Builder(this)
                .setMessage(R.string.accountAuthInProgress)
                .setOnCancelListener(new Dialog.OnCancelListener() {

                    public void onCancel(DialogInterface dialog) {
                        signInTask.cancel(true);
                    }

                }).create();

        nameInput = (EditText) findViewById(R.id.username);
        passwordInput = (EditText) findViewById(R.id.password);

        Button signInButton = (Button) findViewById(R.id.signIn);
        signInButton.setOnClickListener(new OnClickListener() {

            public void onClick(View view) {
                onSignIn();
            }

        });
    }

    private void setDefaultSyncSettings() {
        PreferenceManager.setDefaultValues(this, R.xml.sync_settings, false);
    }

    /**
     * Creates new account.
     * 
     * <p>
     * We allow to create several accounts, which are differ by username.
     */
    private void onSignIn() {
        /*
         * Checks that user has entered name.
         */
        username = nameInput.getText().toString();
        if (StringUtils.isNullOrEmpty(username)) {
            showToast(R.string.accountNameEmpty);
            return;
        }

        /*
         * Checks that user account with the same name not exists.
         */
        AccountManager manager = AccountManager.get(this);
        Account[] accounts = manager.getAccountsByType(accountType);
        for (Account account : accounts) {
            if (username.equals(account.name)) {
                Log.d(TAG, format("Account for {0} already exists.", username));
                showToast(R.string.accountAlreadyExists);
                return;
            }
        }

        /*
         * Checks that user has entered password.
         */
        password = passwordInput.getText().toString();
        if (StringUtils.isNullOrEmpty(password)) {
            showToast(R.string.accountPasswordEmpty);
            return;
        }

        /*
         * Verify name and password.
         */
        signInTask = new SignInTask();
        signInTask.execute();
        signInDialog.show();
    }

    public void onAuthCompleted(Boolean authenticated) {
        signInDialog.dismiss();

        if (!authenticated) {
            showToast(R.string.accountAuthFailed);
            return;
        }

        createAccount();

        username = null;
        password = null;

        finish();
    }

    private void createAccount() {
        Log.d(TAG, format("Create account for {0}.", username));

        AccountManager manager = AccountManager.get(this);
        Account account = new Account(username, accountType);
        boolean accountAdded = manager.addAccountExplicitly(account, password,
                null);
        if (!accountAdded) {
            Log.d(TAG, format("Account for {0} was not created.", username));
            showToast(R.string.accountNotAdded);
            return;
        }

        Bundle bundle = new Bundle();
        bundle.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
        bundle.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
        setAccountAuthenticatorResult(bundle);
    }

    public void onAuthCancelled() {
        Log.d(TAG, "Authentication cancelled by user.");
        showToast(R.string.accountAuthCancelled);
    }

    private void showToast(int messageId) {
        Toast.makeText(this, messageId, Toast.LENGTH_SHORT).show();
    }

    private class SignInTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... args) {
            try {
                RestClient restClient = new RestClient(SignInActivity.this);
                restClient.getMy(username, password);
                return true;
            } catch (NotAvailableException exception) {
                Log.d(TAG, "Not available.", exception);
            } catch (NotAuthorizedException exception) {
                Log.d(TAG, "Invalid credentials.", exception);
            }

            return false;
        }

        @Override
        protected void onPostExecute(Boolean authenticated) {
            onAuthCompleted(authenticated);
        }

        @Override
        protected void onCancelled() {
            onAuthCancelled();
        }
    }

}