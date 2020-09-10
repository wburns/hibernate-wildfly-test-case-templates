/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.integration;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.UserTransaction;

import org.hibernate.Session;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.model.TestEntity;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;
import org.jboss.shrinkwrap.descriptor.api.persistence21.PersistenceDescriptor;
import org.jboss.shrinkwrap.descriptor.api.persistence21.PersistenceUnitTransactionType;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;


/**
 * @author Andrea Boriero
 */
@RunWith(Arquillian.class)
public class WildFlyIntegrationTest {

	// Add your entities here.
	// .addClass( TestEntity.class )
	@Deployment
	public static WebArchive createDeployment() {
		return ShrinkWrap.create( WebArchive.class )
				.addClass( TestEntity.class )
				.addAsWebInfResource( EmptyAsset.INSTANCE, "beans.xml" )
				.addAsResource( new StringAsset( persistenceXml().exportAsString() ), "META-INF/persistence.xml" );
	}

	private static PersistenceDescriptor persistenceXml() {
		return Descriptors.create( PersistenceDescriptor.class )
				.version( "2.1" )
				.createPersistenceUnit()
				.name( "primary" )
				.transactionType( PersistenceUnitTransactionType._JTA )
				.jtaDataSource( "java:jboss/datasources/ExampleDS" )
				.sharedCacheMode( "ENABLE_SELECTIVE" )
				.getOrCreateProperties()
				.createProperty()
				.name( "hibernate.hbm2ddl.auto" )
				.value( "create-drop" )
				.up()
				.createProperty()
				.name( "hibernate.show_sql" )
				.value( "true" )
				.up()
				.createProperty()
				.name( AvailableSettings.USE_SECOND_LEVEL_CACHE )
				.value( "true" )
				.up()
				.createProperty()
				.name( AvailableSettings.USE_QUERY_CACHE )
				.value( "false" )
				.up()
				.up()
				.up();
	}

	static Logger log = Logger.getLogger( WildFlyIntegrationTest.class.getCanonicalName() );

	@PersistenceContext
	private EntityManager entityManager;

	@Inject
	private UserTransaction transaction;

	@Test
	public void testIt() throws Throwable {
		log.info( "---> test started." );

		TestEntity e = new TestEntity( "Hibernate" );
		try {
			transaction.begin();
			entityManager.persist( e );
			transaction.commit();
		}
		catch (Throwable throwable) {
			transaction.rollback();
			throw throwable;
		}
		TestEntity finded = entityManager.find( TestEntity.class, e.getId() );
		assertThat( finded, notNullValue() );
	}

}
