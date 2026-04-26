package com.bclaw.app.remote

internal object NativeEnet {
    init {
        System.loadLibrary("bclaw_remote")
    }

    external fun nativeCreate(
        host: String,
        port: Int,
        connectData: Long,
        timeoutMs: Int,
    ): Long

    external fun nativeSend(
        handle: Long,
        payload: ByteArray,
        channelId: Int,
        reliable: Boolean,
        unsequenced: Boolean,
    ): Boolean

    external fun nativeService(handle: Long, timeoutMs: Int): Int

    external fun nativeClose(handle: Long)

    external fun nativeRecoverFec(
        dataShards: Int,
        parityShards: Int,
        shardSize: Int,
        shards: Array<ByteArray?>,
    ): Array<ByteArray>?
}
