package com.example.matter.storage

import android.annotation.SuppressLint
import android.content.Context
import com.example.matter.api.MatterDevice

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

    @Synchronized
    @SuppressLint("ApplySharedPref")
    fun markCommissioned(nodeId: Long) {
        val nodeIds = preferences.getStringSet(KEY_COMMISSIONED_NODE_IDS, emptySet()).orEmpty()
        check(
            preferences.edit()
                .putStringSet(KEY_COMMISSIONED_NODE_IDS, nodeIds + nodeId.toString())
                .putBoolean(KEY_DEVICE_LIST_MIGRATED, true)
                .commit(),
        ) {
            "Unable to save the commissioned Matter node"
        }
    }

    @Synchronized
    @SuppressLint("ApplySharedPref")
    fun saveDevice(device: MatterDevice) {
        check(
            preferences.edit()
                .putString(deviceSnapshotKey(device.id), MatterDeviceSnapshotCodec.encode(device))
                .commit(),
        ) {
            "Unable to save the Matter device directory entry"
        }
    }

    @Synchronized
    fun restoredDevice(nodeId: Long): MatterDevice? =
        preferences.getString(deviceSnapshotKey(nodeId.toString()), null)
            ?.let { encoded -> runCatching { MatterDeviceSnapshotCodec.decode(encoded) }.getOrNull() }

    @Synchronized
    @SuppressLint("ApplySharedPref")
    fun commissionedNodeIds(): Set<Long> {
        if (!preferences.getBoolean(KEY_DEVICE_LIST_MIGRATED, false)) {
            val savedNodeIds = preferences.getStringSet(KEY_COMMISSIONED_NODE_IDS, emptySet()).orEmpty()
            val nextNodeId = preferences.getLong(KEY_NEXT_NODE_ID, FIRST_NODE_ID)
            val migratedNodeIds =
                if (savedNodeIds.isEmpty() && nextNodeId > FIRST_NODE_ID) {
                    setOf((nextNodeId - 1).toString())
                } else {
                    savedNodeIds
                }
            check(
                preferences.edit()
                    .putStringSet(KEY_COMMISSIONED_NODE_IDS, migratedNodeIds)
                    .putBoolean(KEY_DEVICE_LIST_MIGRATED, true)
                    .commit(),
            ) {
                "Unable to migrate commissioned Matter nodes"
            }
        }
        return preferences.getStringSet(KEY_COMMISSIONED_NODE_IDS, emptySet()).orEmpty()
            .mapNotNull(String::toLongOrNull)
            .toSet()
    }

    @Synchronized
    @SuppressLint("ApplySharedPref")
    fun removeCommissioned(nodeId: Long) {
        val nodeIds = preferences.getStringSet(KEY_COMMISSIONED_NODE_IDS, emptySet()).orEmpty()
        check(
            preferences.edit()
                .putStringSet(KEY_COMMISSIONED_NODE_IDS, nodeIds - nodeId.toString())
                .remove(deviceSnapshotKey(nodeId.toString()))
                .putBoolean(KEY_DEVICE_LIST_MIGRATED, true)
                .commit(),
        ) {
            "Unable to remove the commissioned Matter node"
        }
    }

    private companion object {
        fun deviceSnapshotKey(deviceId: String) = "device_snapshot_$deviceId"
        const val KEY_NEXT_NODE_ID = "next_node_id"
        const val KEY_COMMISSIONED_NODE_IDS = "commissioned_node_ids"
        const val KEY_DEVICE_LIST_MIGRATED = "device_list_migrated"
        const val FIRST_NODE_ID = 1L
    }
}
