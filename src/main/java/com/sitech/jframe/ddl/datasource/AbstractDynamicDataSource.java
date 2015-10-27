package com.sitech.jframe.ddl.datasource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.jdbc.datasource.lookup.DataSourceLookup;
import org.springframework.jdbc.datasource.lookup.JndiDataSourceLookup;
import org.springframework.util.Assert;

import com.sitech.jframe.ddl.datasource.lb.DefaultDataSourceSelector;
import com.sitech.jframe.ddl.datasource.lb.IDataSourceSelector;

/***
 * 
 * 动态数据源 , 根据 {@DataSourceLookupKey} 选择数据源
 * @author zhangsf
 *
 */
public abstract class AbstractDynamicDataSource extends AbstractDataSource implements InitializingBean {
	
	private Map<String, DataSource> masterDataSources = new HashMap<String, DataSource>();
	
	private Map<String, List<DataSource>> slaveDataSources = new HashMap<String, List<DataSource>>();
	
//	private Object defaultTargetDataSource;
//	
//	private DataSource resolvedDefaultDataSource;
	
	private DataSourceLookup dataSourceLookup = new JndiDataSourceLookup();
	
	private Set<DataSourcesDescriptor> dataSourceDescriptors = new HashSet<DataSourcesDescriptor>();
	
	private List<String> identitys = new ArrayList<String>();
	
	private boolean hasDefaultDataSources = false;
	
	private IDataSourceSelector dataSourceSelector;
	
	/**
	 * 根据数据源描述信息 DataSourcesDescriptor ， 生成带标识的数据源 
	 */
	@Override
	public void afterPropertiesSet() {
		// 设置 主从数据库 DataSource
		for (DataSourcesDescriptor descriptor : dataSourceDescriptors) {
			String identity = descriptor.getIdentity(); // 数据源标识
			boolean isDefaultDataSources = descriptor.isDefaultDataSources(); // 可以标识默认数据源
			DataSource masterObject = descriptor.getMasterDataSource();  // 主数据源  master DataSource
			List<DataSource> slaveObjectList = descriptor.getSlaveDataSourceList(); // 从数据源 slave DataSource list
			
			if (identity == null || ("").equals(identity)) {
				throw new IllegalArgumentException("DataSourcesDescriptor 配置中 identity 标识不能为空!!!");
			}
			
			if (isDefaultDataSources && hasDefaultDataSources) {
				throw new IllegalArgumentException("default identity has been setting!!!");
			}
			
			if (isDefaultDataSources) { // 额外多增加一个 default 标识, 默认的数据源选择
				 //如果 identity 要作为默认的数据源， 则额外增加一个默认数据源标识
				List<DataSource> listClone = new ArrayList<DataSource>();
				listClone.addAll(slaveObjectList);
				prepareDataSourcesByIdentity(DataSourceLookupKey.defaultDataSourceKey, masterObject, listClone);
				hasDefaultDataSources = true;
			}
			
			prepareDataSourcesByIdentity(identity, masterObject, slaveObjectList);
		}
		
		if (!hasDefaultDataSources) {
			logger.warn("...... 未设置默认数据源  defaultDataSources 标识 , 将把第一个数据源配置设置为默认数据源");
			if (dataSourceDescriptors.iterator().hasNext()) { // 设置一个默认数据源
				DataSourcesDescriptor defaultDescriptor = dataSourceDescriptors.iterator().next();
				List<DataSource> listClone = new ArrayList<DataSource>();
				listClone.addAll(defaultDescriptor.getSlaveDataSourceList());
				prepareDataSourcesByIdentity(DataSourceLookupKey.defaultDataSourceKey, defaultDescriptor.getMasterDataSource(), listClone);
				hasDefaultDataSources = true;
			}
		}
		
		checkIllegalDataSourceCfg(); // 校验数据源配置是否合法
		
		// 设置默认数据库 DataSource
//		if (this.defaultTargetDataSource != null) {
//			this.resolvedDefaultDataSource = resolveSpecifiedDataSource(this.defaultTargetDataSource);
//		}
		
		setDataSourceSelectorIfNecessary();
		dataSourceSelector.setDataSources(slaveDataSources);
	}
	
	
	private void prepareDataSourcesByIdentity(String identity, DataSource masterDataSource, List<DataSource> slaveDataSourcesList) {
		identitys.add(identity);
		Assert.notNull(masterDataSource, "Master DataSource 不能为 NULL !!!");
		masterDataSources.put(identity, masterDataSource);
		if (slaveDataSourcesList != null && !slaveDataSourcesList.isEmpty()) { // TODO
			if (slaveDataSourcesList.contains(masterDataSource)) {// 有可能读写配置是同一个库，则remove读库， 使得读库默认指向写库 
				slaveDataSourcesList.remove(masterDataSource);
			}
			slaveDataSources.put(identity, slaveDataSourcesList);
		}
	}
	
