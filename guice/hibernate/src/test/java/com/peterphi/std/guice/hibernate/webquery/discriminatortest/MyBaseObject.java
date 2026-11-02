package com.peterphi.std.guice.hibernate.webquery.discriminatortest;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;

@Entity(name="inherit_base")
@Inheritance(strategy = InheritanceType.JOINED)
// DiscriminatorColum has been commented out to work around a bug in Hibernate - see https://hibernate.atlassian.net/browse/HHH-9501
// A Discriminator is not required for this functionality in our library, but I've left this reference here to make it easier to figure
// Out what's going wrong in the future
//@DiscriminatorColumn(discriminatorType = DiscriminatorType.STRING)
abstract class MyBaseObject
{
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	public Long id;


	@Override
	public String toString()
	{
		return getClass().getSimpleName() + "{" +
		       "id=" + id +
		       '}';
	}
}
