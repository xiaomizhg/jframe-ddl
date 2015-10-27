package com.sitech.jframe.ddl.spring.support.mybatis;

import static java.lang.reflect.Proxy.newProxyInstance;
import static org.apache.ibatis.reflection.ExceptionUtil.unwrapThrowable;
import static org.mybatis.spring.SqlSessionUtils.closeSqlSession;
import static org.mybatis.spring.SqlSessionUtils.getSqlSession;
import static org.mybatis.spring.SqlSessionUtils.isSqlSessionTransactional;
import static org.springframework.util.Assert.notNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSession.StrictMap;
import org.mybatis.spring.MyBatisExceptionTranslator;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.dao.support.PersistenceExceptionTranslator;

import com.sitech.jframe.ddl.datasource.DataSourceLookupKey;
import com.sitech.jframe.ddl.datasource.DynamicDataSourceContextHolder;
import com.sitech.jframe.ddl.datasource.ReadWriteEnum;
import com.sitech.jframe.ddl.datasource.ReadWriteSeparate;
import com.sitech.jframe.ddl.sharding.AbstractSharding;
import com.sitech.jframe.ddl.sharding.ISharding;
import com.sitech.jframe.ddl.sharding.NoSharding;
import com.sitech.jframe.ddl.sharding.ShardingContext;



/**
 * 
 * @author zhangsf
 *
 */
public class DynamicSqlSessionTemplate implements SqlSession {

	private final SqlSessionFactory sqlSessionFactory;

	private final ExecutorType executorType;

	private final SqlSession sqlSessionProxy;

	private final PersistenceExceptionTranslator exceptionTranslator;
	
	private ISharding sharding;
	
	
	
	public DynamicSqlSessionTemplate(SqlSessionFactory sqlSessionFactory, ISharding sharding) {
		this(sqlSessionFactory, sqlSessionFactory.getConfiguration().getDefaultExecutorType(), sharding);
	}
	
