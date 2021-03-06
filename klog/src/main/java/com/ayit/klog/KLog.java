package com.ayit.klog;


import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;


import com.ayit.klog.klog.BaseLog;
import com.ayit.klog.klog.FileLog;
import com.ayit.klog.klog.JsonLog;
import com.ayit.klog.klog.XmlLog;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

/**
 * This is a Log tool，with this you can the following
 * <ol>
 * <li>use KLog.d(),you could print whether the method execute,and the default tag is current class's name</li>
 * <li>use KLog.d(msg),you could print log as before,and you could location the method with a click in Android Studio Logcat</li>
 * <li>use KLog.json(),you could print json string with well format automatic</li>
 * </ol>
 *
 * @author zhaokaiqiang
 * github https://github.com/ZhaoKaiQiang/KLog
 * 15/11/17 扩展功能，添加对文件的支持
 * 15/11/18 扩展功能，增加对XML的支持，修复BUG
 * 15/12/8  扩展功能，添加对任意参数的支持
 * 15/12/11 扩展功能，增加对无限长字符串支持
 * 16/6/13  扩展功能，添加对自定义全局Tag的支持,修复内部类不能点击跳转的BUG
 * 16/6/15  扩展功能，添加不能关闭的KLog.debug(),用于发布版本的Log打印,优化部分代码
 * 16/6/20  扩展功能，添加堆栈跟踪功能KLog.trace()
 */
