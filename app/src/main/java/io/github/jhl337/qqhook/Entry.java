package io.github.jhl337.qqhook;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Entry implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam param) throws Throwable {
        try {
            String pn = param.packageName;
            ClassLoader cl = param.classLoader;
            if ("com.tencent.mobileqq".equals(pn)) {
                hookChannelProxyExt(cl);
                hookMsfCore(cl);
            }
        } catch (Throwable e) {
            XposedBridge.log("QQHook Error: " + e.getMessage());
        }
    }

    private void hookChannelProxyExt(final ClassLoader cl) {
        try {
            Class<?> clazz = XposedHelpers.findClass("com.tencent.mobileqq.channel.ChannelProxyExt", cl);
            XC_MethodHook sendMessageHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String cmd = (String) param.args[0];
                    long callbackId = (long) param.args[2];

                    if (cmd != null && (cmd.startsWith("trpc.o3.report.") || cmd.startsWith("trpc.o3.mobile_security."))) {
                        XposedBridge.log("QQHook: report detected, cmd: " + cmd);
                        try {
                            Class<?> cmClass = XposedHelpers.findClass("com.tencent.mobileqq.channel.ChannelManager", cl);
                            Object cmInstance = XposedHelpers.callStaticMethod(cmClass, "getInstance");
                            try {
                                XposedHelpers.callMethod(cmInstance, "onNativeReceive",
                                    cmd, new byte[0], true, 1000, callbackId);
                            } catch (Throwable e) {
                                XposedBridge.log("QQHook: call onNativeReceive failed: " + e.getMessage() + " try fallback.");
                                XposedHelpers.callMethod(cmInstance, "onNativeReceive",
                                    cmd, new byte[0], 1, 1000, callbackId);
                            }
                        } catch (Throwable e) {
                            XposedBridge.log("QQHook: on hookChannelProxyExt error: " + e.getMessage());
                        }
                        param.setResult(null);
                    }
                }
            };
            try {
                XposedHelpers.findAndHookMethod(
                    "com.tencent.mobileqq.channel.ChannelProxyExt",
                    cl,
                    "sendMessage",
                    String.class, byte[].class, long.class,
                    sendMessageHook
                );
            } catch (Throwable t) {
                XposedBridge.log("QQHook: sendMessage not found, try fallback.");
                try {
                    XposedHelpers.findAndHookMethod(
                        "com.tencent.mobileqq.channel.ChannelProxyExt",
                        cl,
                        "sendMessageInner",
                        String.class, byte[].class, long.class,
                        sendMessageHook
                    ); // for newer qqnt
                } catch (Throwable ignored) {
                    XposedBridge.log("QQHook: fallback sendMessageInner not found, hook failed.");
                }
            }

        } catch (Throwable t) {
            XposedBridge.log("QQHook: hookChannelProxyExt failed: " + t.getMessage());
        }
    }

    private void hookMsfCore(final ClassLoader cl) {
        try {
            Class<?> msfCoreClass = XposedHelpers.findClass("com.tencent.mobileqq.msf.core.MsfCore", cl);
            Class<?> toServiceMsgClass = XposedHelpers.findClass("com.tencent.qphone.base.remote.ToServiceMsg", cl);

            XposedHelpers.findAndHookMethod(msfCoreClass, "sendSsoMsg",
                toServiceMsgClass,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object toServiceMsg = param.args[0];
                        if (toServiceMsg == null) return;
                        String serviceCmd = (String) XposedHelpers.callMethod(toServiceMsg, "getServiceCmd");
                        if (serviceCmd != null && (serviceCmd.contains("trpc.o3.report.") || serviceCmd.contains("trpc.o3.mobile_security."))) {
                            XposedBridge.log("QQHook: msf report detected, cmd: " + serviceCmd);
                            int seq = (int) XposedHelpers.callMethod(toServiceMsg, "getRequestSsoSeq");
                            param.setResult(seq);
                        }
                    }
                });
        } catch (Throwable t) {
            XposedBridge.log("QQHook: hookMsfCore failed: " + t.getMessage());
        }
    }
}
