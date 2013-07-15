package grytsenko.contacts.repository;

import static grytsenko.contacts.util.StringUtils.digitsOnly;
import static java.text.MessageFormat.format;
import grytsenko.contacts.model.Contact;

import java.util.List;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.stereotype.Repository;

/**
 * Repository of contacts in directory service.
 */
@Repository
public class DsContactsRepository implements ContactsRepository {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(DsContactsRepository.class);

    @Autowired
    protected LdapContextSource ldapContextSource;

    @Value("#{ldapProperties['ldap.users']}")
    protected String usersGroup;

    @Value("#{ldapProperties['ldap.users.filter.username']}")
    protected String filterByUsernameTemplate;
    @Value("#{ldapProperties['ldap.users.filter.location']}")
    protected String filterByLocationTemplate;

    @Value("#{ldapProperties['ldap.user.username']}")
    protected String usernameAttr;
    @Value("#{ldapProperties['ldap.user.firstname']}")
    protected String firstnameAttr;
    @Value("#{ldapProperties['ldap.user.lastname']}")
    protected String lastnameAttr;
    @Value("#{ldapProperties['ldap.user.mail']}")
    protected String mailAttr;
    @Value("#{ldapProperties['ldap.user.phone']}")
    protected String phoneAttr;
    @Value("#{ldapProperties['ldap.user.location']}")
    protected String locationAttr;

    @Override
    public Contact findByUserName(String userName) {
        LOGGER.debug("Search contact for {}.", userName);

        String filter = format(filterByUsernameTemplate, userName);
        List<Contact> contacts = findUsingFilter(filter);

        if (contacts.isEmpty()) {
            LOGGER.debug("Contact for {} was not found.", userName);
            return null;
        }

        return contacts.get(0);
    }

    @Override
    public List<Contact> findByLocation(String location) {
        LOGGER.debug("Search contacts of people from {}.", location);

        String filter = format(filterByLocationTemplate, location);
        return findUsingFilter(filter);
    }

    private List<Contact> findUsingFilter(String filter) {
        LOGGER.debug("Find contacts using filter '{}'.", filter);

        LdapTemplate template = new LdapTemplate(ldapContextSource);

        @SuppressWarnings("unchecked")
        List<Contact> contacts = template.search(usersGroup, filter,
                new ContactMapper());

        LOGGER.debug("Found {} contacts.", contacts.size());

        return contacts;
    }

    /**
     * Factory, that creates a contact using data from DS.
     */
    private class ContactMapper implements AttributesMapper {

        @Override
        public Contact mapFromAttributes(Attributes attrs)
                throws NamingException {
            Contact contact = new Contact();

            contact.setUserName(asString(usernameAttr, attrs));

            contact.setFirstName(asString(firstnameAttr, attrs));
            contact.setLastName(asString(lastnameAttr, attrs));

            contact.setMail(asString(mailAttr, attrs));
            contact.setPhone(digitsOnly(asString(phoneAttr, attrs)));

            contact.setLocation(asString(locationAttr, attrs));

            return contact;
        }

        private String asString(String attrId, Attributes attrs)
                throws NamingException {
            Attribute attr = attrs.get(attrId);
            return attr == null ? null : (String) attr.get();
        }

    }

}
