package com.sitech.jframe.ddl.mybatis;

import java.sql.Connection;
import java.util.Properties;

import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;

import com.sitech.jframe.ddl.datasource.DataSourceLookupKey;
import com.sitech.jframe.ddl.datasource.ReadWriteEnum;
import com.sitech.jframe.ddl.datasource.ReadWriteSeparate;
import com.sitech.jframe.ddl.utils.ReflectionUtil;

/**
 * mybatis 插件 ，用来解析分库分表内容
 * @author zhangsf
 *
 */
@Intercepts({@Signature(
		type=StatementHandler.class, 
		method="prepare", 
		args={Connection.class})})
public class StatementHandlerPlugin implements Interceptor {

	@Override
	public Object intercept(Invocation invocation) throws Throwable {
		
		MappedStatement mappedStatement = null;
		StatementHandler statementHandler = (StatementHandler)invocation.getTarget();
		if (statementHandler instanceof RoutingStatementHandler) {
			StatementHandler delegate = (StatementHandler) ReflectionUtil.getFieldValue(statementHandler, "delegate");
			mappedStatement = (MappedStatement) ReflectionUtil.getFieldValue(delegate, "mappedStatement");
		} else {
			mappedStatement = (MappedStatement) ReflectionUtil.getFieldValue(statementHandler, "mappedStatement");
		}
		
		String mapperId = mappedStatement.getId();
		String sql = statementHandler.getBoundSql().getSql();
		Object parameterObject = statementHandler.getBoundSql().getParameterObject();
		SqlCommandType sqlCommandType = mappedStatement.getSqlCommandType();
		
		ReadWriteSeparate readWriteSeparate = null;
		if (sqlCommandType == SqlCommandType.SELECT) {
			readWriteSeparate = new ReadWriteSeparate(ReadWriteEnum.READ);
		} else {
			readWriteSeparate = new ReadWriteSeparate(ReadWriteEnum.WRITE); 
		}
		readWriteSeparate.bindToThread();
		
		return invocation.proceed();
	}
	
	/**
	 * 解析 mybatis 的Signature 注解方法是否生成 proxy
	 */
	@Override
	public Object plugin(Object target) {
		return Plugin.wrap(target, this);
	}
	
	
	@Override
	public void setProperties(Properties properties) {
		// TODO Auto-generated method stub
	}
	
}
