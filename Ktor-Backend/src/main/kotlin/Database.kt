package com.ewan

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

object Database {
    fun connect(): DataSource {
        Class.forName("com.mysql.cj.jdbc.Driver")
        val config = HikariConfig().apply {
            jdbcUrl = System.getenv("JDBC_DATABASE_URL") ?: DBCredentials.DB_URL
            username = System.getenv("JDBC_DATABASE_USER") ?: DBCredentials.DB_USER
            password = System.getenv("JDBC_DATABASE_PASSWORD") ?: DBCredentials.DB_PASS
            maximumPoolSize = 10
        }
        return HikariDataSource(config)
    }
}
