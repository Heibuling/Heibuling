package com.example.ntfypop.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 开发者诊断用的运行时状态（只记录"连接与收发状态"，不涉及任何人的消息内容或相册）。
 * 密码入口在 App 里，仅本机可见。
 */
object DiagState {

    data class TopicState(
        val topic: String,
        val connected: Boolean = false,
        val attempts: Int = 0,
        val lastEventAt: Long = 0L,
        val lastEvent: String = "",
        val lastServerId: String = "",
        val lastError: String = "",
    )

    private val _topics = MutableStateFlow<Map<String, TopicState>>(emptyMap())
    val topics: StateFlow<Map<String, TopicState>> = _topics

    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events: StateFlow<List<String>> = _events

    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun update(topic: String, block: (TopicState) -> TopicState) {
        val cur = _topics.value[topic] ?: TopicState(topic)
        _topics.value = _topics.value + (topic to block(cur))
    }

    fun log(line: String) {
        val stamp = fmt.format(Date())
        _events.value = (listOf(stamp + "  " + line) + _events.value).take(60)
    }

    fun clear() {
        _topics.value = emptyMap()
        _events.value = emptyList()
    }
}
