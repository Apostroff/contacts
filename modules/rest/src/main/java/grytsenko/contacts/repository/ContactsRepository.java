package grytsenko.contacts.repository;

import grytsenko.contacts.model.Contact;

import java.util.List;

/**
 * Repository of contacts.
 */
public interface ContactsRepository {

    /**
     * Finds contact of concrete person.
     * 
     * @return the found contact or <code>null</code> if contact was not found.
     */
    Contact findByUserName(String userName);

    /**
     * Finds all contacts of people from one location.
     */
    List<Contact> findByLocation(String location);

}