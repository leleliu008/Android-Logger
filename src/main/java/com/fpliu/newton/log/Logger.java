package com.fpliu.newton.log;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import okio.Okio;

/**
 * 调试日志
 *
 * @author 792793182@qq.com 2015-06-11
 */
public final class Logger {

    /**
     * 调试日志的开关，一般Debug版本中打开，便于开发人员观察日志，Release版本中关闭
     */
    public static final boolean ENABLED = true;

    /**
     * TAG的前缀，便于过滤
     */
    public static final String PREFIX = Logger.class.getSimpleName();

    public static Context appContext;

    public static int BLOCKING_SIZE = 100;

    private static final BlockingQueue<Item> blockingQueue = new ArrayBlockingQueue<>(BLOCKING_SIZE);

    private static final Thread worker = new Thread("logger") {
        @Override
        public void run() {
            super.run();

            while (true) {
                try {
                    Item item = blockingQueue.take();
                    syncSaveFile(item.logFile, item.content);
                } catch (InterruptedException e) {
                    e(PREFIX, "run()", e);
                }
            }
        }
    };

    static {
        worker.start();
    }

    private Logger() {
    }


    public static int v(String tag, String msg) {
        return ENABLED ? Log.v(PREFIX + tag, "" + msg) : 0;
    }

    public static int d(String tag, String msg) {
        //华为的这款手机只能打印information信息
        if ("GEM-703L".equals(Build.MODEL)
                || "H60-L11".equals(Build.MODEL)) {
            return i(tag, msg);
        }
        return ENABLED ? Log.d(PREFIX + tag, "" + msg) : 0;
    }

    public static int i(String tag, String msg) {
        return ENABLED ? Log.i(PREFIX + tag, "" + msg) : 0;
    }

    public static int w(String tag, String msg) {
        return ENABLED ? Log.w(PREFIX + tag, "" + msg) : 0;
    }

    public static int e(String tag, String msg) {
        return ENABLED ? Log.e(PREFIX + tag, "" + msg) : 0;
    }

    public static int e(String tag, String msg, Throwable throwable) {
        return ENABLED ? Log.e(PREFIX + tag, msg, throwable) : 0;
    }

    /**
     * 保存异常堆栈信息
     *
     * @param throwable
     * @return
     */
    public static String getExceptionTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        } else {
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            throwable.printStackTrace(printWriter);
            return stringWriter.toString();
        }
    }

    /**
     * 同步保存异常堆栈信息到文件
     */
    public static void syncSaveFile(File logFile, Throwable throwable) {
        if (logFile == null || throwable == null) {
            return;
        }

        String content = getEnvironmentInfo(appContext).append(getExceptionTrace(throwable)).toString();
        syncSaveFile(logFile, content);
    }

    /**
     * 同步保存异常堆栈信息到文件
     */
    public static void asyncSaveFile(File logFile, String content) {
        if (logFile == null || TextUtils.isEmpty(content)) {
            return;
        }

        blockingQueue.add(new Item(logFile, content));
    }

    /**
     * 同步保存异常堆栈信息到文件
     */
    public static void syncSaveFile(File logFile, String content) {
        if (logFile == null || TextUtils.isEmpty(content)) {
            return;
        }

        try {
            Okio.buffer(Okio.sink(logFile)).writeUtf8(content);
        } catch (IOException e) {
            e(PREFIX, "syncSaveFile()", e);
        }
    }

    /**
     * 组装环境信息
     */
    private static StringBuilder getEnvironmentInfo(Context context) {
        StringBuilder info = new StringBuilder();
        info.append("time = ").append(getCurrentFormatDateTime()).append("\n");
        info.append("versionName = ").append(getMyVersionName(context)).append("\n");
        info.append(getAllInfo().toString()).append("\n");
        return info;
    }

    private static StringBuilder getAllInfo() {
        StringBuilder info = new StringBuilder();

        Class<?> clazz = null;
        try {
            clazz = Class.forName("android.os.Build");
        } catch (ClassNotFoundException ex) {
            try {
                clazz = Class.forName("miui.os.Build");
            } catch (ClassNotFoundException e) {
                e(PREFIX, "getAllInfo()", e);
            }
        }

        if (clazz != null) {
            try {
                Field[] fields = clazz.getDeclaredFields();
                for (Field field : fields) {
                    field.setAccessible(true);

                    info.append(field.getName());
                    info.append(" = ");
                    info.append(field.get(null));
                    info.append("\n");
                }
            } catch (Exception e) {
                e(PREFIX, "getAllInfo()", e);
            }
        }
        return info;
    }

    /**
     * 获取当前时间
     *
     * @return
     */
    private static String getCurrentFormatDateTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINESE).format(System.currentTimeMillis());
    }

    /**
     * 获取版本名称
     */
    private static String getMyVersionName(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName;
        } catch (Exception e) {
            e(PREFIX, "getMyVersionName()", e);
        }
        return "";
    }

    private static class Item {

        public File logFile;

        public String content;

        Item(File logFile, String content) {
            this.logFile = logFile;
            this.content = content;
        }
    }
}
