package io.github.earth1283.dBManager.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.configuration.ConfigurationSection
import java.io.File
import javax.sql.DataSource

class ConnectionManager(private val dataFolder: File) {
    private val dataSources = mutableMapOf<String, HikariDataSource>()

    fun loadFromConfig(config: ConfigurationSection?) {
        closeAll()
        config?.getKeys(false)?.forEach { key ->
            val section = config.getConfigurationSection(key) ?: return@forEach
            val type = section.getString("type", "sqlite")?.lowercase()
            
            val hikariConfig = HikariConfig().apply {
                poolName = "DBManagerPool-$key"
                maximumPoolSize = section.getInt("pool.maximumPoolSize", 10)
                minimumIdle = section.getInt("pool.minimumIdle", 2)
                connectionTimeout = 5000
            }

            when (type) {
                "sqlite" -> {
                    val dbFile = File(dataFolder, section.getString("file", "$key.db")!!)
                    hikariConfig.jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
                    hikariConfig.driverClassName = "org.sqlite.JDBC"
                }
                "mysql", "mariadb" -> {
                    val host = section.getString("host", "localhost")
                    val port = section.getInt("port", 3306)
                    val db = section.getString("database", "")
                    hikariConfig.jdbcUrl = "jdbc:mysql://$host:$port/$db"
                    hikariConfig.username = section.getString("username")
                    hikariConfig.password = section.getString("password")
                    hikariConfig.driverClassName = "com.mysql.cj.jdbc.Driver"
                }
                "postgresql" -> {
                    val host = section.getString("host", "localhost")
                    val port = section.getInt("port", 5432)
                    val db = section.getString("database", "")
                    hikariConfig.jdbcUrl = "jdbc:postgresql://$host:$port/$db"
                    hikariConfig.username = section.getString("username")
                    hikariConfig.password = section.getString("password")
                    hikariConfig.driverClassName = "org.postgresql.Driver"
                }
            }
            try {
                dataSources[key] = HikariDataSource(hikariConfig)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getDataSource(name: String): DataSource? = dataSources[name]
    fun getAvailableDatabases(): Set<String> = dataSources.keys

    fun closeAll() {
        dataSources.values.forEach { it.close() }
        dataSources.clear()
    }
}
