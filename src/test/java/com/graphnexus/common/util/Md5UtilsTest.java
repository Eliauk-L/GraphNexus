package com.graphnexus.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Md5Utils 单元测试。
 *
 * @author Jay
 * @date 2026/06/15
 */
@DisplayName("Md5Utils MD5 计算测试")
class Md5UtilsTest {

    @Test
    @DisplayName("相同内容产生相同 MD5")
    void shouldProduceSameMd5ForSameContent() {
        byte[] data1 = "Hello, GraphNexus!".getBytes(StandardCharsets.UTF_8);
        byte[] data2 = "Hello, GraphNexus!".getBytes(StandardCharsets.UTF_8);

        assertEquals(Md5Utils.computeMd5(data1), Md5Utils.computeMd5(data2));
    }

    @Test
    @DisplayName("不同内容产生不同 MD5")
    void shouldProduceDifferentMd5ForDifferentContent() {
        byte[] data1 = "CSV file content A".getBytes(StandardCharsets.UTF_8);
        byte[] data2 = "CSV file content B".getBytes(StandardCharsets.UTF_8);

        assertNotEquals(Md5Utils.computeMd5(data1), Md5Utils.computeMd5(data2));
    }

    @Test
    @DisplayName("MD5 为 32 位十六进制小写")
    void shouldReturn32CharLowercaseHex() {
        String md5 = Md5Utils.computeMd5("test".getBytes(StandardCharsets.UTF_8));

        assertEquals(32, md5.length());
        assertTrue(md5.matches("^[0-9a-f]{32}$"), "MD5 应为 32 位小写十六进制: " + md5);
    }

    @Test
    @DisplayName("空字节数组 → 正确 MD5")
    void shouldHandleEmptyByteArray() {
        String md5 = Md5Utils.computeMd5(new byte[0]);

        assertEquals(32, md5.length());
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", md5);
    }

    @Test
    @DisplayName("边界: 大数组（1MB）→ 计算成功")
    void shouldHandleLargeByteArray() {
        byte[] large = new byte[1024 * 1024]; // 1MB of zeros
        String md5 = Md5Utils.computeMd5(large);

        assertEquals(32, md5.length());
        assertNotNull(md5);
    }
}