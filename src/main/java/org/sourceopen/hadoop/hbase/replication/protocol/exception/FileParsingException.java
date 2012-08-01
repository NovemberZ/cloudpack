/*
 * Copyright 2012 Alibaba.com All right reserved. This software is the
 * confidential and proprietary information of Alibaba.com ("Confidential
 * Information"). You shall not disclose such Confidential Information and shall
 * use it only in accordance with the terms of the license agreement you entered
 * into with Alibaba.com.
 */
package org.sourceopen.hadoop.hbase.replication.protocol.exception;

/**
 * 类FileParsingException.java的实现描述： 文件解析出错
 * 
 * @author dongsh 2012-3-1 下午04:53:10
 */
public class FileParsingException extends Exception {

    public FileParsingException(String string, Exception e){
        super(string, e);
    }

    public FileParsingException(String string){
        super(string);
    }

    private static final long serialVersionUID = -5193043184877698845L;

}
