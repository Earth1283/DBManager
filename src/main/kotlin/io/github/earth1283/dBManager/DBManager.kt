package io.github.earth1283.dBManager

import io.github.earth1283.dBManager.database.ConnectionManager
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bukkit.plugin.java.JavaPlugin

class DBManager : JavaPlugin() {
    companion object {
        var connectionManager: ConnectionManager? = null
            private set
        var webServer: io.github.earth1283.dBManager.web.WebServer? = null
            private set
        var audiences: BukkitAudiences? = null
            private set
    }

    override fun onEnable() {
        val startTime = System.currentTimeMillis()

        logger.info("===========================================")
        logger.info("  DBManager v${description.version} starting up...")
        logger.info("  Server: ${server.name} ${server.version}")
        logger.info("===========================================")

        // Config
        saveDefaultConfig()
        logger.info("Config loaded from: ${dataFolder.absolutePath}/config.yml")

        // Adventure audiences (works on both Paper and Spigot)
        audiences = BukkitAudiences.create(this)
        logger.info("Adventure platform initialized (Spigot-compatible colored output ready).")

        // Database pools
        logger.info("--- Loading database pools ---")
        val manager = ConnectionManager(dataFolder, logger)
        manager.loadFromConfig(config.getConfigurationSection("databases"))
        connectionManager = manager
        val poolCount = manager.getAvailableDatabases().size
        logger.info("--- ${poolCount} pool(s) active: ${manager.getAvailableDatabases().joinToString()} ---")

        // Command
        getCommand("dbmanager")?.setExecutor(io.github.earth1283.dBManager.commands.DBCommand())
        logger.info("Command /db (alias: /dbmanager) registered.")

        // Web UI
        if (config.getBoolean("web-ui.enabled", true)) {
            val port = config.getInt("web-ui.port", 8080)
            logger.info("--- Starting Web UI on port $port ---")
            webServer = io.github.earth1283.dBManager.web.WebServer(port)
            try {
                webServer?.start()
                logger.info("Web UI started. Use /db web in-game to generate a login link.")
                logger.info("Web UI listening at: http://localhost:$port/")
            } catch (e: Exception) {
                logger.severe("Web UI failed to start on port $port: ${e.message}")
                logger.severe("Check that port $port is not already in use.")
                webServer = null
            }
        } else {
            logger.info("Web UI is disabled (web-ui.enabled: false in config.yml).")
        }

        val elapsed = System.currentTimeMillis() - startTime
        logger.info("===========================================")
        logger.info("  DBManager enabled in ${elapsed}ms.")
        logger.info("===========================================")
    }

    override fun onDisable() {
        logger.info("DBManager shutting down...")

        webServer?.stop()
        if (webServer != null) logger.info("Web UI stopped.")

        connectionManager?.closeAll()
        val poolCount = connectionManager?.getAvailableDatabases()?.size ?: 0
        logger.info("Closed $poolCount database pool(s).")

        audiences?.close()
        logger.info("Adventure platform closed.")

        logger.info("DBManager disabled. Goodbye!")
    }
}
