/**
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.data.neo4j.config;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.kernel.impl.transaction.SpringTransactionManager;
import org.neo4j.kernel.impl.transaction.UserTransactionImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.convert.ConversionService;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.data.convert.DefaultTypeMapper;
import org.springframework.data.convert.TypeMapper;
import org.springframework.data.neo4j.core.GraphDatabase;
import org.springframework.data.neo4j.core.TypeRepresentationStrategy;
import org.springframework.data.neo4j.fieldaccess.FieldAccessorFactoryFactory;
import org.springframework.data.neo4j.fieldaccess.Neo4jConversionServiceFactoryBean;
import org.springframework.data.neo4j.fieldaccess.NodeDelegatingFieldAccessorFactory;
import org.springframework.data.neo4j.fieldaccess.RelationshipDelegatingFieldAccessorFactory;
import org.springframework.data.neo4j.mapping.EntityInstantiator;
import org.springframework.data.neo4j.support.DelegatingGraphDatabase;
import org.springframework.data.neo4j.support.MappingInfrastructureFactoryBean;
import org.springframework.data.neo4j.support.Neo4jExceptionTranslator;
import org.springframework.data.neo4j.support.Neo4jTemplate;
import org.springframework.data.neo4j.support.index.IndexProvider;
import org.springframework.data.neo4j.support.index.IndexProviderImpl;
import org.springframework.data.neo4j.support.mapping.ClassNameAlias;
import org.springframework.data.neo4j.support.mapping.EntityAlias;
import org.springframework.data.neo4j.support.mapping.EntityStateHandler;
import org.springframework.data.neo4j.support.mapping.IndexCreationMappingEventListener;
import org.springframework.data.neo4j.support.mapping.Neo4jEntityFetchHandler;
import org.springframework.data.neo4j.support.mapping.Neo4jMappingContext;
import org.springframework.data.neo4j.support.mapping.SourceStateTransmitter;
import org.springframework.data.neo4j.support.mapping.TRSTypeAliasAccessor;
import org.springframework.data.neo4j.support.node.NodeEntityInstantiator;
import org.springframework.data.neo4j.support.node.NodeEntityStateFactory;
import org.springframework.data.neo4j.support.relationship.RelationshipEntityInstantiator;
import org.springframework.data.neo4j.support.relationship.RelationshipEntityStateFactory;
import org.springframework.data.neo4j.support.typerepresentation.ClassValueTypeInformationMapper;
import org.springframework.data.neo4j.support.typerepresentation.TypeRepresentationStrategyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.transaction.jta.UserTransactionAdapter;

import javax.transaction.TransactionManager;
import javax.validation.Validator;

import java.util.Collection;
import java.util.Set;

import static java.util.Arrays.asList;

/**
 * Abstract base class for code based configuration of Spring managed Neo4j infrastructure.
 * <p>Subclasses are required to provide an implementation of graphDbService ....
 * 
 * @author Thomas Risberg
 */
@Configuration
public abstract class Neo4jConfiguration {
    private GraphDatabaseService graphDatabaseService;

    private ConversionService conversionService;

    private Set<? extends Class<?>> initialEntitySet;

    @Autowired(required = false)
    private Validator validator;

    public GraphDatabaseService getGraphDatabaseService() {
        return graphDatabaseService;
    }

    @Qualifier("conversionService")
    @Autowired(required = false)
    public void setConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Autowired(required = true)
    public void setGraphDatabaseService(GraphDatabaseService graphDatabaseService) {
        this.graphDatabaseService = graphDatabaseService;
    }

    @Bean
    public MappingInfrastructureFactoryBean mappingInfrastructure() throws Exception {
        MappingInfrastructureFactoryBean factoryBean = new MappingInfrastructureFactoryBean();
        factoryBean.setGraphDatabaseService(getGraphDatabaseService());
        factoryBean.setTypeRepresentationStrategyFactory(typeRepresentationStrategyFactory());
        factoryBean.setConversionService(neo4jConversionService());
        factoryBean.setMappingContext(neo4jMappingContext());
        factoryBean.setEntityStateHandler(entityStateHandler());

        factoryBean.setNodeEntityStateFactory(nodeEntityStateFactory());
        factoryBean.setNodeTypeRepresentationStrategy(nodeTypeRepresentationStrategy());
        factoryBean.setNodeEntityInstantiator(graphEntityInstantiator());

        factoryBean.setRelationshipEntityStateFactory(relationshipEntityStateFactory());
        factoryBean.setRelationshipTypeRepresentationStrategy(relationshipTypeRepresentationStrategy());
        factoryBean.setRelationshipEntityInstantiator(graphRelationshipInstantiator());

        factoryBean.setTransactionManager(neo4jTransactionManager());
        factoryBean.setGraphDatabase(graphDatabase());
        
        factoryBean.setIndexProvider(indexProvider());

        if (validator!=null) {
            factoryBean.setValidator(validator);
        }
        return factoryBean;
    }

    @Bean
    public Neo4jTemplate neo4jTemplate() throws Exception {
        return new Neo4jTemplate(mappingInfrastructure().getObject());
	}

    @Bean
    public TypeRepresentationStrategy<Relationship> relationshipTypeRepresentationStrategy() throws Exception {
        return typeRepresentationStrategyFactory().getRelationshipTypeRepresentationStrategy();
    }

    @Bean
    public TypeRepresentationStrategy<Node> nodeTypeRepresentationStrategy() throws Exception {
        return typeRepresentationStrategyFactory().getNodeTypeRepresentationStrategy();
    }

    @Bean
    public TypeRepresentationStrategyFactory typeRepresentationStrategyFactory() throws Exception {
        return new TypeRepresentationStrategyFactory(graphDatabase(), indexProvider());
    }

