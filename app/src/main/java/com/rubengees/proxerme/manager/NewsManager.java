package com.rubengees.proxerme.manager;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.support.annotation.NonNull;

import com.rubengees.proxerme.entity.News;
import com.rubengees.proxerme.receiver.BootReceiver;
import com.rubengees.proxerme.receiver.NewsReceiver;

import java.util.List;

/**
 * TODO: Describe Class
 *
 * @author Ruben Gees
 */
public class NewsManager {

    public static final int OFFSET_NOT_CALCULABLE = -2;
    public static final int OFFSET_TOO_LARGE = -1;
    public static final int NEWS_ON_PAGE = 15;

    private static final int INTERVAL_HOUR = 60 * 60 * 1000;

    private static NewsManager INSTANCE;

    private Context context;

    private int lastId;
    private int newNews = 0;

    private NewsManager(@NonNull Context context) {
        this.context = context;

        loadId();
        loadNewNews();
    }

    public static NewsManager getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            INSTANCE = new NewsManager(context);
        }
        return INSTANCE;
    }

    public static int calculateOffsetFromStart(@NonNull List<News> list, @NonNull News last) {
        return calculateOffsetFromStart(list, last.getId());
    }

    public static int calculateOffsetFromEnd(@NonNull List<News> list, @NonNull News first) {
        return calculateOffsetFromEnd(list, first.getId());
    }

    public static int calculateOffsetFromStart(@NonNull List<News> list, int id) {
        if (list.isEmpty()) {
            return OFFSET_NOT_CALCULABLE;
        } else {
            for (int i = 0; i < list.size() || i < NEWS_ON_PAGE; i++) {
                if (id == list.get(i).getId()) {
                    return i;
                }
            }

            return OFFSET_TOO_LARGE;
        }
    }

    public static int calculateOffsetFromEnd(@NonNull List<News> list, int id) {
        if (list.isEmpty()) {
            return OFFSET_NOT_CALCULABLE;
        } else {
            int lastSearchableIndex = list.size() - NEWS_ON_PAGE;

            for (int i = list.size() - 1; i >= 0 && i >= lastSearchableIndex; i--) {
                if (id == list.get(i).getId()) {
                    return (list.size() - 1) - i;
                }
            }

            return OFFSET_TOO_LARGE;
        }
    }

    public int getLastId() {
        return lastId;
    }

    public void setLastId(int id) {
        lastId = id;

        saveId();
    }

    public int getNewNews() {
        return newNews;
    }

    public void setNewNews(int newNews) {
        this.newNews = newNews;

        saveNewNews();
    }

    public void retrieveNewsLater() {
        cancelNewsRetrieval();
        if (isNewsRetrievalEnabled()) {
            AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, NewsReceiver.class);
            PendingIntent alarmIntent = PendingIntent.getBroadcast(context, 0, intent, 0);

            alarmMgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + INTERVAL_HOUR, INTERVAL_HOUR, alarmIntent);

            ComponentName receiver = new ComponentName(context, BootReceiver.class);
            PackageManager pm = context.getPackageManager();

            pm.setComponentEnabledSetting(receiver,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }

    public void cancelNewsRetrieval() {
        ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE))
                .cancel(PendingIntent.getBroadcast(context, 0,
                        new Intent(context, NewsReceiver.class), 0));
        ComponentName receiver = new ComponentName(context, BootReceiver.class);
        PackageManager pm = context.getPackageManager();

        pm.setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    public boolean isNewsRetrievalEnabled() {
        return PreferenceManager.areNotificationsEnabled(context);
    }

    private void saveId() {
        PreferenceManager.setLastId(context, lastId);
    }

    private void loadId() {
        lastId = PreferenceManager.getLastId(context);
    }

    private void saveNewNews() {
        PreferenceManager.setNewNews(context, newNews);
    }

    private void loadNewNews() {
        newNews = PreferenceManager.getNewNews(context);
    }
}
