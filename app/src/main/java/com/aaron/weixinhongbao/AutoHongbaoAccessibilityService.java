package com.aaron.weixinhongbao;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 微信自动抢红包后台服务
 * QQ的自动获取红包原理类似，但可能复杂点，例如口令红包
 */
public class AutoHongbaoAccessibilityService extends AccessibilityService {
    private static final String QQ_HONGBAO_KEY_WORD = "[QQ红包]";
    private static final String QQ_MAIN_UI_PACKAGE_NAME = "com.tencent.mobileqq.activity.ChatActivity";

    private static final String NOTIFICATION_WEIXIN_HONGBAO_KEY_WORD = "[微信红包]";
    private static final String WEIXIN_HONGBAO_KEY_WORD = "微信红包";
    private static final String WEIXIN_MAIN_UI_PACKAGE_NAME = "com.tencent.mm.ui.LauncherUI";
    private static final String WEIXIN_HONGBAO_UI_PACKAGE_NAME = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI";
    private static final String WEIXIN_HONGBAO_DETAIL_UI_PACKAGE_NAME = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI";
    private static final String OPEN_HONGBAO_KEY_ID = "com.tencent.mm:id/be_";
    private static final String HONGBAO_DETAIL_CLOSE_KEY_ID = "com.tencent.mm:id/gr";
    private static final String HONGBAO_DETAIL_MONEY_KEY_ID = "com.tencent.mm:id/bbe";
    //id/bbe 显示钱的TextView
    //id/a27 EditText 文本编辑
    //id/a20 发送按钮

    private List<AccessibilityNodeInfo> hongbaoList;//红包列表
    private List<AccessibilityNodeInfo> oldHongbaoList;//已经开过的红包

