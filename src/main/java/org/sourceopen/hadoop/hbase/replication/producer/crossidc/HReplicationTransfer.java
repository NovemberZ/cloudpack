package org.sourceopen.hadoop.hbase.replication.producer.crossidc;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.regionserver.wal.HLog.Entry;
import org.apache.zookeeper.KeeperException;

import org.sourceopen.hadoop.hbase.replication.core.HBaseService;
import org.sourceopen.hadoop.hbase.replication.core.hlog.domain.HLogEntry;
import org.sourceopen.hadoop.hbase.replication.core.hlog.domain.HLogGroup;
import org.sourceopen.hadoop.hbase.replication.core.hlog.domain.HLogPersistence;
import org.sourceopen.hadoop.hbase.replication.core.hlog.domain.HLogEntry.Type;
import org.sourceopen.hadoop.hbase.replication.core.hlog.reader.HLogReader;
import org.sourceopen.hadoop.hbase.replication.producer.UuidService;
import org.sourceopen.hadoop.hbase.replication.protocol.MetaData;
import org.sourceopen.hadoop.hbase.replication.protocol.ProtocolAdapter;
import org.sourceopen.hadoop.hbase.replication.protocol.ProtocolBody;
import org.sourceopen.hadoop.hbase.replication.protocol.ProtocolHead;
import org.sourceopen.hadoop.hbase.replication.utility.HLogUtil;
import org.sourceopen.hadoop.hbase.replication.utility.ProducerConstants;

/**
 * HReplicationProducer <BR>
 * 1. <BR>
 * 2. 负责将 group 中的数据搬运至 Protocol 中 <BR>
 * 类HBaseReplicationSink.java的实现描述：TODO 类实现描述
 * 
 * @author zalot.zhaoh Feb 29, 2012 2:27:45 PM
 */
public class HReplicationTransfer implements Runnable {

    protected static final Log LOG                      = LogFactory.getLog(HReplicationTransfer.class);
    private long               minGroupOperatorInterval = ProducerConstants.HLOG_GROUP_INTERVAL;
    private long               maxReaderBuffer          = ProducerConstants.HLOG_READERBUFFER;
    private long               replicationSleepTime;

    // 外部对象
    protected ProtocolAdapter  adapter;
    protected HLogPersistence  hlogEntryPersistence;
    protected HBaseService     hlogService;

