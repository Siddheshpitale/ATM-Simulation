import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * ATMApp.java
 *
 * Single-file ATM Simulator (Swing UI)
 *
 * Features:
 *  - Register account with PIN (stored hashed using SHA-256)
 *  - Login using account number + PIN
 *  - Deposit, Withdraw, Check Balance
 *  - Transaction history per account
 *  - Change PIN
 *  - CSV persistence (accounts.csv, transactions.csv)
 *
 * Usage:
 *   javac ATMApp.java
 *   java ATMApp
 *
 * File: ATMApp.java
 */
public class ATMApp extends JFrame {
    // ---------- Data models ----------
    private static class Account {
        String accNo;
        String holderName;
        String accountType; // "Savings" / "Checking"
        double balance;
        String pinHash; // SHA-256 hex string
        boolean locked; // optional: account lock

        Account(String accNo, String holderName, String accountType, double balance, String pinHash) {
            this.accNo = accNo;
            this.holderName = holderName;
            this.accountType = accountType;
            this.balance = balance;
            this.pinHash = pinHash;
            this.locked = false;
        }

        String toCSV() {
            // accNo,holderName,accountType,balance,pinHash,locked
            return escapeCSV(accNo) + "," + escapeCSV(holderName) + "," + escapeCSV(accountType) + "," +
                    balance + "," + escapeCSV(pinHash) + "," + (locked ? "1" : "0");
        }

        static Account fromCSV(String line) {
            String[] p = parseCSVLineStatic(line);
            if (p.length < 6) return null;
            double bal = 0;
            try { bal = Double.parseDouble(p[3]); } catch (Exception ignore) {}
            Account a = new Account(p[0], p[1], p[2], bal, p[4]);
            a.locked = "1".equals(p[5]);
            return a;
        }
    }

    private static class Transaction {
        String txId;
        String accNo;
        String type; // Deposit / Withdraw / Transfer / PIN_CHANGE
        double amount;
        String time; // timestamp

        Transaction(String txId, String accNo, String type, double amount, String time) {
            this.txId = txId; this.accNo = accNo; this.type = type; this.amount = amount; this.time = time;
        }

        String toCSV() {
            // txId,accNo,type,amount,time
            return escapeCSV(txId) + "," + escapeCSV(accNo) + "," + escapeCSV(type) + "," + amount + "," + escapeCSV(time);
        }

        static Transaction fromCSV(String line) {
            String[] p = parseCSVLineStatic(line);
            if (p.length < 5) return null;
            double amt = 0;
            try { amt = Double.parseDouble(p[3]); } catch (Exception ignore) {}
            return new Transaction(p[0], p[1], p[2], amt, p[4]);
        }
    }

    // ---------- Paths & persistence ----------
    private final Path ACC_FILE = Paths.get("accounts.csv");
    private final Path TX_FILE = Paths.get("transactions.csv");

    // ---------- In-memory state ----------
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final List<Transaction> transactions = new ArrayList<>();
    private int accSeq = 1000;
    private int txSeq = 1;

    // ---------- UI fields ----------
    private CardLayout cards;
    private JPanel mainCards;

    // Login panel fields
    private JTextField tfLoginAcc;
    private JPasswordField pfLoginPin;
    private JLabel lblLoginMsg;

    // Register panel fields
    private JTextField tfRegName;
    private JComboBox<String> cbRegType;
    private JPasswordField pfRegPin;
    private JPasswordField pfRegPinConfirm;
    private JLabel lblRegMsg;

    // Dashboard fields
    private JLabel lblDashboardWelcome;
    private JLabel lblBalance;
    private DefaultTableModel txTableModel;
    private JTable txTable;
    private JTextField tfAmount;
    private JLabel lblAccNo;
    private Account currentAccount = null;

    // Util
    private final SimpleDateFormat dtfmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // Theme colors
    private final Color primaryA = new Color(30, 40, 52);
    private final Color primaryB = new Color(18, 24, 32);
    private final Color accent = new Color(60, 180, 200);

