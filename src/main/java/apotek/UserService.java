package apotek;

import java.sql.*;
import java.util.*;

public final class UserService {
  @FunctionalInterface
  public interface Connections {
    Connection open() throws SQLException;
  }

  private static final String COLUMNS = "id_user,username,sex,dob,phone_number,email,address,role";
  private final Connections connections;
  private volatile User session;
  private long generation;

  public UserService(Connections connections) {
    this.connections = Objects.requireNonNull(connections);
  }

  public User session() {
    return session;
  }

  public synchronized void logout() {
    session = null;
    generation++;
  }

  public boolean isAdmin() {
    return session != null && session.role().equals(User.ADMIN);
  }

  public User login(String username, char[] password) throws SQLException {
    long attempt;
    synchronized (this) {
      session = null;
      attempt = ++generation;
    }
    try {
      if (username == null || !username.matches("[A-Za-z0-9_.-]{3,40}") || password.length > 128)
        return null;
      try (Connection c = connections.open();
          PreparedStatement p =
              c.prepareStatement(
                  "SELECT " + COLUMNS + ",password FROM tbl_user WHERE username=?")) {
        p.setString(1, username);
        try (ResultSet r = p.executeQuery()) {
          if (!r.next() || !Passwords.verify(password, r.getString("password"))) return null;
          User authenticated = read(r);
          synchronized (this) {
            if (generation != attempt) return null;
            session = authenticated;
            return session;
          }
        }
      }
    } finally {
      Arrays.fill(password, '\0');
    }
  }

  public List<User> list(String search) throws SQLException {
    User current;
    long readGeneration;
    synchronized (this) {
      current = requireLogin();
      readGeneration = generation;
    }
    try (Connection c = connections.open()) {
      User fresh;
      try (PreparedStatement p =
          c.prepareStatement("SELECT " + COLUMNS + " FROM tbl_user WHERE id_user=?")) {
        p.setString(1, current.id());
        try (ResultSet r = p.executeQuery()) {
          if (!r.next()) throw new SecurityException("Akun tidak tersedia. Masuk kembali.");
          fresh = read(r);
        }
      }
      synchronized (this) {
        if (generation != readGeneration
            || session == null
            || !session.id().equalsIgnoreCase(current.id()))
          throw new SecurityException("Sesi telah berakhir.");
        session = fresh;
      }
      boolean admin = User.ADMIN.equals(fresh.role());
      String query =
          "SELECT "
              + COLUMNS
              + " FROM tbl_user WHERE (id_user LIKE ? OR username LIKE ?)"
              + (admin ? "" : " AND id_user=?")
              + " ORDER BY username";
      try (PreparedStatement p = c.prepareStatement(query)) {
        String term = "%" + Objects.toString(search, "").trim() + "%";
        p.setString(1, term);
        p.setString(2, term);
        if (!admin) p.setString(3, current.id());
        p.setMaxRows(500);
        try (ResultSet r = p.executeQuery()) {
          List<User> users = new ArrayList<>();
          while (r.next()) users.add(read(r));
          return users;
        }
      }
    }
  }

  private User requireLogin() {
    User user = session;
    if (user == null) throw new SecurityException("Silakan masuk terlebih dahulu.");
    return user;
  }

  private void requireAdmin() {
    if (!isAdmin()) throw new SecurityException("Hanya Administrator yang dapat mengelola user.");
  }