    public HReplicationTransfer(Configuration conf) throws IOException, KeeperException, InterruptedException{
        maxReaderBuffer = conf.getLong(ProducerConstants.CONFKEY_HLOG_READERBUFFER, ProducerConstants.HLOG_READERBUFFER);
        minGroupOperatorInterval = conf.getLong(ProducerConstants.CONFKEY_HLOG_GROUP_INTERVAL,
                                                ProducerConstants.HLOG_GROUP_INTERVAL);
        replicationSleepTime = conf.getLong(ProducerConstants.CONFKEY_CROSSIDC_REPLICATION_SLEEPTIME,
                                            ProducerConstants.CROSSIDC_REPLICATION_SLEEPTIME);
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(replicationSleepTime);
                doProducer();
            } catch (Exception e) {
                LOG.error("Producer running error ", e);
            }
        }
    }

    public void doProducer() throws Exception {
        List<String> groups = hlogEntryPersistence.listGroupName();
        for (String groupName : groups) {
            if (hlogEntryPersistence.lockGroup(groupName)) {
                HLogGroup group = hlogEntryPersistence.getGroupByName(groupName, false);
                if (group != null) {
                    // 每个Group不能连续操作，需要间隔 (优化后)
                    if (group.getLastOperatorTime() + minGroupOperatorInterval < System.currentTimeMillis()) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug(HLogUtil.getBaseInfo(null) + " - doGroup " + group);
                        }
                        doSinkGroup(group);
                        group.setLastOperatorTime(System.currentTimeMillis());
                        hlogEntryPersistence.updateGroup(group, false);
                    }
                }
            }
            // FIXME by dsh 此处存在这线程中断然后无人释放锁的风险
            // FIXME 锁的内容容器唯一（相同hlogEntryPersistence的锁内容相同），isMeLockGroup可以避免多进程问题，但是无法避免多线程问题
            if (hlogEntryPersistence.isMeLockGroup(groupName)) {
                hlogEntryPersistence.unlockGroup(groupName);
            }
        }
    }

    private void doSinkGroup(HLogGroup group) throws Exception {
        List<HLogEntry> entrys = hlogEntryPersistence.listEntry(group.getGroupName());
        Collections.sort(entrys);
        HLogReader reader = null;
        HLogEntry entry;

        ProtocolHead head = MetaData.getProtocolHead(hlogService.getConf());
        ProtocolBody body = null;

        for (int idx = 0; idx < entrys.size(); idx++) {
            entry = entrys.get(idx);
            if (entry.getType() == Type.END || entry.getType() == Type.UNKNOW) {
                continue;
            }
            reader = hlogService.getReader(entry);
            body = MetaData.getProtocolBody(head);
            int count = 0;
            while (true) {
                Entry ent = null;
                try {
                    ent = reader.next();
                } catch (OutOfMemoryError e) {
                    entry.setPos(entry.getLastVerifiedPos());
                    hlogEntryPersistence.updateEntry(entry);
                    return;
                } catch (Exception e) {
                    entry.setPos(entry.getLastVerifiedPos());
                    hlogEntryPersistence.updateEntry(entry);
                    return;
                }
                if (ent == null) {
                    break;
                } else {
                    entry.setLastVerifiedPos(reader.getPosition());
                }
                // FIXME by dsh 是否完成多记录的去重与合并
                count = count + HLogUtil.put2Body(ent, body, UuidService.getMySelfUUID());
                if (count > maxReaderBuffer) {
                    setHead(head, group.getGroupName(), entry.getTimestamp(), entry.getPos(), reader.getPosition(),
                            count);
                    if (doSinkPart(head, body)) {
                        entry.setPos(reader.getPosition());
                        hlogEntryPersistence.updateEntry(entry);
                        body = MetaData.getProtocolBody(head);
                    } else {
                        reader.seek(entry.getPos());
                    }
                    count = 0;
                }
            }

            if (count > 0) {
                setHead(head, group.getGroupName(), entry.getTimestamp(), entry.getPos(), reader.getPosition(), count);
                if (!doSinkPart(head, body)) {
                    // 如果失败则返回,不再继续更新
                    return;
                }
            }

            // 如果指针移动了则更新
            if (entry.getPos() < reader.getPosition()) {
                entry.setPos(reader.getPosition());
            }

            // 如果后面还有 HLogEntry 则说明这个 Reader 的数据都以经读完 (优化后)
            if (reader.getPosition() > 0 && idx + 1 < entrys.size()) {
                entry.setType(Type.END);
            }

            hlogEntryPersistence.updateEntry(entry);
            reader.close();
        }
    }

    private void setHead(ProtocolHead head, String groupName, long timeStamp, long start, long end, long count) {
        head.setCount(count);
        head.setGroupName(groupName);
        head.setFileTimestamp(timeStamp);
        head.setStartOffset(start);
        head.setEndOffset(end);
    }

    private boolean doSinkPart(ProtocolHead head, ProtocolBody body) {
        if (doAdapter(head, body)) {
            return true;
        }
        return false;
    }

    private boolean doAdapter(ProtocolHead head, ProtocolBody body) {
        MetaData data = new MetaData(head, body);
        try {
            adapter.write(data);
            return true;
        } catch (Exception e) {
            LOG.error("doAdapter error " + head, e);
            return false;
        }
    }

    public ProtocolAdapter getAdapter() {
        return adapter;
    }

    public void setAdapter(ProtocolAdapter adapter) {
        this.adapter = adapter;
    }

    public HLogPersistence getHlogEntryPersistence() {
        return hlogEntryPersistence;
    }

    public void setHLogPersistence(HLogPersistence hlogEntryPersistence) {
        this.hlogEntryPersistence = hlogEntryPersistence;
    }

    public HBaseService getHlogService() {
        return hlogService;
    }

    public void setHBaseService(HBaseService hlogService) {
        this.hlogService = hlogService;
    }

    public static HReplicationTransfer newInstance(Configuration conf, ProtocolAdapter adapter, HLogPersistence dao,
                                                   HBaseService service) throws IOException, KeeperException,
                                                                        InterruptedException {
        HReplicationTransfer prod = new HReplicationTransfer(conf);
        prod.setAdapter(adapter);
        prod.setHLogPersistence(dao);
        prod.setHBaseService(service);
        return prod;
    }
}