    // ---------- Constructor ----------
    public ATMApp() {
        super("ATM Simulator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(920, 620);
        setLocationRelativeTo(null);
        initUI();
        loadAll();
    }

    // ---------- UI assembly ----------
    private void initUI() {
        // root split: left decorative panel + right cards
        JPanel leftPanel = buildLeftPanel();
        mainCards = buildCardPanels();

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(leftPanel, BorderLayout.WEST);
        getContentPane().add(mainCards, BorderLayout.CENTER);

        // top-level style
        getRootPane().setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
    }

    private JPanel buildLeftPanel() {
        JPanel p = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D)g;
                GradientPaint gp = new GradientPaint(0,0,primaryA,0,getHeight(),primaryB);
                g2.setPaint(gp);
                g2.fillRect(0,0,getWidth(),getHeight());
            }
        };
        p.setPreferredSize(new Dimension(300, 0));
        p.setLayout(new BorderLayout());
        JLabel title = new JLabel("<html><div style='padding:20px;color:#eaf6f9'><h1>ATM SIM</h1><div style='font-size:12px;color:#cfeff2'>Secure ATM Simulator</div></div></html>");
        title.setBorder(new EmptyBorder(20,20,20,20));
        p.add(title, BorderLayout.NORTH);

        // info + quick operations
        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBorder(new EmptyBorder(12,12,12,12));

        JLabel infoText = new JLabel("<html><div style='color:#d8eef1'>This simulator demonstrates:<ul>"
                + "<li>Account creation with PIN (SHA-256 hash)</li>"
                + "<li>Login & basic operations</li>"
                + "<li>Transaction history & CSV persistence</li>"
                + "</ul></div></html>");
        infoText.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(infoText);

        info.add(Box.createVerticalStrut(12));

        JButton btnHow = gradientButton("How to use", new Color(0,130,150), new Color(0,90,110));
        btnHow.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnHow.addActionListener(e -> {
            JOptionPane.showMessageDialog(this,
                    "Steps:\n1) Register an account\n2) Login with account number and PIN\n3) Deposit/Withdraw and view transaction history\n\nPINs are stored hashed (SHA-256) for demo security.",
                    "How to", JOptionPane.INFORMATION_MESSAGE);
        });
        info.add(btnHow);
        p.add(info, BorderLayout.CENTER);

        return p;
    }

    private JPanel buildCardPanels() {
        cards = new CardLayout();
        JPanel root = new JPanel(cards);

        root.add(buildWelcomeCard(), "WELCOME");
        root.add(buildRegisterCard(), "REGISTER");
        root.add(buildLoginCard(), "LOGIN");
        root.add(buildDashboardCard(), "DASH");

        cards.show(root, "WELCOME");
        return root;
    }

    // ---------- Cards ----------

    private JPanel buildWelcomeCard() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(245,245,245));
        JPanel center = new JPanel();
        center.setBackground(Color.WHITE);
        center.setBorder(BorderFactory.createEmptyBorder(24,24,24,24));
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JLabel h = new JLabel("Welcome to ATM Simulator");
        h.setFont(new Font("Segoe UI", Font.BOLD, 22));
        h.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(h);
        center.add(Box.createVerticalStrut(12));
        JLabel sub = new JLabel("<html><div style='text-align:center'>Create account or login to start using the ATM simulator.</div></html>");
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(sub);
        center.add(Box.createVerticalStrut(20));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER,12,0));
        btnRow.setBackground(Color.WHITE);
        JButton btnReg = gradientButton("Register", new Color(40,150,120), new Color(20,110,90));
        JButton btnLogin = gradientButton("Login", new Color(20,120,160), new Color(10,80,120));
        btnReg.addActionListener(e -> cards.show(mainCards, "REGISTER"));
        btnLogin.addActionListener(e -> cards.show(mainCards, "LOGIN"));
        btnRow.add(btnReg); btnRow.add(btnLogin);
        center.add(btnRow);

        p.add(center, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildRegisterCard() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(18,18,18,18));
        p.setBackground(Color.WHITE);

        JLabel title = new JLabel("Create New Account");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        p.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(10,10,10,10);
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx=0; c.gridy=0; form.add(new JLabel("Full name:"), c);
        tfRegName = new JTextField(); c.gridx=1; form.add(tfRegName, c);

        c.gridx=0; c.gridy=1; form.add(new JLabel("Account Type:"), c);
        cbRegType = new JComboBox<>(new String[]{"Savings","Checking"}); c.gridx=1; form.add(cbRegType, c);

        c.gridx=0; c.gridy=2; form.add(new JLabel("Choose 4-digit PIN:"), c);
        pfRegPin = new JPasswordField(); c.gridx=1; form.add(pfRegPin, c);

        c.gridx=0; c.gridy=3; form.add(new JLabel("Confirm PIN:"), c);
        pfRegPinConfirm = new JPasswordField(); c.gridx=1; form.add(pfRegPinConfirm, c);

        c.gridx=0; c.gridy=4; c.gridwidth=2;
        JButton btnCreate = gradientButton("Create Account", new Color(30,140,110), new Color(20,100,80));
        form.add(btnCreate, c);
        c.gridwidth=1;

        c.gridx=0; c.gridy=5; c.gridwidth=2;
        lblRegMsg = new JLabel(" ");
        lblRegMsg.setForeground(Color.RED);
        form.add(lblRegMsg, c);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.setBackground(Color.WHITE);
        JButton btnBack = gradientButton("Back", new Color(120,120,140), new Color(90,90,110));
        btnBack.addActionListener(e -> {
            clearRegisterForm();
            cards.show(mainCards, "WELCOME");
        });
        bottom.add(btnBack);

        p.add(form, BorderLayout.CENTER);
        p.add(bottom, BorderLayout.SOUTH);

        btnCreate.addActionListener(e -> handleCreateAccount());

        return p;
    }

    private JPanel buildLoginCard() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(18,18,18,18));
        p.setBackground(Color.WHITE);

        JLabel title = new JLabel("Login to your Account");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        p.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(10,10,10,10);
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx=0; c.gridy=0; form.add(new JLabel("Account Number:"), c);
        tfLoginAcc = new JTextField(); c.gridx=1; form.add(tfLoginAcc, c);

        c.gridx=0; c.gridy=1; form.add(new JLabel("PIN:"), c);
        pfLoginPin = new JPasswordField(); c.gridx=1; form.add(pfLoginPin, c);

        c.gridx=0; c.gridy=2; c.gridwidth=2;
        JButton btnLogin = gradientButton("Login", new Color(10,120,160), new Color(10,80,120));
        form.add(btnLogin, c);
        c.gridwidth=1;

        c.gridx=0; c.gridy=3; c.gridwidth=2;
        lblLoginMsg = new JLabel(" ");
        lblLoginMsg.setForeground(Color.RED);
        form.add(lblLoginMsg, c);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.setBackground(Color.WHITE);
        JButton btnBack = gradientButton("Back", new Color(120,120,140), new Color(90,90,110));
        btnBack.addActionListener(e -> { clearLoginForm(); cards.show(mainCards, "WELCOME"); });
        bottom.add(btnBack);

        p.add(form, BorderLayout.CENTER);
        p.add(bottom, BorderLayout.SOUTH);

        btnLogin.addActionListener(e -> handleLogin());

        return p;
    }

    private JPanel buildDashboardCard() {
        JPanel p = new JPanel(new BorderLayout(12,12));
        p.setBorder(new EmptyBorder(12,12,12,12));
        p.setBackground(new Color(245,245,245));

        // Header area
        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setBackground(Color.WHITE);
        hdr.setBorder(new EmptyBorder(12,12,12,12));
        lblDashboardWelcome = new JLabel("Welcome, guest");
        lblDashboardWelcome.setFont(new Font("Segoe UI", Font.BOLD, 18));
        hdr.add(lblDashboardWelcome, BorderLayout.WEST);

        JPanel rightHdr = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightHdr.setBackground(Color.WHITE);
        JButton btnLogout = gradientButton("Logout", new Color(180,80,80), new Color(150,50,50));
        JButton btnChangePin = gradientButton("Change PIN", new Color(80,120,200), new Color(60,90,160));
        rightHdr.add(btnChangePin);
        rightHdr.add(btnLogout);
        hdr.add(rightHdr, BorderLayout.EAST);

        p.add(hdr, BorderLayout.NORTH);

        // center: split operations + tx history
        JPanel center = new JPanel(new GridLayout(1,2,12,12));
        center.setBackground(new Color(245,245,245));

        // Left: operations card
        JPanel ops = new JPanel();
        ops.setBackground(Color.WHITE);
        ops.setLayout(new BoxLayout(ops, BoxLayout.Y_AXIS));
        ops.setBorder(new EmptyBorder(12,12,12,12));

        lblAccNo = new JLabel("Account: -");
        lblAccNo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        ops.add(lblAccNo);
        ops.add(Box.createVerticalStrut(8));

        lblBalance = new JLabel("Balance: ₹ 0.00");
        lblBalance.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblBalance.setForeground(new Color(0,120,100));
        ops.add(lblBalance);
        ops.add(Box.createVerticalStrut(16));

        JPanel amtRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        amtRow.setBackground(Color.WHITE);
        tfAmount = new JTextField("0.00", 12);
        amtRow.add(new JLabel("Amount:")); amtRow.add(tfAmount);
        ops.add(amtRow);

        ops.add(Box.createVerticalStrut(10));
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT,10,6));
        btns.setBackground(Color.WHITE);
        JButton btnDeposit = gradientButton("Deposit", new Color(10,120,160), new Color(10,80,120));
        JButton btnWithdraw = gradientButton("Withdraw", new Color(200,60,60), new Color(160,40,40));
        btns.add(btnDeposit); btns.add(btnWithdraw);
        ops.add(btns);

        ops.add(Box.createVerticalStrut(10));
        JButton btnRefresh = gradientButton("Refresh Balance", new Color(110,110,160), new Color(80,80,120));
        ops.add(btnRefresh);

        center.add(ops);

        // Right: transaction history
        JPanel txPanel = new JPanel(new BorderLayout());
        txPanel.setBackground(Color.WHITE);
        txPanel.setBorder(new EmptyBorder(12,12,12,12));
        txPanel.add(new JLabel("Transaction History (latest first)"), BorderLayout.NORTH);
        txTableModel = new DefaultTableModel(new String[]{"Tx ID","Type","Amount","Time"}, 0) {
            public boolean isCellEditable(int r,int c){ return false; }
        };
        txTable = new JTable(txTableModel);
        JScrollPane sp = new JScrollPane(txTable);
        txPanel.add(sp, BorderLayout.CENTER);

        center.add(txPanel);

        p.add(center, BorderLayout.CENTER);

        // footer small status / actions
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
        footer.setBackground(Color.WHITE);
        footer.setBorder(new EmptyBorder(8,8,8,8));
        JLabel info = new JLabel("Tip: Use small deposits/withdrawals for testing.");
        footer.add(info);
        p.add(footer, BorderLayout.SOUTH);

        // actions wiring
        btnLogout.addActionListener(e -> {
            logout();
            cards.show(mainCards, "LOGIN");
        });
        btnDeposit.addActionListener(e -> handleDeposit());
        btnWithdraw.addActionListener(e -> handleWithdraw());
        btnRefresh.addActionListener(e -> refreshDashboard());
        btnChangePin.addActionListener(e -> handleChangePin());

        return p;
    }

    // ---------- Handlers ----------

    private void handleCreateAccount() {
        lblRegMsg.setText(" ");
        String name = tfRegName.getText().trim();
        String type = (String) cbRegType.getSelectedItem();
        char[] pin = pfRegPin.getPassword();
        char[] pin2 = pfRegPinConfirm.getPassword();

        if (name.isEmpty()) { lblRegMsg.setText("Enter full name"); return; }
        if (pin.length == 0) { lblRegMsg.setText("Enter PIN"); return; }
        if (!Arrays.equals(pin, pin2)) { lblRegMsg.setText("PINs do not match"); return; }
        String pinStr = new String(pin);
        if (pinStr.length() != 4 || !pinStr.matches("\\d{4}")) { lblRegMsg.setText("PIN must be 4 digits"); return; }

        String accNo = generateAccNo();
        String hash = sha256Hex(pinStr);
        Account a = new Account(accNo, name, type, 0.0, hash);
        accounts.put(accNo, a);
        saveAccounts(); // persist immediately
        JOptionPane.showMessageDialog(this, "Account created: " + accNo + "\nKeep your PIN secret.", "Created", JOptionPane.INFORMATION_MESSAGE);

        // clear form
        clearRegisterForm();
        cards.show(mainCards, "LOGIN");
    }

    private void handleLogin() {
        lblLoginMsg.setText(" ");
        String accNo = tfLoginAcc.getText().trim();
        char[] pinc = pfLoginPin.getPassword();
        if (accNo.isEmpty() || pinc.length == 0) { lblLoginMsg.setText("Enter account and PIN"); return; }
        Account a = accounts.get(accNo);
        if (a == null) { lblLoginMsg.setText("Account not found"); return; }
        if (a.locked) { lblLoginMsg.setText("Account is locked"); return; }
        String providedHash = sha256Hex(new String(pinc));
        if (!providedHash.equals(a.pinHash)) {
            lblLoginMsg.setText("Invalid PIN");
            return;
        }
        // success
        currentAccount = a;
        refreshDashboard();
        cards.show(mainCards, "DASH");
        clearLoginForm();
    }

    private void handleDeposit() {
        if (!ensureLoggedIn()) return;
        double amt = parseDoubleSafe(tfAmount.getText().trim(), -1);
        if (amt <= 0) { JOptionPane.showMessageDialog(this, "Enter amount > 0", "Invalid", JOptionPane.WARNING_MESSAGE); return; }
        currentAccount.balance += amt;
        Transaction t = new Transaction(genTxId(), currentAccount.accNo, "Deposit", amt, now());
        transactions.add(t);
        saveAll();
        refreshDashboard();
        JOptionPane.showMessageDialog(this, String.format("Deposited ₹ %.2f", amt), "OK", JOptionPane.INFORMATION_MESSAGE);
    }

    private void handleWithdraw() {
        if (!ensureLoggedIn()) return;
        double amt = parseDoubleSafe(tfAmount.getText().trim(), -1);
        if (amt <= 0) { JOptionPane.showMessageDialog(this, "Enter amount > 0", "Invalid", JOptionPane.WARNING_MESSAGE); return; }
        if (currentAccount.balance < amt) { JOptionPane.showMessageDialog(this, "Insufficient balance", "Error", JOptionPane.ERROR_MESSAGE); return; }
        currentAccount.balance -= amt;
        Transaction t = new Transaction(genTxId(), currentAccount.accNo, "Withdraw", amt, now());
        transactions.add(t);
        saveAll();
        refreshDashboard();
        JOptionPane.showMessageDialog(this, String.format("Withdrew ₹ %.2f", amt), "OK", JOptionPane.INFORMATION_MESSAGE);
    }

    private void handleChangePin() {
        if (!ensureLoggedIn()) return;
        JPanel panel = new JPanel(new GridLayout(0,1,6,6));
        JPasswordField pfOld = new JPasswordField();
        JPasswordField pfNew = new JPasswordField();
        JPasswordField pfNew2 = new JPasswordField();
        panel.add(new JLabel("Enter current PIN:")); panel.add(pfOld);
        panel.add(new JLabel("Enter new 4-digit PIN:")); panel.add(pfNew);
        panel.add(new JLabel("Confirm new PIN:")); panel.add(pfNew2);
        int res = JOptionPane.showConfirmDialog(this, panel, "Change PIN", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;
        if (!sha256Hex(new String(pfOld.getPassword())).equals(currentAccount.pinHash)) {
            JOptionPane.showMessageDialog(this, "Current PIN incorrect", "Error", JOptionPane.ERROR_MESSAGE); return;
        }
        String n1 = new String(pfNew.getPassword()), n2 = new String(pfNew2.getPassword());
        if (!n1.equals(n2) || !n1.matches("\\d{4}")) {
            JOptionPane.showMessageDialog(this, "New PIN must be 4 digits and match confirmation", "Error", JOptionPane.ERROR_MESSAGE); return;
        }
        currentAccount.pinHash = sha256Hex(n1);
        Transaction t = new Transaction(genTxId(), currentAccount.accNo, "PIN_CHANGE", 0.0, now());
        transactions.add(t);
        saveAll();
        JOptionPane.showMessageDialog(this, "PIN changed successfully", "OK", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---------- Session helpers ----------
    private boolean ensureLoggedIn() {
        if (currentAccount == null) {
            JOptionPane.showMessageDialog(this, "Login first", "Not logged in", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private void logout() {
        currentAccount = null;
        tfAmount.setText("0.00");
        txTableModel.setRowCount(0);
        lblDashboardWelcome.setText("Welcome, guest");
        lblBalance.setText("Balance: ₹ 0.00");
    }

    private void refreshDashboard() {
        if (currentAccount == null) return;
        lblDashboardWelcome.setText("Welcome, " + currentAccount.holderName);
        lblAccNo.setText("Account: " + currentAccount.accNo + " (" + currentAccount.accountType + ")");
        lblBalance.setText(String.format("Balance: ₹ %.2f", currentAccount.balance));

        // load txs for this account (most recent first)
        txTableModel.setRowCount(0);
        List<Transaction> list = new ArrayList<>();
        for (int i = transactions.size()-1; i >= 0; --i) {
            Transaction t = transactions.get(i);
            if (t.accNo.equals(currentAccount.accNo)) list.add(t);
        }
        for (Transaction t : list) {
            txTableModel.addRow(new Object[]{t.txId, t.type, String.format("₹ %.2f", t.amount), t.time});
        }
    }

    // ---------- Persistence ----------
    private void saveAccounts() {
        try (BufferedWriter w = Files.newBufferedWriter(ACC_FILE)) {
            w.write("accNo,holder,accType,balance,pinHash,locked\n");
            for (Account a : accounts.values()) w.write(a.toCSV() + "\n");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void saveTransactions() {
        try (BufferedWriter w = Files.newBufferedWriter(TX_FILE)) {
            w.write("txId,accNo,type,amount,time\n");
            for (Transaction t : transactions) w.write(t.toCSV() + "\n");
        } catch (IOException ex) { ex.printStackTrace(); }
    }

    private void saveAll() {
        saveAccounts();
        saveTransactions();
    }

    private void loadAll() {
        accounts.clear(); transactions.clear();
        accSeq = 1000; txSeq = 1;
        if (Files.exists(ACC_FILE)) {
            try (BufferedReader r = Files.newBufferedReader(ACC_FILE)) {
                r.readLine(); // skip header
                String line;
                while ((line = r.readLine()) != null) {
                    Account a = Account.fromCSV(line);
                    if (a != null) {
                        accounts.put(a.accNo, a);
                        // update accSeq
                        if (a.accNo.startsWith("ACC")) {
                            try { int v = Integer.parseInt(a.accNo.substring(3)); accSeq = Math.max(accSeq, v); } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (IOException ex) { ex.printStackTrace(); }
        }
        if (Files.exists(TX_FILE)) {
            try (BufferedReader r = Files.newBufferedReader(TX_FILE)) {
                r.readLine();
                String line;
                while ((line = r.readLine()) != null) {
                    Transaction t = Transaction.fromCSV(line);
                    if (t != null) {
                        transactions.add(t);
                        if (t.txId.startsWith("TX")) {
                            try { int v = Integer.parseInt(t.txId.substring(2)); txSeq = Math.max(txSeq, v); } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (IOException ex) { ex.printStackTrace(); }
        }
        // adjust sequences next use
        accSeq++; txSeq++;
    }

    // ---------- Utilities ----------

    private String generateAccNo() {
        // ACC1001, ACC1002...
        while (true) {
            accSeq++;
            String candidate = "ACC" + accSeq;
            if (!accounts.containsKey(candidate)) return candidate;
        }
    }

    private String genTxId() {
        txSeq++;
        return "TX" + txSeq;
    }

    private String now() { return dtfmt.format(new Date()); }

    private double parseDoubleSafe(String s, double defaultVal) {
        try { return Double.parseDouble(s.replace(",", "").trim()); } catch (Exception e) { return defaultVal; }
    }

    // ---------- Small UI utilities ----------
    private void clearRegisterForm() {
        tfRegName.setText("");
        pfRegPin.setText("");
        pfRegPinConfirm.setText("");
        cbRegType.setSelectedIndex(0);
        lblRegMsg.setText(" ");
    }

    private void clearLoginForm() {
        tfLoginAcc.setText("");
        pfLoginPin.setText("");
        lblLoginMsg.setText(" ");
    }

    // ---------- Hashing ----------
    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(s.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // ---------- CSV helpers ----------
    private static String escapeCSV(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            s = s.replace("\"", "\"\"");
            return "\"" + s + "\"";
        }
        return s;
    }

    private static String[] parseCSVLineStatic(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i=0;i<line.length();i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i+1 < line.length() && line.charAt(i+1) == '"') { cur.append('"'); i++; } else { inQuotes = false; }
                } else cur.append(ch);
            } else {
                if (ch == '"') inQuotes = true;
                else if (ch == ',') { fields.add(cur.toString()); cur.setLength(0); }
                else cur.append(ch);
            }
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }

    // ---------- Custom button (gradient rounded) ----------
    private JButton gradientButton(String text, Color a, Color b) {
        JButton btn = new JButton(text) {
            boolean hover = false;
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                int w = getWidth(), h = getHeight();
                Color start = hover ? a.brighter() : a;
                Color end = hover ? b.brighter() : b;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0,0,start, w, h, end);
                g2.setPaint(gp);
                g2.fillRoundRect(0,0,w,h,14,14);
                g2.setColor(Color.WHITE);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int tx = (w - fm.stringWidth(getText()))/2;
                int ty = (h - fm.getHeight())/2 + fm.getAscent();
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setForeground(Color.WHITE);
        btn.setPreferredSize(new Dimension(150, 36));
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) { btn.repaint(); }
            public void mouseExited(java.awt.event.MouseEvent e) { btn.repaint(); }
        });
        return btn;
    }

    // ---------- Main ----------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
            ATMApp app = new ATMApp();
            app.setVisible(true);
        });
    }
}
