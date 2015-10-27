package com.sitech.jframe.ddl.spring.support.mybatis;

import org.mybatis.spring.SqlSessionFactoryBean;

import com.sitech.jframe.ddl.sharding.ISharding;
import com.sitech.jframe.ddl.sharding.ShardingContext;



public class DynamicSqlSessionFactoryBean extends SqlSessionFactoryBean {
	
	private ISharding sharding;

	
	public ISharding getSharding() {
		return sharding;
	}

	public void setSharding(ISharding sharding) {
		this.sharding = sharding;
	}
	
	
	public void afterPropertiesSet() throws Exception {
		super.afterPropertiesSet();
		ShardingContext.getInstance().setSharding(sharding);
	}
}
