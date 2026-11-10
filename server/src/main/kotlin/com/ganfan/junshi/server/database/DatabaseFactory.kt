package com.ganfan.junshi.server.database

import com.ganfan.junshi.server.database.tables.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDateTime

object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    fun init() {
        // 优先从环境变量读取
        val dbUrl = System.getenv("DB_URL")
        // 如果没有提供 DB_URL，且未明确指定使用 Postgres，则默认使用 H2 (为了方便本地开发)
        val usePostgres = System.getenv("USE_POSTGRES")?.toBoolean() ?: (dbUrl != null)

        val config = if (usePostgres) {
            logger.info("正在连接到 PostgreSQL 数据库...")
            HikariConfig().apply {
                // 默认值与 docker-compose.yml 保持一致
                jdbcUrl = dbUrl ?: "jdbc:postgresql://localhost:5432/ganfan_junshi"
                username = System.getenv("DB_USER") ?: "ganfan"
                password = System.getenv("DB_PASS") ?: "ganfan123"
                driverClassName = "org.postgresql.Driver"
                maximumPoolSize = 10
            }
        } else {
            logger.info("正在连接到 H2 数据库 (本地开发模式)...")
            // 确保目录存在
            File("server/data").mkdirs()
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:file:./server/data/ganfan_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
                driverClassName = "org.h2.Driver"
                maximumPoolSize = 10
            }
        }

        try {
            val dataSource = HikariDataSource(config)
            Database.connect(dataSource)

            // 自动创建表结构 (如果不存在)
            transaction {
                SchemaUtils.createMissingTablesAndColumns(
                    Users, UserTasteProfiles, UserRelations, Restaurants, Favorites,
                    MatchPosts, MatchApplications, Reviews, AiConversations, AiMessages,
                    ChatConversations, ChatMessages, Notifications
                )
            }
            logger.info("数据库初始化成功。")

            // 插入初始数据
            seedData()

        } catch (e: Exception) {
            logger.error("数据库连接失败: ${e.message}")
            if (usePostgres && dbUrl == null) {
                logger.info("PostgreSQL 连接失败，尝试切换到 H2 备用数据库...")
                initH2Fallback()
            } else {
                throw e
            }
        }
    }

    private fun initH2Fallback() {
        File("server/data").mkdirs()
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:file:./server/data/ganfan_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
            driverClassName = "org.h2.Driver"
            maximumPoolSize = 10
        }
        val dataSource = HikariDataSource(config)
        Database.connect(dataSource)
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                Users, UserTasteProfiles, UserRelations, Restaurants, Favorites,
                MatchPosts, MatchApplications, Reviews, AiConversations, AiMessages,
                ChatConversations, ChatMessages, Notifications
            )
        }
        logger.info("备用 H2 数据库初始化成功。")
        seedData()
    }

    private fun seedData() {
        transaction {
            if (Restaurants.selectAll().empty()) {
                logger.info("检测到餐厅表为空，正在插入初始测试数据...")
                Restaurants.insert {
                    it[name] = "蜀香小馆"
                    it[address] = "北京市朝阳区三里屯 10 号"
                    it[cuisine] = "川菜"
                    it[rating] = java.math.BigDecimal("4.8")
                    it[avgPrice] = 65
                    it[lat] = java.math.BigDecimal("39.9333")
                    it[lng] = java.math.BigDecimal("116.4500")
                    it[createdAt] = LocalDateTime.now()
                }
                Restaurants.insert {
                    it[name] = "清粥小厨"
                    it[address] = "北京市海淀区中关村 5 号"
                    it[cuisine] = "粤菜/粥品"
                    it[rating] = java.math.BigDecimal("4.5")
                    it[avgPrice] = 35
                    it[lat] = java.math.BigDecimal("39.9833")
                    it[lng] = java.math.BigDecimal("116.3100")
                    it[createdAt] = LocalDateTime.now()
                }
                Restaurants.insert {
                    it[name] = "樱花寿司"
                    it[address] = "北京市西城区西单 22 号"
                    it[cuisine] = "日料"
                    it[rating] = java.math.BigDecimal("4.9")
                    it[avgPrice] = 120
                    it[lat] = java.math.BigDecimal("39.9133")
                    it[lng] = java.math.BigDecimal("116.3700")
                    it[createdAt] = LocalDateTime.now()
                }
                logger.info("初始测试数据插入完成。")
            }
        }
    }
}
