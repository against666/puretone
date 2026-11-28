package com.puretone.player.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * 应用内使用说明。
 *
 * 排版取向：像一张排版干净的纸，不是卡片墙。
 * 编号小节 + 细分隔线 + 「术语 · 一句话」的紧凑条目，扫一眼就能找到要的那条。
 * 文案与功能同步维护——加了新功能记得回来补一条。
 */
@Composable
fun HelpScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.padding(top = 4.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text("← 返回")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "使用说明",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "歌存在手机里，在线搜索帮你把歌找回来。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        Chapter(1, "把歌放进来") {
            Point("本地导入", "音乐库右上角「导入」，选手机里的音频文件。")
            Point("在线下载", "搜索页选音源，输入歌名，试听确认后下载。")
            Point("下载去处", "弹窗里选「音乐库」或某个歌单；已在库的歌选歌单会直接加入。")
        }

        Chapter(2, "试听与下载") {
            Point("试听", "不占存储、不进音乐库，独立播放器，不会打断正在听的歌。")
            Point("操作", "点行左侧圆钮播放/暂停，点中间歌名进歌词页。")
            Point("下载", "音频存进手机，之后断网也能听。")
            Point("只有 30 秒", "平台版权限制，源站只给试听片段；下载和试听页都会提示，换音源常能找到完整版。")
        }

        Chapter(3, "播放与歌词") {
            Point("播放", "在音乐库或歌单点任意一首开始；底部迷你条点开是完整播放页。")
            Point("歌词", "跟随进度滚动。点任意一句跳到那句；上下拖动回看，松手几秒自动跟随。")
            Point("进度条", "按住圆点左右拖动，松手跳到对应位置。")
            Point("播放模式", "顺序 → 单曲循环 → 随机。随机为加权：久未放到的歌概率更高，刚听过的不易重复。")
            Point("播放队列", "播放页右下角，查看接下来要放的歌。")
        }

        Chapter(4, "歌单") {
            Point("新建", "音乐库页右上角「新建歌单」。")
            Point("加歌", "进歌单点「添加歌曲」；或在搜索结果下载时直接选这个歌单。")
            Point("删除", "删歌单不会删文件和音乐库里的歌。")
        }

        Chapter(5, "设置") {
            Point("外观", "深色 / 浅色 / 跟随系统。")
            Point("歌词字号", "小 / 标准 / 大。")
            Point("时间偏移", "歌词对不上时整体微调，±2 秒；往右歌词延后，点「复位」归零。")
            Point("更新", "每次打开都会检查。点「立即更新」在应用内直接下载安装，不用去浏览器。")
        }

        Chapter(6, "常见问题") {
            Point("搜不到歌", "换个音源再搜，各平台曲库不同。")
            Point("下载失败", "这首在该音源下架或受限，换音源或换同名版本。")
            Point("歌词不对", "先用「时间偏移」微调；歌词本身错了只能删掉重新导入。")
            Point("显示只读", "密钥额度用完，本地播放与歌单不受影响，新密钥下发后自动恢复。")
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "本文档随版本更新。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(36.dp))
    }
}

@Composable
private fun Chapter(
    index: Int,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        thickness = 1.dp,
    )
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = "%02d".format(index),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
    Spacer(Modifier.height(10.dp))
    Column(content = content)
    Spacer(Modifier.height(18.dp))
}

/** 术语 + 一句话。术语加重在前，正文用次要色，扫读时先抓术语 */
@Composable
private fun Point(term: String, body: String) {
    Text(
        text = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                ),
            ) {
                append(term)
            }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                append("　$body")
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.5f,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}
