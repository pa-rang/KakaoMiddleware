package com.example.kakaomiddleware

object PushWakePayload {
    private const val TYPE = "outbound_available"
    private const val SCHEMA_VERSION = "1"

    fun isSupported(data: Map<String, String>): Boolean =
        data["type"] == TYPE && data["schemaVersion"] == SCHEMA_VERSION
}
