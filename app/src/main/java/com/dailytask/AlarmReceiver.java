package com.dailytask;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "DailyTask_Alarm_Channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        String taskTitle = intent.getStringExtra("TASK_TITLE");
        String taskMember = intent.getStringExtra("TASK_MEMBER");

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "家庭任務提醒",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("用於 DailyTask 家庭任務到期時的自動提醒");
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ 家庭任務時間到！")
                .setContentText("負責人 " + taskMember + " 該執行任務囉：" + taskTitle)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }
}