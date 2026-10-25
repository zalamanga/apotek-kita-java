package apotek;

import java.security.*;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Passwords {
  private Passwords() {}

  public static String hash(char[] password) {
    if (password.length < 12 || password.length > 128)
      throw new IllegalArgumentException("Password baru harus 12 sampai 128 karakter.");
    byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);
    return "pbkdf2-sha256$210000$"
        + Base64.getEncoder().encodeToString(salt)
        + "$"
        + Base64.getEncoder().encodeToString(derive(password, salt, 210000));
  }

  public static boolean verify(char[] password, String hash) {
    if (hash == null || password.length > 128) return false;
    try {
      String[] x = hash.split("\\$");
      if (x.length != 4 || !x[0].equals("pbkdf2-sha256")) return false;
      int rounds = Integer.parseInt(x[1]);
      if (rounds < 100000 || rounds > 1000000) return false;
      byte[] salt = Base64.getDecoder().decode(x[2]), expected = Base64.getDecoder().decode(x[3]);
      return salt.length == 16
          && expected.length == 32
          && MessageDigest.isEqual(expected, derive(password, salt, rounds));
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  private static byte[] derive(char[] password, byte[] salt, int rounds) {
    PBEKeySpec spec = new PBEKeySpec(password, salt, rounds, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("PBKDF2 unavailable", ex);
    } finally {
      spec.clearPassword();
    }
  }
}
