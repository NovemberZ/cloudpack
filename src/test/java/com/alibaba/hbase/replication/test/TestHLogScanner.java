package com.alibaba.hbase.replication.test;

import java.io.IOException;
import java.util.Random;

import junit.framework.Assert;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.zookeeper.KeeperException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.hbase.replication.hlog.HLogEntryPoolZookeeperPersistence;
import com.alibaba.hbase.replication.hlog.HLogService;
import com.alibaba.hbase.replication.hlog.domain.HLogEntryGroup;
import com.alibaba.hbase.replication.hlog.domain.HLogEntryGroups;
import com.alibaba.hbase.replication.producer.HLogGroupZookeeperScanner;
import com.alibaba.hbase.replication.utility.HLogUtil;
import com.alibaba.hbase.replication.utility.ProducerConstants;
import com.alibaba.hbase.replication.utility.ZKUtil;
import com.alibaba.hbase.replication.zookeeper.NothingZookeeperWatch;
import com.alibaba.hbase.replication.zookeeper.RecoverableZooKeeper;

public class TestHLogScanner extends BaseReplicationTest {

    private static final Logger LOG = LoggerFactory.getLogger(TestHLogScanner.class);
    @BeforeClass
    public static void init() throws Exception {
        LOG.error("bbb");
        init1();
        LOG.error("aaa");
    }

    @Test
    public void testSThreadScan() throws Exception {
        HLogService service = new HLogService(conf1);
        
        HLogGroupZookeeperScanner scan;
        RecoverableZooKeeper zk1 = ZKUtil.connect(conf1, new NothingZookeeperWatch());
        HLogEntryPoolZookeeperPersistence dao1 = new HLogEntryPoolZookeeperPersistence(conf1, zk1);
        dao1.setZookeeper(zk1);
        dao1.init(conf1);

        FileSystem fs = util1.getTestFileSystem();
        scan = new HLogGroupZookeeperScanner(conf1);
        scan.setHlogEntryPersistence(dao1);
        scan.setHlogService(service);
//        scan.setZooKeeper(zooKeeper);
        
        int count = 0;
        while (true) {
            insertData(pool1, TABLEA, COLA, "test", 1000);
            Thread.sleep(10000 * 2);
            HLogEntryGroups groups = new HLogEntryGroups();
            groups.put(HLogUtil.getHLogsByHDFS(fs, service.getHLogDir()));
            groups.put(HLogUtil.getHLogsByHDFS(fs, service.getOldHLogDir()));
            Assert.assertTrue(dao1.listGroupName().size() == groups.getGroups().size());
            for (HLogEntryGroup group : groups.getGroups()) {
                Assert.assertTrue(dao1.listEntry(group.getGroupName()).size() == group.getEntrys().size());
            }
            System.out.println("checkok -- " + count + " groups [ " + groups.getGroups().size() + " ] ");
            if (count > 10) return;
            count++;
        }
    }

    @Test
    public void testMThreadScan() throws Exception {
        HLogService service = new HLogService(conf1);
        final HLogGroupZookeeperScanner scan1;
        final HLogGroupZookeeperScanner scan2;

        RecoverableZooKeeper zk1 = ZKUtil.connect(conf1, new NothingZookeeperWatch());
        final HLogEntryPoolZookeeperPersistence dao1 = new HLogEntryPoolZookeeperPersistence(conf1 , zk1);
        dao1.setZookeeper(zk1);
        dao1.init(conf1);

        RecoverableZooKeeper zk2 = ZKUtil.connect(conf1, new NothingZookeeperWatch());
        final HLogEntryPoolZookeeperPersistence dao2 = new HLogEntryPoolZookeeperPersistence(conf2, zk2);
        dao2.setZookeeper(zk2);
        dao2.init(conf1);

        RecoverableZooKeeper zk3 = ZKUtil.connect(conf1, new NothingZookeeperWatch());
        HLogEntryPoolZookeeperPersistence dao3 = new HLogEntryPoolZookeeperPersistence(conf1 , zk3);
        dao3.setZookeeper(zk3);
        dao3.init(conf1);

        FileSystem fs = util1.getTestFileSystem();

        scan1 = new HLogGroupZookeeperScanner(conf1);
        scan2 = new HLogGroupZookeeperScanner(conf1);

        Path hlogPath = service.getHLogDir();
        Path oldPath = service.getOldHLogDir();

        int count = 0;

        Thread rndShutDownScan = new Thread() {

            @Override
            public void run() {
                super.run();
                while (true) {
                    try {
                        HLogGroupZookeeperScanner scan = rndShutDownScan(scan1, scan2);
//                        if (!scan.isAlive()) {
//                            System.out.println("start scan [" + scan.getName() + "] ...");
//                            scan.start();
//                        }
//                        Thread.sleep(AliHBaseConstants.ZOO_SCAN_LOCK_RETRYTIME * 2);
                        Thread.sleep(ProducerConstants.ZOO_SCAN_LOCK_RETRYTIME * 2);
                    } catch (Exception e) {
                    }
                }
            }
        };

        Thread rndShutDownDao = new Thread() {

            @Override
            public void run() {
                super.run();
                while (true) {
                    try {
//                        HLogZookeeperPersistence dao = rndShutDownDaoZk(dao1, dao2);
//                        Thread.sleep(1000);
//                        RecoverableZooKeeper zk = ZKUtil.connect(conf1, new ReplicationZookeeperWatch());
//                        dao.setZookeeper(zk);
//                        System.out.println("start [dao] " + dao.getName());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        rndShutDownDao.start();

        while (true) {
            insertData(pool1, TABLEA, COLA, "test", 1000);
            Thread.sleep(10000 * 2);
            HLogEntryGroups groups = new HLogEntryGroups();
            groups.put(HLogUtil.getHLogsByHDFS(fs, hlogPath));
            groups.put(HLogUtil.getHLogsByHDFS(fs, oldPath));
            Assert.assertTrue(dao3.listGroupName().size() == groups.getGroups().size());
            for (HLogEntryGroup group : groups.getGroups()) {
                Assert.assertTrue(dao3.listEntry(group.getGroupName()).size() == group.getEntrys().size());
            }
            System.out.println("checkok -- " + count + " groups [ " + groups.getGroups().size() + " ] ");
            if (count > 10) return;
            count++;
        }
    }

    static Random rnd = new Random();

    public static HLogGroupZookeeperScanner rndShutDownScan(HLogGroupZookeeperScanner scan1,
                                                            HLogGroupZookeeperScanner scan2) throws IOException,
                                                                                            KeeperException,
                                                                                            InterruptedException {
        HLogGroupZookeeperScanner canStopScan;
        if (rnd.nextBoolean()) {
            canStopScan = scan1;
        } else {
            canStopScan = scan2;
        }

//        if (canStopScan.isAlive()) {
//            try {
//                canStopScan.stop();
//                System.out.println("stop scan [" + canStopScan.getName() + "] ...");
//            } catch (Exception e) {
//
//            }
//        }
        return canStopScan;
    }

//    private static HLogZookeeperPersistence rndShutDownDaoZk(HLogZookeeperPersistence dao1,
//                                                             HLogZookeeperPersistence dao2)
//                                                                                           throws InterruptedException,
//                                                                                           IOException {
//        HLogZookeeperPersistence canStop;
//        if (rnd.nextBoolean()) {
//            canStop = dao1;
//        } else {
//            canStop = dao2;
//        }
//
//        canStop.getZookeeper().close();
//        System.out.println("stop [dao] " + canStop.getName());
//        return canStop;
//    }
}