    /**
     * 当启动服务的时候就会被调用
     */
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        hongbaoList = new ArrayList<>();
        oldHongbaoList = new ArrayList<>();

    }

    /**
     * 监听窗口变化的回调
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int eventType = event.getEventType();
        switch (eventType) {
            //当通知栏发生改变时
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED://通知状态变化
                openSofeUI(event);
                break;
            //当窗口的状态发生改变时
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED://界面状态变化
                String className = event.getClassName().toString();
                Log.e("onAccessibilityEvent", "界面状态变化："+className);
                if (className.equals(WEIXIN_MAIN_UI_PACKAGE_NAME)) {
                    //点击最后一个红包
                    Log.e("onAccessibilityEvent", "进入微信页面");
                    clickLastHongbao();
                } else if (className.equals(WEIXIN_HONGBAO_UI_PACKAGE_NAME)) {
                    //开红包
                    Log.e("onAccessibilityEvent", "开红包");
                    show("开红包");
                    simulateClick(OPEN_HONGBAO_KEY_ID);
                } else if (className.equals(WEIXIN_HONGBAO_DETAIL_UI_PACKAGE_NAME)) {
                    //获取抢到的金额
                    simulateClick(HONGBAO_DETAIL_MONEY_KEY_ID);
                    Log.e("demo", "退出红包");
                    //退出红包
                    simulateClick(HONGBAO_DETAIL_CLOSE_KEY_ID);
                }
                break;
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:// 从微信主界面进入聊天界面
//                String className2 = event.getClassName().toString();
//                Log.e("onAccessibilityEvent", className2);
                break;
        }
    }

    /**
     * 打开通知关联的软件界面
     */
    private void openSofeUI(AccessibilityEvent event) {
        List<CharSequence> texts = event.getText();
        if (texts.isEmpty()) {
            return;
        }
        for (CharSequence text : texts) {
            String content = text.toString();
            if (!content.contains(NOTIFICATION_WEIXIN_HONGBAO_KEY_WORD)) {
                continue;
            }
            //检查屏幕是否亮起
            wakeUpAndUnlock(getApplicationContext());
            //模拟打开通知栏消息，即打开微信
            if (event.getParcelableData() != null &&
                    event.getParcelableData() instanceof Notification) {
                Notification notification = (Notification) event.getParcelableData();
                PendingIntent pendingIntent = notification.contentIntent;
                try {
                    pendingIntent.send();
                    Log.e("demo", "进入微信");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 通过ID获取控件，并进行模拟点击
     *
     * @param clickId
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void simulateClick(String clickId) {
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo == null) {
            return;
        }
        List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByViewId(clickId);
        Log.e("simulateClick",list.size()+"");
        for (AccessibilityNodeInfo item : list) {
            if (clickId.equals(HONGBAO_DETAIL_MONEY_KEY_ID)) {
                show("抢了红包:" + item.getText() + "元");
            } else {
                item.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        }
    }

    /**
     * 获取最后一个红包，并进行模拟点击
     * （因为使用AccessibilityService的限制，只能获取显示界面的控件元素信息
     * ，导致不在显示区域的红包无法获取，所以这里只点击最新的红包，之前的存在漏点的情况）
     */
    private void clickLastHongbao() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        searchHongbao(rootNode);
        if (hongbaoList == null || hongbaoList.size() <= 0) return;
        Log.e("clickLastHongbao", "hongbaoList:" + hongbaoList.size());
        AccessibilityNodeInfo accessibilityNodeInfo = hongbaoList.get(hongbaoList.size() - 1);
        if (accessibilityNodeInfo != null) {
//            hongbaoList.clear();
            remove(accessibilityNodeInfo);
            accessibilityNodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
    }

    private void add(AccessibilityNodeInfo info){
        if(oldHongbaoList.contains(info)){
            Log.e("add", "oldHongbaoList有:" + info.hashCode());
            return;
        }
        Log.e("add", "oldHongbaoList没有" + info.hashCode());
        hongbaoList.add(info);
    }

    private void remove(AccessibilityNodeInfo info){
//        boolean is = hongbaoList.remove(info);
        hongbaoList.clear();
//        Log.e("remove", info.hashCode()+"从hongbaoList删除：" + is);
        oldHongbaoList.add(info);
    }

    /**
     * 循环遍历视图节点，获取包含红包关键字的节点并保存在List中
     *
     * @param info
     */
    public void searchHongbao(AccessibilityNodeInfo info) {
        if (info.getChildCount() == 0) {
            if (info.getText() == null) {
                return;
            }
            if (WEIXIN_HONGBAO_KEY_WORD.equals(info.getText().toString())) {
                if(info.isClickable()){
//                    hongbaoList.add(info);
                    add(info);
                    return;
                }
                AccessibilityNodeInfo parent = info.getParent();
                //循环向上获取可点击的控件，并保存到List中
                while (parent != null) {
                    if (parent.isClickable()) {
//                        hongbaoList.add(parent);
                        Log.e("searchHongbao", "要添加的对象："+parent.hashCode());
                        add(parent);
                        break;
                    }
                    parent = parent.getParent();
                }
            }
        } else {
            for (int i = 0; i < info.getChildCount(); i++) {
                if (info.getChild(i) != null) {
//                    Log.e("searchHongbao",info.getClassName().toString());
                    searchHongbao(info.getChild(i));
                }
            }
        }
    }

    /**
     * 中断服务的回调
     */
    @Override
    public void onInterrupt() {
        show("自动抢红包功能关闭");
    }

    /**
     * 显示Toast
     *
     * @param msg
     */
    private void show(String msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    /**
     * 检查屏幕是否亮着并且唤醒屏幕
     *
     * @param context
     */
    private void wakeUpAndUnlock(Context context) {
        // 获取电源管理器对象
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (!pm.isScreenOn()) {
            KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            KeyguardManager.KeyguardLock kl = km.newKeyguardLock("unLock");
            // 解锁
            kl.disableKeyguard();
            // 获取PowerManager.WakeLock对象,后面的参数|表示同时传入两个值,最后的是LogCat里用的Tag
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_DIM_WAKE_LOCK, "bright");
            // 点亮屏幕
            wl.acquire();
            // 释放
            wl.release();
        }
    }
}
