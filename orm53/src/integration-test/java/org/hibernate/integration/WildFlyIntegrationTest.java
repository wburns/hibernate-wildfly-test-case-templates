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

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.model.Employee;

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
				.name( "hibernate.show_sql" )
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

		try {
			transaction.begin();
			log.info( "------> Transaction started" );
			try {
				Employee e = new Employee( "John Smith", "Engineer" );
				e.setOca( 0 );
				log.info( "------> Persisting entity('" + e.getName() + "', " + e.getOca() + ", '" + e.getTitle() + "')" );
				entityManager.persist( e );
			}
			catch (Throwable t) {
				try {
					transaction.rollback();
				}
				catch (Throwable rf) {
					log.log( Level.SEVERE, rf.getMessage(), rf );
				}
				throw t;
			}
			transaction.commit(); // entity persisted here unless explicitly flushed earlier
			log.info( "------> Transaction committed" );

			final ProcedureConsumer completeOnThread = (p) -> {
				Thread thread = new Thread( () -> {
					try {
						p.run();
					}
					catch (Throwable t) {
						log.log( Level.SEVERE, t.getMessage(), t );
					}
				} );
				thread.start();
				thread.join();
			};

			final ProcedureConsumer completeInTransaction = (p) -> {
				log.info( "------> Transaction starting ..." );
				transaction.begin();
				log.info( "------> Transaction started" );

				try {
					p.run();
				}
				catch (Throwable t) {
					try {
						log.info( "------> Transaction rolling back ..." );
						transaction.rollback();
						log.info( "------> Transaction rolled back" );
					}
					catch (Throwable rf) {
						log.log( Level.SEVERE, rf.getMessage(), rf );
					}
					throw t;
				}
				log.info( "------> Transaction committing ..." );
				transaction.commit();
				log.info( "------> Transaction committed" );
			};

			Procedure checkCache = () -> {
				log.info( "------> checkCache Retrieving entity ..." );
				// The container will substitute a unique instance if run on a different thread
				Employee e = entityManager.find( Employee.class, "John Smith" );
				log.info( "------> checkCache Found entity('" + e.getName() + "', " + e.getOca() + ", '" + e.getTitle() + "')" );
			};

			completeInTransaction.accept( () -> {
				completeOnThread.accept( checkCache ); // run on different thread
				log.info( "------> Updating employee ..." );
				entityManager.createQuery( "update Employee e set e.oca = 1, e.title = 'Senior Engineer'" )
						.executeUpdate();
				log.info( "------> Updated employee" );
				// The below triggers a query with EAP 7.2 / Hibernate 5.3
				// That indicates the update query (above) has cleared the level 2 cache before the tx commit
				// This did not happen with EAP 6.4 / Hibernate 4.2 nor EAP 7.1 / Hibernate 5.1
				completeOnThread.accept( checkCache ); // run on different thread
			} );

			{
				log.info( "------> Retrieving entity ..." );
				// The below does not trigger a query with EAP 7.2 / Hibernate 5.3
				// That indicates the update query transaction (above) did not cleared the level 2 cache during the tx commit
				// The cache was cleared with EAP 6.4 / Hibernate 4.2 and EAP 7.1 / Hibernate 5.1
				Employee e = entityManager.find( Employee.class, "John Smith" );
				log.info( "------> Found entity('" + e.getName() + "', " + e.getOca() + ", '" + e.getTitle() + "')" );
				if ( e.getOca() != 1 ) {
					// With EAP 7.2 / Hibernate 5.3 background thread reload leaves stale data in the cache
					fail( "Stale entity found with oca = " + e.getOca() );
				}
			}
		}
		catch (Throwable throwable) {
			throw throwable;
		}
		finally {
			log.info( "---> test completed." );
		}
	}

	@FunctionalInterface
	private interface Procedure {
		void run() throws Throwable;
	}

	@FunctionalInterface
	private interface ProcedureConsumer {
		void accept(Procedure p) throws Throwable;
	}

}
