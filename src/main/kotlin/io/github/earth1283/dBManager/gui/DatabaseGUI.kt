package io.github.earth1283.dBManager.gui

import io.github.earth1283.dBManager.DBManager
import io.github.earth1283.dBManager.database.DatabaseExplorer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object DatabaseGUI {

    fun openDatabaseList(player: Player) {
        val dbs = DBManager.connectionManager?.getAvailableDatabases() ?: emptySet()
        val inv = Bukkit.createInventory(null, 27, "§8Select Database (Experimental)")
        
        dbs.forEachIndexed { index, name ->
            if (index >= 27) return@forEachIndexed
            val item = ItemStack(Material.ANVIL)
            val meta = item.itemMeta
            meta?.setDisplayName("§b$name")
            item.itemMeta = meta
            inv.setItem(index, item)
        }
        
        player.openInventory(inv)
    }

    fun openTableList(player: Player, dbName: String) {
        val ds = DBManager.connectionManager?.getDataSource(dbName) ?: return
        val tables = DatabaseExplorer.getTables(ds)
        val inv = Bukkit.createInventory(null, 54, "§8Tables: $dbName")
        
        tables.forEachIndexed { index, name ->
            if (index >= 54) return@forEachIndexed
            val item = ItemStack(Material.BOOK)
            val meta = item.itemMeta
            meta?.setDisplayName("§e$name")
            item.itemMeta = meta
            inv.setItem(index, item)
        }
        
        player.openInventory(inv)
    }
}
