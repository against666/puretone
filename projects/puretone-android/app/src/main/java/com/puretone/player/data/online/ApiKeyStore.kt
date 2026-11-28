package com.puretone.player.data.online

/**
 * API 密钥的中转站。
 *
 * 内置密钥打进 APK（BuildConfig），远程配置下发的密钥存在这里。
 * UpdateChecker 每次成功拉到配置就刷新 [remote]； OnlineMusicApi 每次请求
 * 通过 [effective] 取当前该用的那把——远程有值用远程，否则退回内置。
 *
 * 用内存单例而不是直接读 DataStore，是因为请求在 IO 线池里跑，
 * 这里只放一个 volatile 字段最省事也最不容易出并发问题。
 */
object ApiKeyStore {

    @Volatile
    private var remote: String = ""

    fun update(key: String) {
        remote = key.trim()
    }

    fun effective(builtin: String): String = remote.ifBlank { builtin }
}
