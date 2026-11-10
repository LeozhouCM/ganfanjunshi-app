package com.ganfan.junshi.server.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.util.concurrent.ConcurrentHashMap

fun Application.configureWebSockets() {
    install(WebSockets)
    routing {
        // 聊天 WebSocket — 基于会话 ID 的群组通信
        webSocket("/ws/chat/{conversationId}") {
            val conversationId = call.parameters["conversationId"] ?: "0"
            val userId = call.request.queryParameters["userId"]?.toLongOrNull()

            val connections = chatConnections.getOrPut(conversationId) { mutableSetOf() }
            connections.add(this)

            // 注册用户级连接（用于在线状态检测 + 通知推送）
            if (userId != null) {
                val userConns = userChatConnections.getOrPut(userId) { mutableSetOf() }
                userConns.add(this)
            }

            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        connections.forEach { session ->
                            if (session != this) session.send(text)
                        }
                    }
                }
            } finally {
                connections.remove(this)
                if (userId != null) {
                    userChatConnections[userId]?.remove(this)
                }
            }
        }

        // 拼桌匹配实时通知 WebSocket
        webSocket("/ws/match") {
            val userId = call.request.queryParameters["userId"]
            val connections = matchConnections.getOrPut(userId ?: "0") { mutableSetOf() }
            connections.add(this)
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        connections.forEach { session ->
                            if (session != this) session.send(text)
                        }
                    }
                }
            } finally {
                connections.remove(this)
            }
        }
    }
}

private val chatConnections = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()
private val matchConnections = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()

/** 用户级连接池：userId → 该用户当前所有 WebSocket 会话集合 */
val userChatConnections = ConcurrentHashMap<Long, MutableSet<DefaultWebSocketServerSession>>()
