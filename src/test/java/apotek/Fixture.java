package apotek;

import java.sql.*;
import java.time.LocalDate;
import java.util.UUID;

public final class Fixture {
  public static final String PASSWORD = "Synthetic-Test-Only!42";
  private final String url =
      "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
  public final UserService service = new UserService(this::open);

  public Fixture() throws Exception {
    try (Connection c = open()) {
      DatabaseSetup.install(c, "test-admin", PASSWORD.toCharArray());
    }
    service.login("test-admin", PASSWORD.toCharArray());
  }

  public Connection open() throws SQLException {
    return DriverManager.getConnection(url);
  }

  public static User user(String id, String name, String role) {
    return new User(
        id,
        name,
        "F",
        LocalDate.of(2000, 1, 1),
        "0000000000",
        name + "@example.test",
        "Alamat sintetis",
        role);
  }

  public int count(String sql) throws SQLException {
    try (Connection c = open();
        Statement s = c.createStatement();
        ResultSet r = s.executeQuery(sql)) {
      r.next();
      return r.getInt(1);
    }
  }
}
