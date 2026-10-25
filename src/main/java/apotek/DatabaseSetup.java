package apotek;

import DB.DatabaseConnection;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Arrays;

public final class DatabaseSetup {
  private DatabaseSetup() {}

  public static void install(Connection c, String username, char[] password)
      throws SQLException, IOException {
    User admin =
        new User(
            "USR_ADMIN_DEMO",
            username,
            "M",
            java.time.LocalDate.of(2000, 1, 1),
            "0000000000",
            "admin@example.test",
            "Alamat admin demo",
            User.ADMIN);
    String hash = Passwords.hash(password);
    try (InputStream in = DatabaseSetup.class.getResourceAsStream("/database/schema.sql")) {
      if (in == null) throw new IOException("Schema resource missing.");
      String schema = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      try (Statement s = c.createStatement()) {
        for (String command : schema.split(";")) if (!command.isBlank()) s.execute(command.trim());
      }
    }
    try (PreparedStatement p =
        c.prepareStatement(
            "INSERT INTO tbl_user (id_user,username,password,sex,dob,phone_number,email,address,role) VALUES (?,?,?,?,?,?,?,?,?)")) {
      p.setString(1, admin.id());
      p.setString(2, admin.username());
      p.setString(3, hash);
      p.setString(4, admin.sex());
      p.setDate(5, java.sql.Date.valueOf(admin.dob()));
      p.setString(6, admin.phone());
      p.setString(7, admin.email());
      p.setString(8, admin.address());
      p.setString(9, admin.role());
      p.executeUpdate();
    }
  }

  public static void main(String[] args) {
    if (args.length != 1 || !args[0].equals("--empty-demo-database")) {
      System.err.println(
          "Usage: DatabaseSetup --empty-demo-database. Use an EMPTY demonstration database.");
      return;
    }
    String raw = System.getenv("APP_ADMIN_PASSWORD");
    char[] password = raw == null ? new char[0] : raw.toCharArray();
    try (Connection c = DatabaseConnection.open()) {
      install(c, System.getenv("APP_ADMIN_USERNAME"), password);
      System.out.println("Schema dan akun Administrator demo dibuat.");
    } catch (SQLException | IOException | IllegalArgumentException ex) {
      System.err.println(
          "Setup gagal. Periksa database demo kosong, koneksi, username dan password 12+ karakter. Tabel yang sudah ada tidak ditimpa.");
      System.exit(1);
    } finally {
      Arrays.fill(password, '\0');
    }
  }
}
