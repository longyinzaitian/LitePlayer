package org.loader.liteplayer.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.widget.RemoteViews;
import android.widget.Toast;

import org.loader.liteplayer.R;
import org.loader.liteplayer.activity.PlayActivity;
import org.loader.liteplayer.utils.Constants;
import org.loader.liteplayer.utils.ImageTools;
import org.loader.liteplayer.utils.LogUtil;
import org.loader.liteplayer.utils.MusicIconLoader;
import org.loader.liteplayer.utils.MusicUtils;
import org.loader.liteplayer.utils.SpUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 2015年8月15日 16:34:37
 * 博文地址：http://blog.csdn.net/u010156024
 * 音乐播放服务 服务中启动了硬件加速器感应器
 * 实现功能：摇动手机自动播放下一首歌曲
 * @author longyinzaitian
 */
public class PlayService extends Service implements
        MediaPlayer.OnCompletionListener {
    private static final String TAG = "PlayService";

    private SensorManager mSensorManager;
    private MediaPlayer mPlayer;
    private OnMusicEventListener mListener;
    /**获取设备电源锁，防止锁屏后服务被停止*/
    private WakeLock mWakeLock = null;
    /**通知栏*/
    private Notification notification;
    /**通知栏布局*/
    private RemoteViews remoteViews;
    private NotificationManager notificationManager;
    /**单线程池*/
    private ExecutorService mProgressUpdatedListener = Executors
            .newSingleThreadExecutor();
    /**当前正在播放*/
    private int mPlayingPosition;
    private boolean isShaking;
    private boolean isFinish = false;
    private boolean isMediaPlayCom = false;

    public class PlayBinder extends Binder {
        public PlayService getService() {
            return PlayService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        mSensorManager.registerListener(mSensorEventListener,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_GAME);
        return new PlayBinder();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate() {
        super.onCreate();
        acquireWakeLock();//获取设备电源锁
        mSensorManager = (SensorManager)
                getSystemService(Context.SENSOR_SERVICE);
        
        MusicUtils.initMusicList();

        mPlayingPosition = (Integer)
                SpUtils.get(Constants.PLAY_POS, 0);

        if(MusicUtils.sMusicList.size()<=0){
            Toast.makeText(getApplicationContext(),
                    "当前手机没有MP3文件", Toast.LENGTH_LONG).show();
        }else{
            if(getPlayingPosition()<0){
                mPlayingPosition=0;
            }
            Uri uri = Uri.parse(MusicUtils.sMusicList.get(
                    getPlayingPosition()).getUri());
            mPlayer = MediaPlayer.create(PlayService.this,uri);
            mPlayer.setOnCompletionListener(this);
        }

        // 开始更新进度的线程
        mProgressUpdatedListener.execute(mPublishProgressRunnable);
        
        //该方法虽然被抛弃过时，但是通用！
        PendingIntent pendingIntent = PendingIntent
                .getActivity(PlayService.this,
                0, new Intent(PlayService.this, PlayActivity.class), 0);

        remoteViews = new RemoteViews(getPackageName(),
                R.layout.play_notification);

        notification = new Notification(R.drawable.ic_launcher,
                "歌曲正在播放", System.currentTimeMillis());
        notification.contentIntent = pendingIntent;
        notification.contentView = remoteViews;
        //标记位，设置通知栏一直存在
        notification.flags =Notification.FLAG_ONGOING_EVENT;
        
        Intent intent = new Intent(PlayService.class.getSimpleName());
        intent.putExtra("BUTTON_NOTI", 1);
        PendingIntent preIntent = PendingIntent.getBroadcast(
                PlayService.this,
                1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(
                R.id.music_play_pre, preIntent);
        
        intent.putExtra("BUTTON_NOTI", 2);
        PendingIntent pauseIntent = PendingIntent.getBroadcast(
                PlayService.this,
                2, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(
                R.id.music_play_pause, pauseIntent);
        
        intent.putExtra("BUTTON_NOTI", 3);
        PendingIntent nextIntent = PendingIntent.getBroadcast
                (PlayService.this,
                3, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(
                R.id.music_play_next, nextIntent);
        
        intent.putExtra("BUTTON_NOTI", 4);
        PendingIntent exit = PendingIntent.getBroadcast(PlayService.this,
                4, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(
                R.id.music_play_notifi_exit, exit);
        
        notificationManager = (NotificationManager)
                getSystemService(NOTIFICATION_SERVICE);
        setRemoteViews();
        
        /*
         * 注册广播接收者
         * 功能：
         * 监听通知栏按钮点击事件 
         */
        IntentFilter filter = new IntentFilter(
                PlayService.class.getSimpleName());
        MyBroadCastReceiver receiver = new MyBroadCastReceiver();
        registerReceiver(receiver, filter);
    }
    
    public void setRemoteViews(){
        LogUtil.l(TAG, "进入——》setRemoteViews()");

        if(getPlayingPosition()>0){
            remoteViews.setTextViewText(R.id.music_name,
                    MusicUtils.sMusicList.get(
                            getPlayingPosition()).getTitle());
            remoteViews.setTextViewText(R.id.music_author,
                    MusicUtils.sMusicList.get(
                            getPlayingPosition()).getArtist());
            Bitmap icon = MusicIconLoader.getInstance().load(
                    MusicUtils.sMusicList.get(
                            getPlayingPosition()).getImage());
            remoteViews.setImageViewBitmap(R.id.music_icon,icon == null
                    ? ImageTools.scaleBitmap(R.drawable.ic_launcher)
                            : ImageTools
                            .scaleBitmap(icon));
            
        }
        if (isPlaying()) {
            remoteViews.setImageViewResource(R.id.music_play_pause,
                    R.drawable.btn_notification_player_stop_normal);
        }else {
            remoteViews.setImageViewResource(R.id.music_play_pause,
                    R.drawable.btn_notification_player_play_normal);
        }
        //通知栏更新
        notificationManager.notify(5, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //让服务前台运行
        startForeground(0, notification);
        return Service.START_STICKY;
    }

    /**
     * 感应器的时间监听器
     */
    private SensorEventListener mSensorEventListener =
            new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (isShaking){
                return;
            }

            if (Sensor.TYPE_ACCELEROMETER == event.sensor.getType()) {
                float[] values = event.values;
                int yuZhi = 8;
                //监听三个方向上的变化，数据变化剧烈，next()方法播放下一首歌曲*/
                if (Math.abs(values[0]) > yuZhi && Math.abs(values[1]) > yuZhi
                        && Math.abs(values[2]) > yuZhi) {
                    isShaking = true;
                    next();
                    // 延迟200毫秒 防止抖动
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            isShaking = false;
                        }
                    }, 200);
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    /**
     * 更新进度的线程
     */
    private Runnable mPublishProgressRunnable = new Runnable() {
        @Override
        public void run() {
            while (!isFinish) {
                if (mPlayer != null && mPlayer.isPlaying()
                        && mListener != null) {
                    mListener.onPublish(mPlayer.getCurrentPosition());
                }
            /*
             * SystemClock.sleep(millis) is a utility function very similar
             * to Thread.sleep(millis), but it ignores InterruptedException.
             * Use this function for delays if you do not use
             * Thread.interrupt(), as it will preserve the interrupted state
             * of the thread. 这种sleep方式不会被Thread.interrupt()所打断
             */SystemClock.sleep(200);
            }
        }
    };

    /**
     * 设置回调
     * 
     * @param l 监听器
     */
    public void setOnMusicEventListener(OnMusicEventListener l) {
        mListener = l;
    }

    /**
     * 播放
     * 
     * @param position
     *            音乐列表播放的位置
     * @return 当前播放的位置
     */
    public int play(int position) {
        LogUtil.l(TAG, "play(int position)方法");

        if(MusicUtils.sMusicList.size()<=0){
            Toast.makeText(getApplicationContext(),
                    "当前手机没有MP3文件", Toast.LENGTH_LONG).show();
            return -1;
        }

        if (position < 0){
            position = 0;
        }

        if (position >= MusicUtils.sMusicList.size()) {
            position = MusicUtils.sMusicList.size() - 1;
        }

        try {
            mPlayer.reset();
            mPlayer.setDataSource(MusicUtils
                    .sMusicList.get(position).getUri());
            mPlayer.prepare();

            start();

            if (mListener != null) {
                mListener.onChange(position);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        mPlayingPosition = position;
        SpUtils.put(Constants.PLAY_POS, mPlayingPosition);
        setRemoteViews();

        return mPlayingPosition;
    }

    /**
     * 继续播放
     * 
     * @return 当前播放的位置 默认为0
     */
    public int resume() {
        if(mPlayer==null){
            return -1;
        }

        if (isPlaying()) {
            return -1;
        }

        mPlayer.start();
        setRemoteViews();

        return mPlayingPosition;
    }

    /**
     * 暂停播放
     * 
     * @return 当前播放的位置
     */
    public int pause() {
        if(MusicUtils.sMusicList.size()==0){
            Toast.makeText(getApplicationContext(),
                    "当前手机没有MP3文件", Toast.LENGTH_LONG).show();
            return -1;
        }

        if (!isPlaying()) {
            return -1;
        }

        mPlayer.pause();
        setRemoteViews();

        return mPlayingPosition;
    }

    /**
     * 下一曲
     * 
     * @return 当前播放的位置
     */
    public int next() {
        if (mPlayingPosition >= MusicUtils.sMusicList.size() - 1) {
            return play(0);
        }

        if (isMediaPlayCom && (Boolean) SpUtils.get(Constants.PLAY_ORDER, false)){
            isMediaPlayCom = false;
            return play(mPlayingPosition);
        }
        return play(mPlayingPosition + 1);
    }

    /**
     * 上一曲
     * 
     * @return 当前播放的位置
     */
    public int pre() {
        if (mPlayingPosition <= 0) {
            return play(MusicUtils.sMusicList.size() - 1);
        }

        return play(mPlayingPosition - 1);
    }

    /**
     * 是否正在播放
     * 
     * @return boolean
     */
    public boolean isPlaying() {
        return null != mPlayer && mPlayer.isPlaying();
    }

    /**
     * 获取正在播放的歌曲在歌曲列表的位置
     * 
     * @return int
     */
    public int getPlayingPosition() {
        return mPlayingPosition;
    }

    /**
     * 获取当前正在播放音乐的总时长
     * 
     * @return int
     */
    public int getDuration() {
        if (!isPlaying()) {
            return 0;
        }

        return mPlayer.getDuration();
    }

    /**
     * 拖放到指定位置进行播放
     * 
     * @param sec int
     */
    public void seek(int sec) {
        if (!isPlaying()) {
            return;
        }

        mPlayer.seekTo(sec);
    }

    /**
     * 开始播放
     */
    private void start() {
        mPlayer.start();
    }

    /**
     * 音乐播放完毕 自动下一曲
     */
    @Override
    public void onCompletion(MediaPlayer mp) {
        isMediaPlayCom = true;
        next();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        LogUtil.l("play service", "unbind");
        mSensorManager.unregisterListener(mSensorEventListener);
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        if (mListener != null){
            mListener.onChange(mPlayingPosition);
        }
    }

    @Override
    public void onDestroy() {
        LogUtil.l(TAG, "PlayService.java的onDestroy()方法调用");

        release();
        stopForeground(true);
        mSensorManager.unregisterListener(mSensorEventListener);

        super.onDestroy();
    }

    /**
     * 服务销毁时，释放各种控件
     */
    private void release() {
        if (!mProgressUpdatedListener.isShutdown()){
            mProgressUpdatedListener.shutdownNow();
        }
        mProgressUpdatedListener = null;

        isFinish = true;
        if (mPublishProgressRunnable != null){
            mPublishProgressRunnable = null;
        }

        //释放设备电源锁
        releaseWakeLock();

        if (mPlayer != null){
            mPlayer.release();
        }
        mPlayer = null;
    }

    /**
     * 申请设备电源锁
     */
    private void acquireWakeLock() {
        LogUtil.l(TAG, "正在申请电源锁");

        if (null == mWakeLock) {
            PowerManager pm = (PowerManager) this
                    .getSystemService(Context.POWER_SERVICE);

            if (pm == null){
                return;
            }

            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK
                    | PowerManager.ON_AFTER_RELEASE, "");

            if (null != mWakeLock) {
                mWakeLock.acquire(5000);
                LogUtil.l(TAG, "电源锁申请成功");
            }
        }
    }

    /**
     * 释放设备电源锁
     */
    private void releaseWakeLock() {
        LogUtil.l(TAG, "正在释放电源锁");

        if (null != mWakeLock) {
            mWakeLock.release();
            mWakeLock = null;
            LogUtil.l(TAG, "电源锁释放成功");
        }
    }

    private class MyBroadCastReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction()!= null && intent.getAction().equals(
                    PlayService.class.getSimpleName())) {
                LogUtil.l(TAG, "MyBroadCastReceiver类——》onReceive（）");
                LogUtil.l(TAG, "button_noti-->"
                +intent.getIntExtra("BUTTON_NOTI", 0));

                switch (intent.getIntExtra("BUTTON_NOTI", 0)) {
                case 1:
                    pre();
                    if (mListener != null) {
                        mListener.onChange(getPlayingPosition());
                    }
                    break;

                case 2:
                    if (isPlaying()) {
                        pause(); // 暂停
                    } else {
                        resume(); // 播放
                    }
                    if (mListener != null) {
                        mListener.onChange(getPlayingPosition());
                    }
                    break;

                case 3:
                    next();
                    if (mListener != null) {
                        mListener.onChange(getPlayingPosition());
                    }
                    break;

                case 4:
                    if (isPlaying()) {
                        pause();
                    }

                    if (mListener != null) {
                        mListener.onChange(getPlayingPosition());
                    }

                    //取消通知栏
                    notificationManager.cancel(5);
                    break;

                default:
                    break;
                }
            }
        }
    }
    
    /**
     * 音乐播放回调接口
     */
    public interface OnMusicEventListener {
        void onPublish(int percent);

        void onChange(int position);
    }
}