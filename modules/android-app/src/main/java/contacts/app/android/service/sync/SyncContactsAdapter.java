package contacts.app.android.service.sync;

import static java.lang.Thread.currentThread;
import static java.text.MessageFormat.format;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;
import contacts.app.android.R;
import contacts.app.android.model.Contact;
import contacts.app.android.repository.ContactsRepository;
import contacts.app.android.repository.ContactsRepositoryRest;
import contacts.app.android.repository.RepositoryException;

/**
 * Synchronizes contacts.
 */
public class SyncContactsAdapter extends AbstractThreadedSyncAdapter {

    private static final String TAG = SyncContactsAdapter.class.getName();

    private ContactsRepository contactsRepository;

    private ContentResolver contentResolver;

    public SyncContactsAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);

        contactsRepository = new ContactsRepositoryRest(context);
        contentResolver = context.getContentResolver();
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
            ContentProviderClient provider, SyncResult syncResult) {
        Log.d(TAG, "Sync started.");

        try {
            String groupTitle = getContext().getString(R.string.groupCoworkers);
            String groupId = findGroup(account, groupTitle);

            if (isCanceled()) {
                return;
            }

            List<Contact> contacts = contactsRepository.findByLocation(account);
            Log.d(TAG,
                    format("Found {0} contacts in repository.", contacts.size()));

            if (isCanceled()) {
                return;
            }

            addContacts(account, groupId, contacts);
        } catch (RepositoryException exception) {
            Log.e(TAG, "Repository is not accessible.", exception);
        } catch (SyncException exception) {
            Log.e(TAG, "Sync could not be completed.", exception);
        }

        Log.d(TAG, "Sync finished.");
    }

    private String findGroup(Account account, String title)
            throws SyncException {
        String[] projection = new String[] { ContactsContract.Groups._ID,
                ContactsContract.Groups.TITLE };
        String selection = ContactsContract.Groups.TITLE + "=? and "
                + ContactsContract.Groups.ACCOUNT_NAME + "=? and "
                + ContactsContract.Groups.ACCOUNT_TYPE + "=?";
        Cursor cursor = contentResolver.query(
                ContactsContract.Groups.CONTENT_URI, projection, selection,
                new String[] { title, account.name, account.type }, null);

        try {
            if (cursor.getCount() <= 0) {
                Log.d(TAG, format("Group {0} not found.", title));
                return createGroup(account, title);
            }

            cursor.moveToNext();
            String id = cursor.getString(cursor
                    .getColumnIndex(ContactsContract.Groups._ID));
            Log.d(TAG, format("Group {0} with id {1} was found.", title, id));
            return id;
        } finally {
            cursor.close();
        }
    }

    private String createGroup(Account account, String title)
            throws SyncException {
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

        ops.add(ContentProviderOperation
                .newInsert(ContactsContract.Groups.CONTENT_URI)
                .withValue(ContactsContract.Groups.TITLE, title)
                .withValue(ContactsContract.Groups.ACCOUNT_NAME, account.name)
                .withValue(ContactsContract.Groups.ACCOUNT_TYPE, account.type)
                .withValue(ContactsContract.Groups.GROUP_VISIBLE, 1).build());

        try {
            ContentProviderResult[] results = contentResolver.applyBatch(
                    ContactsContract.AUTHORITY, ops);
            String id = Long.toString(ContentUris.parseId(results[0].uri));
            Log.d(TAG, format("Group {0} with id {1} was created.", title, id));
            return id;
        } catch (Exception exception) {
            Log.e(TAG, format("Can not create group {0}.", title), exception);
            throw new SyncException("Group not created.", exception);
        }
    }

    private void addContacts(Account account, String groupId,
            List<Contact> contacts) {
        Set<String> knownContacts = getKnownContacts(account);

        for (Contact contact : contacts) {
            String userName = contact.getUserName();
            Log.d(TAG, format("Sync contact for {0}.", userName));
            if (isCanceled()) {
                return;
            }

            if (knownContacts.contains(userName)) {
                Log.d(TAG, format("Contact for {0} already exists.", userName));
                continue;
            }

            try {
                addContact(account, groupId, contact);
            } catch (SyncException exception) {
                Log.d(TAG, format("Contact for {0} skipped.", userName));
            }
        }
    }

    private void addContact(Account account, String groupId, Contact contact)
            throws SyncException {
        String userName = contact.getUserName();

        Log.d(TAG, format("Add contact for {0}.", userName));

        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_NAME, account.name)
                .withValue(RawContacts.ACCOUNT_TYPE, account.type)
                .withValue(RawContacts.SYNC1, userName).build());

        ops.add(ContentProviderOperation
                .newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(
                        ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                        contact.getFirstName())
                .withValue(
                        ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
                        contact.getLastName())
                .withValueBackReference(
                        ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID,
                        0).build());

        ops.add(addContactData(
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                contact.getMail()));
        ops.add(addContactData(
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                contact.getFormattedPhone()));
        ops.add(addContactData(
                ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Organization.DEPARTMENT,
                contact.getLocation()));
        ops.add(addContactData(
                ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID,
                groupId));

        try {
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (Exception exception) {
            Log.w(TAG, format("Can not add contact for {0}.", userName),
                    exception);
            throw new SyncException("Contact not added.", exception);
        }
    }

    private Set<String> getKnownContacts(Account account) {
        Uri uri = RawContacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(RawContacts.ACCOUNT_NAME, account.name)
                .appendQueryParameter(RawContacts.ACCOUNT_TYPE, account.type)
                .build();

        String[] projection = new String[] { RawContacts.SYNC1 };
        Cursor cursor = contentResolver
                .query(uri, projection, null, null, null);

        try {
            int contactsNum = cursor.getCount();
            Log.d(TAG,
                    format("Found {0} contacts in address book.", contactsNum));
            if (contactsNum == 0) {
                return Collections.emptySet();
            }

            Set<String> knownContacts = new HashSet<String>(contactsNum);
            for (int i = 0; i < contactsNum; ++i) {
                cursor.moveToNext();
                knownContacts.add(cursor.getString(0));
            }

            return knownContacts;
        } finally {
            cursor.close();
        }
    }

    private static ContentProviderOperation addContactData(String type,
            String key, String value) {
        return ContentProviderOperation
                .newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.MIMETYPE, type)
                .withValue(key, value)
                .withValueBackReference(
                        ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID,
                        0).build();
    }

    /**
     * Checks, that synchronization is canceled.
     */
    private boolean isCanceled() {
        boolean canceled = currentThread().isInterrupted();
        if (canceled) {
            Log.d(TAG, "Sync canceled.");
        }

        return canceled;
    }

}
