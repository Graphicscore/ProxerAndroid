package me.proxer.app.news

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.ContextCompat
import me.proxer.app.MainActivity
import me.proxer.app.R
import me.proxer.app.util.NotificationUtils.NEWS_CHANNEL
import me.proxer.app.util.extension.getQuantityString
import me.proxer.app.util.wrapper.MaterialDrawerWrapper.DrawerItem
import me.proxer.library.entity.notifications.NewsArticle

/**
 * @author Ruben Gees
 */
object NewsNotifications {

    private const val ID = 1357913213

    fun showOrUpdate(context: Context, news: Collection<NewsArticle>) {
        val notification = buildNewsNotification(context, news)

        when (notification) {
            null -> NotificationManagerCompat.from(context).cancel(ID)
            else -> NotificationManagerCompat.from(context).notify(ID, notification)
        }
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(ID)

    private fun buildNewsNotification(context: Context, news: Collection<NewsArticle>): Notification? {
        if (news.isEmpty()) {
            return null
        }

        val builder = NotificationCompat.Builder(context, NEWS_CHANNEL)
        val newsAmount = context.getQuantityString(R.plurals.notification_news_amount, news.size)
        val style: NotificationCompat.Style
        val title: String
        val content: String

        when (news.size) {
            1 -> {
                val current = news.first()

                title = current.subject.trim()
                content = current.description.trim()

                style = NotificationCompat.BigTextStyle(builder)
                        .bigText(content)
                        .setBigContentTitle(title)
                        .setSummaryText(newsAmount)
            }
            else -> {
                title = context.getString(R.string.notification_news_title)
                content = newsAmount

                style = NotificationCompat.InboxStyle().apply {
                    news.forEach {
                        addLine(it.subject)
                    }

                    setBigContentTitle(context.getString(R.string.notification_news_title))
                    setSummaryText(newsAmount)
                }
            }
        }

        return builder.setAutoCancel(true)
                .setSmallIcon(R.drawable.ic_stat_proxer)
                .setContentTitle(title)
                .setContentText(content)
                .setDeleteIntent(NewsNotificationDeletionReceiver.getPendingIntent(context))
                .setContentIntent(PendingIntent.getActivity(context, 0,
                        MainActivity.getSectionIntent(context, DrawerItem.NEWS),
                        PendingIntent.FLAG_UPDATE_CURRENT))
                .addAction(R.drawable.ic_stat_check, context.getString(R.string.notification_news_read_action),
                        NewsNotificationReadReceiver.getPendingIntent(context))
                .setColor(ContextCompat.getColor(context, R.color.primary))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOnlyAlertOnce(true)
                .setNumber(news.size)
                .setStyle(style)
                .build()
    }
}