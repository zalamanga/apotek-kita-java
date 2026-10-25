package GUI;

import DB.DatabaseConnection;
import JAVA.LoginProcess;
import apotek.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class MainPage extends JFrame {
  private final UserService service;
  private final LoginProcess loginProcess;
  private final CardLayout cards = new CardLayout();
  private final JPanel pages = new JPanel(cards),
      editor = new JPanel(new BorderLayout()),
      data = new JPanel(new BorderLayout(10, 10));
  private final JTextArea status = new JTextArea(2, 30);
  private final JTextField loginUsername = new JTextField(22), search = new JTextField(20);
  private final JPasswordField loginPassword = new JPasswordField(22);
  private final JTextField id = new JTextField(20),
      username = new JTextField(20),
      dob = new JTextField(20),
      phone = new JTextField(20),
      email = new JTextField(20);
  private final JPasswordField password = new JPasswordField(20),
      confirmation = new JPasswordField(20);
  private final JComboBox<String> sex = new JComboBox<>(new String[] {"M", "F"}),
      role = new JComboBox<>(new String[] {User.ADMIN, User.CASHIER});
  private final JTextArea address = new JTextArea(3, 20);
  private final DefaultTableModel model =
      new DefaultTableModel(
          new String[] {
            "ID", "Username", "Gender", "Tanggal lahir", "Telepon", "Email", "Alamat", "Role"
          },
          0) {
        @Override
        public boolean isCellEditable(int r, int c) {
          return false;
        }
      };
  private final JTable table = new JTable(model);
  private final JButton update, delete, login;
  private List<User> rows = List.of();
  private String selected;
  private boolean working, loading;

  public MainPage() {
    this(new UserService(DatabaseConnection::open));
  }

  public MainPage(UserService service) {
    super("ApotekKita / Data User");
    this.service = service;
    this.loginProcess = new LoginProcess(service);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setSize(1180, 800);
    setMinimumSize(new Dimension(800, 620));
    setLocationRelativeTo(null);
    setLayout(new BorderLayout());
    JPanel header = new JPanel(new BorderLayout(18, 4));
    header.setBackground(Color.WHITE);
    header.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));
    java.net.URL icon = MainPage.class.getResource("/IMAGES/icon.png");
    if (icon != null) {
      Image image = new ImageIcon(icon).getImage().getScaledInstance(64, 72, Image.SCALE_SMOOTH);
      JLabel label = new JLabel(new ImageIcon(image));
      label.getAccessibleContext().setAccessibleName("Ikon farmasi ApotekKita asli");
      header.add(label, BorderLayout.WEST);
    }
    JPanel identity = new JPanel(new GridLayout(0, 1));
    identity.setOpaque(false);
    JLabel title = new JLabel("ApotekKita");
    title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 27));
    identity.add(title);
    identity.add(new JLabel("Login dan pengelolaan data user"));
    header.add(identity);
    add(header, BorderLayout.NORTH);
    pages.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
    add(pages);
    status.setEditable(false);
    status.setFocusable(false);
    status.setLineWrap(true);
    status.setWrapStyleWord(true);
    status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
    status.setBorder(BorderFactory.createEmptyBorder(8, 20, 10, 20));
    status.getAccessibleContext().setAccessibleName("Status operasi");
    add(status, BorderLayout.SOUTH);
    loginUsername.setName("loginUsername");
    loginPassword.setName("loginPassword");
    search.setName("search");
    JPanel loginFields = new JPanel(new GridBagLayout());
    field(loginFields, "Username", loginUsername, 0);
    field(loginFields, "Password", loginPassword, 1);
    JCheckBox show = new JCheckBox("Lihat password");
    show.setName("showPassword");
    char echo = loginPassword.getEchoChar();
    show.addActionListener(e -> loginPassword.setEchoChar(show.isSelected() ? (char) 0 : echo));
    field(loginFields, "", show, 2);
    login = button("Masuk", this::login);
    primary(login);
    JPanel loginActions = new JPanel();
    loginActions.add(login);
    loginActions.add(
        button(
            "Kosongkan",
            () -> {
              loginUsername.setText("");
              loginPassword.setText("");
              loginUsername.requestFocusInWindow();
            }));
    JPanel credentials = new JPanel(new BorderLayout(12, 12));
    credentials.add(loginFields);
    credentials.add(loginActions, BorderLayout.SOUTH);
    credentials.setBorder(BorderFactory.createEmptyBorder(40, 50, 40, 50));
    JPanel loginPage = new JPanel(new BorderLayout());
    loginPage.add(credentials);
    java.net.URL sketch = MainPage.class.getResource("/IMAGES/ts.jpg");
    if (sketch != null) {
      JLabel picture = new JLabel(new ImageIcon(sketch));
      picture.getAccessibleContext().setAccessibleName("Sketsa dari aplikasi ApotekKita asli");
      picture.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 35));
      loginPage.add(picture, BorderLayout.EAST);
    }
    pages.add(loginPage, "login");
    JPanel form = new JPanel(new GridBagLayout());
    List<Map.Entry<String, JComponent>> fields =
        List.of(
            Map.entry("ID User", id),
            Map.entry("Username", username),
            Map.entry("Password baru", password),
            Map.entry("Ulangi password", confirmation),
            Map.entry("Gender (M/F)", sex),
            Map.entry("Lahir (YYYY-MM-DD)", dob),
            Map.entry("Telepon", phone),
            Map.entry("Email", email),
            Map.entry("Alamat", new JScrollPane(address)),
            Map.entry("Role", role));
    for (int n = 0; n < fields.size(); n++)
      field(form, fields.get(n).getKey(), fields.get(n).getValue(), n);
    id.setName("id");
    username.setName("username");
    password.setName("password");
    confirmation.setName("confirmation");
    sex.setName("sex");
    dob.setName("dob");
    phone.setName("phone");
    email.setName("email");
    address.setName("address");
    role.setName("role");
    id.setEditable(false);
    address.setLineWrap(true);
    address.setWrapStyleWord(true);
    editor.add(new JScrollPane(form));
    JPanel formActions = new JPanel(new GridLayout(0, 2, 8, 8));
    JButton create = button("Input data", () -> save(false));
    primary(create);
    update = button("Simpan perubahan", () -> save(true));
    delete = button("Hapus", this::delete);
    formActions.add(create);
    formActions.add(update);
    formActions.add(delete);
    formActions.add(button("Reset form", this::clear));
    editor.add(formActions, BorderLayout.SOUTH);
    JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JLabel label = new JLabel("Cari ID / username");
    label.setLabelFor(search);
    controls.add(label);
    controls.add(search);
    controls.add(button("Cari", this::refresh));
    controls.add(
        button(
            "Lihat semua",
            () -> {
              search.setText("");
              refresh();
            }));
    controls.add(button("Keluar akun", this::logout));
    search.addActionListener(e -> refresh());
    data.add(controls, BorderLayout.NORTH);
    table.setName("users");
    table.setRowHeight(28);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    table.setAutoCreateRowSorter(true);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (!loading && !e.getValueIsAdjusting()) select();
            });
    pages.add(data, "users");
    clear();
    showLogin();
    getRootPane()
        .registerKeyboardAction(
            e -> {
              if (service.session() != null) clear();
              else {
                loginUsername.setText("");
                loginPassword.setText("");
              }
            },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
  }

  private static void field(JPanel panel, String text, JComponent input, int row) {
    JLabel label = new JLabel(text);
    label.setLabelFor(input);
    input.getAccessibleContext().setAccessibleName(text);
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = row;
    c.anchor = GridBagConstraints.LINE_START;
    c.insets = new Insets(4, 6, 4, 10);
    panel.add(label, c);
    c.gridx = 1;
    c.weightx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    panel.add(input, c);
  }

  private static JButton button(String label, Runnable action) {
    JButton b = new JButton(label);
    b.setName(label);
    b.setMargin(new Insets(8, 14, 8, 14));
    b.addActionListener(e -> action.run());
    return b;
  }

  private static void primary(JButton b) {
    b.setUI(
        new javax.swing.plaf.basic.BasicButtonUI() {
          @Override
          protected void paintFocus(
              Graphics g, AbstractButton b, Rectangle v, Rectangle t, Rectangle i) {
            g.setColor(Color.WHITE);
            g.drawRect(4, 4, b.getWidth() - 9, b.getHeight() - 9);
          }
        });
    b.setBackground(Color.BLACK);
    b.setForeground(Color.WHITE);
    b.setOpaque(true);
  }

  private void login() {
    String user = loginUsername.getText().trim();
    char[] secret = loginPassword.getPassword();
    if (user.isEmpty() || secret.length == 0) {
      Arrays.fill(secret, '\0');
      status.setText("Isi username dan password.");
      return;
    }
    task(
        "Memeriksa akun...",
        () -> loginProcess.authenticate(user, secret),
        ok -> {
          loginPassword.setText("");
          if (!ok) {
            status.setText("Username atau password salah.");
            return;
          }
          showUsers();
        });
  }

  private void showLogin() {
    cards.show(pages, "login");
    getRootPane().setDefaultButton(login);
    status.setText("Masuk menggunakan akun pada database demo ApotekKita Anda.");
  }

  private void showUsers() {
    Component old = ((BorderLayout) data.getLayout()).getLayoutComponent(BorderLayout.CENTER);
    if (old != null) data.remove(old);
    JScrollPane list = new JScrollPane(table);
    if (service.isAdmin()) {
      JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editor, list);
      split.setResizeWeight(.5);
      editor.setMinimumSize(new Dimension(390, 100));
      list.setMinimumSize(new Dimension(280, 100));
      data.add(split);
      SwingUtilities.invokeLater(() -> split.setDividerLocation(.5));
    } else data.add(list);
    cards.show(pages, "users");
    getRootPane().setDefaultButton(null);
    data.revalidate();
    clear();
    refresh();
  }

  public void refresh() {
    String term = search.getText();
    task(
        "Memuat data user...",
        () -> service.list(term),
        users -> {
          loading = true;
          rows = users;
          model.setRowCount(0);
          for (User u : users)
            model.addRow(
                new Object[] {
                  u.id(),
                  u.username(),
                  u.sex(),
                  u.dob(),
                  u.phone(),
                  u.email(),
                  u.address(),
                  u.role()
                });
          for (int n = 0; n < table.getColumnCount(); n++)
            table.getColumnModel().getColumn(n).setPreferredWidth(n == 6 ? 220 : 155);
          loading = false;
          clear();
          status.setText(
              users.isEmpty()
                  ? "Tidak ada user yang cocok."
                  : users.size()
                      + " user ditampilkan. "
                      + (service.isAdmin()
                          ? "Pilih baris untuk mengubah; password kosong mempertahankan password lama."
                          : "Cashier hanya dapat melihat profilnya sendiri."));
        });
  }

  private void clear() {
    selected = null;
    table.clearSelection();
    id.setText(
        "USR"
            + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT));
    username.setText("");
    password.setText("");
    confirmation.setText("");
    sex.setSelectedIndex(0);
    dob.setText("");
    phone.setText("");
    email.setText("");
    address.setText("");
    role.setSelectedItem(User.CASHIER);
    update.setEnabled(false);
    delete.setEnabled(false);
  }

  private void select() {
    if (!service.isAdmin() || table.getSelectedRow() < 0) return;
    User u = rows.get(table.convertRowIndexToModel(table.getSelectedRow()));
    selected = u.id();
    id.setText(u.id());
    username.setText(u.username());
    sex.setSelectedItem(u.sex());
    dob.setText(u.dob().toString());
    phone.setText(u.phone());
    email.setText(u.email());
    address.setText(u.address());
    role.setSelectedItem(u.role());
    password.setText("");
    confirmation.setText("");
    update.setEnabled(true);
    delete.setEnabled(!u.id().equals(service.session().id()));
    status.setText(
        "User dipilih. Password tidak ditampilkan; kosongkan kedua kolom password untuk mempertahankannya.");
  }

  private void save(boolean editing) {
    if (editing && selected == null || !editing && selected != null) {
      status.setText(
          editing ? "Pilih user terlebih dahulu." : "Tekan Reset form sebelum menambah user.");
      return;
    }
    char[] secret = password.getPassword(), repeat = confirmation.getPassword();
    if (!Arrays.equals(secret, repeat)
        || (!editing || secret.length > 0) && (secret.length < 12 || secret.length > 128)) {
      Arrays.fill(secret, '\0');
      Arrays.fill(repeat, '\0');
      status.setText("Password harus sama pada kedua kolom dan berisi 12 sampai 128 karakter.");
      return;
    }
    Arrays.fill(repeat, '\0');
    User u;
    try {
      u =
          new User(
              id.getText(),
              username.getText().trim(),
              (String) sex.getSelectedItem(),
              LocalDate.parse(dob.getText().trim()),
              phone.getText().trim(),
              email.getText().trim(),
              address.getText().trim(),
              (String) role.getSelectedItem());
    } catch (RuntimeException ex) {
      Arrays.fill(secret, '\0');
      status.setText(
          ex instanceof java.time.format.DateTimeParseException
              ? "Tanggal lahir harus memakai YYYY-MM-DD."
              : ex.getMessage());
      return;
    }
    task(
        "Menyimpan data user...",
        () -> {
          service.save(u, secret, editing);
          return true;
        },
        ok -> refresh());
  }

  private void delete() {
    if (selected == null) return;
    if (JOptionPane.showConfirmDialog(
            this, "Hapus user " + selected + "?", "Konfirmasi", JOptionPane.OK_CANCEL_OPTION)
        != JOptionPane.OK_OPTION) return;
    String userId = selected;
    task(
        "Menghapus user...",
        () -> {
          service.delete(userId);
          return true;
        },
        ok -> refresh());
  }

  private void logout() {
    service.logout();
    rows = List.of();
    model.setRowCount(0);
    clear();
    search.setText("");
    showLogin();
  }

  private <T> void task(String message, Callable<T> action, Consumer<T> complete) {
    if (working) return;
    working = true;
    status.setText(message);
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    new SwingWorker<T, Void>() {
      @Override
      protected T doInBackground() throws Exception {
        return action.call();
      }

      @Override
      protected void done() {
        working = false;
        setCursor(Cursor.getDefaultCursor());
        if (!isDisplayable()) return;
        try {
          complete.accept(get());
        } catch (Exception ex) {
          Throwable cause = ex;
          while (cause.getCause() != null
              && !(cause instanceof SQLException)
              && !(cause instanceof IllegalArgumentException)
              && !(cause instanceof SecurityException)) cause = cause.getCause();
          if (cause instanceof IllegalArgumentException || cause instanceof SecurityException)
            status.setText(cause.getMessage());
          else {
            if (cause instanceof SQLException sql)
              java.util.logging.Logger.getLogger("apotek")
                  .warning(
                      "Database failure state="
                          + sql.getSQLState()
                          + " code="
                          + sql.getErrorCode());
            status.setText(
                "Data belum dimuat/disimpan. Periksa koneksi database dan username unik, lalu coba kembali.");
          }
        }
      }
    }.execute();
  }

  public boolean isWorking() {
    return working;
  }

  public static void installStyle() {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception ignored) {
    }
    for (String key :
        List.of("Label.font", "Button.font", "TextField.font", "TextArea.font", "Table.font"))
      UIManager.put(key, new Font(Font.SANS_SERIF, Font.PLAIN, 14));
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(
        () -> {
          installStyle();
          new MainPage().setVisible(true);
        });
  }
}
