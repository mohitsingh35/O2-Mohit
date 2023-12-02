package com.ncs.o2.HelperClassesimport android.app.NotificationChannelimport android.app.NotificationManagerimport android.app.PendingIntentimport android.content.Contextimport android.content.Intentimport android.graphics.Colorimport android.media.RingtoneManagerimport androidx.core.app.NotificationCompatimport androidx.core.app.NotificationCompat.GROUP_ALERT_SUMMARYimport com.ncs.o2.Constants.NotificationTypeimport com.ncs.o2.Domain.Models.FCMNotificationimport com.ncs.o2.Rimport com.ncs.o2.UI.MainActivityimport com.ncs.o2.UI.Notifications.Requests.RequestActivity/*File : NotificationUtil.kt -> com.ncs.o2.HelperClassesDescription : Helper for notifications Author : Alok Ranjan (VC uname : apple)Link : https://github.com/arpitmxFrom : Bitpolarity x Noshbae (@Project : O2 Android)Creation : 2:59 am on 01/06/23Todo >Tasks CLEAN CODE : Tasks BUG FIXES : Tasks FEATURE MUST HAVE : Tasks FUTURE ADDITION : */object NotificationBuilderUtil {    fun showNotification(context: Context, notification: FCMNotification) {        val channelId = "fcm1"        val channelName = "FCM Channel"        val notificationId = System.currentTimeMillis().toInt()        val notificationBuilder: NotificationCompat.Builder        // Create a notification builder        when (notification.notificationType) {            NotificationType.TASK_REQUEST_RECIEVED_NOTIFICATION -> {                val intent = Intent(context, RequestActivity::class.java)                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK                val pendingIntent = PendingIntent.getActivity(                    context,                    0,                    intent,                    PendingIntent.FLAG_IMMUTABLE                )                notificationBuilder = NotificationCompat.Builder(context, channelId)                    .setSmallIcon(R.drawable.round_task_alt_24)                    .setContentTitle(notification.title)                    .setContentText(notification.message)                    .setPriority(NotificationCompat.PRIORITY_HIGH)                    .setDefaults(NotificationCompat.DEFAULT_ALL)                    .setAutoCancel(false)                    .setColor(Color.GREEN)                    .setStyle(                        NotificationCompat.BigTextStyle()                            .bigText(notification.message)                    )                    .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)) // Set default notification sound                    .setContentIntent(pendingIntent)            }            NotificationType.REQUEST_FAILED_NOTIFICATION -> {                val intent = Intent(context, RequestActivity::class.java)                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK                val pendingIntent = PendingIntent.getActivity(                    context,                    0,                    intent,                    PendingIntent.FLAG_IMMUTABLE                )                notificationBuilder = NotificationCompat.Builder(context, channelId)                    .setContentTitle(notification.title)                    .setContentText(notification.message)                    .setSmallIcon(R.drawable.baseline_refresh_24)                    .setColor(Color.RED)                    .setAutoCancel(false)                    .setStyle(                        NotificationCompat.BigTextStyle()                            .bigText(notification.message)                    )                    .setPriority(NotificationCompat.PRIORITY_HIGH)                    .setDefaults(NotificationCompat.DEFAULT_ALL)                    .setContentIntent(pendingIntent)            }            NotificationType.TASK_CREATION_NOTIFICATION -> {                val intent = Intent(context, RequestActivity::class.java)                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK                val pendingIntent = PendingIntent.getActivity(                    context,                    0,                    intent,                    PendingIntent.FLAG_IMMUTABLE                )                notificationBuilder = NotificationCompat.Builder(context, channelId)                    .setContentTitle(notification.title)                    .setContentText(notification.message)                    .setSmallIcon(R.drawable.baseline_refresh_24)                    .setColor(Color.RED)                    .setAutoCancel(false)                    .setStyle(                        NotificationCompat.BigTextStyle()                            .bigText(notification.message)                    )                    .setContentIntent(pendingIntent)            }            NotificationType.TASK_COMMENT_MENTION_NOTIFICATION -> {                val intent = Intent(context, RequestActivity::class.java)                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK                val pendingIntent = PendingIntent.getActivity(                    context,                    0,                    intent,                    PendingIntent.FLAG_IMMUTABLE                )                notificationBuilder = NotificationCompat.Builder(context, channelId)                    .setContentTitle(notification.title)                    .setContentText(notification.message)                    .setSmallIcon(R.drawable.baseline_refresh_24)                    .setColor(Color.RED)                    .setAutoCancel(false)                    .setStyle(                        NotificationCompat.BigTextStyle()                            .bigText(notification.message)                    )                    .setContentIntent(pendingIntent)            }            NotificationType.TASK_COMMENT_NOTIFICATION -> {                val inboxStyle: NotificationCompat.InboxStyle = NotificationCompat.InboxStyle()                //Creating intent                val intent = Intent(context, MainActivity::class.java)                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK                val pendingIntent = PendingIntent.getActivity(                    context,                    0,                    intent,                    PendingIntent.FLAG_IMMUTABLE                )                // Notification builder                notificationBuilder = NotificationCompat.Builder(context, channelId)                    .setContentTitle(notification.title)                    .setContentText(notification.taskID)                    .setSmallIcon(R.drawable.notifoxygen)                    .setColor(context.resources.getColor(R.color.primary))                    .setAutoCancel(false)                    .setStyle(NotificationCompat.BigTextStyle().bigText(notification.message))                    .setContentIntent(pendingIntent)                    .setDefaults(NotificationCompat.DEFAULT_ALL)                    .setPriority(NotificationCompat.PRIORITY_HIGH)//                    .setGroup("CommentGroup")//                    .setGroupSummary(true)//                    .setGroupAlertBehavior(GROUP_ALERT_SUMMARY)            }        }        // Create a notification manager        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager        val channel = NotificationChannel(            channelId,            channelName,            NotificationManager.IMPORTANCE_HIGH,        ).apply {            enableLights(true)            lightColor = Color.RED            enableVibration(true)        }        // Create the notification channel        notificationManager.createNotificationChannel(channel)        // Show the notification        notificationManager.notify(notificationId, notificationBuilder.build())    }}