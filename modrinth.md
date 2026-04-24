# 📊 DBManager: The Ultimate Database Toolkit

![Logo Placeholder](https://via.placeholder.com/1200x300.png?text=DBManager+for+Paper/Spigot)

**DBManager** is a powerful, lightweight plugin designed to handle all your server's database needs. Whether you're a server owner looking for a better way to manage data or a developer tired of writing boilerplate connection code, DBManager has you covered.

## 🚀 Key Features

### 🌐 Secure Web Dashboard
Stop squinting at console logs! DBManager includes an **embedded web server**. Run `/db web` in-game to generate a secure, one-time login link and manage your databases from a clean, modern browser interface.
* **SQL Console**: Run queries with syntax highlighting.
* **Schema Browser**: View tables and column structures at a glance.
* **Metrics**: Real-time stats on your database connection health.

### 🛠️ Developer-First API
Building a plugin that needs a database? Don't reinvent the wheel. DBManager provides a rock-solid **HikariCP API**.
* Borrow connections from high-performance pools.
* Support for **SQLite, MySQL, and PostgreSQL** with zero extra setup.
* Let DBManager handle the lifecycle while you focus on your features.

### 🎮 In-Game Tools
* **Experimental GUI**: Browse your tables directly inside a chest interface.
* **Command Power**: Execute SQL updates or lookups via `/db execute`.
* **Zero Downtime**: Reload database configurations on the fly.

## 📦 Supported Databases
* **SQLite** (Local file-based)
* **MySQL** & **MariaDB**
* **PostgreSQL**

## 🔧 Installation
1. Drop the JAR into your `plugins` folder.
2. Restart your server.
3. Edit `plugins/DBManager/config.yml` to add your databases.
4. Enjoy a more organized server!

---

*Made with ❤️ for the Minecraft community. Check out our [GitHub](https://github.com/earth1283/DBManager) for the source code!*
