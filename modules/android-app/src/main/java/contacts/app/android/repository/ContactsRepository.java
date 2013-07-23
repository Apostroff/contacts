package contacts.app.android.repository;

import java.util.List;

import android.accounts.Account;
import contacts.app.android.model.Contact;
import contacts.app.android.rest.AuthorizationException;
import contacts.app.android.rest.NetworkException;

/**
 * Remote repository that stores contacts.
 */
public interface ContactsRepository {

    /**
     * Finds contacts of people from the same office with user.
     */
    List<Contact> findByOffice(Account account) throws AuthorizationException,
            NetworkException;

}
