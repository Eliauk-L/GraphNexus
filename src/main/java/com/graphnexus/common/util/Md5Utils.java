package com.graphnexus.common.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MD5 计算工具 — 从 FileServiceImpl 提取为公共方法。
 *
 * <p>PDF 上传和 CSV 上传均使用 MD5 做内容判重。见 DESIGN §0.5.2。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
public final class Md5Utils {

    private Md5Utils() {
        // 工具类，禁止实例化
    }

    /**
     * 计算字节数组的 MD5（32 位小写十六进制）。
     *
     * @param data 原始字节数组
     * @return 32 位十六进制小写 MD5 字符串
     * @throws IllegalStateException 如果 MD5 算法不可用（JDK 环境异常）
     */
    public static String computeMd5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }
}