package org.pac4j.couch.profile.service;

import org.apache.shiro.authc.credential.DefaultPasswordService;
import org.ektorp.CouchDbConnector;
import org.junit.*;
import org.pac4j.core.exception.*;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.service.AbstractProfileService;
import org.pac4j.core.util.TestsConstants;
import org.pac4j.core.credentials.UsernamePasswordCredentials;
import org.pac4j.core.credentials.password.PasswordEncoder;
import org.pac4j.core.credentials.password.ShiroPasswordEncoder;
import org.pac4j.core.util.TestsHelper;
import org.pac4j.couch.profile.CouchProfile;
import org.pac4j.couch.test.tools.CouchServer;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests the {@link MongoAuthenticator}.
 *
 * @author Elie Roux
 * @since 2.0.0
 */
public final class CouchProfileServiceTests implements TestsConstants {

    private static final int PORT = 13598;
    private static final String COUCH_ID_FIELD = CouchProfileService.COUCH_ID;
    private static final String COUCH_ID = "couchId";
    private static final String COUCH_LINKED_ID = "couchLinkedId";
    private static final String COUCH_USER = "couchUser";
    private static final String COUCH_USER2 = "couchUser2";
    private static final String COUCH_PASS = "couchPass";
    private static final String COUCH_PASS2 = "couchPass2";
    private static final String IDPERSON1 = "idperson1";
    private static final String IDPERSON2 = "idperson2";
    private static final String IDPERSON3 = "idperson3";
    
    public final static PasswordEncoder PASSWORD_ENCODER = new ShiroPasswordEncoder(new DefaultPasswordService());
    public static CouchDbConnector couchDbConnector = null;
    private static final CouchServer couchServer = new CouchServer();


    @BeforeClass
    public static void setUp() {
        couchDbConnector = couchServer.start(PORT);
        final String password = PASSWORD_ENCODER.encode(PASSWORD);
		final CouchProfileService couchProfileService = new CouchProfileService(couchDbConnector);
        couchProfileService.setPasswordEncoder(PASSWORD_ENCODER);
        // insert sample data
        final Map<String, Object> properties1 = new HashMap<>();
		properties1.put(USERNAME, GOOD_USERNAME);
		properties1.put(FIRSTNAME, FIRSTNAME_VALUE);
		CouchProfile couchProfile = new CouchProfile();
		couchProfile.build(IDPERSON1, properties1);
		couchProfileService.create(couchProfile, PASSWORD);
		// second person, 
		final Map<String, Object> properties2 = new HashMap<>();
		properties2.put(USERNAME, MULTIPLE_USERNAME);
		couchProfile = new CouchProfile();
		couchProfile.build(IDPERSON2, properties2);
		couchProfileService.create(couchProfile, PASSWORD);
		final Map<String, Object> properties3 = new HashMap<>();
		properties3.put(USERNAME, MULTIPLE_USERNAME);
		properties3.put(PASSWORD, password);
		couchProfile = new CouchProfile();
		couchProfile.build(IDPERSON3, properties3);
		couchProfileService.create(couchProfile, PASSWORD);
		
    }

    @AfterClass
    public static void tearDown() {
        //couchServer.stop();
    }

    @Test
    public void testNullConnector() {
        final CouchProfileService couchProfileService = new CouchProfileService(null);
        couchProfileService.setPasswordEncoder(PASSWORD_ENCODER);
        TestsHelper.expectException(() -> couchProfileService.init(null), TechnicalException.class, "couchDbConnector cannot be null");
    }

    @Test(expected = AccountNotFoundException.class)
    public void authentFailed() throws HttpAction, CredentialsException {
        final CouchProfileService couchProfileService = new CouchProfileService(couchDbConnector);
        couchProfileService.setPasswordEncoder(PASSWORD_ENCODER);
        final UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(BAD_USERNAME, PASSWORD, CLIENT_NAME);
        couchProfileService.validate(credentials, null);
    }

    @Test
    public void authentSuccessNoAttribute() throws HttpAction, CredentialsException {
        final CouchProfileService couchProfileService = new CouchProfileService(couchDbConnector);
        couchProfileService.setPasswordEncoder(PASSWORD_ENCODER);
        final UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(GOOD_USERNAME, PASSWORD, CLIENT_NAME);
        couchProfileService.validate(credentials, null);

        final CommonProfile profile = credentials.getUserProfile();
        assertNotNull(profile);
        assertTrue(profile instanceof CouchProfile);
        final CouchProfile couchProfile = (CouchProfile) profile;
        assertEquals(GOOD_USERNAME, couchProfile.getId());
        assertEquals(0, couchProfile.getAttributes().size());
    }

