package JAVA;

import apotek.UserService;
import DB.DatabaseConnection;
import java.sql.SQLException;

public final class LoginProcess implements GUI.Login {
  private final UserService service;

  public LoginProcess() {
    this(new UserService(DatabaseConnection::open));
  }

  public LoginProcess(UserService service) {
    this.service = service;
  }

  public boolean authenticate(String username, char[] password) throws SQLException {
    return service.login(username, password) != null;
  }

  public boolean getStatusLogin() {
    return service.session() != null;
  }

  @Override
  public void cek(String username, String password) {
    try {
      authenticate(username, password.toCharArray());
    } catch (SQLException ex) {
      throw new IllegalStateException("Koneksi database belum tersedia.", ex);
    }
  }
}