public final class KLog {
    public static FileWriter fileWriter;
    public static DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
    public static DateFormat dataFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
    private static long lastCheckFileTime = 0;
    /**
     * 将log 写入到文件中
     * @param msg
     */
    public static void writeFile(final String msg) {
        if (!IS_WRITE_LOG){
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (KLog.class){
                    long currentTime = System.currentTimeMillis();
                    if (currentTime-lastCheckFileTime>60*60*1000){
                        newLogFile(currentTime);
                        clearTimeOutFile(currentTime);
                        lastCheckFileTime = currentTime;
                    }else{
                        if (fileWriter != null) {
                            try {
                                fileWriter.write(msg);
                                fileWriter.write("\r\n");
                                fileWriter.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

            }
        }).start();

//        Observable.just(1).map(
//                new Function<Integer, Integer>() {
//                    @Override
//                    public Integer apply(Integer value) throws Exception {
////                        synchronized (KLog.class){
//                            long currentTime = System.currentTimeMillis();
//                            if (currentTime-lastCheckFileTime>60*60*1000){
//                                newLogFile(currentTime);
//                                clearTimeOutFile(currentTime);
//                                lastCheckFileTime = currentTime;
//                            }else{
//                                if (fileWriter != null) {
//                                    try {
//                                        fileWriter.write(msg);
//                                        fileWriter.write("\r\n");
//                                        fileWriter.flush();
//                                    } catch (IOException e) {
//                                        e.printStackTrace();
//                                    }
//                                }
////                            }
//                        }
//
//
//                        return 1;
//                    }
//                })
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribe(new Consumer<Integer>() {
//                    @Override
//                    public void accept(Integer value) throws Exception {
//
//                    }
//                });


    }


    public static final String LINE_SEPARATOR = System.getProperty("line.separator");
    public static final String NULL_TIPS = "Log with null object";

    private static final String DEFAULT_MESSAGE = "execute";
    private static final String PARAM = "Param";
    private static final String NULL = "null";
    private static final String TAG_DEFAULT = "klog";

    private static final int LOGFILES_HOLDING_DAYS_DEFAULT = 7;
    private static final String SUFFIX = ".java";

    public static final int JSON_INDENT = 4;

    public static final int V = 0x1;
    public static final int D = 0x2;
    public static final int I = 0x3;
    public static final int W = 0x4;
    public static final int E = 0x5;
    public static final int A = 0x6;

    private static final int JSON = 0x7;
    private static final int XML = 0x8;

    private static final int STACK_TRACE_INDEX_5 = 5;
    private static final int STACK_TRACE_INDEX_4 = 4;

    private static String mGlobalTag;
    private static boolean mIsGlobalTagEmpty = true;
    private static boolean IS_SHOW_LOG = true;
    private static boolean IS_WRITE_LOG = false;

    private static String mGlobalLogFilesPercent;

    private static int mGlobalHoldingDays;

    public static String currentLogDay;

    public static void init(Context context,boolean isShowLog) {
        String path = context.getExternalCacheDir()+"/klogs/";
        init(context,isShowLog,TAG_DEFAULT);
    }

    public static void init(Context context,boolean isShowLog, @Nullable String tag) {
        String path = context.getExternalCacheDir()+"/klogs/";
        init(context,isShowLog,false, tag, path, LOGFILES_HOLDING_DAYS_DEFAULT);
    }

    public static void init(Context context,boolean isShowLog, boolean isWriteLog,@Nullable String tag,String logFilesDir, int holdingDays) {
        IS_SHOW_LOG = isShowLog;
        IS_WRITE_LOG = isWriteLog;
        mGlobalTag = tag;
        mIsGlobalTagEmpty = TextUtils.isEmpty(mGlobalTag);
        mGlobalLogFilesPercent = logFilesDir;
        mGlobalHoldingDays = holdingDays;
        Log.d(tag,logFilesDir);

        //判断log 文件夹路径 是否为空
        if (!TextUtils.isEmpty(logFilesDir)&& IS_WRITE_LOG) {
            //检查权限

            //检查权限（NEED_PERMISSION）是否被授权 PackageManager.PERMISSION_GRANTED表示同意授权
            if (Build.VERSION.SDK_INT >= 23 && ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e(tag,"Manifest.permission.WRITE_EXTERNAL_STORAGE IS PERMISSION_DENIED");
            }else{
//                File tagFileParent = new File(logFilesPercent);
//                if (tagFileParent.exists()) {
//                    for (File file : tagFileParent.listFiles()) {
//                        String fileName = file.getName();
//                        if (fileName.startsWith("klog_") && fileName.endsWith(".log")) {
//                            try {
//                                Date parse = dataFormatter.parse(fileName.split("_")[1].split("\\.")[0]);
//                                if (System.currentTimeMillis() - parse.getTime() >= holdingDays * 24 * 60 * 60 * 1000) {
//                                    boolean delete = file.delete();
//                                }
//                            } catch (ParseException e) {
//                                e.printStackTrace();
//                            }
//                        }
//                    }
//                }
                if (IS_WRITE_LOG){
                    long currentTime = System.currentTimeMillis();
                    clearTimeOutFile(currentTime);
                    newLogFile(currentTime);
                }

//                File tagFile = new File(logFilesPercent, "klog_" + dataFormatter.format(new Date()) + ".log");
//                if (!tagFile.getParentFile().exists()) {
//                    tagFile.getParentFile().mkdirs();
//                }
//                if (!tagFile.exists()) {
//                    try {
//                        tagFile.createNewFile();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//                try {
//                    fileWriter = new FileWriter(tagFile, true);
//                    fileWriter.write("\r\n");
//                    fileWriter.write("\r\n");
//                    fileWriter.write("klog_start:" + formatter.format(new Date()) + "------------------------------------------------------------------");
//                    fileWriter.write("\r\n");
//                } catch (IOException e) {
//                    e.printStackTrace();
//                    fileWriter = null;
//                }
            }
        }
    }

    private static void newLogFile(long currentTime){
        if (fileWriter == null){
            currentLogDay = dataFormatter.format(new Date(currentTime));
            File tagFile = new File(mGlobalLogFilesPercent, "klog_" + currentLogDay+ ".log");
            if (!tagFile.getParentFile().exists()) {
                tagFile.getParentFile().mkdirs();
            }
            if (!tagFile.exists()) {
                try {
                    tagFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                fileWriter = new FileWriter(tagFile, true);
                fileWriter.write("\r\n");
                fileWriter.write("\r\n");
                fileWriter.write("klog_start:" + formatter.format(new Date(currentTime)) + "------------------------------------------------------------------");
                fileWriter.write("\r\n");
            } catch (IOException e) {
                e.printStackTrace();
                fileWriter = null;
            }
        }else{
            String logDay = dataFormatter.format(new Date(currentTime));
            if (!currentLogDay.endsWith(logDay)){
                if (fileWriter!=null){
                    try{
                        fileWriter.flush();
                        fileWriter.close();
                        fileWriter = null;
                    }catch (Exception e){
                        try{
                            fileWriter.close();
                        }catch (Exception E){
                            fileWriter = null;
                        }
                    }
                    newLogFile(currentTime);
                }
            }
        }
    }

    private static void clearTimeOutFile(long currentTime){
        File tagFileParent = new File(mGlobalLogFilesPercent);
        if (tagFileParent.exists()) {
            for (File file : tagFileParent.listFiles()) {
                String fileName = file.getName();
                if (fileName.startsWith("klog_") && fileName.endsWith(".log")) {
                    try {
                        Date parse = dataFormatter.parse(fileName.split("_")[1].split("\\.")[0]);
                        if (currentTime - parse.getTime() >= mGlobalHoldingDays * 24 * 60 * 60 * 1000) {
                            boolean delete = file.delete();
                        }
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public static void v() {
        printLog(V, null, DEFAULT_MESSAGE);
    }

    public static void v(Object msg) {
        printLog(V, null, msg);
    }

    public static void v(String tag, Object... objects) {
        printLog(V, tag, objects);
    }

    public static void d() {
        printLog(D, null, DEFAULT_MESSAGE);
    }

    public static void d(Object msg) {
        printLog(D, null, msg);
    }

    public static void d(String tag, Object... objects) {
        printLog(D, tag, objects);
    }

    public static void i() {
        printLog(I, null, DEFAULT_MESSAGE);
    }

    public static void i(Object msg) {
        printLog(I, null, msg);
    }

    public static void i(String tag, Object... objects) {
        printLog(I, tag, objects);
    }

    public static void w() {
        printLog(W, null, DEFAULT_MESSAGE);
    }

    public static void w(Object msg) {
        printLog(W, null, msg);
    }

    public static void w(String tag, Object... objects) {
        printLog(W, tag, objects);
    }

    public static void e() {
        printLog(E, null, DEFAULT_MESSAGE);
    }

    public static void e(Object msg) {
        printLog(E, null, msg);
    }

    public static void e(String tag, Object... objects) {
        printLog(E, tag, objects);
    }

    public static void a() {
        printLog(A, null, DEFAULT_MESSAGE);
    }

    public static void a(Object msg) {
        printLog(A, null, msg);
    }

    public static void a(String tag, Object... objects) {
        printLog(A, tag, objects);
    }

    public static void json(String jsonFormat) {
        printLog(JSON, null, jsonFormat);
    }

    public static void json(String tag, String jsonFormat) {
        printLog(JSON, tag, jsonFormat);
    }

    public static void xml(String xml) {
        printLog(XML, null, xml);
    }

    public static void xml(String tag, String xml) {
        printLog(XML, tag, xml);
    }

    public static void file(File targetDirectory, Object msg) {
        printFile(null, targetDirectory, null, msg);
    }

    public static void file(String tag, File targetDirectory, Object msg) {
        printFile(tag, targetDirectory, null, msg);
    }

    public static void file(String tag, File targetDirectory, String fileName, Object msg) {
        printFile(tag, targetDirectory, fileName, msg);
    }

    public static void debug() {
        printDebug(null, DEFAULT_MESSAGE);
    }

    public static void debug(Object msg) {
        printDebug(null, msg);
    }

    public static void debug(String tag, Object... objects) {
        printDebug(tag, objects);
    }

    public static void trace() {
        printStackTrace();
    }

    private static void printStackTrace() {

        if (!IS_SHOW_LOG) {
            return;
        }

        Throwable tr = new Throwable();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        tr.printStackTrace(pw);
        pw.flush();
        String message = sw.toString();

        String traceString[] = message.split("\\n\\t");
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        for (String trace : traceString) {
            if (trace.contains("at com.socks.library.KLog")) {
                continue;
            }
            sb.append(trace).append("\n");
        }
        String[] contents = wrapperContent(STACK_TRACE_INDEX_4, null, sb.toString());
        String tag = contents[0];
        String msg = contents[1];
        String headString = contents[2];
        BaseLog.printDefault(D, tag, headString + msg);
    }

    private static void printLog(int type, String tagStr, Object... objects) {
        if (!IS_SHOW_LOG) {
            return;
        }

        String[] contents = wrapperContent(STACK_TRACE_INDEX_5, tagStr, objects);
        String tag = contents[0];
        String msg = contents[1];
        String headString = contents[2];

        switch (type) {
            case V:
            case D:
            case I:
            case W:
            case E:
            case A:
                BaseLog.printDefault(type, tag, headString + msg);
                break;
            case JSON:
                JsonLog.printJson(tag, msg, headString);
                break;
            case XML:
                XmlLog.printXml(tag, msg, headString);
                break;
        }
        if (onLogListener!=null){
            for (Object o:objects){
                onLogListener.onLog(o.toString());
            }

        }
    }

    private static OnLogListener onLogListener;

    public static void setOnLogListener(OnLogListener l){
        onLogListener = l;
    }
    public interface OnLogListener{
        void onLog(String log);
    }

    private static void printDebug(String tagStr, Object... objects) {
        String[] contents = wrapperContent(STACK_TRACE_INDEX_5, tagStr, objects);
        String tag = contents[0];
        String msg = contents[1];
        String headString = contents[2];
        BaseLog.printDefault(D, tag, headString + msg);
    }


    private static void printFile(String tagStr, File targetDirectory, String fileName, Object objectMsg) {

        if (!IS_SHOW_LOG) {
            return;
        }

        String[] contents = wrapperContent(STACK_TRACE_INDEX_5, tagStr, objectMsg);
        String tag = contents[0];
        String msg = contents[1];
        String headString = contents[2];

        FileLog.printFile(tag, targetDirectory, fileName, headString, msg);
    }

    private static String[] wrapperContent(int stackTraceIndex, String tagStr, Object... objects) {

        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

        StackTraceElement targetElement = stackTrace[stackTraceIndex];
        String className = targetElement.getClassName();
        String[] classNameInfo = className.split("\\.");
        if (classNameInfo.length > 0) {
            className = classNameInfo[classNameInfo.length - 1] + SUFFIX;
        }

        if (className.contains("$")) {
            className = className.split("\\$")[0] + SUFFIX;
        }

        String methodName = targetElement.getMethodName();
        int lineNumber = targetElement.getLineNumber();

        if (lineNumber < 0) {
            lineNumber = 0;
        }

        String tag = (tagStr == null ? className : tagStr);

        if (mIsGlobalTagEmpty && TextUtils.isEmpty(tag)) {
            tag = TAG_DEFAULT;
        } else if (!mIsGlobalTagEmpty) {
            tag = mGlobalTag;
        }

        String msg = (objects == null) ? NULL_TIPS : getObjectsString(objects);
        String headString = "[ (" + className + ":" + lineNumber + ")#" + methodName + " ] ";

        return new String[]{tag, msg, headString};
    }

    private static String getObjectsString(Object... objects) {

        if (objects.length > 1) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("\n");
            for (int i = 0; i < objects.length; i++) {
                Object object = objects[i];
                if (object == null) {
                    stringBuilder.append(PARAM).append("[").append(i).append("]").append(" = ").append(NULL).append("\n");
                } else {
                    stringBuilder.append(PARAM).append("[").append(i).append("]").append(" = ").append(object.toString()).append("\n");
                }
            }
            return stringBuilder.toString();
        } else {
            Object object = objects[0];
            return object == null ? NULL : object.toString();
        }
    }

}
