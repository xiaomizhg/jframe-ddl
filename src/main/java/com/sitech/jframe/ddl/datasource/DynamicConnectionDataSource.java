package com.sitech.jframe.ddl.datasource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.Constants;
import org.springframework.jdbc.datasource.ConnectionProxy;
import org.springframework.util.Assert;


/**
 * 动态链接数据源代理 , 可以只执行 实际的 getConnection 之前， 动态选择 DataSource
 * @author zhangsf
 *
 */
public class DynamicConnectionDataSource extends AbstractDynamicDataSource {
	
	/** Constants instance for TransactionDefinition */
	private static final Constants constants = new Constants(Connection.class);

	private static final Log logger = LogFactory.getLog(DynamicConnectionDataSource.class);
	
	
	
	private Boolean defaultWriteAutoCommit;

	private Integer defaultWriteTransactionIsolation;
	
	
	private Boolean defaultReadAutoCommit;
	
	private Integer defaultReadTransactionIsolation;
	

	/**
	 * Create a new LazyConnectionDataSourceProxy.
	 * @see #setTargetDataSource
	 */
	public DynamicConnectionDataSource() {
	}

	/**
	 * Create a new LazyConnectionDataSourceProxy.
	 * @param targetDataSource the target DataSource
	 */
	/*public DynamicConnectionDataSourceProxy(DataSource targetDataSource) {
		setTargetDataSource(targetDataSource);
		afterPropertiesSet();
	}*/




