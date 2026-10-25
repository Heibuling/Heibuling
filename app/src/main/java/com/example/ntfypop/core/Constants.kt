package com.example.ntfypop.core

object Constants {
    const val LOG_TAG = "Heibuling"

    // ---- 协议 ----
    const val DEFAULT_SERVER = "https://ntfy.sh"
    const val PROTOCOL_VERSION = 1
    const val TOPIC_PREFIX = "np1_"
    const val DEV_TOPIC_PREFIX = "np1_dev_"
    const val TOPIC_RANDOM_LEN = 22
    const val INVITE_PREFIX = "NTP1-"
    const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    // ---- 弹框 ----
    const val BUBBLE_DURATION_MS = 5000L
    const val ENTER_ANIM_MS = 220
    const val EXIT_ANIM_MS = 180
    const val BUBBLE_MAX_HEIGHT_RATIO = 0.40f

    // ---- 连接 ----
    const val HEARTBEAT_TIMEOUT_MS = 90_000L
    val RECONNECT_BACKOFF_MS = longArrayOf(1000L, 2000L, 4000L, 8000L, 16000L, 30000L)

    // ---- 前台服务 ----
    const val NOTIF_CHANNEL_PERSISTENT = "ntfypop_persistent"
    const val NOTIF_ID_PERSISTENT = 1001
}
