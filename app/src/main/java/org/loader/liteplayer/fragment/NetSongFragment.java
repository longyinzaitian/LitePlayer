package org.loader.liteplayer.fragment;

import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.PopupWindow;
import android.widget.PopupWindow.OnDismissListener;
import android.widget.Toast;

import org.loader.liteplayer.R;
import org.loader.liteplayer.activity.MainActivity;
import org.loader.liteplayer.adapter.NetSongPagerAdapter;
import org.loader.liteplayer.adapter.SearchResultAdapter;
import org.loader.liteplayer.application.BaseApplication;
import org.loader.liteplayer.engine.GetDownloadInfo;
import org.loader.liteplayer.engine.GetDownloadInfo.OnDownloadGetListener;
import org.loader.liteplayer.engine.SearchMusic;
import org.loader.liteplayer.engine.SongsRecommendation;
import org.loader.liteplayer.pojo.HotSong;
import org.loader.liteplayer.pojo.Item;
import org.loader.liteplayer.pojo.SearchResult;
import org.loader.liteplayer.utils.Constants;
import org.loader.liteplayer.utils.MusicUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 2015年8月15日 16:34:37
 * 博文地址：http://blog.csdn.net/u010156024
 * @author longyinzaitian
 */
public class NetSongFragment extends BaseFragment
                    implements OnClickListener {

    private static NetSongFragment instance;

    private ViewPager mViewPager;
    private View mPopView;
    private TabLayout mTabLayout;

    private PopupWindow mPopupWindow;

    private SearchResultAdapter mSearchResultAdapter;
    private ArrayList<HotSong> mResultData = new ArrayList<>();
    private ArrayList<Item> items;

    private int mPage = 0;
    private int mLastItem;
    private boolean hasMoreData = true;
    /**
     * 该类是android系统中的下载工具类，非常好用
     */
    private DownloadManager mDownloadManager;

    private boolean isFirstShown = true;

    public static NetSongFragment getInstance(){
        if (instance == null){
            instance = new NetSongFragment();
        }

        return instance;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.search_music_layout;
    }

    @Override
    protected void bindView(View view) {
        mViewPager = view.findViewById(R.id.net_song_view_pager);
        mTabLayout = view.findViewById(R.id.net_song_tab_layout);

        Toolbar toolbar = view.findViewById(R.id.tool_bar);
        ((MainActivity)getActivity()).setSupportActionBar(toolbar);

        mDownloadManager = (DownloadManager) BaseApplication.getContext()
                .getSystemService(Context.DOWNLOAD_SERVICE);
    }

    @Override
    protected void bindListener() {
        mSearchResultAdapter.setOnItemClickListener(mResultItemClickListener);

    }

    @Override
    protected void loadData() {
        initItem();
        initAdapter();
    }

    private void initItem(){
        items = new ArrayList<>();
        items.add(new Item("内地", 5));
        items.add(new Item("港台", 6));
        items.add(new Item("韩国", 16));
        items.add(new Item("日本", 17));
        items.add(new Item("热歌", 26));
        items.add(new Item("新歌", 27));
        for (Item item : items){
            mTabLayout.newTab().setText(item.getTitle());
        }

        mTabLayout.setSelectedTabIndicatorColor(getResources().getColor(R.color.main));
        mTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int pos = tab.getPosition();
                mViewPager.setCurrentItem(pos);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });
    }

    private void initAdapter(){
        List<Fragment> fragments = new ArrayList<>();
        for (Item item : items){
            SongListFragment songListFragment = new SongListFragment();
            Bundle bundle = new Bundle();
            bundle.putInt("id", item.getId());
            songListFragment.setArguments(bundle);
            fragments.add(songListFragment);
        }

        NetSongPagerAdapter adapter = new NetSongPagerAdapter(getChildFragmentManager(), fragments);
        mViewPager.setAdapter(adapter);
        mViewPager.setOffscreenPageLimit(items.size());
        mViewPager.setCurrentItem(0);
    }

    /**
     * 该方法实现的功能是： 当该Fragment不可见时，isVisibleToUser=false
     * 当该Fragment可见时，isVisibleToUser=true
     * 该方法由系统调用，重写该方法实现用户可见当前Fragment时再进行数据的加载
     */
    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        // 当Fragment可见且是第一次加载时
        if (isVisibleToUser && isFirstShown) {

            isFirstShown = false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        SongsRecommendation.getInstance().cleanWork();
    }

    /**
     * 列表中每一列的点击时间监听器
     */
    private OnItemClickListener mResultItemClickListener
                            = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                long id) {
            if (position >= mResultData.size() || position < 0) {
                return;
            }

            showDownloadDialog(position);
        }
    };
    /**
     * 底部对话框
     * @param position 位置
     */
    private void showDownloadDialog(final int position) {

        if (mPopupWindow == null) {
            mPopView = View.inflate(BaseApplication.getContext(), R.layout.download_pop_layout,
                    null);

            mPopupWindow = new PopupWindow(mPopView, LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT);
            mPopupWindow.setBackgroundDrawable(new ColorDrawable(
                    Color.TRANSPARENT));
            mPopupWindow.setAnimationStyle(R.style.popwin_anim);
            mPopupWindow.setFocusable(true);
            mPopupWindow.setOnDismissListener(new OnDismissListener() {
                @Override
                public void onDismiss() {
                }
            });
        }

        //下载按钮点击时间
        mPopView.findViewById(R.id.tv_pop_download).setOnClickListener(
            new OnClickListener() {
                @Override
                public void onClick(View v) {
                    GetDownloadInfo
                            .getInstance()
                            .setListener(mDownloadUrlListener)
                            .parse(position,
                                    mResultData.get(position).getUrl());
                    dismissDialog();
                }
            });

        mPopView.findViewById(R.id.tv_pop_cancel).setOnClickListener(
            new OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismissDialog();
                }
            });

        //设置对话框展示的位置
//        mPopupWindow.showAtLocation(BaseApplication.getContext().getWindow().getDecorView(),
//                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
    }

    private void dismissDialog() {
        if (mPopupWindow != null && mPopupWindow.isShowing()) {
            mPopupWindow.dismiss();
        }
    }

    private OnDownloadGetListener mDownloadUrlListener =
            new OnDownloadGetListener() {
        @Override
        public void onMusic(int position, String url) {
            if (position == -1 || url == null) {
                Toast.makeText(BaseApplication.getContext(), "歌曲链接失效",
                        Toast.LENGTH_SHORT).show();
                return;
            }

        }

        @Override
        public void onLrc(int position, String url) {
            if (url == null){
                return;
            }

        }
    };

    private RecyclerView.OnScrollListener mListViewScrollListener =
        new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (mLastItem == mSearchResultAdapter.getItemCount() && hasMoreData
                        && newState == OnScrollListener.SCROLL_STATE_IDLE) {

                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                // 计算可见列表的最后一条的列表是不是最后一个
//                    mLastItem = firstVisibleItem + visibleItemCount;
            }
    };

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        default:
            break;
        }
    }
}