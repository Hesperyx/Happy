package cn.hxy.happy.hook.notification

// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.MessagingStyle
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import cn.hxy.happy.utils.log.LogUtils
import cn.hxy.happy.utils.qq.HostInfo

internal class MessagingNotificationBuilder(
    private val disableConversationSubChannel: () -> Boolean,
    private val disableBubble: () -> Boolean
) {

    private val historyMessage = HashMap<String, MessagingStyle>()
    private val avatarHelper = QQAvatarHelper()

    private val context: Context get() = HostInfo.hostContext

    fun clearHistory() {
        historyMessage.clear()
    }

    fun createNotification(
        recentInfo: Any,
        shortcutIntent: Intent,
        oldNotification: Notification
    ): Notification? {
        val info = recentInfo.toRecentContactSnapshot()
        if (!info.isSupportedChat()) return null

        val content = info.extractContent() ?: return null
        val senderName = info.senderName() ?: return null
        val senderUin = info.senderUin.takeIf { it != 0L } ?: return null
        val notificationIcon = iconFromNotification(oldNotification)
        val senderIcon = if (info.chatType == CHAT_TYPE_GROUP) {
            avatarHelper.getAvatar(senderUin.toString()) ?: appIcon()
        } else {
            notificationIcon ?: appIcon()
        }
        val target = resolveConversationTarget(info, senderName, senderUin, senderIcon, oldNotification)
        val shortcut = buildShortcut(target, shortcutIntent)
        val messageStyle = getOrCreateMessageStyle(target)
        messageStyle.addMessage(createMessage(content, senderName, senderUin, senderIcon, oldNotification))

        val builder = NotificationCompat.Builder(context, oldNotification)
            .setContentTitle(target.mainName)
            .setContentText(content)
            .setLargeIcon(null as Bitmap?)
            .setStyle(messageStyle)
            .setShortcutInfo(shortcut)

        if (disableConversationSubChannel()) {
            builder.setChannelId(target.channel.channelId())
        } else {
            builder.setChannelId(ensureConversationChannel(target, shortcut).id)
        }

        applyBubble(builder, shortcut)
        return builder.build()
    }

    fun clearRedundantConversationChannels() {
        runCatching {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            val baseChannelIds = NotifyChannel.entries.map { it.channelId() }.toSet()
            notificationManager.notificationChannels
                .filter { it.group == CHANNEL_GROUP_ID && it.id !in baseChannelIds }
                .forEach { notificationManager.deleteNotificationChannel(it.id) }
        }.onFailure {
            LogUtils.e("MessagingStyleNotification clear redundant sub channels failed", it)
        }
    }

    private fun resolveConversationTarget(
        info: RecentContactSnapshot,
        senderName: String,
        senderUin: Long,
        senderIcon: IconCompat,
        oldNotification: Notification
    ): ConversationTarget {
        if (info.chatType == CHAT_TYPE_GROUP) {
            return ConversationTarget(
                mainUin = info.peerUin,
                mainName = info.peerName.orEmpty(),
                mainIcon = iconFromNotification(oldNotification),
                channel = NotifyChannel.GROUP,
                isGroupConversation = true
            )
        }

        return ConversationTarget(
            mainUin = senderUin,
            mainName = senderName,
            mainIcon = senderIcon,
            channel = if (info.isSpecialCare()) NotifyChannel.FRIEND_SPECIAL else NotifyChannel.FRIEND,
            isGroupConversation = false
        )
    }

    private fun getOrCreateMessageStyle(target: ConversationTarget): MessagingStyle {
        historyMessage[target.historyKey]?.let { return it }
        return MessagingStyle(
            Person.Builder()
                .setName(target.mainName)
                .setIcon(target.mainIcon)
                .setKey(target.mainUin.toString())
                .build()
        ).also {
            it.setConversationTitle(target.mainName)
            it.setGroupConversation(target.isGroupConversation)
            historyMessage[target.historyKey] = it
        }
    }

    private fun createMessage(
        content: String,
        senderName: String,
        senderUin: Long,
        senderIcon: IconCompat,
        oldNotification: Notification
    ): MessagingStyle.Message {
        val sender = Person.Builder()
            .setName(senderName)
            .setIcon(senderIcon)
            .setKey(senderUin.toString())
            .build()
        return MessagingStyle.Message(content, oldNotification.`when`, sender)
    }

    private fun buildShortcut(target: ConversationTarget, intent: Intent): ShortcutInfoCompat {
        val shortcutIntent = Intent(intent).apply {
            if (action == null) action = Intent.ACTION_VIEW
        }
        val shortcut = ShortcutInfoCompat.Builder(context, shortcutId(target))
            .setLongLived(true)
            .setIntent(shortcutIntent)
            .setShortLabel(target.mainName)
            .setIcon(target.mainIcon ?: appIcon())
            .setLocusId(LocusIdCompat(shortcutId(target)))
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        return shortcut
    }

    private fun shortcutId(target: ConversationTarget): String {
        return if (target.isGroupConversation) "group_${target.mainUin}" else "private_${target.mainUin}"
    }

    private fun ensureConversationChannel(
        target: ConversationTarget,
        shortcut: ShortcutInfoCompat
    ): NotificationChannel {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.getNotificationChannel(target.mainUin.toString())?.let { return it }

        val channel = NotificationChannel(
            target.mainUin.toString(),
            target.mainName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            group = CHANNEL_GROUP_ID
            description = "来自 ${target.mainName} 的消息"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setConversationId(target.channel.channelId(), shortcut.id)
            }
        }
        notificationManager.createNotificationChannel(channel)
        return channel
    }

    private fun applyBubble(builder: NotificationCompat.Builder, shortcut: ShortcutInfoCompat) {
        if (disableBubble() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        builder.setBubbleMetadata(NotificationCompat.BubbleMetadata.Builder(shortcut.id).build())
    }

    private fun iconFromNotification(notification: Notification): IconCompat? {
        // 注意：Notification.largeIcon 是公开的 Bitmap 字段（已废弃），会遮蔽 getLargeIcon()，
        // 必须显式调 getter 才能拿到 Icon。
        return runCatching {
            notification.getLargeIcon()?.let { IconCompat.createFromIcon(context, it) }
        }.getOrNull()
    }

    private fun appIcon(): IconCompat {
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        return IconCompat.createWithBitmap(drawable.toBitmap())
    }
}
