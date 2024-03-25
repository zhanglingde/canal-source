package com.taobao.tddl.dbsync.binlog;import java.nio.charset.Charset;/** * string cache * @since  1.1.7 */public class NameCache {    static final NameCacheEntry[]  NAME_CACHE  = new NameCacheEntry[8192];    static final NameCacheEntry2[] NAME_CACHE2 = new NameCacheEntry2[8192];    static final class NameCacheEntry {        final String name;        final long   value;        public NameCacheEntry(String name, long value){            this.name = name;            this.value = value;        }    }    static final class NameCacheEntry2 {        final String name;        final long   value0;        final long   value1;        public NameCacheEntry2(String name, long value0, long value1){            this.name = name;            this.value0 = value0;            this.value1 = value1;        }    }    static String name(byte[] buf, int from, int length, Charset charset) {        long nameValue0 = -1, nameValue1 = -1;        switch (length) {            case 1:                nameValue0 = buf[from];                break;            case 2:                nameValue0 = (buf[from + 1] << 8) + (buf[from] & 0xFF);                break;            case 3:                nameValue0 = (buf[from + 2] << 16) + ((buf[from + 1] & 0xFF) << 8) + (buf[from] & 0xFF);                break;            case 4:                nameValue0 = (buf[from + 3] << 24) + ((buf[from + 2] & 0xFF) << 16) + ((buf[from + 1] & 0xFF) << 8)                             + (buf[from] & 0xFF);                break;            case 5:                nameValue0 = (((long) buf[from + 4]) << 32) + ((buf[from + 3] & 0xFFL) << 24)                             + ((buf[from + 2] & 0xFFL) << 16) + ((buf[from + 1] & 0xFFL) << 8) + (buf[from] & 0xFFL);                break;            case 6:                nameValue0 = (((long) buf[from + 5]) << 40) + ((buf[from + 4] & 0xFFL) << 32)                             + ((buf[from + 3] & 0xFFL) << 24) + ((buf[from + 2] & 0xFFL) << 16)                             + ((buf[from + 1] & 0xFFL) << 8) + (buf[from] & 0xFFL);                break;            case 7:                nameValue0 = (((long) buf[from + 6]) << 48) + ((buf[from + 5] & 0xFFL) << 40)                             + ((buf[from + 4] & 0xFFL) << 32) + ((buf[from + 3] & 0xFFL) << 24)                             + ((buf[from + 2] & 0xFFL) << 16) + ((buf[from + 1] & 0xFFL) << 8) + (buf[from] & 0xFFL);                break;            case 8:                nameValue0 = (((long) buf[from + 7]) << 56) + ((buf[from + 6] & 0xFFL) << 48)                             + ((buf[from + 5] & 0xFFL) << 40) + ((buf[from + 4] & 0xFFL) << 32)                             + ((buf[from + 3] & 0xFFL) << 24) + ((buf[from + 2] & 0xFFL) << 16)                             + ((buf[from + 1] & 0xFFL) << 8) + (buf[from] & 0xFFL);                break;            case 9:                nameValue0 = buf[from];                nameValue1 = (((long) buf[from + 8]) << 56) + ((buf[from + 7] & 0xFFL) << 48)                             + ((buf[from + 6] & 0xFFL) << 40) + ((buf[from + 5] & 0xFFL) << 32)                             + ((buf[from + 4] & 0xFFL) << 24) + ((buf[from + 3] & 0xFFL) << 16)                             + ((buf[from + 2] & 0xFFL) << 8) + (buf[from + 1] & 0xFFL);                break;            case 10:                nameValue0 = (buf[from + 1] << 8) + (buf[from]);                nameValue1 = (((long) buf[from + 9]) << 56) + ((buf[from + 8] & 0xFFL) << 48)                             + ((buf[from + 7] & 0xFFL) << 40) + ((buf[from + 6] & 0xFFL) << 32)                             + ((buf[from + 5] & 0xFFL) << 24) + ((buf[from + 4] & 0xFFL) << 16)                             + ((buf[from + 3] & 0xFFL) << 8) + (buf[from + 2] & 0xFFL);                break;            case 11:                nameValue0 = (buf[from + 2] << 16) + (buf[from + 1] << 8) + (buf[from]);                nameValue1 = (((long) buf[from + 10]) << 56) + ((buf[from + 9] & 0xFFL) << 48)                             + ((buf[from + 8] & 0xFFL) << 40) + ((buf[from + 7] & 0xFFL) << 32)                             + ((buf[from + 6] & 0xFFL) << 24) + ((buf[from + 5] & 0xFFL) << 16)                             + ((buf[from + 4] & 0xFFL) << 8) + (buf[from + 3] & 0xFFL);                break;            case 12:                nameValue0 = (buf[from + 3] << 24) + (buf[from + 2] << 16) + (buf[from + 1] << 8) + (buf[from]);                nameValue1 = (((long) buf[from + 11]) << 56) + ((buf[from + 10] & 0xFFL) << 48)                             + ((buf[from + 9] & 0xFFL) << 40) + ((buf[from + 8] & 0xFFL) << 32)                             + ((buf[from + 7] & 0xFFL) << 24) + ((buf[from + 6] & 0xFFL) << 16)                             + ((buf[from + 5] & 0xFFL) << 8) + (buf[from + 4] & 0xFFL);                break;            case 13:                nameValue0 = (((long) buf[from + 4]) << 32) + (((long) buf[from + 3]) << 24)                             + (((long) buf[from + 2]) << 16) + (((long) buf[from + 1]) << 8) + ((long) buf[from]);                nameValue1 = (((long) buf[from + 12]) << 56) + ((buf[from + 11] & 0xFFL) << 48)                             + ((buf[from + 10] & 0xFFL) << 40) + ((buf[from + 9] & 0xFFL) << 32)                             + ((buf[from + 8] & 0xFFL) << 24) + ((buf[from + 7] & 0xFFL) << 16)                             + ((buf[from + 6] & 0xFFL) << 8) + (buf[from + 5] & 0xFFL);                break;            case 14:                nameValue0 = (((long) buf[from + 5]) << 40) + ((buf[from + 4] & 0xFFL) << 32)                             + ((buf[from + 3] & 0xFFL) << 24) + ((buf[from + 2] & 0xFFL) << 16)                             + ((buf[from + 1] & 0xFFL) << 8) + (buf[from] & 0xFFL);                nameValue1 = (((long) buf[from + 13]) << 56) + ((buf[from + 12] & 0xFFL) << 48)                             + ((buf[from + 11] & 0xFFL) << 40) + ((buf[from + 10] & 0xFFL) << 32)                             + ((buf[from + 9] & 0xFFL) << 24) + ((buf[from + 8] & 0xFFL) << 16)                             + ((buf[from + 7] & 0xFFL) << 8) + (buf[from + 6] & 0xFFL);                break;            case 15:                nameValue0 = (((long) buf[from + 6]) << 48) + ((buf[from + 5] & 0xFFL) << 40)                             + ((buf[from + 4] & 0xFFL) << 32) + ((buf[from + 3] & 0xFFL) << 24)                             + ((buf[from + 2] & 0xFFL) << 16) + ((buf[from + 1] & 0xFFL) << 8) + (buf[from] & 0xFFL);                nameValue1 = (((long) buf[from + 14]) << 56) + ((buf[from + 13] & 0xFFL) << 48)                             + ((buf[from + 12] & 0xFFL) << 40) + ((buf[from + 11] & 0xFFL) << 32)                             + ((buf[from + 10] & 0xFFL) << 24) + ((buf[from + 9] & 0xFFL) << 16)                             + ((buf[from + 8] & 0xFFL) << 8) + (buf[from + 7] & 0xFFL);                break;            case 16:                nameValue0 = (((long) buf[from + 7]) << 56) + ((buf[from + 6] & 0xFFL) << 48)                             + ((buf[from + 5] & 0xFFL) << 40) + ((buf[from + 4] & 0xFFL) << 32)                             + ((buf[from + 3] & 0xFFL) << 24) + ((buf[from + 2] & 0xFFL) << 16)                             + ((buf[from + 1] & 0xFFL) << 8) + (buf[from] & 0xFFL);                nameValue1 = (((long) buf[from + 15]) << 56) + ((buf[from + 14] & 0xFFL) << 48)                             + ((buf[from + 13] & 0xFFL) << 40) + ((buf[from + 12] & 0xFFL) << 32)                             + ((buf[from + 11] & 0xFFL) << 24) + ((buf[from + 10] & 0xFFL) << 16)                             + ((buf[from + 9] & 0xFFL) << 8) + (buf[from + 8] & 0xFFL);                break;            default:                break;        }        if (nameValue0 != -1) {            if (nameValue1 != -1) {                int indexMask = ((int) nameValue1) & (NameCache.NAME_CACHE2.length - 1);                NameCache.NameCacheEntry2 entry = NameCache.NAME_CACHE2[indexMask];                if (entry == null) {                    String name = new String(buf, from, length, charset);                    NameCache.NAME_CACHE2[indexMask] = new NameCacheEntry2(name, nameValue0, nameValue1);                    return name;                } else if (entry.value0 == nameValue0 && entry.value1 == nameValue1) {                    return entry.name;                }            } else {                int indexMask = ((int) nameValue0) & (NAME_CACHE.length - 1);                NameCacheEntry entry = NAME_CACHE[indexMask];                if (entry == null) {                    String name = new String(buf, from, length, charset);                    NAME_CACHE[indexMask] = new NameCacheEntry(name, nameValue0);                    return name;                } else if (entry.value == nameValue0) {                    return entry.name;                }            }        }        return null;    }}