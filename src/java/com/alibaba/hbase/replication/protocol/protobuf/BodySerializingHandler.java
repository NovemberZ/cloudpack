/*
 * Copyright 2012 Alibaba.com All right reserved. This software is the
 * confidential and proprietary information of Alibaba.com ("Confidential
 * Information"). You shall not disclose such Confidential Information and shall
 * use it only in accordance with the terms of the license agreement you entered
 * into with Alibaba.com.
 */
package com.alibaba.hbase.replication.protocol.protobuf;

import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.hbase.replication.protocol.Body;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

/**
 * 类BodySerializingHandler.java的实现描述
 * 
 * @author dongsh 2012-2-27 下午06:52:09
 */
public class BodySerializingHandler {

    // private static Logger log = LoggerFactory.getLogger(BodySerializingHandler.class);

    public Body deserialize(byte[] in) throws InvalidProtocolBufferException {
        BodyBinObject.bodyBinObject bodyBinObject = BodyBinObject.bodyBinObject.parseFrom(in);
        Body body = new Body(bodyBinObject.getClusterId());
        List<BodyBinObject.tableBinObject> tableBinObjectList = bodyBinObject.getTableBinObjectListList();
        if (CollectionUtils.isNotEmpty(tableBinObjectList)) {
            for (BodyBinObject.tableBinObject tableBinObject : tableBinObjectList) {
                String tableName = tableBinObject.getTableName();
                List<BodyBinObject.editBinObject> editBinObjectList = tableBinObject.getEditBinObjectListList();
                for (BodyBinObject.editBinObject editBinObject : editBinObjectList) {
                    Body.Edit edit = new Body.Edit();
                    edit.setType(Body.Type.valueOfCode(editBinObject.getType()));
                    edit.setFamily(editBinObject.getFamily().toByteArray());
                    edit.setQualifier(editBinObject.getQualifier().toByteArray());
                    edit.setValue(editBinObject.getValue().toByteArray());
                    edit.setRowKey(editBinObject.getRowKey().toByteArray());
                    body.addEdit(tableName, edit);
                }
            }
        }
        return body;
    }

    public byte[] serialize(Body body) {
        // FIXME NPE
        BodyBinObject.bodyBinObject.Builder bodyBinObject = BodyBinObject.bodyBinObject.newBuilder();
        bodyBinObject.setClusterId(body.getClusterId());
        for (String tableName : body.getEditMap().keySet()) {
            BodyBinObject.tableBinObject.Builder tableBinObject = BodyBinObject.tableBinObject.newBuilder();
            tableBinObject.setTableName(tableName);
            for (Body.Edit edit : body.getEditMap().get(tableName)) {
                BodyBinObject.editBinObject.Builder editBinObject = BodyBinObject.editBinObject.newBuilder();
                editBinObject.setType(edit.getType().getCode());
                editBinObject.setFamily(ByteString.copyFrom(edit.getFamily()));
                editBinObject.setQualifier(ByteString.copyFrom(edit.getQualifier()));
                editBinObject.setValue(ByteString.copyFrom(edit.getValue()));
                editBinObject.setRowKey(ByteString.copyFrom(edit.getRowKey()));
                tableBinObject.addEditBinObjectList(editBinObject);
            }
            bodyBinObject.addTableBinObjectList(tableBinObject);
        }
        return bodyBinObject.build().toByteArray();
    }
}
