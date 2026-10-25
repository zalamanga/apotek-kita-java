package apotek;

import java.time.LocalDate;
import java.util.Set;

public record User(
    String id,
    String username,
    String sex,
    LocalDate dob,
    String phone,
    String email,
    String address,
    String role) {
  public static final String ADMIN = "Administrator", CASHIER = "Cashier";

  public User {
    if (id == null || !id.matches("[A-Za-z0-9_-]{1,32}"))
      throw new IllegalArgumentException("ID user tidak valid.");
    if (username == null || !username.matches("[A-Za-z0-9_.-]{3,40}"))
      throw new IllegalArgumentException(
          "Username harus 3-40 karakter: huruf, angka, titik, garis atau underscore.");
    if (sex == null || !Set.of("M", "F").contains(sex))
      throw new IllegalArgumentException("Pilih gender.");
    if (dob == null || dob.isBefore(LocalDate.of(1900, 1, 1)) || dob.isAfter(LocalDate.now()))
      throw new IllegalArgumentException("Tanggal lahir harus dari tahun 1900 hingga hari ini.");
    if (phone == null || !phone.matches("[+0-9 ()-]{5,30}"))
      throw new IllegalArgumentException("Nomor telepon tidak valid.");
    if (email == null || email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      throw new IllegalArgumentException("Alamat email tidak valid.");
    if (address == null || address.isBlank() || address.length() > 250)
      throw new IllegalArgumentException("Alamat wajib diisi, maksimal 250 karakter.");
    if (role == null || !Set.of(ADMIN, CASHIER).contains(role))
      throw new IllegalArgumentException("Role tidak dikenali.");
  }
}
