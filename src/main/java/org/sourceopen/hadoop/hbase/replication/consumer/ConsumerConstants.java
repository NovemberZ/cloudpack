/*
 * Copyright 2012 Alibaba.com All right reserved. This software is the
 * confidential and proprietary information of Alibaba.com ("Confidential
 * Information"). You shall not disclose such Confidential Information and shall
 * use it only in accordance with the terms of the license agreement you entered
 * into with Alibaba.com.
 */
package org.sourceopen.hadoop.hbase.replication.consumer;

import java.util.UUID;

import org.sourceopen.hadoop.hbase.replication.producer.ProducerConstants;

/**
 * 类Constant.java的实现描述：常量数据
 * 
 * @author dongsh 2012-2-29 上午10:25:47
 */
public class ConsumerConstants {

    // common
    public static final String FILE_SEPERATOR                      = "/";
    public static final String CHANNEL_NAME                        = "channel_";
    public static final String CONSUMER_CONFIG_FILE                = "META-INF/consumer-configuration.xml";
    public static final String COMMON_CONFIG_FILE                  = ProducerConstants.COMMON_CONFIG_FILE;
    // slave集群数据特有的clusterID
    public static final UUID   SLAVE_CLUSTER_ID                    = UUID.randomUUID();
    public static final int    WAIT_MILLIS                         = 500;
    public static final String MD5_DIR                             = "md5";
    public static final String MD5_SUFFIX                          = "_md5";

    // conf key
    public static final String CONFKEY_ROOT_ZOO                    = "org.sourceopen.hadoop.hbase.replication.consumer.zookeeper.znoderoot";
    public static final String CONFKEY_PRODUCER_FS                 = "org.sourceopen.hadoop.hbase.replication.producer.fs";
    public static final String CONFKEY_PRODUCER_FS_TARGET          = "org.sourceopen.hadoop.hbase.replication.producer.fs.target";
    public static final String PRODUCER_EMPTY_FS                   = "";
    public static final String CONFKEY_ZK_QUORUM                   = "org.sourceopen.hadoop.hbase.replication.consumer.zookeeper.quorum";

    public static final String CONFKEY_REP_FILE_CHANNEL_POOL_SIZE  = "org.sourceopen.hadoop.hbase.replication.consumer.fileChannelPoolSize";
    public static final String CONFKEY_REP_DATA_LAODING_POOL_SIZE  = "org.sourceopen.hadoop.hbase.replication.consumer.dataLoadingPoolSize";
    public static final String CONFKEY_REP_DATA_LAODING_BATCH_SIZE = "org.sourceopen.hadoop.hbase.replication.consumer.dataLoadingBatchSize";
    public static final String CONFKEY_THREADPOOL_SIZE             = "org.sourceopen.hadoop.hbase.replication.consumer.threadpool.queuesize";
    public static final String CONFKEY_THREADPOOL_KEEPALIVE_TIME   = "org.sourceopen.hadoop.hbase.replication.consumer.threadpool.keepAliveTime";
    // zk node
    public static final String ZK_CURRENT                          = "cur";
    public static final String ZK_QUEUE                            = "queue";
    public static final int    ZK_ANY_VERSION                      = -1;

    public static final String ROOT_ZOO                            = ProducerConstants.ROOT_ZOO;

}