	@Override
	public void afterPropertiesSet() {
		super.afterPropertiesSet();
		
		try { // 校验读写库的数据源属性 ， 其中 TransactionIsolation 属性必须保持一致， AutoCommit 属性必须为 true
			checkWriteDataSourceProperties(getMasterDataSources()); 
			checkReadDataSourceProperties(getSlaveDataSources());
			
			if (defaultReadAutoCommit() == null) { // 读库的 auto commit 为  null
				this.defaultReadAutoCommit = defaultWriteAutoCommit();
			}
			if (defaultReadTransactionIsolation() == null) {//读库的 TransactionIsolation 为null
				this.defaultReadTransactionIsolation = defaultWriteTransactionIsolation();
			}
		} catch (SQLException ex) {
			logger.warn("Could not retrieve default auto-commit and transaction isolation settings", ex);
		}
	}
	
	
	/**
	 * 校验写库属性
	 * @param masterDataSources
	 * @throws SQLException
	 */
	protected synchronized void checkWriteDataSourceProperties(Map<String, DataSource> masterDataSources) throws SQLException {
		// TODO 校验算法待优化
		
		if (masterDataSources.isEmpty()) {
			throw new IllegalArgumentException("Master DataSources cannot empty");
		}
		
		// 校验  Master DataSources  的 autoCommit 为 true,  TransactionIsolation 必须一致
		Map<String, DataSourceProperties> masterDataSourcePropMap = new HashMap<String, DataSourceProperties>();
		for (Map.Entry<String, DataSource> entry : masterDataSources.entrySet()) { // 获取所有master数据源连接属性
			String lookupKey = entry.getKey();
			DataSource masterDataSourcesItem = entry.getValue();
			
			Connection masterConn = null;
			try {
				masterConn = masterDataSourcesItem.getConnection();
				boolean masterAutoCommit = masterConn.getAutoCommit();
				int isolation = masterConn.getTransactionIsolation();
				DataSourceProperties dsp = new DataSourceProperties(masterAutoCommit, isolation);
				masterDataSourcePropMap.put(lookupKey, dsp);
			} finally {
				masterConn.close();
			}
		}
		
		Set<Integer> isolationSet = new HashSet<Integer>(5);
		Set<Boolean> autoCommitSet = new HashSet<Boolean>(2);
		// 校验 master AutoCommit 必须为 true，  TransactionIsolation 必须一致
		for (Map.Entry<String, DataSourceProperties> entry : masterDataSourcePropMap.entrySet()) {
			logger.info("Master DataSource Identity : " + entry.getKey() 
					+" , AutoCommit Propertie is :" + entry.getValue().isAutoCommit() 
					+ " , TransactionIsolation propertie is : " + entry.getValue().getTransactionIsolation());
			
			isolationSet.add(entry.getValue().getTransactionIsolation());
			autoCommitSet.add(entry.getValue().isAutoCommit());
		}
		
		if (isolationSet.size() == 1) { // 所有的 TransactionIsolation 属性必须保持一致
			this.defaultWriteTransactionIsolation = isolationSet.iterator().next();
		} else {
			throw new IllegalArgumentException("Master DataSources Connection propertie TransactionIsolation must be consistent");
		}
		
		if (autoCommitSet.size() == 1) {// 所有的 AutoCommit 属性必须保持一致
			if (autoCommitSet.iterator().next() == false) { // AutoCommit 属性必须为 true
				throw new IllegalArgumentException("Master DataSources Connection propertie AutoCommit must be true");
			} else {
				this.defaultWriteAutoCommit = autoCommitSet.iterator().next();
			}
		} else {
			throw new IllegalArgumentException("Master DataSources Connection propertie AutoCommit must be consistent");
		}
	}
	
	
	/**
	 * 校验读库属性
	 * 校验规则 ，所有的 TransactionIsolation 保持一致， 所有的 AutoCommit 保持一致 并且为 true
	 * @param slaveDataSources
	 * @throws SQLException
	 */
	protected synchronized void checkReadDataSourceProperties(Map<String, List<DataSource>> slaveDataSources) throws SQLException {
		//TODO 校验算法待优化
		if (slaveDataSources.isEmpty()) {
			return;
		}
		
		// 校验  slave DataSources  的 autoCommit 为 true,  TransactionIsolation 必须一致
		Map<String, List<DataSourceProperties>> slaveDataSourcePropMap = new HashMap<String, List<DataSourceProperties>>();
		for (Map.Entry<String, List<DataSource>> entry : slaveDataSources.entrySet()) {
			String lookupKey = entry.getKey();
			List<DataSource> slaveDataSourcesItemList = entry.getValue();
			if (slaveDataSourcesItemList != null && !slaveDataSourcesItemList.isEmpty()) {
				List<DataSourceProperties> dspList = new ArrayList<DataSourceProperties>();
				for (DataSource slaveDs : slaveDataSourcesItemList) {
					Connection slaveConn = null;
					try {
						slaveConn = slaveDs.getConnection();
						boolean slaveAutoCommit = slaveConn.getAutoCommit();
						int isolation = slaveConn.getTransactionIsolation();
						DataSourceProperties dsp = new DataSourceProperties(slaveAutoCommit, isolation);
						dspList.add(dsp);
					} finally {
						slaveConn.close();
					}
				}
				slaveDataSourcePropMap.put(lookupKey, dspList);
			}
		}
		
		
		Set<Integer> isolationSet = new HashSet<Integer>(5);
		Set<Boolean> autoCommitSet = new HashSet<Boolean>(2);
		// 校验 master AutoCommit 必须为 true，  TransactionIsolation 必须一致
		for (Map.Entry<String, List<DataSourceProperties>> entry : slaveDataSourcePropMap.entrySet()) {
			String lookupKey = entry.getKey();
			List<DataSourceProperties> dspList = entry.getValue();
			for (DataSourceProperties dsp : dspList) {
				logger.info("Slave DataSource Identity : " + lookupKey 
						+" , AutoCommit Propertie is :" + dsp.isAutoCommit() 
						+ " , TransactionIsolation propertie is : " + dsp.getTransactionIsolation());
				
				isolationSet.add(dsp.getTransactionIsolation());
				autoCommitSet.add(dsp.isAutoCommit());
			}
		}
		
		if (!slaveDataSourcePropMap.isEmpty()) { // slave 可能为空， 或者 与 master 重复
			if (isolationSet.size() == 1) { // 所有的 TransactionIsolation 属性必须保持一致
				this.defaultReadTransactionIsolation = isolationSet.iterator().next();
			} else {
				throw new IllegalArgumentException("Slave DataSources Connection propertie TransactionIsolation must be consistent！！！");
			}
			
			if (autoCommitSet.size() == 1) {// 所有的 AutoCommit 属性必须保持一致
				if (autoCommitSet.iterator().next() == false) { // AutoCommit 属性必须为 true
					throw new IllegalArgumentException("Slave DataSources Connection propertie AutoCommit must be true");
				} else {
					this.defaultReadAutoCommit = autoCommitSet.iterator().next();
				}
			} else {
				throw new IllegalArgumentException("Slave DataSources Connection propertie AutoCommit must be consistent");
			}
		}
	}
	
	
	protected Boolean defaultReadAutoCommit() {
		return this.defaultReadAutoCommit;
	}
	protected Integer defaultReadTransactionIsolation() {
		return this.defaultReadTransactionIsolation;
	}
	
