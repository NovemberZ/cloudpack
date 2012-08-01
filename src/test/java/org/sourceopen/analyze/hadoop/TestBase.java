package org.sourceopen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.HTablePool;
import org.apache.hadoop.hbase.client.Put;
import org.junit.AfterClass;

/**
 * 集群基础测试类<BR>
 * 类BaseReplicationTest.java的实现描述：TODO 类实现描述
 * 
 * @author zalot.zhaoh Mar 5, 2012 6:19:57 PM
 */
public class TestBase {

    protected static Random              rnd    = new Random();
    protected static HBaseTestingUtility _util1;
    protected static Configuration       _confA;
    protected static HTablePool          _poolA;
    protected static boolean             _init1 = false;

    protected static HBaseTestingUtility _util2;
    protected static Configuration       _conf2;
    protected static HTablePool          _pool2;
    protected static boolean             _init2 = false;

    @AfterClass
    public static void closed() throws Exception {
        if (_init1) {
            _util1.shutdownMiniCluster();
            _util1.shutdownMiniZKCluster();
        }
        if (_init2) {
            _util2.shutdownMiniCluster();
            _util2.shutdownMiniZKCluster();
        }
    }

    public static void init() throws Exception {
        initClusterA();
        initClusterB();
    }

    public static void initClusterA() {
        _confA = HBaseConfiguration.create();
        _confA.setBoolean("hbase.regionserver.info.port.auto", true);
        _confA.setLong("hbase.regionserver.hlog.blocksize", 1024 * 200);
        _confA.setInt("hbase.regionserver.maxlogs", 2);
        _confA.set(HConstants.ZOOKEEPER_ZNODE_PARENT, "/1");
        _confA.setBoolean("dfs.support.append", true);
        _confA.setInt("hbase.master.info.port", 60010);
    }

    public static void startHBaseClusterA(int hbaseNum, int zkNum) throws Exception {
        _util1 = new HBaseTestingUtility(_confA);
        _util1.startMiniZKCluster(zkNum);
        if (hbaseNum > 0) _util1.startMiniCluster(1, hbaseNum);
        _poolA = new HTablePool(_confA, 20);
        _init1 = true;
    }

    public static void initClusterB() {
        _conf2 = HBaseConfiguration.create();
        _conf2.set(HConstants.ZOOKEEPER_ZNODE_PARENT, "/2");
        _conf2.setBoolean("dfs.support.append", true);
        _conf2.setBoolean("hbase.regionserver.info.port.auto", true);
        _conf2.setLong("hbase.regionserver.hlog.blocksize", 1024);
        _conf2.setInt("hbase.master.info.port", 60011);
    }

    public static void startHBaseClusterB(int hbaseNum, int zkNum) throws Exception {
        _util2 = new HBaseTestingUtility(_conf2);
        _util2.startMiniZKCluster(zkNum);
        if (hbaseNum > 0) _util2.startMiniCluster(1, hbaseNum);
        _pool2 = new HTablePool(_conf2, 20);
        _init2 = true;
    }

    public static String getRndString(String base) {
        return base + UUID.randomUUID().toString().substring(0, 10);
    }

    public static String getRndStringInArray(String[] strArray) {
        return strArray[rnd.nextInt(strArray.length)];
    }

    public static void createDefTable(Configuration conf) throws IOException {
        createTable(conf, new String[] { "testA", "testB" }, new String[] { "colA", "colB" });
    }

    public static void createTable(Configuration conf, String[] tables, String[] familys) throws IOException {
        HBaseAdmin admin = new HBaseAdmin(conf);
        for (String tableInfo : tables) {
            HTableDescriptor htableDes = new HTableDescriptor(tableInfo);
            for (String family : familys) {
                htableDes.addFamily(new HColumnDescriptor(family));
            }
            admin.createTable(htableDes);
        }
    }

    public static void insertRndData(HTablePool pool, String[] tables, String[] familys, String qualifier, int size)
                                                                                                                    throws IOException {
        for (String table : tables) {
            for (String family : familys)
                insertRndData(pool, table, family, qualifier, size);
        }
    }

    public static void insertRndData(HTablePool pool, String table, String family, String qualifier, int size)
                                                                                                              throws IOException {
        HTableInterface htable = pool.getTable(table);
        List<Put> puts = new ArrayList<Put>();
        for (int x = 0; x < size; x++) {
            Put put = new Put(getRndString(table).getBytes());
            put.add(family.getBytes(), qualifier == null ? getRndString("rnd-").getBytes() : qualifier.getBytes(),
                    getRndString(null).getBytes());
            puts.add(put);
        }
        htable.put(puts);
        System.out.println("insert Data " + size);
    }

    public static void printDFS(FileSystem fs, Path path) throws IOException {
        if (fs.isFile(path)) {
            System.out.println(path.toString() + " - len = " + fs.getFileStatus(path).getLen());
        } else {
            for (FileStatus fss : fs.listStatus(path)) {
                printDFS(fs, fss.getPath());
            }
        }
    }
}
