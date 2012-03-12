package com.alibaba.hbase.replication.producer;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.zookeeper.RecoverableZooKeeper;
import org.apache.hadoop.hbase.zookeeper.ZKUtil;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.Stat;

import com.alibaba.hbase.replication.hlog.HLogOperatorPersistence;
import com.alibaba.hbase.replication.hlog.domain.HLogEntryGroup;
import com.alibaba.hbase.replication.hlog.domain.HLogEntryGroups;
import com.alibaba.hbase.replication.utility.ConsumerConstants;
import com.alibaba.hbase.replication.utility.HLogUtil;
import com.alibaba.hbase.replication.utility.ProducerConstants;
import com.alibaba.hbase.replication.zookeeper.NothingZookeeperWatch;

/**
 * HLogGroup 扫描器<BR>
 * 类HLogGroupZookeeperScanner.java的实现描述：TODO 类实现描述
 * 
 * @author zalot.zhaoh Mar 1, 2012 10:44:45 AM
 */
public class HLogGroupZookeeperScanner implements Runnable {

    protected static final Log         LOG            = LogFactory.getLog(HLogGroupZookeeperScanner.class);
    protected String                   name;
    protected String                   zooScanBasePath;
    protected String                   zooScanLockPath;
    protected Path                     dfsHLogPath;
    protected Path                     dfsOldHLogPath;
    
    protected int                      errorCount     = 0;
    // 休息时间
    // 争抢到 scanner 后 间隔时间
    protected long                     flushSleepTime;
    // scanner 争抢重试时间
    protected long                     scannerTryLockTime;
    protected boolean                  isLock         = false;

    protected long                     scanOldHlogTimeOut;
    protected boolean                  hasScanOldHLog = false;

    // 外部对象引用
    protected Configuration            conf;
    protected HLogOperatorPersistence hlogDAO;
    protected FileSystem               fs;
    protected RecoverableZooKeeper     zoo;

    public Path getHlogPath() {
        return dfsHLogPath;
    }

    public void setHlogPath(Path hlogPath) {
        this.dfsHLogPath = hlogPath;
    }

    public Path getOldHlogPath() {
        return dfsOldHLogPath;
    }

    public void setOldHlogPath(Path oldHlogPath) {
        this.dfsOldHLogPath = oldHlogPath;
    }

    public HLogGroupZookeeperScanner(Configuration conf) throws KeeperException, InterruptedException, IOException{
        this(UUID.randomUUID().toString(), conf, null);
    }
    
    public HLogGroupZookeeperScanner(Configuration conf, RecoverableZooKeeper zoo) throws KeeperException, InterruptedException, IOException{
        this(UUID.randomUUID().toString(), conf, zoo);
    }

    public HLogGroupZookeeperScanner(String name, Configuration conf, RecoverableZooKeeper zoo) throws KeeperException, InterruptedException,
                                                                     IOException{
        if(zoo == null)
            zoo = ZKUtil.connect(conf, new NothingZookeeperWatch());
        this.zoo = zoo;
        this.fs = FileSystem.get(URI.create(conf.get(ConsumerConstants.CONFKEY_PRODUCER_FS)), conf);
        this.name = name;
        this.conf = conf;
        init();
    }

    private void init() throws KeeperException, InterruptedException {
        zooScanBasePath = conf.get(ProducerConstants.CONFKEY_ZOO_SCAN_ROOT, ProducerConstants.ZOO_SCAN_ROOT);
        zooScanLockPath = zooScanBasePath + ProducerConstants.ZOO_SCAN_LOCK;
        flushSleepTime = conf.getLong(ProducerConstants.CONFKEY_ZOO_SCAN_LOCK_FLUSHSLEEPTIME,
                                      ProducerConstants.ZOO_SCAN_LOCK_FLUSHSLEEPTIME);
        scannerTryLockTime = conf.getLong(ProducerConstants.CONFKEY_ZOO_SCAN_LOCK_TRYLOCKTIME,
                                          ProducerConstants.ZOO_SCAN_LOCK_TRYLOCKTIME);
        scanOldHlogTimeOut = conf.getLong(ProducerConstants.CONFKEY_ZOO_SCAN_OLDHLOG_TIMEOUT,
                                          ProducerConstants.ZOO_SCAN_OLDHLOG_TIMEOUT);

        Stat stat = zoo.exists(zooScanBasePath, false);
        if (stat == null) {
            try {
                zoo.create(zooScanBasePath, null, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } catch (NodeExistsException e) {
            }
        }
    }

    public boolean lock() throws KeeperException, InterruptedException {
        Stat stat = zoo.exists(zooScanLockPath, false);
        if (stat != null) {
            return false;
        }
        zoo.create(zooScanLockPath, null, Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        return true;
    }

    private void unlock() {
        try {
            Stat stat = zoo.exists(zooScanLockPath, false);
            if (stat != null) zoo.delete(zooScanLockPath, stat.getVersion());
        } catch (Exception e) {
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                LOG.debug("Scanner Start ....");
                Thread.sleep(scannerTryLockTime);
                isLock = lock();
                if (isLock) {
                    scanning();
                }
            } catch (Exception e) {
                isLock = false;
                LOG.error(e);
                errorCount++;
                // 如果 超过 3次错误则释放 lock
                if (errorCount > 3) {
                    reinizoo();
                }
            } finally {
                if (isLock) {
                    unlock();
                }
            }
        }
    }

    private void reinizoo() {
        if (zoo != null) {
            try {
                zoo.close();
                zoo = ZKUtil.connect(conf, new NothingZookeeperWatch());
            } catch (Exception e) {
                LOG.error(e.getMessage());
            }
        }
    }

    private void scanning() throws Exception {
        while (true) {
            // TODO : 如果 flushSleepTime 这段时间内有 Hlog - > OldHlog 那么就要更换策略
            // 所以常情保持 Hlog 的数量和大小，确保 在 flushSleepTime 时间段内， Hlog 一直都在 .logs 目录中
            Thread.sleep(flushSleepTime);
            LOG.debug(Thread.currentThread().getName() + " scanning ....");
            HLogEntryGroups groups = new HLogEntryGroups();
            putHLog(groups);
            putOldHLog(groups);
            for (HLogEntryGroup group : groups.getGroups()) {
                hlogDAO.createOrUpdateGroup(group, true);
            }
            LOG.debug(Thread.currentThread().getName() + " scanning ok");
        }
    }

    protected void putHLog(HLogEntryGroups groups) throws IOException {
        groups.put(HLogUtil.getHLogsByHDFS(fs, dfsHLogPath));
    }

    protected void putOldHLog(HLogEntryGroups groups) throws IOException {
        // need to scan oldlogs ?
        /*
         * Stat stat = zoo.exists(scanBasePath, false); byte[] tstTmp = zoo.getData(scanBasePath, false, stat); long
         * lastScanTst = -1; if (tstTmp != null) { lastScanTst = Bytes.toLong(tstTmp, -1); }
         */
        // yes, scan oldlogs
        /*
         * if (lastScanTst == -1 || lastScanTst + scanOldHlogTimeOut >= System.currentTimeMillis()) {
         * groups.put(HLogUtil.getHLogsByHDFS(fs, oldHlogPath)); }
         */
        if (!hasScanOldHLog) {
            groups.put(HLogUtil.getHLogsByHDFS(fs, dfsOldHLogPath));
            hasScanOldHLog = true;
        }
    }

    public long getFlushSleepTime() {
        return flushSleepTime;
    }

    public byte[] getLastScanTime() {
        return Bytes.toBytes(System.currentTimeMillis());
    }
}