	/**
	 * 校验 masterDataSources 与 slaveDataSources 的合法性
	 * 主要校验 masterDataSources 中的 DataSource 不能出现在  slaveDataSources 中， master 和 slave 不能重复
	 */
	private void checkIllegalDataSourceCfg() {
		for (Map.Entry<String, DataSource> masterEntry : masterDataSources.entrySet()) {
			DataSource masterDs = masterEntry.getValue();
			for (Map.Entry<String, List<DataSource>> slaveEntry : slaveDataSources.entrySet()) {
				List<DataSource> slaveList = slaveEntry.getValue();
				if (slaveList.contains(masterDs)) {
					throw new IllegalArgumentException("Master identity : " + masterEntry.getKey() + " 与 Slave identity : " + slaveEntry.getKey() + " DataSource 有冲突!");
				}
			}
		}
	}
	
	
	/**
	 * 从一个 array 数组DataSource 中选择一个 DataSource， 默认实现是随机选择
	 */
	private void setDataSourceSelectorIfNecessary() {
		if(dataSourceSelector == null) {
			
			this.dataSourceSelector = new DefaultDataSourceSelector();
		}
	}
	
	
	/*protected DataSource resolveSpecifiedDataSource(Object dataSource) throws IllegalArgumentException {
		if (dataSource instanceof DataSource) {
			return (DataSource) dataSource;
		}
		else if (dataSource instanceof String) {
			return this.dataSourceLookup.getDataSource((String) dataSource);
		}
		else {
			throw new IllegalArgumentException(
					"Illegal data source value - only [javax.sql.DataSource] and String supported: " + dataSource);
		}
	}*/
	

	
	
	/**
	 * 根据ThreadLocal信息确定路由数据源
	 * @return
	 */
	protected DataSourceWrapper determineTargetDataSource() {
//		Assert.notEmpty(this.masterDataSources, "DataSource router not initialized");
//		Assert.notEmpty(this.masterDataSources, "DataSource router not initialized");
		DataSource targetDataSource = null;
		DataSourceWrapper dataSourceWrapper = new DataSourceWrapper();
		DataSourceLookupKey dataSourceLookupKey = determineCurrentLookupKey();
		ReadWriteSeparate readWriteSeparate = determineCurrentReadWrite();
		
		if (dataSourceLookupKey == null && readWriteSeparate == null) { // 如果未设置线程变量 DataSourceLookupKey / ReadWriteSeparate ， 返回 默认配置的 master DataSource
			DataSource dataSourceDef = this.masterDataSources.get(DataSourceLookupKey.defaultDataSourceKey);
			dataSourceWrapper.setTargetDataSource(dataSourceDef);
			dataSourceWrapper.setReadWriteEnum(ReadWriteEnum.WRITE);
			dataSourceWrapper.setDataSourceKey(DataSourceLookupKey.defaultDataSourceKey);
			return dataSourceWrapper;
		}
		
		if (dataSourceLookupKey == null && readWriteSeparate != null) { // 未设置 DataSourceLookupKey ， 则返回 默认的数据源 defaultDataSourceKey 
			ReadWriteEnum readWriteEnum =  readWriteSeparate.getReadWriteEnum();
			if (readWriteEnum == ReadWriteEnum.READ 
					&& !slaveDataSources.isEmpty() 
					&& slaveDataSources.get(DataSourceLookupKey.defaultDataSourceKey) != null 
					&& !slaveDataSources.get(DataSourceLookupKey.defaultDataSourceKey).isEmpty()) {
				targetDataSource = dataSourceSelector.selector(DataSourceLookupKey.defaultDataSourceKey); // 返回 NULL 的情况未考虑
				dataSourceWrapper.setReadWriteEnum(ReadWriteEnum.READ);
				dataSourceWrapper.setDataSourceKey(DataSourceLookupKey.defaultDataSourceKey);
			} else {
				targetDataSource = masterDataSources.get(DataSourceLookupKey.defaultDataSourceKey);
				dataSourceWrapper.setReadWriteEnum(ReadWriteEnum.WRITE);
				dataSourceWrapper.setDataSourceKey(DataSourceLookupKey.defaultDataSourceKey);
			}
		}
		
		if (dataSourceLookupKey != null && readWriteSeparate == null) { // 未设置读写标识则默认为写库
			String lookupKey = dataSourceLookupKey.getDataSourceKey() == null ? DataSourceLookupKey.defaultDataSourceKey : dataSourceLookupKey.getDataSourceKey();
			targetDataSource = masterDataSources.get(lookupKey);
			dataSourceWrapper.setReadWriteEnum(ReadWriteEnum.WRITE);
			dataSourceWrapper.setDataSourceKey(lookupKey);
		}
		
		if (dataSourceLookupKey != null && readWriteSeparate != null) {
			String lookupKey = dataSourceLookupKey.getDataSourceKey() == null ? DataSourceLookupKey.defaultDataSourceKey : dataSourceLookupKey.getDataSourceKey();
			ReadWriteEnum readWriteEnum =  readWriteSeparate.getReadWriteEnum();
			if (readWriteEnum == ReadWriteEnum.READ 
					&& !slaveDataSources.isEmpty() 
					&& slaveDataSources.get(lookupKey) != null 
					&& !slaveDataSources.get(lookupKey).isEmpty()) {
				targetDataSource = dataSourceSelector.selector(lookupKey);
				dataSourceWrapper.setReadWriteEnum(ReadWriteEnum.READ);
				dataSourceWrapper.setDataSourceKey(lookupKey);
			} else {
				targetDataSource = masterDataSources.get(lookupKey);
				dataSourceWrapper.setReadWriteEnum(ReadWriteEnum.WRITE);
				dataSourceWrapper.setDataSourceKey(lookupKey);
			}
		}
		
		
		if (dataSourceWrapper.getReadWriteEnum() == ReadWriteEnum.READ && targetDataSource == null) { // 如果是读 数据源， 并且为NULL, 则切换到写 数据源，【只配置写数据源，未配置读数据源】
			logger.warn("lookupKey : " + dataSourceWrapper.getDataSourceKey() + " 未找到对应的 读 数据源, 自动切换到 写 数据源 !");
			targetDataSource = this.masterDataSources.get(dataSourceWrapper.getDataSourceKey());
			dataSourceWrapper.setReadWriteEnum(ReadWriteEnum.WRITE);
		}
		if (targetDataSource == null) {
			throw new IllegalStateException("Cannot determine target DataSource for lookup key [" + dataSourceLookupKey.getDataSourceKey() + "] , "
					+ "actual lookup key [" + dataSourceWrapper.getDataSourceKey() + "]");
		}
		dataSourceWrapper.setTargetDataSource(targetDataSource);
		return dataSourceWrapper;
	}
	
	
	
