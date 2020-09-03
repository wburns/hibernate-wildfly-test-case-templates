/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hiberntate.util;

import java.util.function.Consumer;
import java.util.function.Function;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

import org.jboss.logging.Logger;

public class TransactionUtil {
	private static final Logger log = Logger.getLogger( TransactionUtil.class );
	public static final String ACTION_COMPLETED_TXN = "Execution of action caused managed transaction to be completed";

	public static void inEntityManager(EntityManagerFactory emf, Consumer<EntityManager> action) {
		log.trace( "#inEntityManager(EntityManagerFactory,action)" );
		final EntityManager entityManager = emf.createEntityManager();
		try {
			log.trace( "EntityManager opened, calling action" );
			action.accept( entityManager );
			log.trace( "called action" );
		}
		finally {
			if ( entityManager != null ) {
				entityManager.close();
			}
			log.trace( "EntityManager closed" );
		}
	}

	public static <R> R fromEntityManager(EntityManagerFactory emf, Function<EntityManager, R> action) {
		log.trace( "#fromEntityManager(EntityManagerFactory,action)" );
		EntityManager entityManager = emf.createEntityManager();
		try {
			log.trace( "EntityManager opened, calling action" );
			return action.apply( entityManager );
		}
		finally {
			if ( entityManager != null ) {
				entityManager.close();
			}
			log.trace( "EntityManager closed" );
		}
	}


	public static void inTransaction(EntityManagerFactory factory, Consumer<EntityManager> action) {
		log.trace( "#inTransaction(factory, action)" );

		inEntityManager(
				factory,
				entityManager -> inTransaction( entityManager, action )
		);
	}

	public static <R> R fromTransaction(EntityManagerFactory factory, Function<EntityManager, R> action) {
		log.trace( "#inTransaction(factory, action)" );

		return fromEntityManager(
				factory,
				entityManager -> fromTransaction( entityManager, action )
		);
	}

	public static void inTransaction(EntityManager entityManager, Consumer<EntityManager> action) {
		log.trace( "inTransaction(entityManager,action)" );

		final EntityTransaction txn = entityManager.getTransaction();
		log.trace( "Started transaction" );

		try {
			txn.begin();
			log.trace( "Calling action in txn" );
			action.accept( entityManager );
			log.trace( "Called action - in txn" );

			if ( !txn.isActive() ) {
				throw new TransactionManagementException( ACTION_COMPLETED_TXN );
			}
		}
		catch (Exception e) {
			// an error happened in the action
			if ( !txn.isActive() ) {
				log.warn( ACTION_COMPLETED_TXN, e );
			}
			else {
				log.trace( "Rolling back transaction due to action error" );
				try {
					txn.rollback();
					log.trace( "Rolled back transaction due to action error" );
				}
				catch (Exception inner) {
					log.trace( "Rolling back transaction due to action error failed; throwing original error" );
				}
			}

			throw e;
		}

		// action completed with no errors - attempt to commit the transaction allowing
		// 		any RollbackException to propagate.  Note that when we get here we know the
		//		txn is active

		log.trace( "Committing transaction after successful action execution" );
		try {
			txn.commit();
			log.trace( "Committing transaction after successful action execution - success" );
		}
		catch (Exception e) {
			log.trace( "Committing transaction after successful action execution - failure" );
			throw e;
		}
	}

	public static <R> R fromTransaction(EntityManager entityManager, Function<EntityManager, R> action) {
		log.trace( "inTransaction(entityManager,action)" );

		final EntityTransaction txn = entityManager.getTransaction();

		log.trace( "Started transaction" );
		final R result;
		try {
			txn.begin();
			log.trace( "Calling action in txn" );
			result = action.apply( entityManager );
			log.trace( "Called action - in txn" );

			if ( !txn.isActive() ) {
				throw new TransactionManagementException( ACTION_COMPLETED_TXN );
			}
		}
		catch (Exception e) {
			// an error happened in the action
			if ( !txn.isActive() ) {
				log.warn( ACTION_COMPLETED_TXN, e );
			}
			else {
				log.trace( "Rolling back transaction due to action error" );
				try {
					txn.rollback();
					log.trace( "Rolled back transaction due to action error" );
				}
				catch (Exception inner) {
					log.trace( "Rolling back transaction due to action error failed; throwing original error" );
				}
			}

			throw e;
		}

		assert result != null;

		// action completed with no errors - attempt to commit the transaction allowing
		// 		any RollbackException to propagate.  Note that when we get here we know the
		//		txn is active

		log.trace( "Committing transaction after successful action execution" );
		try {
			txn.commit();
			log.trace( "Committing transaction after successful action execution - success" );
		}
		catch (Exception e) {
			log.trace( "Committing transaction after successful action execution - failure" );
			throw e;
		}

		return result;
	}

	private static class TransactionManagementException extends RuntimeException {
		public TransactionManagementException(String message) {
			super( message );
		}
	}
}
