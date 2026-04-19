package io.github.earth1283.dBManager.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.configuration.ConfigurationSection
import java.io.File
import java.util.logging.Logger
import javax.sql.DataSource

class ConnectionManager(private val dataFolder: File, private val logger: Logger) {
    private val dataSources = mutableMapOf<String, HikariDataSource>()

    fun loadFromConfig(config: ConfigurationSection?) {
        closeAll()

        val keys = config?.getKeys(false) ?: emptySet()
        if (keys.isEmpty()) {
            logger.warning("No databases configured in config.yml — nothing to load.")
            return
        }
        logger.info("Found ${keys.size} database entry(s) to load: ${keys.joinToString()}")

        for (key in keys) {
            val section = config!!.getConfigurationSection(key) ?: run {
                logger.warning("  [$key] Skipping — could not read config section.")
                continue
            }
            val type = section.getString("type", "sqlite")?.lowercase() ?: "sqlite"

            val maxPool = section.getInt("pool.maximumPoolSize", 10)
            val minIdle = section.getInt("pool.minimumIdle", 2)

            val hikariConfig = HikariConfig().apply {
                poolName = "DBManagerPool-$key"
                maximumPoolSize = maxPool
                minimumIdle = minIdle
                connectionTimeout = 5000
            }

            when (type) {
                "sqlite" -> {
                    val fileName = section.getString("file", "$key.db")!!
                    val dbFile = File(dataFolder, fileName)
                    hikariConfig.jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
                    hikariConfig.driverClassName = "org.sqlite.JDBC"
                    logger.info("  [$key] type=sqlite  file=${dbFile.absolutePath}  pool=$minIdle..$maxPool")
                }
                "mysql", "mariadb" -> {
                    val host = section.getString("host", "localhost")
                    val port = section.getInt("port", 3306)
                    val db = section.getString("database", "")
                    hikariConfig.jdbcUrl = "jdbc:mysql://$host:$port/$db"
                    hikariConfig.username = section.getString("username")
                    hikariConfig.password = section.getString("password")
                    hikariConfig.driverClassName = "com.mysql.cj.jdbc.Driver"
                    logger.info("  [$key] type=mysql  host=$host:$port  db=$db  pool=$minIdle..$maxPool")
                }
                "postgresql" -> {
                    val host = section.getString("host", "localhost")
                    val port = section.getInt("port", 5432)
                    val db = section.getString("database", "")
                    hikariConfig.jdbcUrl = "jdbc:postgresql://$host:$port/$db"
                    hikariConfig.username = section.getString("username")
                    hikariConfig.password = section.getString("password")
                    hikariConfig.driverClassName = "org.postgresql.Driver"
                    logger.info("  [$key] type=postgresql  host=$host:$port  db=$db  pool=$minIdle..$maxPool")
                }
                else -> {
                    logger.warning("  [$key] Unknown database type '$type' — skipping.")
                    continue
                }
            }

            try {
                dataSources[key] = HikariDataSource(hikariConfig)
                logger.info("  [$key] Pool started successfully.")
            } catch (e: Exception) {
                logger.severe("  [$key] Failed to start pool: ${e.message}")
                logger.severe("         Check your credentials and connectivity, then reload.")
            }
        }
    }

    fun getDataSource(name: String): DataSource? = dataSources[name]
    fun getAvailableDatabases(): Set<String> = dataSources.keys

    fun addSqliteConnection(name: String, filePath: String): Boolean {
        val cfg = HikariConfig().apply {
            poolName = "DBManagerPool-$name"
            maximumPoolSize = 10
            minimumIdle = 2
            connectionTimeout = 5000
            jdbcUrl = "jdbc:sqlite:$filePath"
            driverClassName = "org.sqlite.JDBC"
        }
        return tryAddPool(name, cfg)
    }

    fun addRemoteConnection(
        name: String, type: String,
        host: String, port: Int, database: String,
        username: String, password: String
    ): Boolean {
        val cfg = HikariConfig().apply {
            poolName = "DBManagerPool-$name"
            maximumPoolSize = 10
            minimumIdle = 2
            connectionTimeout = 8000
            this.username = username
            this.password = password
            when (type) {
                "mysql", "mariadb" -> {
                    jdbcUrl = "jdbc:mysql://$host:$port/$database"
                    driverClassName = "com.mysql.cj.jdbc.Driver"
                }
                "postgresql" -> {
                    jdbcUrl = "jdbc:postgresql://$host:$port/$database"
                    driverClassName = "org.postgresql.Driver"
                }
            }
        }
        return tryAddPool(name, cfg)
    }

    fun removeConnection(name: String): Boolean {
        val ds = dataSources.remove(name) ?: return false
        ds.close()
        logger.info("Dynamic connection removed: [$name]")
        return true
    }

    private fun tryAddPool(name: String, cfg: HikariConfig): Boolean {
        return try {
            dataSources[name] = HikariDataSource(cfg)
            logger.info("Dynamic connection added: [$name] url=${cfg.jdbcUrl}")
            true
        } catch (e: Exception) {
            logger.severe("Failed to add dynamic connection [$name]: ${e.message}")
            false
        }
    }

    fun closeAll() {
        dataSources.values.forEach { it.close() }
        dataSources.clear()
    }
}
