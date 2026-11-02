package com.peterphi.std.guice.hibernate.webquery.discriminatortest;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.util.UUID;

@Entity(name = "inherit_one")
@DiscriminatorValue("one")
class MyChildObject1 extends MyBaseObject
{
	@Column(nullable = true)
	String someId = UUID.randomUUID().toString();
}
