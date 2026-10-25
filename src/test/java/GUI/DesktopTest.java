package GUI;

import apotek.*;
import org.junit.jupiter.api.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

@Tag("desktop")
class DesktopTest {
  @AfterEach
  void closeOwnWindows() throws Exception {
    edt(
        () -> {
          for (Window w : Window.getWindows()) w.dispose();
          return null;
        });
  }

  @Test
  void loginCreateSearchUpdateDeleteResetLogoutAndCashierView() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    Fixture db = new Fixture();
    db.service.logout();
    MainPage frame =
        edt(
            () -> {
              MainPage.installStyle();
              var f = new MainPage(db.service);
              f.setVisible(true);
              return f;
            });
    screenshot(frame, "01-login.png");
    edt(
        () -> {
          ((JCheckBox) find(frame, "showPassword")).doClick();
          assertEquals((char) 0, ((JPasswordField) find(frame, "loginPassword")).getEchoChar());
          ((JCheckBox) find(frame, "showPassword")).doClick();
          ((JTextField) find(frame, "loginUsername")).setText("clear-me");
          return null;
        });
    click(frame, "Kosongkan");
    assertEquals("", edt(() -> ((JTextField) find(frame, "loginUsername")).getText()));
    edt(
        () -> {
          ((JTextField) find(frame, "loginUsername")).setText("test-admin");
          ((JPasswordField) find(frame, "loginPassword")).setText(Fixture.PASSWORD);
          frame.getRootPane().getDefaultButton().doClick();
          return null;
        });
    idle(frame);
    assertNotNull(db.service.session());
    edt(
        () -> {
          ((JTextField) find(frame, "username")).setText("ui-user");
          ((JPasswordField) find(frame, "password")).setText(Fixture.PASSWORD);
          ((JPasswordField) find(frame, "confirmation")).setText(Fixture.PASSWORD);
          ((JTextField) find(frame, "dob")).setText("2000-01-01");
          ((JTextField) find(frame, "phone")).setText("0000000000");
          ((JTextField) find(frame, "email")).setText("ui@example.test");
          ((JTextArea) find(frame, "address")).setText("Alamat UI sintetis");
          return null;
        });
    click(frame, "Input data");
    idle(frame);
    assertEquals(2, db.service.list("").size());
    edt(
        () -> {
          ((JTextField) find(frame, "search")).setText("ui-user");
          return null;
        });
    click(frame, "Cari");
    idle(frame);
    JTable table = edt(() -> (JTable) find(frame, "users"));
    assertEquals(1, edt(table::getRowCount));
    edt(
        () -> {
          table.setRowSelectionInterval(0, 0);
          ((JTextField) find(frame, "username")).setText("ui-changed");
          return null;
        });
    click(frame, "Simpan perubahan");
    idle(frame);
    assertEquals(1, db.service.list("ui-changed").size());
    click(frame, "Lihat semua");
    idle(frame);
    screenshot(frame, "02-users.png");
    edt(
        () -> {
          ((JTextField) find(frame, "search")).setText("ui-changed");
          return null;
        });
    click(frame, "Cari");
    idle(frame);
    edt(
        () -> {
          table.setRowSelectionInterval(0, 0);
          return null;
        });
    SwingUtilities.invokeLater(() -> ((JButton) find(frame, "Hapus")).doClick());
    await(
        () ->
            java.util.Arrays.stream(Window.getWindows())
                .anyMatch(w -> w instanceof JDialog && w.isShowing()));
    edt(
        () -> {
          for (Window w : Window.getWindows())
            if (w instanceof JDialog dialog && w.isShowing()) {
              JOptionPane pane = pane(dialog);
              if (pane != null) pane.setValue(JOptionPane.OK_OPTION);
            }
          return null;
        });
    idle(frame);
    assertTrue(db.service.list("ui-changed").isEmpty());
    click(frame, "Reset form");
    db.service.save(
        Fixture.user("CASHIER", "cashier-ui", User.CASHIER), Fixture.PASSWORD.toCharArray(), false);
    click(frame, "Keluar akun");
    assertNull(db.service.session());
    edt(
        () -> {
          ((JTextField) find(frame, "loginUsername")).setText("cashier-ui");
          ((JPasswordField) find(frame, "loginPassword")).setText(Fixture.PASSWORD);
          return null;
        });
    click(frame, "Masuk");
    idle(frame);
    assertEquals(1, db.service.list("").size());
    screenshot(frame, "03-cashier.png");
    click(frame, "Keluar akun");
    assertNull(db.service.session());
  }

  private static Component find(Container root, String name) {
    for (Component c : root.getComponents()) {
      if (name.equals(c.getName())) return c;
      if (c instanceof Container child) {
        Component found = find(child, name);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static JOptionPane pane(Container root) {
    for (Component c : root.getComponents()) {
      if (c instanceof JOptionPane p) return p;
      if (c instanceof Container child) {
        JOptionPane p = pane(child);
        if (p != null) return p;
      }
    }
    return null;
  }

  private static void click(Container frame, String label) throws Exception {
    edt(
        () -> {
          ((JButton) find(frame, label)).doClick();
          return null;
        });
  }

  private static void idle(MainPage f) throws Exception {
    await(() -> !f.isWorking());
    edt(() -> null);
  }

  private static void await(BooleanSupplier c) throws Exception {
    long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < end) {
      if (edt(c::getAsBoolean)) return;
      Thread.sleep(30);
    }
    fail("Timed out waiting for own Swing operation.");
  }

  private static <T> T edt(Callable<T> action) throws Exception {
    java.util.concurrent.FutureTask<T> t = new java.util.concurrent.FutureTask<>(action);
    SwingUtilities.invokeAndWait(t);
    return t.get();
  }

  private static void screenshot(JFrame frame, String name) throws Exception {
    Path dir = Path.of(System.getProperty("apotek.screenshot.dir", "target/desktop-qa"));
    Files.createDirectories(dir);
    BufferedImage image =
        edt(
            () -> {
              frame.validate();
              BufferedImage b =
                  new BufferedImage(
                      frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
              Graphics2D g = b.createGraphics();
              frame.paintAll(g);
              g.dispose();
              return b;
            });
    ImageIO.write(image, "png", dir.resolve(name).toFile());
  }
}
