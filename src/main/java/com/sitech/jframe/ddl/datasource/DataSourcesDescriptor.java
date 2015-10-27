package com.sitech.jframe.ddl.datasource;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;

import com.sitech.jframe.ddl.datasource.lb.DefaultDataSourceSelector;
import com.sitech.jframe.ddl.datasource.lb.IDataSourceSelector;

/**
 * 分布式数据库定义
 * @author zhangsf
 *
 */
public class DataSourcesDescriptor implements InitializingBean, BeanNameAware {
	
	private String identity;
	
	private DataSource masterDataSource;
	
	private List<DataSource> slaveDataSourceList;
	
	private boolean defaultDataSources = false;
	

	public String getIdentity() {
		return identity;
	}
	
	public void setIdentity(String identity) {
		this.identity = identity;
	}

	public DataSource getMasterDataSource() {
		return masterDataSource;
	}

	public void setMasterDataSource(DataSource masterDataSource) {
		this.masterDataSource = masterDataSource;
	}

	public List<DataSource> getSlaveDataSourceList() {
		return slaveDataSourceList;
	}

	public void setSlaveDataSourceList(List<DataSource> slaveDataSourceList) {
		this.slaveDataSourceList = slaveDataSourceList;
	}
	
	

	public boolean isDefaultDataSources() {
		return defaultDataSources;
	}

	public void setDefaultDataSources(boolean defaultDataSources) {
		this.defaultDataSources = defaultDataSources;
	}
	

	@Override
	public void afterPropertiesSet() throws Exception {
		
	}

	@Override
	public void setBeanName(String name) {
		if (identity == null) {
			this.identity = name;
		}
	}
	
	
}
