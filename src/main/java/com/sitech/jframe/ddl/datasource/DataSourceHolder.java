package com.sitech.jframe.ddl.datasource;

import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;


/**
 * DataSource 的资源使用情况, 包括 :
 * 1. 计数器
 * 2. 最后一次使用时间
 * @author zhangsf
 *
 */
public class DataSourceHolder implements Comparable<DataSourceHolder> {
	
	// DataSource 中 getConnection 调用测试
	private AtomicLong useCount = new AtomicLong();
	// DataSource 中 最后一次 调用时间
	private Date lastUserTime;
	
	private DataSource dataSource;
	
	
	public Long useCountIncrement() {
		return useCount.incrementAndGet();
	}
	
	public Long getUseCount() {
		return useCount.get();
	}
	
	public void lastUserTimeSetNow() {
		lastUserTime = new Date();
	}
 

	public Date getLastUserTime() {
		return lastUserTime;
	}

	public DataSource getDataSource() {
		return dataSource;
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}


	@Override
	public int compareTo(DataSourceHolder o) {
		Long useCount = o.getUseCount();
		return getUseCount().compareTo(useCount);
	}
	
}
