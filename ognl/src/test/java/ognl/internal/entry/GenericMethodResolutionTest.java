package ognl.internal.entry;

import ognl.AbstractMemberAccess;
import ognl.Ognl;
import ognl.OgnlContext;
import ognl.OgnlRuntime;
import org.junit.Test;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class GenericMethodResolutionTest
{
	private Class<?>[] resolve(Class<?> target, Method method) throws Exception
	{
		return new GenericMethodParameterTypeFactory().create(new GenericMethodParameterTypeCacheEntry(method, target));
	}

	@Test
	public void resolvesIntersectionBoundsAndMethodVariables() throws Exception
	{
		assertArrayEquals(new Class<?>[]{Integer.class}, resolve(IntegerBase.class, GenericBase.class.getMethod("bounded", Number.class)));
		assertArrayEquals(new Class<?>[]{Number.class}, resolve(IntegerBase.class, GenericBase.class.getMethod("methodBound", Number.class)));
	}

	@Test
	public void resolvesNestedArraysAndWildcardErasure() throws Exception
	{
		assertArrayEquals(new Class<?>[]{Integer[][].class, List.class},
		                  resolve(IntegerBase.class, GenericBase.class.getMethod("arrays", Number[][].class, List.class)));
	}

	@Test
	public void resolvesParameterisedEnclosingOwner() throws Exception
	{
		assertArrayEquals(new Class<?>[]{String.class, Integer.class},
		                  resolve(InnerConcrete.class, Outer.Inner.class.getMethod("pair", Object.class, Object.class)));
	}

	@Test
	public void runtimeParameterLookupUsesResolvedTypes() throws Exception
	{
		assertArrayEquals(new Class<?>[]{Integer.class},
		                  OgnlRuntime.findParameterTypes(IntegerBase.class, GenericBase.class.getMethod("bounded", Number.class)));
	}

	@Test
	public void genericResolutionPreservesOverloadSelection() throws Exception
	{
		StringOverloads root = new StringOverloads();
		OgnlContext context = Ognl.createDefaultContext(root, new AbstractMemberAccess()
		{
			@Override
			public boolean isAccessible(OgnlContext context, Object target, Member member, String property)
			{
				return true;
			}
		});
		assertEquals("generic:hello", Ognl.getValue("choose('hello')", context, root));
		assertEquals("number:7", Ognl.getValue("choose(7)", context, root));
	}

	public static class GenericBase<T extends Number & Comparable<T>>
	{
		public T bounded(T value) { return value; }
		public <N extends Number> N methodBound(N value) { return value; }
		public void arrays(T[][] values, List<? super T> sink) { }
	}

	public static class IntegerBase extends GenericBase<Integer> { }

	public static class Outer<T>
	{
		public class Inner<U>
		{
			public String pair(T first, U second) { return first + ":" + second; }
		}
	}

	public static class InnerConcrete extends Outer<String>.Inner<Integer>
	{
		public InnerConcrete(Outer<String> owner) { owner.super(); }
	}

	public static class GenericOverloads<T>
	{
		public String choose(T value) { return "generic:" + value; }
		public String choose(Number value) { return "number:" + value; }
	}

	public static class StringOverloads extends GenericOverloads<String> { }
}
