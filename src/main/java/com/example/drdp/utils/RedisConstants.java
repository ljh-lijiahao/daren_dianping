package com.example.drdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_PREFIX = "login:code:";
    public static final Long LOGIN_CODE_TTL = 5L;
    public static final String LOGIN_USER_PREFIX = "login:token:";
    public static final Long LOGIN_USER_TTL = 30L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final String CACHE_SHOP_PREFIX = "cache:shop:";
    public static final Long CACHE_SHOP_TTL = 30L;

    public static final String SHOP_TYPE_NAME = "shopType";
    public static final Long SHOP_TYPE_TTL = 30L;

    public static final String LOCK_SHOP_PREFIX = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String LOCK_ORDER_PREFIX = "lock:order:";
    public static final Long LOCK_ORDER_TTL = 5L;

    public static final String SECKILL_STOCK_PREFIX = "seckill:stock:";
    public static final String BLOG_LIKED_PREFIX = "blog:liked:";
    public static final String FOLLOW_PREFIX = "follow:";
    public static final String FEED_PREFIX = "feed:";
    public static final String SHOP_GEO_PREFIX = "shop:geo:";
    public static final String USER_SIGN_PREFIX = "sign:";
}
