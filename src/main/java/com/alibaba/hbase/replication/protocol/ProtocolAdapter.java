package com.alibaba.hbase.replication.protocol;

import org.apache.hadoop.conf.Configuration;

/**
 * 协议适配器 类ProtocolAdapter.java的实现描述：TODO 类实现描述
 * 
 * @author zalot.zhaoh Feb 28, 2012 2:26:38 PM
 */
@Deprecated
public interface ProtocolAdapter {

    public void write(MetaData data) throws Exception;

    public void init(Configuration conf);
}
