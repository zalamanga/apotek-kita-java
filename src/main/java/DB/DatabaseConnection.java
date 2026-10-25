package DB;

import java.sql.*;
import java.util.Properties;

public final class DatabaseConnection {
  public static Connection open() throws SQLException {
    String url = setting("apotek.db.url", "DB_URL"),
        user = setting("apotek.db.user", "DB_USER"),
        password = setting("apotek.db.password", "DB_PASSWORD");
    if (url == null
        || !url.startsWith("jdbc:mysql://")
        || url.toLowerCase().matches(".*[?&](?:user|password)=.*"))
      throw new SQLException("Set DB_URL to a MySQL URL without credentials.");
    if (user == null || user.isBlank() || password == null || password.isBlank())
      throw new SQLException("DB_USER and DB_PASSWORD are required.");
    Properties p = new Properties();
    p.setProperty("user", user);
    p.setProperty("password", password);
    p.setProperty("connectTimeout", "5000");
    p.setProperty("socketTimeout", "10000");
    return DriverManager.getConnection(url, p);
  }

  public Connection setConnection() throws SQLException {
    return open();
  }

  private static String setting(String property, String env) {
    String value = System.getProperty(property);
    return value != null ? value : System.getenv(env);
  }
}
