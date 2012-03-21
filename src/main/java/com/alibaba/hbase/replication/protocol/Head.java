package com.alibaba.hbase.replication.protocol;

import java.io.Serializable;

/**
 * 协议中的 Head 信息 用于 <BR>
 * 1. 传输的基本信息 2. 失败重传的信息 <BR>
 * 
 * @author zalot.zhaoh Feb 28, 2012 3:53:56 PM
 */
public class Head implements Serializable {

    @Override
    public String toString() {
        return "Head [version=" + version + ", groupName=" + groupName + ", fileTimestamp=" + fileTimestamp
               + ", headTimestamp=" + headTimestamp + ", startOffset=" + startOffset + ", endOffset=" + endOffset
               + ", count=" + count + ", retry=" + retry + "]";
    }

    /**
     * 
     */
    private static final long serialVersionUID = 8360136866546325565L;

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public long getFileTimestamp() {
        return fileTimestamp;
    }

    public void setFileTimestamp(long fileTimestamp) {
        this.fileTimestamp = fileTimestamp;
    }

    public long getHeadTimestamp() {
        return headTimestamp;
    }

    public void setHeadTimestamp(long headTimestamp) {
        this.headTimestamp = headTimestamp;
    }

    public long getStartOffset() {
        return startOffset;
    }

    public void setStartOffset(long startOffset) {
        this.startOffset = startOffset;
    }

    public long getEndOffset() {
        return endOffset;
    }

    public void setEndOffset(long endOffset) {
        this.endOffset = endOffset;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    /**
     * @return the retry
     */
    public int getRetry() {
        return retry;
    }

    /**
     * @param retry the retry to set
     */
    public void setRetry(int retry) {
        this.retry = retry;
    }

    /**
     * 版本号
     */
    protected int    version = 1;

    /**
     * 组名 HLog 文件名 = groupName + timeStamp
     */
    protected String groupName;

    /**
     * 文件时间 HLog 文件名 = groupName + timeStamp
     */
    protected long   fileTimestamp;

    /**
     * 头部时间 头部产生的时间，可用于 HLog文件名归类后的 二次排序
     */
    protected long   headTimestamp;

    /**
     * 起始偏移量 （用于 HLog.Reader 中的 position ）
     */
    protected long   startOffset;

    /**
     * 结束偏移量 （用于 HLog.Reader 中的 position ）
     */
    protected long   endOffset;

    /**
     * 数量
     */
    protected long   count;

    /**
     * 重试次数，对于正常文件此项为0
     */
    protected int    retry = 0;

}
