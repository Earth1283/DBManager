DBManager
=========

.. image:: https://img.shields.io/github/v/release/earth1283/DBManager
   :target: https://github.com/earth1283/DBManager/releases
   :alt: GitHub release (latest by date)

**DBManager** is a high-performance database connection pool provider and administration tool for Minecraft (Paper/Spigot) servers. It simplifies database management for both developers and server administrators.

Features
--------

* **Optimized Connection Pooling**: Powered by `HikariCP <https://github.com/brettwooldridge/HikariCP>`_ for maximum performance and reliability.
* **Multi-Engine Support**: Out-of-the-box support for **SQLite**, **MySQL**, **MariaDB**, and **PostgreSQL**.
* **Developer API**: Exposes standard JDBC ``DataSource`` objects to other plugins, allowing them to focus on logic rather than connection management.
* **Secure Web UI**: An embedded Ktor-based dashboard with one-time token authentication. Browse tables and run queries from your browser.
* **In-Game Explorer**: Experimental chest-based GUI and console commands (``/db execute``) for quick database manipulation.

Installation
------------

1. Place the ``DBManager.jar`` in your server's ``plugins/`` folder.
2. Restart the server to generate the default ``config.yml``.
3. Configure your databases in ``config.yml``.
4. Use ``/db reload`` to apply changes.

Developer API Usage
-------------------

To use DBManager in your own plugin, add it as a dependency and access the ``ConnectionManager``:

.. code-block:: kotlin

   import io.github.earth1283.dBManager.DBManager
   import javax.sql.DataSource

   // Get a DataSource by the name defined in config.yml
   val dataSource: DataSource? = DBManager.connectionManager?.getDataSource("main_mysql")

   dataSource?.connection?.use { conn ->
       // Use standard JDBC
   }

Commands
--------

* ``/db list``: Lists all configured and active database pools.
* ``/db execute <db> <sql>``: Directly execute SQL on a managed database.
* ``/db gui``: Open the experimental in-game table browser.
* ``/db web``: Generate a secure, single-use login link for the Web Dashboard.

Configuration
-------------

.. code-block:: yaml

   web-ui:
     enabled: true
     port: 8080

   databases:
     local_storage:
       type: "sqlite"
       file: "storage.db"
       pool:
         maximumPoolSize: 10
