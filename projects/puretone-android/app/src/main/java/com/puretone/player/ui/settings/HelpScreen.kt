package com.puretone.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 应用内使用说明。
 *
 * 面向普通用户：不是一个功能清单，而是「我该从哪里开始、下一步做什么」。
 * 文案与功能同步维护——加了新功能记得回来补一段。
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
        TextButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) {
            Text("← 返回")
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "使用说明",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "PureTone 是一个本地优先的音乐播放器：歌存在你手机里，在线搜索只是帮你把歌找回来。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Section("一、把歌放进来")
        Paragraph("本地导入：音乐库右上角「导入」，选手机里的音频文件即可。导入后歌词栏显示「本地导入 · 无歌词」。")
        Paragraph("在线搜索下载：切到「搜索」页，选音源（QQ音乐 / 网易云 / 酷狗 / 酷我），输入歌名或歌手。搜到后可以先试听，确认是要的版本再下载。")
        Paragraph("下载去处：点「下载」会问你要放进「音乐库」还是某个歌单；没有歌单时会直接进音乐库。已经下载过的歌再选歌单，会直接加进那个歌单。")

        Section("二、试听与下载的区别")
        Paragraph("试听：不占存储、不进音乐库，用独立的播放器，不会打断你正在听的歌。点每行左边的圆钮播放/暂停；点中间的歌名进歌词页。")
        Paragraph("下载：把音频存进手机。下载完成后这首歌就属于音乐库，断网也能听。")
        Paragraph("只有 30 秒？部分歌曲受平台版权限制，源站只提供试听片段。下载时和试听页都会提示；换一个音源（比如网易云换成酷狗）常常能找到完整版。")

        Section("三、播放与歌词")
        Paragraph("播放：在音乐库或歌单里点任意一首开始播放，底部会出现迷你播放条，点它展开完整播放页。")
        Paragraph("歌词：播放页中间是逐行歌词，会跟着进度滚动。点任意一句歌词可以跳到那句；点住上下拖动可以回看，松手几秒后自动跟随。")
        Paragraph("进度条：按住圆点左右拖动，松手跳到对应位置。")
        Paragraph("播放模式：播放页左下角的按钮在「顺序 → 单曲循环 → 随机」之间切换。随机是加权随机——很久没放到的歌概率会变高，刚听过的不会马上重复。")
        Paragraph("播放队列：播放页右下角查看接下来要放的歌。")

        Section("四、歌单")
        Paragraph("新建歌单：音乐库页右上角「新建歌单」。")
        Paragraph("往歌单加歌：进歌单点「添加歌曲」从音乐库里挑；或者在搜索结果里下载时直接选这个歌单。")
        Paragraph("删除：歌单可以整个删除；歌曲只从歌单移除，不会删掉文件和音乐库里的歌。")

        Section("五、设置")
        Paragraph("外观：深色 / 浅色 / 跟随系统。")
        Paragraph("歌词字号：小 / 标准 / 大，直接点选。")
        Paragraph("时间偏移：歌词和声音对不上时用这里的滑杆整体微调，范围 ±2 秒，往右调歌词往后延、往左调往前移，点「复位」回到 0。")
        Paragraph("版本：在「关于」里可以看到当前版本号。")

        Section("六、关于更新")
        Paragraph("每次打开 App 都会检查一次新版本；有更新会弹窗提示，点「去更新」跳到下载页。点「稍后」只关掉这一次，下次打开还会提醒。")

        Section("七、常见问题")
        Paragraph("搜不到歌：换个音源再搜，不同平台的曲库不一样。")
        Paragraph("下载失败 / 提示没有可用资源：这首在该音源下架或受限，换音源或换一首同名的版本。")
        Paragraph("歌词对不上：先用设置里的「时间偏移」整体微调；如果这首歌词本身就错了，目前只能删掉重新导入正确的文件。")
        Paragraph("在线功能显示「只读」：密钥额度用完了，本地播放和歌单不受影响，等新的密钥下发会自动恢复。")

        Spacer(Modifier.height(12.dp))
        Text(
            text = "说明文档会随版本更新。遇到这里没写到的问题，欢迎直接反馈。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(36.dp))
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(22.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Paragraph(text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.25f,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}
