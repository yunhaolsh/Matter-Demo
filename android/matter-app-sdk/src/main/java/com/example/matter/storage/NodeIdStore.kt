package com.example.matter.storage

import android.annotation.SuppressLint
import android.content.Context

internal class NodeIdStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("com.example.matter.node_ids", Context.MODE_PRIVATE)

    @Synchronized
    @SuppressLint("ApplySharedPref")
    fun reserve(): Long {
        // A node ID must be durable before commissioning starts so it cannot be reused after a crash.
        val nodeId = preferences.getLong(KEY_NEXT_NODE_ID, FIRST_NODE_ID)
        check(preferences.edit().putLong(KEY_NEXT_NODE_ID, nodeId + 1).commit()) {
            "Unable to reserve a Matter node ID"
        }
        return nodeId
    }

    private companion object {
        const val KEY_NEXT_NODE_ID = "next_node_id"
        const val FIRST_NODE_ID = 1L
    }
}
