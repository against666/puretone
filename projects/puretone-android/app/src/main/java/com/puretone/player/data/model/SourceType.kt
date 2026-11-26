package com.puretone.player.data.model

/**
 * 音源。[code] 对应开放 API 的 type 参数；[LOCAL] 表示本地导入，不参与联网。
 */
enum class SourceType(val code: String, val displayName: String) {
    QQ("qq", "QQ音乐"),
    WY("wy", "网易云音乐"),
    KG("kg", "酷狗音乐"),
    KW("kw", "酷我音乐"),
    LOCAL("local", "本地导入"),
    ;

    companion object {
        fun fromCode(code: String): SourceType =
            entries.firstOrNull { it.code == code } ?: LOCAL

        /** 搜索页可选的音源，顺序即界面默认展示顺序 */
        val searchable: List<SourceType> = listOf(QQ, WY, KG, KW)
    }
}
