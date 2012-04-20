package com.alibaba.hbase.test.alireplication;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.junit.BeforeClass;
import org.junit.Test;

import com.alibaba.hbase.replication.hlog.HLogEntryPoolPersistence;
import com.alibaba.hbase.replication.hlog.HLogEntryPoolZookeeperPersistence;
import com.alibaba.hbase.replication.producer.ReplicationSinkManger;
import com.alibaba.hbase.replication.server.ReplicationConf;
import com.alibaba.hbase.replication.utility.ZKUtil;
import com.alibaba.hbase.replication.zookeeper.NothingZookeeperWatch;
import com.alibaba.hbase.replication.zookeeper.RecoverableZooKeeper;
import com.alibaba.hbase.test.HBaseTestBase;
import com.alibaba.hbase.test.alireplication.util.TestConfigurationUtil;

/**
 * 类TestMZookeeper.java的实现描述：TODO 类实现描述
 * 
 * @author zalot.zhaoh Apr 1, 2012 11:37:47 AM
 */
public class TestMZookeeper extends HBaseTestBase {

    protected static final int             tableSize    = 10;
    protected static final ReplicationConf confProducer = new ReplicationConf();

    @BeforeClass
    public static void init() throws Exception {
        init1();
        init2();
        TestConfigurationUtil.setProducer(util1.getConfiguration(), util2.getConfiguration(), confProducer);
    }

    @Test
    public void testMZkHLogP() throws Exception {
        final int count = 0;

        final RecoverableZooKeeper[] zks = new RecoverableZooKeeper[count];
        final HLogEntryPoolPersistence[] ps = new HLogEntryPoolPersistence[count];
        final Random rnd = new Random();
        final ThreadPoolExecutor pool = new ThreadPoolExecutor(count, count, 100, TimeUnit.SECONDS,
                                                               new ArrayBlockingQueue<Runnable>(count));

        for (int x = 0; x < count; x++) {
            zks[x] = ZKUtil.connect(confProducer, new NothingZookeeperWatch());
            ps[x] = new HLogEntryPoolZookeeperPersistence(confProducer, zks[x]);
            // pool.execute(new Runnable() {
            // @Override
            // public void run() {
            // while(true){
            // int curRnd = rnd.nextInt(count);
            // }
            // }
            // });
        }
        int c = rnd.nextInt(count);
    }

    // @Test
    public void test() throws Exception {
        ReplicationConf confProducer = new ReplicationConf();
        TestConfigurationUtil.setProducer(util1.getConfiguration(), util2.getConfiguration(), confProducer);

        for (int x = 0; x < 3; x++) {
            ReplicationSinkManger manager = new ReplicationSinkManger();
            manager.setRefConf(confProducer);
            manager.start();
        }
        Thread.sleep(1200000);
    }

    public void rndWrite() {
        // createTable(conf);
    }
}
