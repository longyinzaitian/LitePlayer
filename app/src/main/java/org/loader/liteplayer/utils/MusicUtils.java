package org.loader.liteplayer.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

import org.loader.liteplayer.application.App;
import org.loader.liteplayer.pojo.Music;

import android.os.Environment;

/**
 * 2015年8月15日 16:34:37
 * 博文地址：http://blog.csdn.net/u010156024
 * @author longyinzaitian
 */
public class MusicUtils {
    private static final String TAG = "MusicUtils";

    /**存放歌曲列表*/
    public static ArrayList<Music> sMusicList = new ArrayList<>();

    public static void initMusicList() {
        // 获取歌曲列表
        sMusicList.clear();
        sMusicList.addAll(LocalMusicUtils.queryMusic());
    }

    /**
     * 获取应用程序使用的本地目录
     * @return String
     */
    private static String getAppLocalDir() {
        String dir;

        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_UNMOUNTED)) {
            dir = Environment.getExternalStorageDirectory() + File.separator
                    + "liteplayer" + File.separator;
        } else {
            dir = App.getContext().getFilesDir() + File.separator + "liteplayer" + File.separator;
        }

        return mkDir(dir);
    }

    /**
     * 获取音乐存放目录
     * @return String
     */
    public static String getMusicDir() {
        String musicDir = getAppLocalDir() + "music" + File.separator;
        return mkDir(musicDir);
    }

    /**
     * 获取歌词存放目录
     * 
     * @return String
     */
    public static String getLrcDir() {
        String lrcDir = getAppLocalDir() + "lrc" + File.separator;
        return mkDir(lrcDir);
    }

    /**
     * 创建文件夹
     * @param dir String
     * @return String
     */
    private static String mkDir(String dir) {
        File f = new File(dir);
        if (!f.exists()) {
            if(f.mkdirs()){
                return dir;
            }
            return null;
        }
        LogUtil.l(TAG, "file.path="+f.getAbsolutePath());

        return dir;
    }

    public static String getSongTimeLength(long t){
        long minute = t/60;
        long  seconds = t%60;
        StringBuilder builder = new StringBuilder();
        if (minute > 9){
            builder.append(minute).append(":");
        }else{
            builder.append("0").append(minute).append(":");
        }

        if (seconds > 9){
            builder.append(seconds);
        }else {
            builder.append("0").append(seconds);
        }

        return builder.toString();
    }

    public static String getFileLength(long size){
        size = size / 1000;
        float d = size / 1000.0f;
        return String.format(Locale.CHINA, "%.2fMB", d);
    }
}
