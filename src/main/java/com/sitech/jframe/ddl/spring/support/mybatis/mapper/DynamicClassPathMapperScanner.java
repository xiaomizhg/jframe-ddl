package com.sitech.jframe.ddl.spring.support.mybatis.mapper;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Set;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.StringUtils;

/**
 * 修改扫描器的bean class 注册 , 将原先的 MapperFactoryBean 替换为 DynamicMapperFactoryBean
 * 
 * @author zhangsf
 *
 */
public class DynamicClassPathMapperScanner extends
		ClassPathBeanDefinitionScanner {

	private boolean addToConfig = true;

	private SqlSessionFactory sqlSessionFactory;

	private SqlSessionTemplate sqlSessionTemplate;

	private String sqlSessionTemplateBeanName;

	private String sqlSessionFactoryBeanName;

	private Class<? extends Annotation> annotationClass;

	private Class<?> markerInterface;

	public DynamicClassPathMapperScanner(BeanDefinitionRegistry registry) {
		super(registry, false);
	}

	public void setAddToConfig(boolean addToConfig) {
		this.addToConfig = addToConfig;
	}

	public void setAnnotationClass(Class<? extends Annotation> annotationClass) {
		this.annotationClass = annotationClass;
	}

	public void setMarkerInterface(Class<?> markerInterface) {
		this.markerInterface = markerInterface;
	}

	public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
		this.sqlSessionFactory = sqlSessionFactory;
	}

	public void setSqlSessionTemplate(SqlSessionTemplate sqlSessionTemplate) {
		this.sqlSessionTemplate = sqlSessionTemplate;
	}

	public void setSqlSessionTemplateBeanName(String sqlSessionTemplateBeanName) {
		this.sqlSessionTemplateBeanName = sqlSessionTemplateBeanName;
	}

	public void setSqlSessionFactoryBeanName(String sqlSessionFactoryBeanName) {
		this.sqlSessionFactoryBeanName = sqlSessionFactoryBeanName;
	}

	/**
	 * Configures parent scanner to search for the right interfaces. It can
	 * search for all interfaces or just for those that extends a
	 * markerInterface or/and those annotated with the annotationClass
	 */
	public void registerFilters() {
		boolean acceptAllInterfaces = true;

		// if specified, use the given annotation and / or marker interface
		if (this.annotationClass != null) {
			addIncludeFilter(new AnnotationTypeFilter(this.annotationClass));
			acceptAllInterfaces = false;
		}

		// override AssignableTypeFilter to ignore matches on the actual marker
		// interface
		if (this.markerInterface != null) {
			addIncludeFilter(new AssignableTypeFilter(this.markerInterface) {
				@Override
				protected boolean matchClassName(String className) {
					return false;
				}
			});
			acceptAllInterfaces = false;
		}

		if (acceptAllInterfaces) {
			// default include filter that accepts all classes
			addIncludeFilter(new TypeFilter() {
				public boolean match(MetadataReader metadataReader,
						MetadataReaderFactory metadataReaderFactory)
						throws IOException {
					return true;
				}
			});
		}

		// exclude package-info.java
		addExcludeFilter(new TypeFilter() {
			public boolean match(MetadataReader metadataReader,
					MetadataReaderFactory metadataReaderFactory)
					throws IOException {
				String className = metadataReader.getClassMetadata()
						.getClassName();
				return className.endsWith("package-info");
			}
		});
	}

	/**
	 * Calls the parent search that will search and register all the candidates.
	 * Then the registered objects are post processed to set them as
	 * MapperFactoryBeans
	 */
	@Override
	public Set<BeanDefinitionHolder> doScan(String... basePackages) {
		Set<BeanDefinitionHolder> beanDefinitions = super.doScan(basePackages);

		if (beanDefinitions.isEmpty()) {
			logger.warn("No MyBatis mapper was found in '"
					+ Arrays.toString(basePackages)
					+ "' package. Please check your configuration.");
		} else {
			for (BeanDefinitionHolder holder : beanDefinitions) {
				GenericBeanDefinition definition = (GenericBeanDefinition) holder
						.getBeanDefinition();

				if (logger.isDebugEnabled()) {
					logger.debug("Creating MapperFactoryBean with name '"
							+ holder.getBeanName() + "' and '"
							+ definition.getBeanClassName()
							+ "' mapperInterface");
				}

				// the mapper interface is the original class of the bean
				// but, the actual class of the bean is MapperFactoryBean
				definition.getPropertyValues().add("mapperInterface",
						definition.getBeanClassName());
				definition.setBeanClass(DynamicMapperFactoryBean.class);

				definition.getPropertyValues().add("addToConfig",
						this.addToConfig);

				boolean explicitFactoryUsed = false;
				if (StringUtils.hasText(this.sqlSessionFactoryBeanName)) {
					definition.getPropertyValues().add(
							"sqlSessionFactory",
							new RuntimeBeanReference(
									this.sqlSessionFactoryBeanName));
					explicitFactoryUsed = true;
				} else if (this.sqlSessionFactory != null) {
					definition.getPropertyValues().add("sqlSessionFactory",
							this.sqlSessionFactory);
					explicitFactoryUsed = true;
				}

				if (StringUtils.hasText(this.sqlSessionTemplateBeanName)) {
					if (explicitFactoryUsed) {
						logger.warn("Cannot use both: sqlSessionTemplate and sqlSessionFactory together. sqlSessionFactory is ignored.");
					}
					definition.getPropertyValues().add(
							"sqlSessionTemplate",
							new RuntimeBeanReference(
									this.sqlSessionTemplateBeanName));
					explicitFactoryUsed = true;
				} else if (this.sqlSessionTemplate != null) {
					if (explicitFactoryUsed) {
						logger.warn("Cannot use both: sqlSessionTemplate and sqlSessionFactory together. sqlSessionFactory is ignored.");
					}
					definition.getPropertyValues().add("sqlSessionTemplate",
							this.sqlSessionTemplate);
					explicitFactoryUsed = true;
				}

				if (!explicitFactoryUsed) {
					if (logger.isDebugEnabled()) {
						logger.debug("Enabling autowire by type for MapperFactoryBean with name '"
								+ holder.getBeanName() + "'.");
					}
					definition
							.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
				}
			}
		}

		return beanDefinitions;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean isCandidateComponent(
			AnnotatedBeanDefinition beanDefinition) {
		return (beanDefinition.getMetadata().isInterface() && beanDefinition
				.getMetadata().isIndependent());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean checkCandidate(String beanName,
			BeanDefinition beanDefinition) throws IllegalStateException {
		if (super.checkCandidate(beanName, beanDefinition)) {
			return true;
		} else {
			logger.warn("Skipping MapperFactoryBean with name '" + beanName
					+ "' and '" + beanDefinition.getBeanClassName()
					+ "' mapperInterface"
					+ ". Bean already defined with the same name!");
			return false;
		}
	}

}
