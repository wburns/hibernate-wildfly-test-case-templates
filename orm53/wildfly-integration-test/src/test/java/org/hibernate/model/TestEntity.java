package org.hibernate.model;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

/**
 * @author Andrea Boriero
 */
@Entity
public class TestEntity {
	@Id
	@GeneratedValue
	public Long id;

	public String field;

	TestEntity() {
	}

	public TestEntity(String field) {
		this.field = field;
	}

	public Long getId() {
		return id;
	}

	public String getField() {
		return field;
	}

	public void setField(String field) {
		this.field = field;
	}
}
