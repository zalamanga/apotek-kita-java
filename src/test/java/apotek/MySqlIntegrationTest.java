package apotek;

import DB.DatabaseConnection;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class MySqlIntegrationTest {
  @Test
  void localMysqlAccountCrudAndRoleProtection() throws Exception {
    Assumptions.assumeTrue(
        "1".equals(System.getenv("APOTEK_MYSQL_QA")), "Explicit demo MySQL QA opt-in required.");
    assertEquals("jdbc:mysql://localhost:13316/apotek_kita_demo", System.getenv("DB_URL"));
    String admin = System.getenv("APP_ADMIN_USERNAME"),
        password = System.getenv("APP_ADMIN_PASSWORD");
    assertNotNull(admin);
    assertNotNull(password);
    UserService service = new UserService(DatabaseConnection::open);
    assertNotNull(service.login(admin, password.toCharArray()));
    assertTrue(service.isAdmin());
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8),
        first = "QA_" + suffix,
        second = "QB_" + suffix;
    try {
      User cashier = Fixture.user(first, "qa-" + suffix, User.CASHIER);
      service.save(cashier, Fixture.PASSWORD.toCharArray(), false);
      service.save(
          Fixture.user(second, "qb-" + suffix, User.ADMIN), Fixture.PASSWORD.toCharArray(), false);
      assertEquals(1, service.list(first).size());
      assertTrue(service.list("' OR 1=1 --").isEmpty());
      assertNotNull(service.login(cashier.username(), Fixture.PASSWORD.toCharArray()));
      assertEquals(1, service.list("").size());
      assertThrows(SecurityException.class, () -> service.delete(second));
      service.logout();
      assertThrows(SecurityException.class, () -> service.list(""));
      service.login(admin, password.toCharArray());
      service.save(Fixture.user(first, "updated-" + suffix, User.CASHIER), new char[0], true);
      assertEquals("updated-" + suffix, service.list(first).get(0).username());
      assertThrows(IllegalArgumentException.class, () -> service.delete(service.session().id()));
      service.delete(first);
      service.delete(second);
      assertTrue(service.list(first).isEmpty());
      System.out.println(
          "MySQL synthetic account CRUD, search, password preservation, roles and logout verified.");
    } finally {
      if (!first.matches("QA_[a-f0-9]{8}") || !second.matches("QB_[a-f0-9]{8}"))
        throw new IllegalStateException("Unexpected synthetic ID.");
      try (Connection c = DatabaseConnection.open();
          PreparedStatement p = c.prepareStatement("DELETE FROM tbl_user WHERE id_user IN (?,?)")) {
        p.setString(1, first);
        p.setString(2, second);
        p.executeUpdate();
      }
    }
  }
}
