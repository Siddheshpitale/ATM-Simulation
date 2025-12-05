String readme = 
"# 🏧 ATM Simulator (Java Swing)\n" +
"\n" +
"A fully functional **ATM Simulator** built using **Java Swing**.\n" +
"This project demonstrates core OOP concepts such as **encapsulation**, **data persistence**, **UI event handling**, and **basic security** using **PIN hashing (SHA-256)**.\n" +
"\n" +
"## ⭐ Features\n" +
"- 🔐 **Secure login** with Account Number + PIN (PIN stored as SHA-256 hash)\n" +
"- 📝 **Create new accounts** (Savings / Checking)\n" +
"- 💰 **Deposit and Withdraw money**\n" +
"- 📊 **View balance in real time**\n" +
"- 📜 **Transaction History** (Deposit / Withdraw / PIN change)\n" +
"- 🔑 **Change PIN**\n" +
"- 💾 **CSV file persistence** (`accounts.csv`, `transactions.csv`)\n" +
"- 🎨 **Modern Swing UI** with gradients & clean layout\n" +
"- 🗄️ Single-file Java project: `ATMApp.java`\n" +
"\n" +
"## 📸 Screenshots\n" +
"_Add screenshots here once uploaded to GitHub._\n" +
"\n" +
"## 📦 Project Structure\n" +
"```\n" +
"ATMProject/\n" +
"│── ATMApp.java\n" +
"│── accounts.csv        # auto-generated, stores account data\n" +
"│── transactions.csv    # auto-generated, stores transaction logs\n" +
"└── README.md\n" +
"```\n" +
"\n" +
"## 🚀 How to Run\n" +
"### 1. Compile\n" +
"```\n" +
"javac ATMApp.java\n" +
"```\n" +
"### 2. Run\n" +
"```\n" +
"java ATMApp\n" +
"```\n" +
"\n" +
"## 🔧 Requirements\n" +
"- Java **JDK 8+**\n" +
"- Any IDE or editor (VS Code, IntelliJ, Eclipse, Notepad++)\n" +
"- Git (optional)\n" +
"\n" +
"## 🔐 Security Notes\n" +
"- PINs are **not stored in plain text**.\n" +
"- SHA-256 hashing is used (for educational purposes).\n" +
"- In real banking systems, use salted hashing like **bcrypt / PBKDF2**.\n" +
"\n" +
"## 🧠 Concepts Demonstrated\n" +
"- Encapsulation (Account & Transaction classes)\n" +
"- Abstraction (ATM operations)\n" +
"- Swing GUI programming\n" +
"- Event handling\n" +
"- File I/O for persistence\n" +
"- Data validation & error handling\n" +
"- SHA-256 hashing\n" +
"\n" +
"## 🏗️ Future Enhancements\n" +
"- Fund transfers between accounts\n" +
"- Account lockout after failed attempts\n" +
"- ATM keypad UI for PIN entry\n" +
"- Export transaction history to PDF\n" +
"- Use database instead of CSV\n" +
"\n" +
"## 🤝 Contributing\n" +
"Pull requests are welcome! Open an issue for bugs or suggestions.\n" +
"\n" +
"## 📜 License\n" +
"Licensed under the MIT License.\n";
