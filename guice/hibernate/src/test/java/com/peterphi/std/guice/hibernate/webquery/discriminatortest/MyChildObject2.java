package com.peterphi.std.guice.hibernate.webquery.discriminatortest;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity(name="inherit_two")
@DiscriminatorValue("two")
class MyChildObject2 extends MyBaseObject
{
}
