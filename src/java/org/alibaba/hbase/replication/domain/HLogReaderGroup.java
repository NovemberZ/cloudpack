package org.alibaba.hbase.replication.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.alibaba.hbase.replication.hlog.HLogReader;

/**
 * HLog 组
 * @author zalot.zhaoh
 * 
 */
public class HLogReaderGroup{
	String groupName;
	boolean isOver = false;
	protected List<HLogReader> readers = new ArrayList<HLogReader>();
	
	public HLogReaderGroup(String name) {
		this.groupName = name;
	}
	
	public void add(HLogReader reader) {
		readers.add(reader);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		HLogReaderGroup other = (HLogReaderGroup) obj;
		if (groupName == null) {
			if (other.groupName != null)
				return false;
		} else if (!groupName.equals(other.groupName))
			return false;
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((groupName == null) ? 0 : groupName.hashCode());
		return result;
	}

	public boolean isOver() {
		return isOver;
	}

	public void sort() {
		Collections.sort(readers);
	}
}
