package com.peterphi.std.guice.common.serviceprops.jaxbref;


import com.google.common.base.MoreObjects;
import com.peterphi.std.guice.common.lifecycle.GuiceLifecycleListener;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "MyType")
class MyType implements GuiceLifecycleListener
{
	@XmlAttribute
	public String name;
	public boolean postConstructCalled = false;


	public MyType()
	{
	}


	public MyType(final String name, final boolean postConstructCalled)
	{
		this.name = name;
		this.postConstructCalled = postConstructCalled;
	}


	@Override
	public boolean equals(final Object o)
	{
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		MyType myType = (MyType) o;

		if (postConstructCalled != myType.postConstructCalled)
			return false;
		return name != null ? name.equals(myType.name) : myType.name == null;
	}


	@Override
	public int hashCode()
	{
		int result = name != null ? name.hashCode() : 0;
		result = 31 * result + (postConstructCalled ? 1 : 0);
		return result;
	}


	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this).add("name", name).add("postConstructCalled", postConstructCalled).toString();
	}


	@Override
	public void postConstruct()
	{
		this.postConstructCalled = true;
	}
}
