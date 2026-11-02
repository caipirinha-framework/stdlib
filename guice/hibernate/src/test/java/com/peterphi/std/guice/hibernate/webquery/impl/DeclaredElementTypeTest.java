package com.peterphi.std.guice.hibernate.webquery.impl;

import com.google.common.reflect.TypeToken;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.Type;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class DeclaredElementTypeTest
{
	@Test
	public void concreteTargetRetainsParameterisedInterfaceBindings() throws Exception
	{
		assertEquals(new TypeToken<ConcreteInfo<String>>() {}, resolve(new TypeToken<Owner<String>>() {}, Owner.class.getField("values")));
		assertEquals(new TypeToken<ConcreteInfo<Integer>>() {}, resolve(new TypeToken<Owner<Integer>>() {}, Owner.class.getField("values")));
	}

	@Test
	public void concreteTargetRetainsWildcardAndVariableBounds() throws Exception
	{
		assertEquals(new TypeToken<ConcreteInfo<String>>() {}, resolve(new TypeToken<Owner<String>>() {}, Owner.class.getField("covariant")));
		assertEquals(new TypeToken<ConcreteInfo<String>>() {}, resolve(TypeToken.of(BoundedOwner.class), BoundedOwner.class.getField("values")));
	}

	private TypeToken<?> resolve(TypeToken<?> owner, Field member)
	{
		Type<?> element = new Type<ConcreteInfo>() {
			@Override public PersistenceType getPersistenceType() { return PersistenceType.EMBEDDABLE; }
			@Override public Class<ConcreteInfo> getJavaType() { return ConcreteInfo.class; }
		};
		PluralAttribute<?, ?, ?> attribute = (PluralAttribute<?, ?, ?>) Proxy.newProxyInstance(
				getClass().getClassLoader(), new Class<?>[]{PluralAttribute.class}, (proxy, method, arguments) -> switch (method.getName()) {
					case "getJavaMember" -> member;
					case "getJavaType" -> Set.class;
					case "getElementType" -> element;
					default -> throw new UnsupportedOperationException(method.getName());
				});
		return new PersistentAttributeMetadata(owner, attribute).getElementType(attribute);
	}

	public interface Info<T> {}
	public static class ConcreteInfo<T> implements Info<T> {}
	public static class Owner<T>
	{
		public Set<Info<T>> values;
		public Set<? extends Info<T>> covariant;
	}
	public static class BoundedOwner<T extends Info<String>> { public Set<T> values; }
}
