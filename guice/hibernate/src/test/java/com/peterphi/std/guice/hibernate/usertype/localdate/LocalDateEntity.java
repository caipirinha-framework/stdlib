package com.peterphi.std.guice.hibernate.usertype.localdate;

import org.joda.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity(name = "test_entity")
class LocalDateEntity
{
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	public Long id;

	@Column(name = "some_date", nullable = false)
	public LocalDate someDate = new LocalDate();

	@Column(name = "some_date_nullable", nullable = true)
	public LocalDate someNullDate = null;
}