    @Test
    public void authentSuccessSingleAttribute() throws HttpAction, CredentialsException {
        final CouchProfileService couchProfileService = new CouchProfileService(couchDbConnector);
        couchProfileService.setPasswordEncoder(PASSWORD_ENCODER);
        couchProfileService.setUsernameAttribute(COUCH_ID_FIELD);
        final UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(GOOD_USERNAME, PASSWORD, CLIENT_NAME);
        couchProfileService.validate(credentials, null);

        final CommonProfile profile = credentials.getUserProfile();
        assertNotNull(profile);
        assertTrue(profile instanceof CouchProfile);
        final CouchProfile ldapProfile = (CouchProfile) profile;
        assertEquals(GOOD_USERNAME, ldapProfile.getId());
        assertEquals(1, ldapProfile.getAttributes().size());
        assertEquals(FIRSTNAME_VALUE, ldapProfile.getAttribute(USERNAME));
    }

    @Test
    public void testCreateUpdateFindDelete() throws HttpAction, CredentialsException {
        final CouchProfile profile = new CouchProfile();
        profile.setId(COUCH_ID);
        profile.setLinkedId(COUCH_LINKED_ID);
        profile.addAttribute(USERNAME, COUCH_USER);
        final CouchProfileService couchProfileService = new CouchProfileService(couchDbConnector);
        couchProfileService.setPasswordEncoder(PASSWORD_ENCODER);
        couchProfileService.setPasswordAttribute("userPassword");
        // create
        couchProfileService.create(profile, COUCH_PASS);
        // check credentials
        final UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(COUCH_ID, COUCH_PASS, CLIENT_NAME);
        couchProfileService.validate(credentials, null);
        final CommonProfile profile1 = credentials.getUserProfile();
        assertNotNull(profile1);
        // check data
        final List<Map<String, Object>> results = getData(couchProfileService, COUCH_ID);
        assertEquals(1, results.size());
        final Map<String, Object> result = results.get(0);
        assertEquals(4, result.size());
        assertEquals(COUCH_ID, result.get(COUCH_ID_FIELD));
        assertEquals(COUCH_LINKED_ID, result.get(AbstractProfileService.LINKEDID));
        assertNotNull(result.get(AbstractProfileService.SERIALIZED_PROFILE));
        assertEquals(COUCH_USER, result.get(USERNAME));
        // findById
        final CouchProfile profile2 = couchProfileService.findById(COUCH_ID);
        assertEquals(COUCH_ID, profile2.getId());
        assertEquals(COUCH_LINKED_ID, profile2.getLinkedId());
        assertEquals(COUCH_USER, profile2.getUsername());
        assertEquals(1, profile2.getAttributes().size());
        // update
        profile.addAttribute(USERNAME, COUCH_USER2);
        couchProfileService.update(profile, COUCH_PASS2);
        final List<Map<String, Object>> results2 = getData(couchProfileService, COUCH_ID);
        assertEquals(1, results2.size());
        final Map<String, Object> result2 = results2.get(0);
        assertEquals(4, result2.size());
        assertEquals(COUCH_ID, result2.get(COUCH_ID_FIELD));
        assertEquals(COUCH_LINKED_ID, result2.get(AbstractProfileService.LINKEDID));
        assertNotNull(result2.get(AbstractProfileService.SERIALIZED_PROFILE));
        assertEquals(COUCH_USER2, result2.get(USERNAME));
        // check credentials
        final UsernamePasswordCredentials credentials2 = new UsernamePasswordCredentials(COUCH_ID, COUCH_PASS2, CLIENT_NAME);
        couchProfileService.validate(credentials2, null);
        final CommonProfile profile3 = credentials.getUserProfile();
        assertNotNull(profile3);
        // remove
        couchProfileService.remove(profile);
        final List<Map<String, Object>> results3 = getData(couchProfileService, COUCH_ID);
        assertEquals(0, results3.size());
    }

    private List<Map<String, Object>> getData(final CouchProfileService couchProfileService, final String id) {
        return couchProfileService.read(Arrays.asList(COUCH_ID_FIELD, "username", "linkedid", "password", "serializedprofile"), COUCH_ID_FIELD, id);
    }
}