  public void save(User user, char[] password, boolean update) throws SQLException {
    String hash;
    try {
      requireAdmin();
      hash = update && password.length == 0 ? null : Passwords.hash(password);
    } finally {
      Arrays.fill(password, '\0');
    }
    User current = requireLogin();
    try (Connection c = connections.open()) {
      c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
      c.setAutoCommit(false);
      try {
        int adminCount = lockAdministrators(c, current.id());
        String oldRole = update ? role(c, user.id()) : null;
        if (update && oldRole == null)
          throw new IllegalArgumentException("User tidak ditemukan. Muat ulang data.");
        if (update
            && User.ADMIN.equals(oldRole)
            && !User.ADMIN.equals(user.role())
            && adminCount <= 1)
          throw new IllegalArgumentException(
              "Administrator terakhir tidak boleh diturunkan rolenya.");
        if (update && user.id().equalsIgnoreCase(current.id()) && !User.ADMIN.equals(user.role()))
          throw new IllegalArgumentException(
              "Role akun yang sedang dipakai harus tetap Administrator.");
        String sql =
            update
                ? "UPDATE tbl_user SET username=?,sex=?,dob=?,phone_number=?,email=?,address=?,role=?"
                    + (hash == null ? "" : ",password=?")
                    + " WHERE id_user=?"
                : "INSERT INTO tbl_user (username,sex,dob,phone_number,email,address,role,password,id_user) VALUES (?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement p = c.prepareStatement(sql)) {
          p.setString(1, user.username());
          p.setString(2, user.sex());
          p.setDate(3, java.sql.Date.valueOf(user.dob()));
          p.setString(4, user.phone());
          p.setString(5, user.email());
          p.setString(6, user.address());
          p.setString(7, user.role());
          int next = 8;
          if (hash != null) p.setString(next++, hash);
          p.setString(next, user.id());
          if (p.executeUpdate() != 1) throw new SQLException("Record changed.");
        }
        c.commit();
        synchronized (this) {
          if (session != null && user.id().equalsIgnoreCase(session.id())) session = user;
        }
      } catch (SQLException | RuntimeException ex) {
        rollback(c, ex);
        throw ex;
      }
    }
  }

  public void delete(String id) throws SQLException {
    requireAdmin();
    User current = requireLogin();
    if (id == null || !id.matches("[A-Za-z0-9_-]{1,32}"))
      throw new IllegalArgumentException("Pilih user yang akan dihapus.");
    if (id.equalsIgnoreCase(current.id()))
      throw new IllegalArgumentException("Akun yang sedang dipakai tidak dapat dihapus.");
    try (Connection c = connections.open()) {
      c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
      c.setAutoCommit(false);
      try {
        int count = lockAdministrators(c, current.id());
        String role = role(c, id);
        if (role == null) throw new IllegalArgumentException("User tidak ditemukan.");
        if (User.ADMIN.equals(role) && count <= 1)
          throw new IllegalArgumentException("Administrator terakhir tidak dapat dihapus.");
        try (PreparedStatement p = c.prepareStatement("DELETE FROM tbl_user WHERE id_user=?")) {
          p.setString(1, id);
          if (p.executeUpdate() != 1) throw new SQLException("Record changed.");
        }
        c.commit();
      } catch (SQLException | RuntimeException ex) {
        rollback(c, ex);
        throw ex;
      }
    }
  }

  private static int lockAdministrators(Connection c, String actorId) throws SQLException {
    int count = 0;
    boolean actorStillAdmin = false;
    // The ordered row locks serialize changes that might remove the last admin.
    try (PreparedStatement p =
        c.prepareStatement(
            "SELECT id_user FROM tbl_user WHERE role=? ORDER BY id_user FOR UPDATE")) {
      p.setString(1, User.ADMIN);
      try (ResultSet r = p.executeQuery()) {
        while (r.next()) {
          count++;
          if (actorId.equalsIgnoreCase(r.getString(1))) actorStillAdmin = true;
        }
      }
    }
    if (!actorStillAdmin)
      throw new SecurityException(
          "Hak Administrator telah berubah. Keluar akun dan masuk kembali.");
    return count;
  }

  private static String role(Connection c, String id) throws SQLException {
    try (PreparedStatement p =
        c.prepareStatement("SELECT role FROM tbl_user WHERE id_user=? FOR UPDATE")) {
      p.setString(1, id);
      try (ResultSet r = p.executeQuery()) {
        return r.next() ? r.getString(1) : null;
      }
    }
  }

  private static void rollback(Connection c, Exception original) {
    try {
      c.rollback();
    } catch (SQLException ex) {
      original.addSuppressed(ex);
    }
  }

  private static User read(ResultSet r) throws SQLException {
    return new User(
        r.getString("id_user"),
        r.getString("username"),
        r.getString("sex"),
        r.getDate("dob").toLocalDate(),
        r.getString("phone_number"),
        r.getString("email"),
        r.getString("address"),
        r.getString("role"));
  }
}
