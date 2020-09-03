/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hiberntate.bugs;

import java.util.Map;

import org.junit.Test;

import org.hiberntate.util.WildFlyFunctionalTestCase;

/**
 * This template demonstrates how to develop a test case for Hibernate ORM, using the Java Persistence API.
 */
public class JPAUnitTestCase extends WildFlyFunctionalTestCase {

	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return new Class[] {
				// add coma separated entity classes used by the test here
				// e.g. TestEntity1.class, TestEntity2.class
		};
	}

	// addConfigOptions permits to add additional config options
	@Override
	protected void addConfigOptions(Map options) {
//		options.put( AvailableSettings.USE_SECOND_LEVEL_CACHE, "true" );
//		options.put( AvailableSettings.USE_QUERY_CACHE, "false" );
//		options.put( "hibernate.cache.region.factory_class", "infinispan" );
//		options.put(
//				"hibernate.cache.infinispan.cfg",
//				"org/infinispan/hibernate/cache/commons/builder/infinispan-configs-local.xml"
//		);
	}

	// Add your tests, using standard JUnit.
	@Test
	public void testIt() {
		inTransaction(
				entityManager -> {
					// Do stuff...
				}
		);
	}


}
