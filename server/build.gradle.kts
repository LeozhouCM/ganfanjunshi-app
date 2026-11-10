plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

group = "com.ganfan.junshi.server"
version = "1.0.0"

application {
    mainClass.set("com.ganfan.junshi.server.ApplicationKt")
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.gson)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.postgresql)
    implementation(libs.h2)
    implementation(libs.hikaricp)
    implementation(libs.logback.classic)
    implementation(libs.java.jwt)
    implementation(libs.jedis)
    implementation(libs.bcrypt)
    implementation(libs.gson)
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
