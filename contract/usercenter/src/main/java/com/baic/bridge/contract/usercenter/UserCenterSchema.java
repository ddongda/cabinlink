package com.baic.bridge.contract.usercenter;

/**
 * 用户中心/账号契约（Bridge SDK §9）。模块标识全链路一致：
 * Gradle 路径 :contract:usercenter = artifactId bridge-contract-usercenter = topic 前缀 usercenter. = MODULE。
 */
public final class UserCenterSchema {
    public static final String MODULE = "usercenter";
    public static final int    VERSION = 1;

    // request：消费方主动拉取（首屏兜底）
    public static final String GET_ACCOUNT   = "usercenter.getAccount";
    // event：用户中心 provider 发布（登录/登出/切换/资料变更）
    public static final String ACCOUNT_STATE = "usercenter.account.state";

    // payload 字段常量（双端共用，防拼写漂移）；userId 是账号字段，非模块名
    public static final String K_LOGIN_STATE = "loginState"; // 0 未登录 1 已登录
    public static final String K_USER_ID     = "userId";
    public static final String K_NICKNAME    = "nickname";
    public static final String K_AVATAR      = "avatar";
    public static final String K_VIP_LEVEL   = "vipLevel";

    private UserCenterSchema() {}
}
