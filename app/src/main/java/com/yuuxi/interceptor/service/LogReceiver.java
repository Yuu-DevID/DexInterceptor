package com.yuuxi.interceptor.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.yuuxi.interceptor.util.Const;

public class LogReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Const.ACTION_LOG_ENTRY.equals(intent.getAction())) {
            Intent forward = new Intent(Const.ACTION_LOG_ENTRY);
            forward.putExtra(Const.EXTRA_LOG_JSON, intent.getStringExtra(Const.EXTRA_LOG_JSON));
            forward.setPackage(Const.MY_PACKAGE);
            context.sendBroadcast(forward);
        }
    }
}
