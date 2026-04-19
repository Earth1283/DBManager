package io.github.earth1283.dBManager.database

import java.sql.ResultSet
import javax.sql.DataSource

object DatabaseExplorer {

    fun getTables(dataSource: DataSource): List<String> {
        val tables = mutableListOf<String>()
        dataSource.connection.use { conn ->
            val metaData = conn.metaData
            val rs = metaData.getTables(null, null, "%", arrayOf("TABLE"))
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"))
            }
        }
        return tables
    }

    fun getColumns(dataSource: DataSource, tableName: String): List<String> {
        val columns = mutableListOf<String>()
        dataSource.connection.use { conn ->
            val metaData = conn.metaData
            val rs = metaData.getColumns(null, null, tableName, null)
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"))
            }
        }
        return columns
    }

    fun executeQuery(dataSource: DataSource, query: String): List<Map<String, Any>> {
        val results = mutableListOf<Map<String, Any>>()
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                val isResultSet = stmt.execute(query)
                if (isResultSet) {
                    val rs = stmt.resultSet
                    val metaData = rs.metaData
                    val columnCount = metaData.columnCount
                    while (rs.next()) {
                        val row = mutableMapOf<String, Any>()
                        for (i in 1..columnCount) {
                            val colName = metaData.getColumnName(i)
                            row[colName] = rs.getObject(i) ?: "NULL"
                        }
                        results.add(row)
                    }
                } else {
                    results.add(mapOf("updateCount" to stmt.updateCount))
                }
            }
        }
        return results
    }
}
