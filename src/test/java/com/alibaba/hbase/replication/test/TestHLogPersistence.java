package com.alibaba.hbase.replication.test;

import junit.framework.Assert;

import org.junit.BeforeClass;
import org.junit.Test;

import com.alibaba.hbase.replication.hlog.HLogEntryZookeeperPersistence;
import com.alibaba.hbase.replication.hlog.domain.HLogEntry;
import com.alibaba.hbase.replication.hlog.domain.HLogEntryGroup;
import com.alibaba.hbase.replication.utility.ZKUtil;
import com.alibaba.hbase.replication.zookeeper.NothingZookeeperWatch;
import com.alibaba.hbase.replication.zookeeper.RecoverableZooKeeper;

public class TestHLogPersistence extends BaseReplicationTest {

    @BeforeClass
    public static void init() throws Exception {
        init1();
        // init2();
    }

    @Test
    public void testZookeeperHLogPersistenceSimple() throws Exception {
        RecoverableZooKeeper zk1 = ZKUtil.connect(conf1, new NothingZookeeperWatch());
        final HLogEntryZookeeperPersistence dao1 = new HLogEntryZookeeperPersistence(conf1);
        dao1.setZookeeper(zk1);
        dao1.init(conf1);

        RecoverableZooKeeper zk2 = ZKUtil.connect(conf1, new NothingZookeeperWatch());
        final HLogEntryZookeeperPersistence dao2 = new HLogEntryZookeeperPersistence(conf1);
        dao2.setZookeeper(zk2);
        dao2.init(conf1);

        HLogEntryGroup group = new HLogEntryGroup("test");
        dao1.createGroup(group, false);
        Assert.assertNotNull(dao2.getGroupByName(group.getGroupName(), false));
        Assert.assertNotNull(dao1.getGroupByName(group.getGroupName(), false));

        HLogEntry entry = new HLogEntry("abcd.1234");
        group.put(entry);
        dao1.createOrUpdateGroup(group, true);

        Assert.assertNull(dao1.getHLogEntry(entry.getGroupName(), entry.getName()));
        Assert.assertNull(dao2.getHLogEntry(entry.getGroupName(), entry.getName()));

        entry = new HLogEntry("test.1234");
        group.put(entry);
        dao1.createOrUpdateGroup(group, true);
        Assert.assertNotNull(dao1.getHLogEntry(entry.getGroupName(), entry.getName()));
        Assert.assertNotNull(dao2.getHLogEntry(entry.getGroupName(), entry.getName()));

        Assert.assertTrue(dao1.lockGroup(group.getGroupName()));
        Assert.assertTrue(dao2.isLockGroup(group.getGroupName()));
        Assert.assertFalse(dao2.lockGroup(group.getGroupName()));

        zk1.close();
        Assert.assertFalse(dao2.isLockGroup(group.getGroupName()));
        Assert.assertTrue(dao2.lockGroup(group.getGroupName()));
        zk2.close();
    }

    @Test
    public void testMuThreadPer() throws Exception {
        final HLogEntryGroup group = new HLogEntryGroup("testB");

        RecoverableZooKeeper zk1 = ZKUtil.connect(conf1, new NothingZookeeperWatch());
        final HLogEntryZookeeperPersistence dao1 = new HLogEntryZookeeperPersistence(conf1);
        dao1.setZookeeper(zk1);
        dao1.init(conf1);

        RecoverableZooKeeper zk2 = ZKUtil.connect(conf1, new NothingZookeeperWatch());
        final HLogEntryZookeeperPersistence dao2 = new HLogEntryZookeeperPersistence(conf1);
        dao2.setZookeeper(zk2);
        dao2.init(conf1);

        dao1.createGroup(group, false);

        
        Runnable run1 = new Runnable() {

            @Override
            public void run() {
                int checkCount = 0;
                while (true) {
                    try {
                        if (dao1.lockGroup(group.getGroupName())) {
                            Thread.sleep(300);
                            Assert.assertTrue(dao1.isLockGroup(group.getGroupName()));
                            Assert.assertTrue(dao2.isLockGroup(group.getGroupName()));
                            Assert.assertFalse(dao2.lockGroup(group.getGroupName()));
                            Assert.assertFalse(dao2.isMeLockGroup(group.getGroupName()));
                            dao1.unlockGroup(group.getGroupName());
                            Assert.assertFalse(dao1.isMeLockGroup(group.getGroupName()));
                            checkCount ++ ;
                            if(checkCount > 500){
                                return;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Assert.assertTrue(false);
                    }
                }
            }
        };

        Runnable run2 = new Runnable() {

            @Override
            public void run() {
                while (true) {
                    int checkCount = 0;
                    try {
                        if (dao2.lockGroup(group.getGroupName())) {
                            Thread.sleep(300);
                            Assert.assertTrue(dao2.isLockGroup(group.getGroupName()));
                            Assert.assertTrue(dao1.isLockGroup(group.getGroupName()));
                            Assert.assertFalse(dao1.lockGroup(group.getGroupName()));
                            Assert.assertFalse(dao1.isMeLockGroup(group.getGroupName()));
                            dao2.unlockGroup(group.getGroupName());
                            Assert.assertFalse(dao2.isMeLockGroup(group.getGroupName()));
                            checkCount ++ ;
                            if(checkCount > 500){
                                return;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Assert.assertTrue(false);
                    }

                }
            }
        };

        Thread td1 = new Thread(run1);
        td1.start();

        Thread td2 = new Thread(run2);
        td2.start();
        Thread.sleep(600000);
    }
}
