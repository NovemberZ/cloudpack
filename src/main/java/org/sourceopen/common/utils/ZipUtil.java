package org.sourceopen.common.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ZipUtil {

    public static byte[] gzip(byte[] data) throws Exception {
        byte[] rs;
        GZIPOutputStream gzipOut = null;
        ByteArrayOutputStream byteOut = null;
        try {
            byteOut = new ByteArrayOutputStream();
            gzipOut = new GZIPOutputStream(byteOut);
            gzipOut.write(data);
            gzipOut.finish();
            gzipOut.close();
            rs = byteOut.toByteArray();
            byteOut.close();
            return rs;
        } finally {
            if (gzipOut != null) {
                gzipOut.close();
            }
            if (byteOut != null) {
                byteOut.close();
            }
            gzipOut = null;
            byteOut = null;
        }
    }

    public static byte[] ungzip(byte[] data) throws Exception {
        byte[] b = null;
        ByteArrayInputStream byteIn = null;
        GZIPInputStream gzipIn = null;
        ByteArrayOutputStream byteOut = null;
        try {
            byteIn = new ByteArrayInputStream(data);
            gzipIn = new GZIPInputStream(byteIn);
            byte[] buf = new byte[1024];
            int num = -1;
            byteOut = new ByteArrayOutputStream();
            while ((num = gzipIn.read(buf, 0, buf.length)) != -1) {
                byteOut.write(buf, 0, num);
            }
            b = byteOut.toByteArray();
            byteOut.flush();
            byteOut.close();
            gzipIn.close();
            byteIn.close();
        } finally {
            if (gzipIn != null) {
                gzipIn.close();
            }
            if (byteIn != null) {
                byteIn.close();
            }
            if (byteOut != null) {
                byteOut.close();
            }
            gzipIn = null;
            byteOut = null;
            byteIn = null;
        }
        return b;
    }
}
