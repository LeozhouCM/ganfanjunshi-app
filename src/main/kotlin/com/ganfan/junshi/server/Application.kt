package com.ganfan.junshi.server

import com.ganfan.junshi.server.database.DatabaseFactory
import com.ganfan.junshi.server.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    // 默认使用 8082 端口；可通过环境变量 SERVER_PORT 覆盖
    val port = System.getenv("SERVER_PORT")?.toIntOrNull() ?: 8082
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init()
    // 日志拦截已移至 Routing.kt
    configureSerialization()
    configureSecurity()
    configureHTTP()
    configureRouting()
    configureWebSockets()
}

