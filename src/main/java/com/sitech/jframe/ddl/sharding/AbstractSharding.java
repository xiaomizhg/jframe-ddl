package com.sitech.jframe.ddl.sharding;

import org.apache.ibatis.mapping.SqlCommandType;

import com.sitech.jframe.ddl.datasource.ReadWriteEnum;

public abstract class AbstractSharding implements ISharding {
	
	// SQL 语句
	private static final ThreadLocal<String> sqlTL = new ThreadLocal<String>();
	
	// SQL 入参
	private static final ThreadLocal<Object> parameterTL = new ThreadLocal<Object>();
	
	// mybatis statementId
	private static final ThreadLocal<String> statementIdTL = new ThreadLocal<String>();
	
	// Sql 操作类型
	private static final ThreadLocal<SqlCommandType> sqlCommandTypeTL = new ThreadLocal<SqlCommandType>();
	
	
	/**
	 * 重置 分片
	 */
	public void reset() {
		sqlTL.remove();
		parameterTL.remove();
		statementIdTL.remove();
		sqlCommandTypeTL.remove();
	}
	
	
	public static String getSql() {
		return sqlTL.get();
	}
	
	public static void setSql(String sql) {
		sqlTL.set(sql);
	}
	
	public static Object getParameter() {
		return parameterTL.get();
	}
	
	public static void setParameter(Object parameter) {
		parameterTL.set(parameter);
	}
	
	public static String getStatementId() {
		return statementIdTL.get();
	}
	
	public static void setStatementId(String statementId) {
		statementIdTL.set(statementId);
	}
	
	public static SqlCommandType getSqlCommandType() {
		return sqlCommandTypeTL.get();
	}
	
	public static void setSqlCommandType(SqlCommandType sqlCommandType) {
		sqlCommandTypeTL.set(sqlCommandType);
	}
	
	
	/**
	 * 如果存在事务 ，则默认使用 Spring 事务属性确定读写  @see : ExtTransactionInterceptor
	 * 如果不存在事务, 则 读走读库 ， 写走写库
	 */
	@Override
	public ReadWriteEnum getReadWrite() {
		String sql = getSql();
		String statementId = getStatementId();
		Object param = getParameter();
		return getReadWrite(statementId, sql, param);
	}
	
	
	public ReadWriteEnum getReadWrite(String statementId, String sql, Object param) {
		return null;
	}
	
	
	@Override
	public String getDataSourceKey() {
		String sql = getSql();
		String statementId = getStatementId();
		Object param = getParameter();
		return getDataSourceKey(statementId, sql, param);
	}
	
	
	public abstract String getDataSourceKey(String statementId, String sql, Object param);
	
}
