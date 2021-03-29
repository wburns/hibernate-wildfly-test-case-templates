/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.integration;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.UserTransaction;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.model.Employee;
import org.hibernate.stat.CacheRegionStatistics;

import org.junit.After;
import org.junit.Before;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * @author Andrea Boriero
 * @author Stephen Fikes
 * @author Gail Badner
 */
@RunWith(Arquillian.class)
public class WildFlyIntegration53Test {

	// Add your entities here.
	// .addClass( TestEntity.class )
	@Deployment
	public static WebArchive createDeployment() {
		return ShrinkWrap.create( WebArchive.class )
				.addClass( Employee.class )
				.addAsWebInfResource( EmptyAsset.INSTANCE, "beans.xml" )
				.addAsResource( new StringAsset( persistenceXml().exportAsString() ), "META-INF/persistence.xml" );
	}

	private static PersistenceDescriptor persistenceXml() {
		return Descriptors.create( PersistenceDescriptor.class )
				.version( "2.2" )
				.createPersistenceUnit()
				.name( "primary" )
				.transactionType( PersistenceUnitTransactionType._JTA )
				.jtaDataSource( "java:jboss/datasources/ExampleDS" )
				.sharedCacheMode( "ENABLE_SELECTIVE" )
				.getOrCreateProperties()
				.createProperty()
				.name( AvailableSettings.SHOW_SQL )
				.value( "true" )
				.up()
				.createProperty()
				.name( "hibernate.hbm2ddl.auto" )
				.value( "create-drop" )
				.up()
				.createProperty()
				.name( AvailableSettings.USE_SECOND_LEVEL_CACHE )
				.value( "true" )
				.up()
				.createProperty()
				.name( AvailableSettings.USE_QUERY_CACHE )
				.value( "false" )
				.up()
				.createProperty()
				.name( AvailableSettings.GENERATE_STATISTICS )
				.value( "true" )
				.up()
				.up()
				.up();
	}

	static Logger log = Logger.getLogger( WildFlyIntegration53Test.class.getCanonicalName() );

	@PersistenceContext
	private EntityManager entityManager;

	@Inject
	private UserTransaction userTransaction;

	private CacheRegionStatistics statistics;

	private final ProcedureConsumer completeInTransaction = (p) -> {
		log.info("------> Transaction starting ...");
		userTransaction.begin();
		log.info("------> Transaction started");

		try {
			p.run();
		} catch (Throwable t) {
			try {
				log.info("------> Transaction rolling back ...");
				userTransaction.rollback();
				log.info("------> Transaction rolled back");
			} catch (Throwable rf) {
				log.log(Level.SEVERE, rf.getMessage(), rf);
			}
			throw t;
		}
		log.info("------> Transaction committing ...");
		userTransaction.commit();
		log.info("------> Transaction committed");
	};

	@Before
	public void setupData() throws Throwable {

		entityManager.getEntityManagerFactory().unwrap( SessionFactory.class ).getStatistics().clear();
		statistics = entityManager.getEntityManagerFactory().unwrap( SessionFactory.class ).getStatistics()
				.getCacheRegionStatistics( Employee.class.getName() );

		completeInTransaction.accept( () -> {
			Employee e = new Employee( "John Smith", "Engineer" );
			e.setOca( 0 );
			log.info( "------> Persisting entity('" + e.getName() + "', " + e.getOca() + ", '" + e.getTitle() + "')" );
			entityManager.persist( e );
		});
	}

	@After
	public void cleanupData() throws Throwable{

		completeInTransaction.accept( () -> {
			entityManager.remove( entityManager.find( Employee.class, "John Smith" ) );
		});
	}

	@Test
	public void testOldValueInOtherThreadNewValueInCurrentAfterUpdate() throws Throwable {

		assertTrue( entityManager.getEntityManagerFactory().getCache().contains( Employee.class, "John Smith" ) );

		assertEquals( 0, statistics.getHitCount() );
		assertEquals( 0, statistics.getMissCount() );
		assertEquals( 1, statistics.getPutCount() );

		completeInTransaction.accept(() -> {

			// Verify that the value can be loaded from the cache.
			Employee employeeFromDifferentThread = findOnDifferentThread();
			assertEquals( employeeFromDifferentThread.getOca(), 0 );
			assertEquals( 1, statistics.getHitCount() );
			assertEquals( 0, statistics.getMissCount() );
			assertEquals( 1, statistics.getPutCount() );

			log.info("------> Updating employee ...");
			entityManager.createQuery("update Employee e set e.oca = 1, e.title = 'Senior Engineer'").executeUpdate();

			// The old value should be read in a different thread.
			employeeFromDifferentThread = findOnDifferentThread();
			assertEquals( 0, employeeFromDifferentThread.getOca() );

			// The following assertions show what happened using WildFly 10.1.
			// (there was a cache hit)
			// assertEquals( 2, statistics.getHitCount() );
			// assertEquals( 0, statistics.getMissCount() );
			// assertEquals( 1, statistics.getPutCount() );

			// The following assertions show what happens in Wildfly 22
			// (there was a cache miss, and the old value was put in the cache)
			assertEquals( 1, statistics.getHitCount() );
			assertEquals( 1, statistics.getMissCount() );
			assertEquals( 1, statistics.getPutCount() );

			// Now get the updated value in this thread
			final Employee employee = entityManager.find( Employee.class, "John Smith" );
			// The following fails because the old value was read from the cache.
			// This is obviously wrong.
			assertEquals( 1, employee.getOca() );
		});
	}