    @Bean
    public EntityStateHandler entityStateHandler() throws Exception {
        return new EntityStateHandler(neo4jMappingContext(),graphDatabase());
    }


    @Bean
    public TypeMapper<Node> nodeTypeMapper() throws Exception {
        return new DefaultTypeMapper<Node>(new TRSTypeAliasAccessor<Node>(nodeTypeRepresentationStrategy()),asList(new ClassValueTypeInformationMapper()));
    }

    @Bean
    public TypeMapper<Relationship> relationshipTypeMapper() throws Exception {
        return new DefaultTypeMapper<Relationship>(new TRSTypeAliasAccessor<Relationship>(relationshipTypeRepresentationStrategy()),asList(new ClassValueTypeInformationMapper()));
    }

    @Bean
    public Neo4jEntityFetchHandler entityFetchHandler() throws Exception {
        final SourceStateTransmitter<Node> nodeSourceStateTransmitter = nodeStateTransmitter();
        final SourceStateTransmitter<Relationship> relationshipSourceStateTransmitter = new SourceStateTransmitter<Relationship>(relationshipEntityStateFactory());
        return new Neo4jEntityFetchHandler(entityStateHandler(), neo4jConversionService(), nodeSourceStateTransmitter, relationshipSourceStateTransmitter);
    }

    @Bean
    public SourceStateTransmitter<Node> nodeStateTransmitter() throws Exception {
        return new SourceStateTransmitter<Node>(nodeEntityStateFactory());
    }

    //@Scope(BeanDefinition.SCOPE_PROTOTYPE)
    @Bean
    protected ConversionService neo4jConversionService() throws Exception {
        final Neo4jConversionServiceFactoryBean neo4jConversionServiceFactoryBean = new Neo4jConversionServiceFactoryBean();
        if (conversionService!=null) {
            neo4jConversionServiceFactoryBean.addConverters(conversionService);
            return conversionService;
        }
        return neo4jConversionServiceFactoryBean.getObject();
    }

    @Bean
    protected RelationshipEntityInstantiator graphRelationshipInstantiator() throws Exception {
        return new RelationshipEntityInstantiator(entityStateHandler());
    }

    @Bean
	protected EntityInstantiator<Node> graphEntityInstantiator() throws Exception {
	   return new NodeEntityInstantiator(entityStateHandler());
	}

    @Bean
    public Neo4jMappingContext neo4jMappingContext() throws Exception {
        final Neo4jMappingContext mappingContext = new Neo4jMappingContext();
        if (initialEntitySet!=null) {
            mappingContext.setInitialEntitySet(initialEntitySet);
        }
        mappingContext.setEntityAlias(entityAlias());
        return mappingContext;
    }

    @Bean
    protected EntityAlias entityAlias() {
        return new ClassNameAlias();
    }

    @Bean
    public RelationshipEntityStateFactory relationshipEntityStateFactory() throws Exception {
        return new RelationshipEntityStateFactory(neo4jMappingContext(),relationshipDelegatingFieldAccessorFactory());
    }

    @Bean
    public NodeEntityStateFactory nodeEntityStateFactory() throws Exception {
        return new NodeEntityStateFactory(neo4jMappingContext(), nodeDelegatingFieldAccessorFactory());
    }

    @Bean
    public FieldAccessorFactoryFactory nodeDelegatingFieldAccessorFactory() throws Exception {
        return new NodeDelegatingFieldAccessorFactory.Factory();
    }

    @Bean
    public FieldAccessorFactoryFactory relationshipDelegatingFieldAccessorFactory() throws Exception {
        return new RelationshipDelegatingFieldAccessorFactory.Factory();
    }

    @Bean(name = {"neo4jTransactionManager","transactionManager"})
    @Qualifier("neo4jTransactionManager")
	public PlatformTransactionManager neo4jTransactionManager() {
        return createJtaTransactionManager();
	}

    @Bean
    public IndexCreationMappingEventListener indexCreationMappingEventListener() throws Exception {
        return new IndexCreationMappingEventListener(neo4jTemplate());
    }

    protected JtaTransactionManager createJtaTransactionManager() {
        JtaTransactionManager jtaTm = new JtaTransactionManager();
        final GraphDatabaseService gds = getGraphDatabaseService();
        if (gds instanceof GraphDatabaseAPI) {
            final TransactionManager txManager = ((GraphDatabaseAPI) gds).getTxManager();
            jtaTm.setTransactionManager(new SpringTransactionManager(gds));
            jtaTm.setUserTransaction(new UserTransactionImpl(txManager));
        } else {
            final NullTransactionManager tm = new NullTransactionManager();
            jtaTm.setTransactionManager(tm);
            jtaTm.setUserTransaction(new UserTransactionAdapter(tm));
        }
        return jtaTm;
    }

    @Bean
    @Autowired
    @DependsOn("graphDatabaseService")
    public GraphDatabase graphDatabase() {
        if (graphDatabaseService instanceof GraphDatabase) return (GraphDatabase) graphDatabaseService;
        return new DelegatingGraphDatabase(graphDatabaseService);
    }

    @Bean
    public ConfigurationCheck configurationCheck() throws Exception {
        return new ConfigurationCheck(neo4jTemplate(),neo4jTransactionManager());
    }

    @Bean
    public PersistenceExceptionTranslator persistenceExceptionTranslator() {
        return new Neo4jExceptionTranslator();
    }

    @Bean
    public IndexProvider indexProvider() throws Exception {
        return new IndexProviderImpl(neo4jMappingContext(), graphDatabase());
    }

    public Set<? extends Class<?>> getInitialEntitySet() {
        return initialEntitySet;
    }

    public void setInitialEntitySet(Set<? extends Class<?>> initialEntitySet) {
   		this.initialEntitySet = initialEntitySet;
   	}
}
