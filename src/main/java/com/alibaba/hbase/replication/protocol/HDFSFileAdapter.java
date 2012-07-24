package com.alibaba.hbase.replication.protocol;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.hbase.replication.protocol.exception.FileParsingException;
import com.alibaba.hbase.replication.protocol.exception.FileReadingException;
import com.alibaba.hbase.replication.utility.ConsumerConstants;

/**
 * 文件适配器 类FileAdapter.java的实现描述：TODO 类实现描述
 * 
 * @author zalot.zhaoh Feb 28, 2012 2:26:28 PM
 */
@Service("fileAdapter")
public class HDFSFileAdapter extends ProtocolAdapter {

    public static final String       CONFKEY_HDFS_FS            = "com.alibaba.hbase.replication.protocol.adapter.hdfs.fs";
    public static final String       CONFKEY_HDFS_FS_OLDPATH    = "com.alibaba.hbase.replication.protocol.adapter.hdfs.dir.oldpath";
    public static final String       CONFKEY_HDFS_FS_REJECTPATH = "com.alibaba.hbase.replication.protocol.adapter.hdfs.dir.rejectpath";
    public static final String       CONFKEY_HDFS_FS_TARGETPATH = "com.alibaba.hbase.replication.protocol.adapter.hdfs.dir.targetpath";
    public static final String       CONFKEY_HDFS_FS_ROOT       = "com.alibaba.hbase.replication.protocol.adapter.hdfs.dir.root";
    public static final FsPermission PERMISSION                 = new FsPermission((short) 0777);
    protected static final Log       LOG                        = LogFactory.getLog(HDFSFileAdapter.class);
    public static final String       SPLIT_SYMBOL               = "|";

    public static String head2FileName(ProtocolHead head) {
        return head.version + SPLIT_SYMBOL // [0]
               + head.groupName + SPLIT_SYMBOL // [1]
               + head.fileTimestamp + SPLIT_SYMBOL // [2]
               + head.headTimestamp + SPLIT_SYMBOL // [3]
               + head.startOffset + SPLIT_SYMBOL // [4]
               + head.endOffset + SPLIT_SYMBOL // [5]
               + head.count + SPLIT_SYMBOL // [6]
               + head.retry + SPLIT_SYMBOL // [7]
        ;
    }

    public static String head2MD5FileName(ProtocolHead head) {
        return head2FileName(head) + ConsumerConstants.MD5_SUFFIX;
    }