	/**
	 * Constructs a Spring managed SqlSession with the {@code SqlSessionFactory}
	 * provided as an argument.
	 *
	 * @param sqlSessionFactory
	 */
	public DynamicSqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
		this(sqlSessionFactory, sqlSessionFactory.getConfiguration().getDefaultExecutorType(), null);
	}

	/**
	 * Constructs a Spring managed SqlSession with the {@code SqlSessionFactory}
	 * provided as an argument and the given {@code ExecutorType}
	 * {@code ExecutorType} cannot be changed once the
	 * {@code SqlSessionTemplate} is constructed.
	 *
	 * @param sqlSessionFactory
	 * @param executorType
	 */
	public DynamicSqlSessionTemplate(SqlSessionFactory sqlSessionFactory,
			ExecutorType executorType, ISharding sharding) {
		this(sqlSessionFactory, executorType, new MyBatisExceptionTranslator(
				sqlSessionFactory.getConfiguration().getEnvironment()
						.getDataSource(), true), sharding);
	}

	/**
	 * Constructs a Spring managed {@code SqlSession} with the given
	 * {@code SqlSessionFactory} and {@code ExecutorType}. A custom
	 * {@code SQLExceptionTranslator} can be provided as an argument so any
	 * {@code PersistenceException} thrown by MyBatis can be custom translated
	 * to a {@code RuntimeException} The {@code SQLExceptionTranslator} can also
	 * be null and thus no exception translation will be done and MyBatis
	 * exceptions will be thrown
	 *
	 * @param sqlSessionFactory
	 * @param executorType
	 * @param exceptionTranslator
	 */
	public DynamicSqlSessionTemplate(SqlSessionFactory sqlSessionFactory,
			ExecutorType executorType,
			PersistenceExceptionTranslator exceptionTranslator,
			ISharding sharding) {

		notNull(sqlSessionFactory, "Property 'sqlSessionFactory' is required");
		notNull(executorType, "Property 'executorType' is required");
		if (sharding == null) {
			this.sharding = ShardingContext.getInstance().getSharding();
		} else {
			if (AopUtils.isAopProxy(sharding)) { // sharding 可能是spring proxy
				if (sharding instanceof Advised) {// 获得 sharding proxy 的config 内容
					TargetSource targetSource = ((Advised)sharding).getTargetSource();
					try {
						Object target = targetSource.getTarget();
						this.sharding = (ISharding)target;
//						if (target != null && !targetSource.isStatic()) {
//							targetSource.releaseTarget(target);
//						}
					} catch (Exception e) {
						throw new IllegalArgumentException("sharding is spring proxy, 并且无法解析");
					}
				}
			} else {
				this.sharding = (ISharding) sharding;
			}
		}
		
		this.sqlSessionFactory = sqlSessionFactory;
		this.executorType = executorType;
		this.exceptionTranslator = exceptionTranslator;
		this.sqlSessionProxy = (SqlSession) newProxyInstance(
				SqlSessionFactory.class.getClassLoader(),
				new Class[] { SqlSession.class }, new SqlSessionInterceptor());
	}

	
	public SqlSessionFactory getSqlSessionFactory() {
		return this.sqlSessionFactory;
	}
	

	public ISharding getSharding() {
		return sharding;
	}

	public ExecutorType getExecutorType() {
		return this.executorType;
	}

	public PersistenceExceptionTranslator getPersistenceExceptionTranslator() {
		return this.exceptionTranslator;
	}
	
	
	/**
	 * 数据库分片 算法
	 * 线程安全
	 * @param statement
	 * @param parameter
	 * @return
	 */
	private ISharding prepareSharding(String statement, Object parameter) {
		AbstractSharding.setStatementId(statement);
		AbstractSharding.setSql(getSql(statement, parameter));
		AbstractSharding.setParameter(parameter);
		AbstractSharding.setSqlCommandType(getSqlCommandType(statement));
		return sharding;
	}
	
	/**
	 * 重置 sharding
	 * @param isharding
	 */
	private void resetSharding(ISharding isharding) {
		isharding.reset();
	}
	
	
	/**
	 * 分片标识
	 * @param isharding
	 * @return
	 */
	private DataSourceLookupKey prepareDataSourceLookupKey(ISharding isharding) {
		String dataSourceKey = isharding.getDataSourceKey();
		if (dataSourceKey == null) {
			dataSourceKey = DataSourceLookupKey.defaultDataSourceKey;
		}
		DataSourceLookupKey dataSourceLookupKey = new DataSourceLookupKey(dataSourceKey);
		dataSourceLookupKey.bindToThread();
		return dataSourceLookupKey;
	}
	
	/**
	 * 恢复分片标识
	 * @param dataSourceLookupKey
	 */
	private void restoreDataSourceLookupKey(DataSourceLookupKey dataSourceLookupKey) {
		dataSourceLookupKey.restoreThreadLocalStatus();
	}
	
	
	/**
	 * 读写分离
	 * @param isharding
	 * @return
	 */
	private ReadWriteSeparate prepareReadWriteSeparate(ISharding isharding) {
		ReadWriteEnum readWrite = sharding.getReadWrite();
		if (readWrite != null) {
			ReadWriteSeparate readWriteSeparate = new ReadWriteSeparate(readWrite);
			readWriteSeparate.bindToThread();
			return readWriteSeparate;
		}
		return null;
	}
	
	
	/**
	 * 恢复读写分离标识
	 * @param readWriteSeparate
	 */
	private void restoreReadWriteSeparate(ReadWriteSeparate readWriteSeparate) {
		if (readWriteSeparate != null) {
			readWriteSeparate.restoreThreadLocalStatus();
		}
	}
	
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <T> T selectOne(String statement) {
		// 计算  分片 与 读写分离
		ISharding isharding = prepareSharding(statement, null);
		// 根据计算结果获取 分片标识
		DataSourceLookupKey dataSourceLookupKey = prepareDataSourceLookupKey(isharding);
		// 根据计算结果获取 读写分离标识.  【默认在spring事务中控制， 此处可以对读写更精细化控制】
		ReadWriteSeparate readWriteSeparate = prepareReadWriteSeparate(isharding);
		T t = null;
		try {
		// 执行数据库操作
			t = this.sqlSessionProxy.<T> selectOne(statement);
		} finally {
			// 恢复分片标识
			restoreDataSourceLookupKey(dataSourceLookupKey);
			// 恢复读写分离标识
			restoreReadWriteSeparate(readWriteSeparate);
			//重置线程变量
			resetSharding(isharding);
		}
		return t;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <T> T selectOne(String statement, Object parameter) {
		ISharding isharding = prepareSharding(statement, parameter);
		DataSourceLookupKey dataSourceLookupKey = prepareDataSourceLookupKey(isharding);
		ReadWriteSeparate readWriteSeparate = prepareReadWriteSeparate(isharding);
		T t = null;
		try {
			t = this.sqlSessionProxy.<T> selectOne(statement, parameter);
		} finally {
			restoreDataSourceLookupKey(dataSourceLookupKey);
			restoreReadWriteSeparate(readWriteSeparate);
			resetSharding(isharding);
		}
		return t;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <K, V> Map<K, V> selectMap(String statement, String mapKey) {
		ISharding isharding = prepareSharding(statement, null);
		DataSourceLookupKey dataSourceLookupKey = prepareDataSourceLookupKey(isharding);
		ReadWriteSeparate readWriteSeparate = prepareReadWriteSeparate(isharding);
		Map<K, V> t = null;
		try {
			t = this.sqlSessionProxy.<K, V> selectMap(statement, mapKey);
		} finally {
			restoreDataSourceLookupKey(dataSourceLookupKey);
			restoreReadWriteSeparate(readWriteSeparate);
			resetSharding(isharding);
		}
		return t;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <K, V> Map<K, V> selectMap(String statement, Object parameter,
			String mapKey) {
		
		ISharding isharding = prepareSharding(statement, parameter);
		DataSourceLookupKey dataSourceLookupKey = prepareDataSourceLookupKey(isharding);
		ReadWriteSeparate readWriteSeparate = prepareReadWriteSeparate(isharding);
		Map<K, V> t = null;
		try {
			t = this.sqlSessionProxy.<K, V> selectMap(statement, parameter, mapKey);
		} finally {
			restoreDataSourceLookupKey(dataSourceLookupKey);
			restoreReadWriteSeparate(readWriteSeparate);
			resetSharding(isharding);
		}
		return t;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <K, V> Map<K, V> selectMap(String statement, Object parameter,
			String mapKey, RowBounds rowBounds) {
		
		ISharding isharding = prepareSharding(statement, parameter);
		DataSourceLookupKey dataSourceLookupKey = prepareDataSourceLookupKey(isharding);
		ReadWriteSeparate readWriteSeparate = prepareReadWriteSeparate(isharding);
		Map<K, V> t = null;
		try {
			t = this.sqlSessionProxy.<K, V> selectMap(statement, parameter, mapKey, rowBounds);
		} finally {
			restoreDataSourceLookupKey(dataSourceLookupKey);
			restoreReadWriteSeparate(readWriteSeparate);
			resetSharding(isharding);
		}
		return t;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <E> List<E> selectList(String statement) {
		ISharding isharding = prepareSharding(statement, null);
		DataSourceLookupKey dataSourceLookupKey = prepareDataSourceLookupKey(isharding);
		ReadWriteSeparate readWriteSeparate = prepareReadWriteSeparate(isharding);
		List<E> t = null;
		try {
			t = this.sqlSessionProxy.<E> selectList(statement);
		} finally {
			restoreDataSourceLookupKey(dataSourceLookupKey);
			restoreReadWriteSeparate(readWriteSeparate);
			resetSharding(isharding);
		}
		return t;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <E> List<E> selectList(String statement, Object parameter) {
		ISharding isharding = prepareSharding(statement, parameter);
		DataSourceLookupKey dataSourceLookupKey = prepareDataSourceLookupKey(isharding);
		ReadWriteSeparate readWriteSeparate = prepareReadWriteSeparate(isharding);
		List<E> t = null;
		try {
			t = this.sqlSessionProxy.<E> selectList(statement, parameter);
		} finally {
			restoreDataSourceLookupKey(dataSourceLookupKey);
			restoreReadWriteSeparate(readWriteSeparate);
			resetSharding(isharding);
		}
		return t;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <E> List<E> selectList(String statement, Object parameter,
			RowBounds rowBounds) {
		ISharding isharding = prepareSharding(statement, parameter);
		DataSourceLookupKey dataSourceLookupKey = prepareDataSourceLookupKey(isharding);
		ReadWriteSeparate readWriteSeparate = prepareReadWriteSeparate(isharding);
		List<E> t = null;
		try {
			t = this.sqlSessionProxy.<E> selectList(statement, parameter, rowBounds);
		} finally {
			restoreDataSourceLookupKey(dataSourceLookupKey);
			restoreReadWriteSeparate(readWriteSeparate);
			resetSharding(isharding);
		}
		return t;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void select(String statement, ResultHandler handler) {
		ISharding isharding = prepareSharding(statement, null);
		DataSourceLookupKey dataSourceLookupKey = prepareDataSourceLookupKey(isharding);
		ReadWriteSeparate readWriteSeparate = prepareReadWriteSeparate(isharding);
		try {
			this.sqlSessionProxy.select(statement, handler);
		} finally {
			restoreDataSourceLookupKey(dataSourceLookupKey);
			restoreReadWriteSeparate(readWriteSeparate);
			resetSharding(isharding);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void select(String statement, Object parameter, ResultHandler handler) {
		ISharding isharding = prepareSharding(statement, parameter);
		DataSourceLookupKey dataSourceLookupKey = prepareDataSourceLookupKey(isharding);
		ReadWriteSeparate readWriteSeparate = prepareReadWriteSeparate(isharding);
		try {
			this.sqlSessionProxy.select(statement, parameter, handler);
		} finally {
			restoreDataSourceLookupKey(dataSourceLookupKey);
			restoreReadWriteSeparate(readWriteSeparate);
			resetSharding(isharding);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void select(String statement, Object parameter, RowBounds rowBounds,
			ResultHandler handler) {
		ISharding isharding = prepareSharding(statement, parameter);
		DataSourceLookupKey dataSourceLookupKey = prepareDataSourceLookupKey(isharding);
		ReadWriteSeparate readWriteSeparate = prepareReadWriteSeparate(isharding);
		try {
			this.sqlSessionProxy.select(statement, parameter, rowBounds, handler);
		} finally {
			restoreDataSourceLookupKey(dataSourceLookupKey);
			restoreReadWriteSeparate(readWriteSeparate);
			resetSharding(isharding);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int insert(String statement) {
		ISharding isharding = prepareSharding(statement, null);
		DataSourceLookupKey dataSourceLookupKey = prepareDataSourceLookupKey(isharding);
		ReadWriteSeparate readWriteSeparate = prepareReadWriteSeparate(isharding);
		int t = 0;
		try {
			t = this.sqlSessionProxy.insert(statement);
		} finally {
			restoreDataSourceLookupKey(dataSourceLookupKey);
			restoreReadWriteSeparate(readWriteSeparate);
			resetSharding(isharding);
		}
		return t;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int insert(String statement, Object parameter) {
		ISharding isharding = prepareSharding(statement, parameter);
		DataSourceLookupKey dataSourceLookupKey = prepareDataSourceLookupKey(isharding);
		ReadWriteSeparate readWriteSeparate = prepareReadWriteSeparate(isharding);
		int t = 0;
		try {
			t = this.sqlSessionProxy.insert(statement, parameter);
		} finally {
			restoreDataSourceLookupKey(dataSourceLookupKey);
			restoreReadWriteSeparate(readWriteSeparate);
			resetSharding(isharding);
		}
		return t;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int update(String statement) {
		ISharding isharding = prepareSharding(statement, null);
		DataSourceLookupKey dataSourceLookupKey = prepareDataSourceLookupKey(isharding);
		ReadWriteSeparate readWriteSeparate = prepareReadWriteSeparate(isharding);
		int t = 0;
		try {
			t = this.sqlSessionProxy.update(statement);
		} finally {
			restoreDataSourceLookupKey(dataSourceLookupKey);
			restoreReadWriteSeparate(readWriteSeparate);
			resetSharding(isharding);
		}
		return t;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int update(String statement, Object parameter) {
		ISharding isharding = prepareSharding(statement, parameter);
		DataSourceLookupKey dataSourceLookupKey = prepareDataSourceLookupKey(isharding);
		ReadWriteSeparate readWriteSeparate = prepareReadWriteSeparate(isharding);
		int t = 0;
		try {
			t = this.sqlSessionProxy.update(statement, parameter);
		} finally {
			restoreDataSourceLookupKey(dataSourceLookupKey);
			restoreReadWriteSeparate(readWriteSeparate);
			resetSharding(isharding);
		}
		return t;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int delete(String statement) {
		ISharding isharding = prepareSharding(statement, null);
		DataSourceLookupKey dataSourceLookupKey = prepareDataSourceLookupKey(isharding);
		ReadWriteSeparate readWriteSeparate = prepareReadWriteSeparate(isharding);
		int t = 0;
		try {
			t = this.sqlSessionProxy.delete(statement);
		} finally {
			restoreDataSourceLookupKey(dataSourceLookupKey);
			restoreReadWriteSeparate(readWriteSeparate);
			resetSharding(isharding);
		}
		return t;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int delete(String statement, Object parameter) {
		ISharding isharding = prepareSharding(statement, parameter);
		DataSourceLookupKey dataSourceLookupKey = prepareDataSourceLookupKey(isharding);
		ReadWriteSeparate readWriteSeparate = prepareReadWriteSeparate(isharding);
		int t = 0;
		try {
			t = this.sqlSessionProxy.delete(statement, parameter);
		} finally {
			restoreDataSourceLookupKey(dataSourceLookupKey);
			restoreReadWriteSeparate(readWriteSeparate);
			resetSharding(isharding);
		}
		return t;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <T> T getMapper(Class<T> type) {
		return getConfiguration().getMapper(type, this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void commit() {
		throw new UnsupportedOperationException(
				"Manual commit is not allowed over a Spring managed SqlSession");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void commit(boolean force) {
		throw new UnsupportedOperationException(
				"Manual commit is not allowed over a Spring managed SqlSession");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void rollback() {
		throw new UnsupportedOperationException(
				"Manual rollback is not allowed over a Spring managed SqlSession");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void rollback(boolean force) {
		throw new UnsupportedOperationException(
				"Manual rollback is not allowed over a Spring managed SqlSession");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() {
		throw new UnsupportedOperationException(
				"Manual close is not allowed over a Spring managed SqlSession");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clearCache() {
		this.sqlSessionProxy.clearCache();
	}

	/**
	 * {@inheritDoc}
	 *
	 */
	@Override
	public Configuration getConfiguration() {
		return this.sqlSessionFactory.getConfiguration();
	}

	
	/**
	 * 从默认 的写库 获取链接
	 */
	@Override
	public Connection getConnection() {
		DataSourceLookupKey dataSourceLookupKey = new DataSourceLookupKey(DataSourceLookupKey.defaultDataSourceKey);
		dataSourceLookupKey.bindToThread();
		ReadWriteSeparate readWriteSeparate = new ReadWriteSeparate(ReadWriteEnum.WRITE);
		readWriteSeparate.bindToThread();
		Connection conn = null;
		try {
			conn = this.sqlSessionProxy.getConnection();
		} finally {
			restoreDataSourceLookupKey(dataSourceLookupKey);
			restoreReadWriteSeparate(readWriteSeparate);
		}
		return conn;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @since 1.0.2
	 *
	 */
	@Override
	public List<BatchResult> flushStatements() {
		return this.sqlSessionProxy.flushStatements();
	}
	
	
	private Object wrapCollection(final Object object) {
		if (object instanceof List) {
			StrictMap<Object> map = new StrictMap<Object>();
			map.put("list", object);
			return map;
		} else if (object != null && object.getClass().isArray()) {
			StrictMap<Object> map = new StrictMap<Object>();
			map.put("array", object);
			return map;
		}
		return object;
	}
	
	/**
	 * 获取 SQL 语句
	 * @param statementId
	 * @param parameter
	 * @return
	 */
	private String getSql(String statementId, Object parameter) {
		BoundSql boundSql = this.sqlSessionFactory.getConfiguration().getMappedStatement(statementId).getBoundSql(wrapCollection(parameter));
		String sql = boundSql.getSql();
		return sql;
	}
	
	
	private SqlCommandType getSqlCommandType(String statementId) {
		return this.sqlSessionFactory.getConfiguration().getMappedStatement(statementId).getSqlCommandType();
	}
	

	

	/**
	 * Proxy needed to route MyBatis method calls to the proper SqlSession got
	 * from Spring's Transaction Manager It also unwraps exceptions thrown by
	 * {@code Method#invoke(Object, Object...)} to pass a
	 * {@code PersistenceException} to the
	 * {@code PersistenceExceptionTranslator}.
	 */
	private class SqlSessionInterceptor implements InvocationHandler {
		@Override
		public Object invoke(Object proxy, Method method, Object[] args)
				throws Throwable {
			SqlSession sqlSession = getSqlSession(
					DynamicSqlSessionTemplate.this.sqlSessionFactory,
					DynamicSqlSessionTemplate.this.executorType,
					DynamicSqlSessionTemplate.this.exceptionTranslator);
			try {

				Object result = method.invoke(sqlSession, args);
				if (!isSqlSessionTransactional(sqlSession,
						DynamicSqlSessionTemplate.this.sqlSessionFactory)) {
					// force commit even on non-dirty sessions because some
					// databases require
					// a commit/rollback before calling close()
					sqlSession.commit(true);
				}
				return result;
			} catch (Throwable t) {
				Throwable unwrapped = unwrapThrowable(t);
				if (DynamicSqlSessionTemplate.this.exceptionTranslator != null
						&& unwrapped instanceof PersistenceException) {
					// release the connection to avoid a deadlock if the
					// translator is no loaded. See issue #22
					closeSqlSession(sqlSession,
							DynamicSqlSessionTemplate.this.sqlSessionFactory);
					sqlSession = null;
					Throwable translated = DynamicSqlSessionTemplate.this.exceptionTranslator
							.translateExceptionIfPossible((PersistenceException) unwrapped);
					if (translated != null) {
						unwrapped = translated;
					}
				}
				throw unwrapped;
			} finally {
				if (sqlSession != null) {
					closeSqlSession(sqlSession,
							DynamicSqlSessionTemplate.this.sqlSessionFactory);
				}
			}
		}
	}

}
