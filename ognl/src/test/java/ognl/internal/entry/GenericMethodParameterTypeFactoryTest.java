package ognl.internal.entry;

import org.junit.Test;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;

public class GenericMethodParameterTypeFactoryTest
{
	public abstract static class SingleParameterMap<V> extends AbstractMap<String, V>
	{
	}

	public abstract static class StringMap extends SingleParameterMap<String>
	{
	}

	public abstract static class ReorderedMap<V, K> extends AbstractMap<K, V>
	{
	}

	public abstract static class IntegerValueMap extends ReorderedMap<Integer, String>
	{
	}

	public interface ArrayConsumer<T>
	{
		void accept(T[] values, Set<T> other);
	}

	public abstract static class StringArrayConsumer implements ArrayConsumer<String>
	{
	}

	@Test
	public void resolvesDefaultInterfaceMethodThroughDifferentSuperclassArity() throws Exception
	{
		assertArrayEquals(new Class<?>[]{Object.class, String.class}, resolveMap(StringMap.class));
	}

	@Test
	public void resolvesReorderedTypeVariablesWithoutUsingSuperclassPositions() throws Exception
	{
		assertArrayEquals(new Class<?>[]{Object.class, Integer.class}, resolveMap(IntegerValueMap.class));
	}

	@Test
	public void erasesUnresolvedVariablesAndResolvesGenericArrays() throws Exception
	{
		assertArrayEquals(new Class<?>[]{Object.class, Object.class}, resolveMap(SingleParameterMap.class));
		final var method = ArrayConsumer.class.getMethod("accept", Object[].class, Set.class);
		assertArrayEquals(new Class<?>[]{String[].class, Set.class}, new GenericMethodParameterTypeFactory().create(
				new GenericMethodParameterTypeCacheEntry(method, StringArrayConsumer.class)));
	}

	private Class<?>[] resolveMap(final Class<?> target) throws Exception
	{
		final var method = Map.class.getMethod("getOrDefault", Object.class, Object.class);
		return new GenericMethodParameterTypeFactory().create(new GenericMethodParameterTypeCacheEntry(method, target));
	}
}