	@Test
	public void testUpdatedValueNotCachedBeforeCommit() throws Throwable {
		assertTrue( entityManager.getEntityManagerFactory().getCache().contains( Employee.class, "John Smith" ) );
		assertEquals( 0, statistics.getHitCount() );
		assertEquals( 0, statistics.getMissCount() );
		assertEquals( 1, statistics.getPutCount() );

		completeInTransaction.accept( () -> {

			log.info( "------> Updating employee ..." );
			entityManager.createQuery( "update Employee e set e.oca = 1, e.title = 'Senior Engineer'" ).executeUpdate();

			// The value should be obtained from the database, and the new value should not be cached.
			Employee e = entityManager.find( Employee.class, "John Smith" );
			assertEquals( 1, e.getOca() );
			assertEquals( 0, statistics.getHitCount() );
			assertEquals( 1, statistics.getMissCount() );
			// The following fails because the new value was put in the cache.
			assertEquals( 1, statistics.getPutCount() );
		} );
	}

	@Test
	public void testOldValueFromOtherThreadAfterUpdateBeforeCommit() throws Throwable {
		assertTrue( entityManager.getEntityManagerFactory().getCache().contains( Employee.class, "John Smith" ) );
		assertEquals( 0, statistics.getHitCount() );
		assertEquals( 0, statistics.getMissCount() );
		assertEquals( 1, statistics.getPutCount() );

		completeInTransaction.accept( () -> {

			log.info( "------> Updating employee ..." );
			entityManager.createQuery( "update Employee e set e.oca = 1, e.title = 'Senior Engineer'" ).executeUpdate();

			// The value should be obtained from the database, and the new value should not be cached.
			Employee e = entityManager.find( Employee.class, "John Smith" );
			assertEquals( 1, e.getOca() );

			// #testUpdatedValueNotCachedBeforeCommit showed that the
			// the updated value got cached.

			// A lookup in a different thread should return the old value
			Employee employeeFromDifferentThread = findOnDifferentThread();
			// The following fails because the updated value was read from the cache.
			// This is obviously wrong.
			assertEquals( 0, employeeFromDifferentThread.getOca() );
		} );
	}

	@Test
	public void testUpdatedValueInCacheAfterCommit() throws Throwable {
		assertTrue( entityManager.getEntityManagerFactory().getCache().contains( Employee.class, "John Smith" ) );
		assertEquals( 0, statistics.getHitCount() );
		assertEquals( 0, statistics.getMissCount() );
		assertEquals( 1, statistics.getPutCount() );

		completeInTransaction.accept( () -> {

			log.info( "------> Updating employee ..." );
			entityManager.createQuery( "update Employee e set e.oca = 1, e.title = 'Senior Engineer'" ).executeUpdate();

			// A lookup in a different thread should return the old value
			Employee employeeFromDifferentThread = findOnDifferentThread();
			assertEquals( 0, employeeFromDifferentThread.getOca() );
		});

		Employee employee = entityManager.find( Employee.class, "John Smith" );
		// The following fails because the old value is still cached.
		assertEquals( 1, employee.getOca() );
	}

	@Test
	public void testSimpleCase() throws Throwable {
		assertTrue( entityManager.getEntityManagerFactory().getCache().contains( Employee.class, "John Smith" ) );
		assertEquals( 0, statistics.getHitCount() );
		assertEquals( 0, statistics.getMissCount() );
		assertEquals( 1, statistics.getPutCount() );

		completeInTransaction.accept( () -> {
			log.info( "------> Updating employee ..." );
			entityManager.createQuery( "update Employee e set e.oca = 1, e.title = 'Senior Engineer'" ).executeUpdate();
		});

		completeInTransaction.accept( () -> {

			// First find puts the updated value in the cache.
			Employee employee = entityManager.find( Employee.class, "John Smith" );
			assertEquals( 1, employee.getOca() );
			assertEquals( 0, statistics.getHitCount() );
			assertEquals( 1, statistics.getMissCount() );
			assertEquals( 2, statistics.getPutCount() );

			entityManager.clear();

			// Second find reads from the cache.
			employee = entityManager.find( Employee.class, "John Smith" );
			assertEquals( 1, employee.getOca() );
			assertEquals( 1, statistics.getHitCount() );
			assertEquals( 1, statistics.getMissCount() );
			assertEquals( 2, statistics.getPutCount() );
		});
	}

	private Employee findOnDifferentThread() {
		// H2 appears to do a dirty read if the same thread is used with a new EntityManager.
		// Use a new EntityManager with different thread to read the entity so H2 doesn't get confused.
		try {
			return CompletableFuture.supplyAsync( () -> {
				final EntityManager entityManagerNew = entityManager.getEntityManagerFactory().createEntityManager();
				try {
					return entityManagerNew.find( Employee.class, "John Smith" );
				}
				finally {
					entityManagerNew.close();
				}
			} ).get();
		}
		catch (Exception exception) {
			throw new RuntimeException( exception );
		}
	}

	@FunctionalInterface
	private interface Procedure {
		void run();
	}

	@FunctionalInterface
	private interface ProcedureConsumer  {
		void accept(Procedure p) throws Throwable;
	}
}