    public static ProtocolHead validataFileName(String fileName) {
        String[] info = StringUtils.split(fileName, SPLIT_SYMBOL);
        if (info.length == 8) {
            try {
                ProtocolHead head = new ProtocolHead();
                head.setVersion(Integer.parseInt(info[0]));
                if (StringUtils.isBlank(info[1])) {
                    return null;
                }
                head.setGroupName(info[1]);
                head.setFileTimestamp(Long.parseLong(info[2]));
                head.setHeadTimestamp(Long.parseLong(info[3]));
                head.setStartOffset(Integer.parseInt(info[4]));
                head.setEndOffset(Integer.parseInt(info[5]));
                head.setCount(Integer.parseInt(info[6]));
                head.setRetry(Integer.parseInt(info[7]));
                return head;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    @Autowired
    protected Configuration _conf;

    /**
     * md5摘要文件位置
     */
    protected Path          digestPath;

    /**
     * 已处理的中间文件存放位置
     */
    protected Path          oldPath;

    /**
     * 退回的中间文件存放位置（需要producer端重做）
     */
    protected Path          rejectPath;
    /**
     * 待处理的中间文件存放位置
     */
    protected Path          targetPath;
    /**
     * 生成文件时使用的临时目录
     */
    protected Path          targetTmpPath;

    protected FileSystem    fs;

    /**
     * 清理producer端已处理的中间文件和slave端的临时文件
     * 
     * @param fileHead 中间文件文件名
     * @param fs
     * @throws IOException
     */
    public void clean(ProtocolHead head, FileSystem fs) throws IOException {
        fs.rename(new Path(targetPath, head2FileName(head)), new Path(oldPath, head2FileName(head)));
        fs.rename(new Path(digestPath, head2MD5FileName(head)), new Path(oldPath, head2MD5FileName(head)));
        if (LOG.isInfoEnabled()) LOG.info("doAdapter Over - > " + head);
    }

    /**
     * 方便测试
     * 
     * @return
     */
    public Path getDigestPath() {
        return digestPath;
    }

    /**
     * 方便测试
     * 
     * @return
     */
    public Path getOldPath() {
        return oldPath;
    }

    /**
     * 方便测试
     * 
     * @return
     */
    public Path getRejectPath() {
        return rejectPath;
    }

    /**
     * 方便测试
     * 
     * @return
     */
    public Path getTargetPath() {
        return targetPath;
    }

    /**
     * 方便测试
     * 
     * @return
     */
    public Path getTargetTmpPath() {
        return targetTmpPath;
    }

    public void init(Configuration conf) {
        setPath(conf);
    }

    @Override
    public List<ProtocolHead> listHead() throws Exception {
        List<ProtocolHead> heads = new ArrayList<ProtocolHead>();
        try {
            FileStatus[] fss = fs.listStatus(targetPath);
            ProtocolHead head;
            for (FileStatus fs : fss) {
                head = validataFileName(fs.getPath().getName());
                if (head != null) {
                    heads.add(head);
                } else {
                    // TODO
                }
            }
        } catch (Exception e) {
        }
        return heads;
    }

    @Override
    public List<ProtocolHead> listRejectHead() {
        List<ProtocolHead> heads = new ArrayList<ProtocolHead>();
        try {
            FileStatus[] fss = fs.listStatus(rejectPath);
            ProtocolHead head;
            for (FileStatus fs : fss) {
                head = validataFileName(fs.getPath().getName());
                if (head != null) {
                    heads.add(head);
                } else {
                    // TODO
                }
            }
        } catch (Exception e) {
        }
        return heads;
    }

    protected void producter_check() {
        try {
            if (!fs.exists(targetPath)) {
                fs.mkdirs(targetPath);
            }

            if (!fs.exists(targetTmpPath)) {
                fs.mkdirs(targetTmpPath);
            }

            if (!fs.exists(digestPath)) {
                fs.mkdirs(digestPath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void consumer_check() {
        try {
            if (!fs.exists(oldPath)) {
                fs.mkdirs(oldPath);
            }
            if (!fs.exists(rejectPath)) {
                fs.mkdirs(rejectPath);
            }
        } catch (Exception e) {
            LOG.error("consumer check error", e);
        }
    }

    // protected boolean checkFileOrDirectory(FileStatus fss) {
    // if (!fss.getPermission().getOtherAction().implies(FsAction)) {
    //
    // }
    // if (!fss.getPermission().getOtherAction().implies(FsAction.READ_WRITE)) {
    //
    // }
    // }

    @Override
    public MetaData read(ProtocolHead head) throws Exception {
        return read(head, fs);
    }

    public MetaData read(ProtocolHead head, FileSystem fs) throws FileParsingException, FileReadingException {
        FSDataInputStream in = null;
        byte[] byteArray = null;
        FSDataInputStream md5In = null;
        byte[] md5ByteArray = null;
        try {
            in = fs.open(new Path(targetPath, head2FileName(head)));
            byteArray = IOUtils.toByteArray(in);
            md5In = fs.open(new Path(digestPath, head2MD5FileName(head)));
            md5ByteArray = IOUtils.toByteArray(md5In);
        } catch (IOException e1) {
            throw new FileReadingException("error while reading hdfs file to bytes. file: " + head2FileName(head), e1);
        } finally {
            org.apache.hadoop.io.IOUtils.closeStream(in);
            org.apache.hadoop.io.IOUtils.closeStream(md5In);
        }
        if (byteArray != null && byteArray.length > 0 && md5ByteArray != null && md5ByteArray.length > 0) {
            try {
                MessageDigest digest = MessageDigest.getInstance("MD5");
                if (Bytes.equals(md5ByteArray, digest.digest(byteArray))) {
                    MetaData data = MetaData.getMetaData(head, byteArray);
                    return data;
                } else {
                    throw new FileParsingException("Fail with MD5 digest.The file corrupts probably.");
                }
            } catch (NoSuchAlgorithmException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Coding error!", e);
                }
            } catch (Exception e) {
                throw new FileParsingException("error while parsing body with protobuf.", e);
            }
        }
        return null;
    }

    @Override
    public void recover(MetaData data) {
        try {
            write(data);
            String fileName = head2FileName(data.getHead());
            Path rejectFile = new Path(rejectPath, fileName);
            fs.deleteOnExit(rejectFile);
        } catch (Exception e) {
            LOG.error("recover error ", e);
        }
    }

    /**
     * 中间文件处理失败，退回重做
     * 
     * @param fileHead 中间文件文件名
     * @param fs
     * @throws IOException
     */
    public void reject(ProtocolHead head) throws IOException {
        fs.rename(new Path(targetPath, head2FileName(head)), new Path(rejectPath, head2FileName(head)));
        fs.deleteOnExit(new Path(digestPath, head2MD5FileName(head)));
    }

    public void setPath(Configuration conf) {
        if (fs == null) {
            try {
                Configuration newconf = new Configuration(conf);
                String defaultFs = conf.get(CONFKEY_HDFS_FS);
                if (defaultFs == null) return;
                newconf.set("fs.default.name", defaultFs);
                fs = FileSystem.get(newconf);
                this._conf = newconf;
            } catch (IOException e) {
                LOG.error("init error", e);
                return;
            }
        }

        targetPath = new Path(conf.get(CONFKEY_HDFS_FS_ROOT) + conf.get(CONFKEY_HDFS_FS_TARGETPATH));
        targetTmpPath = new Path(conf.get(CONFKEY_HDFS_FS_ROOT) + conf.get(CONFKEY_HDFS_FS_TARGETPATH) + "tmp/");
        oldPath = new Path(conf.get(CONFKEY_HDFS_FS_ROOT) + conf.get(CONFKEY_HDFS_FS_OLDPATH));
        rejectPath = new Path(conf.get(CONFKEY_HDFS_FS_ROOT) + conf.get(CONFKEY_HDFS_FS_REJECTPATH));
        digestPath = new Path(targetPath, ConsumerConstants.MD5_DIR);
        producter_check();
        consumer_check();
    }

    @Override
    public void write(MetaData data) throws Exception {
        write(data, fs);
    }

    public void write(MetaData data, FileSystem fs) throws Exception {
        FSDataOutputStream targetOutput = null;
        FSDataOutputStream targetMD5Output = null;
        try {
            // write tmpFile
            String fileName = head2FileName(data.getHead());
            byte[] bodyBytes = data.getBody().getBodyData();
            Path targetTmpFilePath = new Path(targetTmpPath, fileName);
            targetOutput = fs.create(targetTmpFilePath, true);
            targetOutput.write(bodyBytes);
            targetOutput.close();
            fs.setPermission(targetTmpFilePath, PERMISSION);

            // write MD5
            String md5fileName = head2MD5FileName(data.getHead());
            MessageDigest digest = MessageDigest.getInstance("MD5");
            Path tmpMD5Path = new Path(targetTmpPath, md5fileName);
            targetMD5Output = fs.create(tmpMD5Path, true);
            targetMD5Output.write(digest.digest(bodyBytes));
            targetMD5Output.close();
            fs.setPermission(tmpMD5Path, PERMISSION);
            // move tmpFile and MD5File to source directory
            Path sourceFilePath = new Path(targetPath, targetTmpFilePath.getName());
            Path sourceMd5FilePath = new Path(digestPath, tmpMD5Path.getName());
            if (!fs.rename(targetTmpFilePath, sourceFilePath)) {
                throw new RuntimeException(targetTmpPath + " rename to " + sourceFilePath);
            }

            if (!fs.rename(tmpMD5Path, sourceMd5FilePath)) {
                throw new RuntimeException(targetTmpPath + " rename to " + sourceFilePath);
            }
            if (LOG.isInfoEnabled()) LOG.info("doAdapter - > " + data.getHead());
        } finally {
            if (targetOutput != null) {
                try {
                    targetOutput.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    targetOutput = null;
                }
            }
            if (targetMD5Output != null) {
                try {
                    targetMD5Output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    targetMD5Output = null;
                }
            }
        }
    }

    @Override
    public void clean(ProtocolHead head) throws Exception {
        clean(head, fs);
    }

    @Override
    public void crush() throws Exception {
        for (FileStatus fst : fs.listStatus(oldPath)) {
            fs.delete(fst.getPath(), true);
        }
        if (LOG.isInfoEnabled()) LOG.info(" -------- crush ----------");
    }
}
