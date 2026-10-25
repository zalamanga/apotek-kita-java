package apotek;

import org.junit.jupiter.api.*;
import java.sql.*;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class UserServiceTest {
  private Fixture db;

  @BeforeEach
  void setup() throws Exception {
    db = new Fixture();
  }

  @Test
  void hashesUseRandomSaltAndRejectPlaintext() {
    String a = Passwords.hash(Fixture.PASSWORD.toCharArray()),
        b = Passwords.hash(Fixture.PASSWORD.toCharArray());
    assertNotEquals(a, b);
    assertTrue(Passwords.verify(Fixture.PASSWORD.toCharArray(), a));
    assertFalse(Passwords.verify("wrong".toCharArray(), a));
    assertFalse(Passwords.verify(Fixture.PASSWORD.toCharArray(), "plaintext"));
  }

  @Test
  void loginRejectsInjectionAndWrongPasswordAndWipesArray() throws Exception {
    char[] p = Fixture.PASSWORD.toCharArray();
    assertNotNull(db.service.login("test-admin", p));
    for (char c : p) assertEquals('\0', c);
    assertNull(db.service.login("' OR 1=1 --", Fixture.PASSWORD.toCharArray()));
    assertNull(db.service.session());
    assertNull(db.service.login("test-admin", "wrong".toCharArray()));
  }

  @Test
  void adminCreatesFindsUpdatesAndDeletesUserWithoutExposingPassword() throws Exception {
    User u = Fixture.user("USR_NEW", "synthetic-user", User.CASHIER);
    db.service.save(u, Fixture.PASSWORD.toCharArray(), false);
    assertEquals(1, db.service.list("synthetic-user").size());
    assertTrue(db.service.list("' OR 1=1 --").isEmpty());
    User changed = Fixture.user("USR_NEW", "changed-user", User.CASHIER);
    db.service.save(changed, new char[0], true);
    assertEquals("changed-user", db.service.list("USR_NEW").get(0).username());
    db.service.delete("USR_NEW");
    assertTrue(db.service.list("USR_NEW").isEmpty());
    assertEquals(1, db.count("SELECT COUNT(*) FROM tbl_user"));
  }

  @Test
  void blankPasswordOnUpdateKeepsHash() throws Exception {
    db.service.save(
        Fixture.user("USR_NEW", "new-user", User.CASHIER), Fixture.PASSWORD.toCharArray(), false);
    db.service.save(Fixture.user("USR_NEW", "new-user", User.CASHIER), new char[0], true);
    assertNotNull(db.service.login("new-user", Fixture.PASSWORD.toCharArray()));
  }

  @Test
  void cashiersSeeOnlyOwnProfileAndCannotWrite() throws Exception {
    db.service.save(
        Fixture.user("USR_NEW", "cashier-user", User.CASHIER),
        Fixture.PASSWORD.toCharArray(),
        false);
    db.service.login("cashier-user", Fixture.PASSWORD.toCharArray());
    assertEquals(1, db.service.list("").size());
    assertEquals("USR_NEW", db.service.list("").get(0).id());
    assertThrows(SecurityException.class, () -> db.service.delete("USR_ADMIN_DEMO"));
    assertThrows(
        SecurityException.class,
        () ->
            db.service.save(
                Fixture.user("OTHER", "other-user", User.ADMIN),
                Fixture.PASSWORD.toCharArray(),
                false));
    db.service.logout();
    assertThrows(SecurityException.class, () -> db.service.list(""));
  }

  @Test
  void lastAdminAndSelfDeletionCannotRemoveManagementAccess() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> db.service.delete("USR_ADMIN_DEMO"));
    assertThrows(IllegalArgumentException.class, () -> db.service.delete("usr_admin_demo"));
    User demote = Fixture.user("USR_ADMIN_DEMO", "test-admin", User.CASHIER);
    assertThrows(IllegalArgumentException.class, () -> db.service.save(demote, new char[0], true));
    assertEquals(User.ADMIN, db.service.list("").get(0).role());
  }

  @Test
  void secondAdminCanBeDemotedOrDeletedButCurrentAdminRemains() throws Exception {
    db.service.save(
        Fixture.user("ADMIN2", "second-admin", User.ADMIN), Fixture.PASSWORD.toCharArray(), false);
    db.service.save(Fixture.user("ADMIN2", "second-admin", User.CASHIER), new char[0], true);
    db.service.delete("ADMIN2");
    assertEquals(1, db.count("SELECT COUNT(*) FROM tbl_user WHERE role='Administrator'"));
  }

  @Test
  void revokedDatabaseRoleCannotUseStaleAdminSession() throws Exception {
    try (Connection c = db.open();
        Statement s = c.createStatement()) {
      s.executeUpdate("UPDATE tbl_user SET role='Cashier'");
    }
    assertThrows(
        SecurityException.class,
        () ->
            db.service.save(
                Fixture.user("USR_NEW", "new-user", User.CASHIER),
                Fixture.PASSWORD.toCharArray(),
                false));
    assertEquals(1, db.service.list("").size());
    assertFalse(db.service.isAdmin());
    assertEquals(1, db.count("SELECT COUNT(*) FROM tbl_user"));
  }

  @Test
  void duplicateUsernameRollsBackAndOriginalRemains() throws Exception {
    assertThrows(
        SQLException.class,
        () ->
            db.service.save(
                Fixture.user("USR_NEW", "test-admin", User.CASHIER),
                Fixture.PASSWORD.toCharArray(),
                false));
    assertEquals(1, db.count("SELECT COUNT(*) FROM tbl_user"));
    assertNotNull(db.service.login("test-admin", Fixture.PASSWORD.toCharArray()));
  }

  @Test
  void validationRejectsInvalidEmailFutureDateAndMissingGender() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new User(
                "ID",
                "valid-user",
                null,
                LocalDate.now(),
                "00000000",
                "a@example.test",
                "Demo",
                User.CASHIER));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new User(
                "ID",
                "valid-user",
                "M",
                LocalDate.now().plusDays(1),
                "00000000",
                "a@example.test",
                "Demo",
                User.CASHIER));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new User(
                "ID",
                "valid-user",
                "M",
                LocalDate.now(),
                "00000000",
                "invalid",
                "Demo",
                User.CASHIER));
  }
}
