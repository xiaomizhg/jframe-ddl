package com.sitech.jframe.ddl.spring.support.mybatis;

import static org.springframework.util.Assert.notNull;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.dao.support.DaoSupport;

import com.sitech.jframe.ddl.sharding.AbstractSharding;
import com.sitech.jframe.ddl.sharding.ISharding;

/**
 * 
 * @author zhangsf
 *
 */
public class DynamicSqlSessionDaoSupport extends DaoSupport {
	
	private SqlSession sqlSession;
	private SqlSessionFactory sqlSessionFactory;
	private ISharding sharding;

//	private boolean externalSqlSession;

	public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
		this.sqlSessionFactory = sqlSessionFactory;
	}
	

	public void setSharding(ISharding sharding) {
		this.sharding = sharding;
	}


	/**
	 * Users should use this method to get a SqlSession to call its statement
	 * methods This is SqlSession is managed by spring. Users should not
	 * commit/rollback/close it because it will be automatically done.
	 *
	 * @return Spring managed thread safe SqlSession
	 */
	public SqlSession getSqlSession() {
		return this.sqlSession;
	}
	
	

	/**
	 * {@inheritDoc}
	 */
	protected void checkDaoConfig() {
		notNull(this.sqlSessionFactory,
				"Property 'sqlSessionFactory' are required");
		
		// 初始化 DynamicSqlSessionTemplate
		this.sqlSession = new DynamicSqlSessionTemplate(sqlSessionFactory, sharding);
		
	}
	

}
