package com.sitech.jframe.ddl.transaction;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.JdbcTransactionObjectSupport;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.ResourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.sitech.jframe.ddl.utils.ReflectionUtil;




@SuppressWarnings("serial")
public class DynamicDataSourceTransactionManager extends AbstractPlatformTransactionManager
		implements ResourceTransactionManager, InitializingBean, BeanFactoryPostProcessor {

	private DataSource dataSource;
	
	private Class<?> replacedAdviceClass = TransactionInterceptor.class;
	private Class<?> newAdviceClass = ExtTransactionInterceptor.class;
	private AtomicBoolean replaced = new AtomicBoolean();

	
	/**
	 * 将 spring transaction adive 替换为 ExtTransactionInterceptor
	 */
	@Override
	public void postProcessBeanFactory(
			ConfigurableListableBeanFactory beanFactory) throws BeansException {
		
		if (!replaced.compareAndSet(false, true)) {
			return;
		}
		
		String[] beanNames = beanFactory.getBeanDefinitionNames();
		for (String beanName : beanNames) {
			BeanDefinition beanDef = beanFactory.getBeanDefinition(beanName);
			if (beanDef != null) {
				if (replacedAdviceClass.getName().equals(beanDef.getBeanClassName())) {
					beanDef.setBeanClassName(newAdviceClass.getName());
					logger.info("transaction advice " + replacedAdviceClass + "is replaced by " + newAdviceClass);
				}
			}
		}
	}
	
	/**
	 * Create a new DataSourceTransactionManager instance.
	 * A DataSource has to be set to be able to use it.
	 * @see #setDataSource
	 */
	public DynamicDataSourceTransactionManager() {
		setNestedTransactionAllowed(true);
	}

	/**
	 * Create a new DataSourceTransactionManager instance.
	 * @param dataSource JDBC DataSource to manage transactions for
	 */
	public DynamicDataSourceTransactionManager(DataSource dataSource) {
		this();
		setDataSource(dataSource);
		afterPropertiesSet();
	}

	/**
	 * Set the JDBC DataSource that this instance should manage transactions for.
	 * <p>This will typically be a locally defined DataSource, for example a
	 * Jakarta Commons DBCP connection pool. Alternatively, you can also drive
	 * transactions for a non-XA J2EE DataSource fetched from JNDI. For an XA
	 * DataSource, use JtaTransactionManager.
	 * <p>The DataSource specified here should be the target DataSource to manage
	 * transactions for, not a TransactionAwareDataSourceProxy. Only data access
	 * code may work with TransactionAwareDataSourceProxy, while the transaction
	 * manager needs to work on the underlying target DataSource. If there's
	 * nevertheless a TransactionAwareDataSourceProxy passed in, it will be
	 * unwrapped to extract its target DataSource.
	 * <p><b>The DataSource passed in here needs to return independent Connections.</b>
	 * The Connections may come from a pool (the typical case), but the DataSource
	 * must not return thread-scoped / request-scoped Connections or the like.
	 * @see TransactionAwareDataSourceProxy
	 * @see org.springframework.transaction.jta.JtaTransactionManager
	 */
	public void setDataSource(DataSource dataSource) {
		if (dataSource instanceof TransactionAwareDataSourceProxy) {
			// If we got a TransactionAwareDataSourceProxy, we need to perform transactions
			// for its underlying target DataSource, else data access code won't see
			// properly exposed transactions (i.e. transactions for the target DataSource).
			this.dataSource = ((TransactionAwareDataSourceProxy) dataSource).getTargetDataSource();
		}
		else {
			this.dataSource = dataSource;
		}
	}

	/**
	 * Return the JDBC DataSource that this instance manages transactions for.
	 */
	public DataSource getDataSource() {
		return this.dataSource;
	}

	public void afterPropertiesSet() {
		if (getDataSource() == null) {
			throw new IllegalArgumentException("Property 'dataSource' is required");
		}
	}


	public Object getResourceFactory() {
		return getDataSource();
	}

	@Override
	protected Object doGetTransaction() {
		DataSourceTransactionObject txObject = new DataSourceTransactionObject();
		txObject.setSavepointAllowed(isNestedTransactionAllowed());
		ConnectionHolder conHolder =
				(ConnectionHolder) TransactionSynchronizationManager.getResource(this.dataSource);
		txObject.setConnectionHolder(conHolder, false);
		return txObject;
	}

	@Override
	protected boolean isExistingTransaction(Object transaction) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
//		return (txObject.getConnectionHolder() != null && txObject.getConnectionHolder().isTransactionActive());
		if (txObject.getConnectionHolder() != null) {
			boolean transactionActive = (Boolean) ReflectionUtil.invokeMethod(txObject.getConnectionHolder(), "isTransactionActive", null);
			if (transactionActive) {
				return true;
			}
		}
		return false;
	}

	/**
	 * This implementation sets the isolation level but ignores the timeout.
	 */
	@Override
	protected void doBegin(Object transaction, TransactionDefinition definition) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
		Connection con = null;

		try {
			if (txObject.getConnectionHolder() == null ||
					txObject.getConnectionHolder().isSynchronizedWithTransaction()) {
				Connection newCon = this.dataSource.getConnection();
				if (logger.isDebugEnabled()) {
					logger.debug("Acquired Connection [" + newCon + "] for JDBC transaction");
				}
				txObject.setConnectionHolder(new ConnectionHolder(newCon), true);
			}

			txObject.getConnectionHolder().setSynchronizedWithTransaction(true);
			con = txObject.getConnectionHolder().getConnection();

			Integer previousIsolationLevel = DataSourceUtils.prepareConnectionForTransaction(con, definition);
			txObject.setPreviousIsolationLevel(previousIsolationLevel);

			// Switch to manual commit if necessary. This is very expensive in some JDBC drivers,
			// so we don't want to do it unnecessarily (for example if we've explicitly
			// configured the connection pool to set it already).
			if (con.getAutoCommit()) {
				txObject.setMustRestoreAutoCommit(true);
				if (logger.isDebugEnabled()) {
					logger.debug("Switching JDBC Connection [" + con + "] to manual commit");
				}
				con.setAutoCommit(false);
			}
//			txObject.getConnectionHolder().setTransactionActive(true);
			ReflectionUtil.invokeMethod(txObject.getConnectionHolder(), "setTransactionActive", new Object[]{true});

			int timeout = determineTimeout(definition);
			if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
				txObject.getConnectionHolder().setTimeoutInSeconds(timeout);
			}

			// Bind the session holder to the thread.
			if (txObject.isNewConnectionHolder()) {
				TransactionSynchronizationManager.bindResource(getDataSource(), txObject.getConnectionHolder());
			}
		}

		catch (Throwable ex) {
			if (txObject.isNewConnectionHolder()) {
				DataSourceUtils.releaseConnection(con, this.dataSource);
				txObject.setConnectionHolder(null, false);
			}
			throw new CannotCreateTransactionException("Could not open JDBC Connection for transaction", ex);
		}
	}

	@Override
	protected Object doSuspend(Object transaction) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
		txObject.setConnectionHolder(null);
		ConnectionHolder conHolder = (ConnectionHolder)
				TransactionSynchronizationManager.unbindResource(this.dataSource);
		return conHolder;
	}

	@Override
	protected void doResume(Object transaction, Object suspendedResources) {
		ConnectionHolder conHolder = (ConnectionHolder) suspendedResources;
		TransactionSynchronizationManager.bindResource(this.dataSource, conHolder);
	}

	@Override
	protected void doCommit(DefaultTransactionStatus status) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
		Connection con = txObject.getConnectionHolder().getConnection();
		if (status.isDebug()) {
			logger.debug("Committing JDBC transaction on Connection [" + con + "]");
		}
		try {
			con.commit();
		}
		catch (SQLException ex) {
			throw new TransactionSystemException("Could not commit JDBC transaction", ex);
		}
	}

	@Override
	protected void doRollback(DefaultTransactionStatus status) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
		Connection con = txObject.getConnectionHolder().getConnection();
		if (status.isDebug()) {
			logger.debug("Rolling back JDBC transaction on Connection [" + con + "]");
		}
		try {
			con.rollback();
		}
		catch (SQLException ex) {
			throw new TransactionSystemException("Could not roll back JDBC transaction", ex);
		}
	}

	@Override
	protected void doSetRollbackOnly(DefaultTransactionStatus status) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
		if (status.isDebug()) {
			logger.debug("Setting JDBC transaction [" + txObject.getConnectionHolder().getConnection() +
					"] rollback-only");
		}
		txObject.setRollbackOnly();
	}

	@Override
	protected void doCleanupAfterCompletion(Object transaction) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;

		// Remove the connection holder from the thread, if exposed.
		if (txObject.isNewConnectionHolder()) {
			TransactionSynchronizationManager.unbindResource(this.dataSource);
		}

		// Reset connection.
		Connection con = txObject.getConnectionHolder().getConnection();
		try {
			if (txObject.isMustRestoreAutoCommit()) {
				con.setAutoCommit(true);
			}
			DataSourceUtils.resetConnectionAfterTransaction(con, txObject.getPreviousIsolationLevel());
		}
		catch (Throwable ex) {
			logger.debug("Could not reset JDBC Connection after transaction", ex);
		}

		if (txObject.isNewConnectionHolder()) {
			if (logger.isDebugEnabled()) {
				logger.debug("Releasing JDBC Connection [" + con + "] after transaction");
			}
			DataSourceUtils.releaseConnection(con, this.dataSource);
		}

		txObject.getConnectionHolder().clear();
	}


	/**
	 * DataSource transaction object, representing a ConnectionHolder.
	 * Used as transaction object by DataSourceTransactionManager.
	 */
	private static class DataSourceTransactionObject extends JdbcTransactionObjectSupport {

		private boolean newConnectionHolder;

		private boolean mustRestoreAutoCommit;

		public void setConnectionHolder(ConnectionHolder connectionHolder, boolean newConnectionHolder) {
			super.setConnectionHolder(connectionHolder);
			this.newConnectionHolder = newConnectionHolder;
		}

		public boolean isNewConnectionHolder() {
			return this.newConnectionHolder;
		}

		public void setMustRestoreAutoCommit(boolean mustRestoreAutoCommit) {
			this.mustRestoreAutoCommit = mustRestoreAutoCommit;
		}

		public boolean isMustRestoreAutoCommit() {
			return this.mustRestoreAutoCommit;
		}

		public void setRollbackOnly() {
			getConnectionHolder().setRollbackOnly();
		}

		public boolean isRollbackOnly() {
			return getConnectionHolder().isRollbackOnly();
		}
	}



}