	protected Boolean defaultWriteAutoCommit() {
		return this.defaultWriteAutoCommit;
	}
	protected Integer defaultWriteTransactionIsolation() {
		return this.defaultWriteTransactionIsolation;
	}

	


	/**
	 * Return a Connection handle that lazily fetches an actual JDBC Connection
	 * when asked for a Statement (or PreparedStatement or CallableStatement).
	 * <p>The returned Connection handle implements the ConnectionProxy interface,
	 * allowing to retrieve the underlying target Connection.
	 * @return a lazy Connection handle
	 * @see ConnectionProxy#getTargetConnection()
	 */
	@Override
	public Connection getConnection() throws SQLException {
		return (Connection) Proxy.newProxyInstance(
				ConnectionProxy.class.getClassLoader(),
				new Class[] {ConnectionProxy.class},
				new DynamicConnectionInvocationHandler());
	}

	/**
	 * Return a Connection handle that lazily fetches an actual JDBC Connection
	 * when asked for a Statement (or PreparedStatement or CallableStatement).
	 * <p>The returned Connection handle implements the ConnectionProxy interface,
	 * allowing to retrieve the underlying target Connection.
	 * @param username the per-Connection username
	 * @param password the per-Connection password
	 * @return a lazy Connection handle
	 * @see ConnectionProxy#getTargetConnection()
	 */
	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		return (Connection) Proxy.newProxyInstance(
				ConnectionProxy.class.getClassLoader(),
				new Class[] {ConnectionProxy.class},
				new DynamicConnectionInvocationHandler(username, password));
	}
	
	
	public class DataSourceProperties {
		private boolean autoCommit;
		private int transactionIsolation;
		private boolean readOnly;
		
		public DataSourceProperties(boolean autoCommit, int transactionIsolation) {
			this.autoCommit = autoCommit;
			this.transactionIsolation = transactionIsolation;
		}
		
		public DataSourceProperties(boolean autoCommit, int transactionIsolation, boolean readOnly) {
			this.autoCommit = autoCommit;
			this.transactionIsolation = transactionIsolation;
			this.readOnly = readOnly;
		}
		
		
		public boolean isAutoCommit() {
			return autoCommit;
		}
		public void setAutoCommit(boolean autoCommit) {
			this.autoCommit = autoCommit;
		}
		public int getTransactionIsolation() {
			return transactionIsolation;
		}
		public void setTransactionIsolation(int transactionIsolation) {
			this.transactionIsolation = transactionIsolation;
		}
		public boolean isReadOnly() {
			return readOnly;
		}
		public void setReadOnly(boolean readOnly) {
			this.readOnly = readOnly;
		}
		
	}


	/**
	 * Invocation handler that defers fetching an actual JDBC Connection
	 * until first creation of a Statement.
	 */
	private class DynamicConnectionInvocationHandler implements InvocationHandler {

		private String username;

		private String password;

		private Boolean readOnly = Boolean.FALSE;

		private Integer writeTransactionIsolation;

		private Boolean writeAutoCommit;
		
		private Integer readTransactionIsolation;
		
		private Boolean readAutoCommit;

		private boolean closed = false;

		private Map<DataSource, Connection> masterTargets = new ConcurrentHashMap<DataSource, Connection>(); // 写库
		
		private Map<DataSource, Connection> slaveTargets = new ConcurrentHashMap<DataSource, Connection>(); // 读库
		

		public DynamicConnectionInvocationHandler() {
			this.writeAutoCommit = defaultWriteAutoCommit();
			this.writeTransactionIsolation = defaultWriteTransactionIsolation();
			this.readAutoCommit = defaultReadAutoCommit();
			this.readTransactionIsolation = defaultReadTransactionIsolation();
		}

		public DynamicConnectionInvocationHandler(String username, String password) {
			this();
			this.username = username;
			this.password = password;
			
			this.writeAutoCommit = defaultWriteAutoCommit();
			this.writeTransactionIsolation = defaultWriteTransactionIsolation();
			this.readAutoCommit = defaultReadAutoCommit();
			this.readTransactionIsolation = defaultReadTransactionIsolation();
		}

		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			// Invocation on ConnectionProxy interface coming in...

			if (method.getName().equals("equals")) {
				// We must avoid fetching a target Connection for "equals".
				// Only consider equal when proxies are identical.
				return (proxy == args[0]);
			}
			else if (method.getName().equals("hashCode")) {
				// We must avoid fetching a target Connection for "hashCode",
				// and we must return the same hash code even when the target
				// Connection has been fetched: use hashCode of Connection proxy.
				return System.identityHashCode(proxy);
			}
			else if (method.getName().equals("unwrap")) {
				if (((Class) args[0]).isInstance(proxy)) {
					return proxy;
				}
			}
			else if (method.getName().equals("isWrapperFor")) {
				if (((Class) args[0]).isInstance(proxy)) {
					return true;
				}
			}
			else if (method.getName().equals("getTargetConnection")) {
				// Handle getTargetConnection method: return underlying connection.
				return getTargetConnection(method);
			} 
			else if (method.getName().equals("clearWarnings")) {
				return clearWarningsDelegate();
			}
			else if (method.getName().equals("getWarnings")) { // 在具体的物理连接中获取警告信息
				// 不返回
			}
			else if (method.getName().equals("toString")) {
				
				return toStringDelegate();
			}
			else if (method.getName().equals("commit")) {
				return commitDelegate();
			}
			else if (method.getName().equals("rollback")) {
				return rollbackDelegate();
			}
			else if (method.getName().equals("close")) {
				this.closed = true;
				return closeDelegate();
			}
			else if (method.getName().equals("isClosed")) {
				return this.closed;
			}
			else if (method.getName().equals("isReadOnly")) {
				return this.readOnly;
			}
			else if (method.getName().equals("setReadOnly")) {
				this.readOnly = (Boolean) args[0];
				setReadOnlyDelegate(this.readOnly);
				return null;
			}
			else if (method.getName().equals("getAutoCommit")) { // 默认返回读库的 writeAutoCommit， 可以根据 ReadWriteSeparate的值， 返回read或write库 的autoCommit
//				boolean autoCommit = this.writeAutoCommit && this.readAutoCommit;
				
				boolean autoCommit = this.writeAutoCommit; //默认 返回  write库 的 autoCommit
//				ReadWriteSeparate readWriteSeparate = determineCurrentReadWrite();
//				if (readWriteSeparate != null) {
//					if (readWriteSeparate.getReadWriteEnum() == ReadWriteEnum.WRITE) {
//						return this.writeAutoCommit;
//					}
//					if (readWriteSeparate.getReadWriteEnum() == ReadWriteEnum.READ) {
//						return this.readAutoCommit;
//					}
//				}
				return autoCommit;
			}
			else if (method.getName().equals("setAutoCommit")) { // 设置 写库的 AutoCommit, 如果读库不是 AutoCommit， 则也设置读库
				this.writeAutoCommit = (Boolean) args[0];
				setAutoCommitDelegate(this.masterTargets, (Boolean) args[0]);
				if (this.readAutoCommit != true) {// 如果读库 不是 AutoCommit , 则根据入参设置, 如果读库本身就是 AutoCommit, 则不需要设置
					this.readAutoCommit = (Boolean) args[0];
					setAutoCommitDelegate(this.slaveTargets, (Boolean) args[0]);
				}
				return null;
			}
			else if (method.getName().equals("getTransactionIsolation")) {
				return this.writeTransactionIsolation;
			}
			else if (method.getName().equals("setTransactionIsolation")) {
				this.writeTransactionIsolation = (Integer) args[0];
				this.readTransactionIsolation = (Integer) args[0];
				setTransactionIsolationDelegate((Integer) args[0]);
				return null;
			}
			else if (this.closed) {
				// Connection proxy closed, without ever having fetched a
				// physical JDBC Connection: throw corresponding SQLException.
				throw new SQLException("Illegal operation: connection is closed");
			}

			
			// Target Connection already fetched,
			// or target Connection necessary for current operation ->
			// invoke method on target connection.
			try {
				return method.invoke(getTargetConnection(method), args);
			}
			catch (InvocationTargetException ex) {
				throw ex.getTargetException();
			}
		}
		
		
		private Object commitDelegate() throws SQLException {
			if (!this.writeAutoCommit) {
				for (Map.Entry<DataSource, Connection> entry :  masterTargets.entrySet()) {
					Connection conn = entry.getValue();
					conn.commit();
				}
			}
			
			if (!this.readAutoCommit) {
				for (Map.Entry<DataSource, Connection> entry :  slaveTargets.entrySet()) {
					Connection conn = entry.getValue();
					conn.commit();
				}
			}
			return null;
		}
		
		private Object rollbackDelegate() throws SQLException {
			if (!this.writeAutoCommit) {
				for (Map.Entry<DataSource, Connection> entry :  masterTargets.entrySet()) {
					Connection conn = entry.getValue();
					conn.rollback();
				}
			}
			if (!this.readAutoCommit) {
				for (Map.Entry<DataSource, Connection> entry :  slaveTargets.entrySet()) {
					Connection conn = entry.getValue();
					conn.rollback();
				}
			}
			return null;
		}
		
		private Object closeDelegate() throws SQLException {
			for (Map.Entry<DataSource, Connection> entry :  masterTargets.entrySet()) {
				Connection conn = entry.getValue();
				conn.close();
			}
			
			for (Map.Entry<DataSource, Connection> entry :  slaveTargets.entrySet()) {
				Connection conn = entry.getValue();
				conn.close();
			}
			
			return null;
		}
		
		private Object clearWarningsDelegate() throws SQLException {
			for (Map.Entry<DataSource, Connection> entry :  masterTargets.entrySet()) {
				Connection conn = entry.getValue();
				conn.clearWarnings();
			}
			
			for (Map.Entry<DataSource, Connection> entry :  slaveTargets.entrySet()) {
				Connection conn = entry.getValue();
				conn.clearWarnings();
			}
			
			return null;
		}
		
		private Object setReadOnlyDelegate(Boolean readOnly) throws SQLException {
			for (Map.Entry<DataSource, Connection> entry :  slaveTargets.entrySet()) {
				Connection conn = entry.getValue();
				conn.setReadOnly(readOnly);
			}
			return null;
		}
		
		private Object setAutoCommitDelegate(Map<DataSource, Connection> targets, Boolean autoCommit) throws SQLException {
			for (Map.Entry<DataSource, Connection> entry :  targets.entrySet()) {
				Connection conn = entry.getValue();
				conn.setAutoCommit(autoCommit);
			}
			return null;
		}
		
		private Object setTransactionIsolationDelegate(int level) throws SQLException {
			for (Map.Entry<DataSource, Connection> entry :  masterTargets.entrySet()) {
				Connection conn = entry.getValue();
				conn.setTransactionIsolation(level);
			}
			for (Map.Entry<DataSource, Connection> entry :  slaveTargets.entrySet()) {
				Connection conn = entry.getValue();
				conn.setTransactionIsolation(level);
			}
			return null;
		}
		
		private String toStringDelegate() {
			if (masterTargets.isEmpty() && slaveTargets.isEmpty()) {
				return "Dynamic Connection proxy for target DataSource Identitys [" + getIdentitysStr() + "]";
			} else {
				StringBuilder sb = new StringBuilder();
				for (Map.Entry<DataSource, Connection> entry :  masterTargets.entrySet()) {
					DataSource ds = entry.getKey();
					Connection conn = entry.getValue();
					sb.append("master DataSource : ");
					sb.append(ds.toString() + " -> ");
					sb.append("Connection : ");
					sb.append(conn.toString());
					sb.append(System.getProperty("line.separator"));
				}
				
				for (Map.Entry<DataSource, Connection> entry :  slaveTargets.entrySet()) {
					DataSource ds = entry.getKey();
					Connection conn = entry.getValue();
					sb.append("slave DataSource : ");
					sb.append(ds.toString() + " -> ");
					sb.append("Connection : ");
					sb.append(conn.toString());
					sb.append(System.getProperty("line.separator"));
				}
				
				return sb.toString();
			}
		}
		
		


		/**
		 * Return the target Connection, fetching it and initializing it if necessary.
		 */
		private Connection getTargetConnection(Method operation) throws SQLException {
			
			DataSourceWrapper dataSourceWrapper = getTargetDataSource();
			ReadWriteEnum readWriteEnum = dataSourceWrapper.getReadWriteEnum();
			Connection conn = null;
			if (readWriteEnum == ReadWriteEnum.READ) { // 读连接
				DataSource slaveDataSource = dataSourceWrapper.getTargetDataSource();
				if (!slaveTargets.containsKey(slaveDataSource)) { // 
					if (logger.isDebugEnabled()) {
						printLog(operation, dataSourceWrapper, 1);
					}
					conn = (this.username != null) ? slaveDataSource.getConnection(this.username, this.password) :
						slaveDataSource.getConnection();
					
					
					if (this.readAutoCommit != null && this.readAutoCommit != conn.getAutoCommit()) {
						conn.setAutoCommit(this.readAutoCommit);
					}
					if (this.readOnly) {
						conn.setReadOnly(this.readOnly);
					}
					if (this.readTransactionIsolation != null && !this.readTransactionIsolation.equals(defaultReadTransactionIsolation())) {
						conn.setTransactionIsolation(this.readTransactionIsolation);
					}
					
					slaveTargets.put(slaveDataSource, conn);
					
				} else {
					if (logger.isDebugEnabled()) {
						printLog(operation, dataSourceWrapper, 2);
					}
					conn =  slaveTargets.get(slaveDataSource);
				}
			} else { //写连接
				DataSource masterDataSource = dataSourceWrapper.getTargetDataSource();
				if (!masterTargets.containsKey(masterDataSource)) {
					if (logger.isDebugEnabled()) {
						printLog(operation, dataSourceWrapper, 1);
					}
					conn = (this.username != null) ? masterDataSource.getConnection(this.username, this.password) : 
						masterDataSource.getConnection();
					
					if (this.writeAutoCommit != null && this.writeAutoCommit != conn.getAutoCommit()) {
						conn.setAutoCommit(this.writeAutoCommit);
					}
					if (this.readOnly) {
						conn.setReadOnly(this.readOnly);
					}
					if (this.writeTransactionIsolation != null && !this.writeTransactionIsolation.equals(defaultWriteTransactionIsolation())) {
						conn.setTransactionIsolation(this.writeTransactionIsolation);
					}
					
					masterTargets.put(masterDataSource, conn);
				} else {
					if (logger.isDebugEnabled()) {
						printLog(operation, dataSourceWrapper, 2);
					}
					conn = masterTargets.get(masterDataSource);
				}
			}
			
			return conn;
		}
		
		
		private void printLog(Method operation, DataSourceWrapper dataSourceWrapper, int type) {
			switch(type) {
				case 1 :
					logger.debug("Connecting to database {dataSourceKey : " + dataSourceWrapper.getDataSourceKey() + "} "
							+ "{ReadWriteEnum : "+ dataSourceWrapper.getReadWriteEnum().toString() + "} "
							+ "{} "
							+ "  for operation '" + operation.getName() + "'");
					break;
				case 2:
					logger.debug("Using self existing database {dataSourceKey : " +dataSourceWrapper.getDataSourceKey()+ "}"
							+ "{ReadWriteEnum : " + dataSourceWrapper.getReadWriteEnum().toString() +"} "
							+ "{}"
							+ " connection for operation '" + operation.getName() + "'");
					break;
				default :
					break;
			}
		}
	}


	@Override
	protected DataSourceLookupKey determineCurrentLookupKey() {
		return DynamicDataSourceContextHolder.getDataSourceLookupKey();
	}

	@Override
	protected ReadWriteSeparate determineCurrentReadWrite() {
		return DynamicDataSourceContextHolder.getReadWriteSeparate();
	}

}
