package com.sitech.jframe.ddl.datasource.lb;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

public abstract class AbstractDataSourceSelector implements IDataSourceSelector {
	
	private final static ThreadLocal<Map<String, DataSource>> slaveDs = new ThreadLocal<Map<String, DataSource>>();

	private Map<String, List<DataSource>> dataSources;

	public Map<String, List<DataSource>> getDataSources() {
		return dataSources;
	}

	public void setDataSources(Map<String, List<DataSource>> dataSources) {
		this.dataSources = dataSources;
	}

	public List<DataSource> getDataSourceList(String lookupKey) {
		return dataSources.get(lookupKey);
	}

	public DataSource[] getDataSourceArray(String lookupKey) {
		return dataSources.get(lookupKey) == null ? null
				: dataSources.get(lookupKey).toArray(new DataSource[0]);
	}
	
	
	@Override
	public DataSource selector(String lookupKey) {
		Map<String, DataSource> slaveMap = slaveDs.get();
		DataSource slaveDataSource = null;
		if (slaveMap == null) {
			Map<String, DataSource> map = new HashMap<String, DataSource>();
			slaveDs.set(map);
			
			slaveDataSource = doSelector(lookupKey);
			map.put(lookupKey, slaveDataSource);
		} else {
			if (!slaveMap.containsKey(lookupKey)) {
				slaveDataSource = doSelector(lookupKey);
				slaveMap.put(lookupKey, slaveDataSource);
			} else {
				slaveDataSource = slaveMap.get(lookupKey);
			}
		}
		
		return slaveDataSource;
	}
	
	
	public abstract DataSource doSelector(String lookupKey);

}