	public void setDataSourceLookup(DataSourceLookup dataSourceLookup) {
		this.dataSourceLookup = (dataSourceLookup != null ? dataSourceLookup : new JndiDataSourceLookup());
	}
	
	
	public Set<DataSourcesDescriptor> getDataSourceDescriptors() {
		return dataSourceDescriptors;
	}

	public void setDataSourceDescriptors(
			Set<DataSourcesDescriptor> dataSourceDescriptors) {
		this.dataSourceDescriptors = dataSourceDescriptors;
	}

	public Map<String, DataSource> getMasterDataSources() {
		return masterDataSources;
	}

	public Map<String, List<DataSource>> getSlaveDataSources() {
		return slaveDataSources;
	}
	
	public List<String> getIdentitys() {
		return identitys;
	}
	
	public String getIdentitysStr() {
		StringBuilder sb = new StringBuilder();
		for (String identity : getIdentitys()) {
			sb.append("{");
			sb.append(identity);
			sb.append("}, ");
		}
		return sb.toString();
	}


	public IDataSourceSelector getDataSourceSelector() {
		return dataSourceSelector;
	}

	public void setDataSourceSelector(IDataSourceSelector dataSourceSelector) {
		this.dataSourceSelector = dataSourceSelector;
	}
	
	
	
	protected DataSourceWrapper getTargetDataSource() {
		DataSourceWrapper dataSourceWrapper = determineTargetDataSource();
		return dataSourceWrapper;
	}

	protected abstract DataSourceLookupKey determineCurrentLookupKey();
	
	protected abstract ReadWriteSeparate determineCurrentReadWrite();
	
	
	public class DataSourceWrapper {
		
		DataSource targetDataSource;
		ReadWriteEnum readWriteEnum;
		String dataSourceKey;
		
		public DataSourceWrapper() {
		}
		
		public DataSourceWrapper(DataSource targetDataSource, ReadWriteEnum readWriteEnum, String dataSourceKey) {
			this.targetDataSource = targetDataSource;
			this.readWriteEnum = readWriteEnum;
			this.dataSourceKey = dataSourceKey;
		}
		
		public DataSource getTargetDataSource() {
			return targetDataSource;
		}
		public void setTargetDataSource(DataSource targetDataSource) {
			this.targetDataSource = targetDataSource;
		}
		public ReadWriteEnum getReadWriteEnum() {
			return readWriteEnum;
		}
		public void setReadWriteEnum(ReadWriteEnum readWriteEnum) {
			this.readWriteEnum = readWriteEnum;
		}
		public String getDataSourceKey() {
			return dataSourceKey;
		}
		public void setDataSourceKey(String dataSourceKey) {
			this.dataSourceKey = dataSourceKey;
		}
	}
}
