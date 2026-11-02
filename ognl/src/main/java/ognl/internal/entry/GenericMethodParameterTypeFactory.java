/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package ognl.internal.entry;

import com.google.common.reflect.TypeToken;
import ognl.internal.CacheException;

import java.lang.reflect.Type;

/**
 * Compatibility replacement for OGNL 3.4.16/3.4.17's generic parameter resolver.
 * Upstream indexes a method's declaring type variables into the target's immediate
 * superclass arguments. Those are different declarations for Hibernate PersistentMap,
 * so Map.getOrDefault fails before invocation. Resolve the complete type hierarchy.
 *
 * @see <a href="https://repo.maven.apache.org/maven2/ognl/ognl/3.4.16/ognl-3.4.16-sources.jar">Upstream factory</a>
 */
public class GenericMethodParameterTypeFactory implements CacheEntryFactory<GenericMethodParameterTypeCacheEntry, Class<?>[]>
{
	@Override
	public Class<?>[] create(final GenericMethodParameterTypeCacheEntry entry) throws CacheException
	{
		final TypeToken<?> owner = TypeToken.of(entry.type);
		final Type[] parameters = entry.method.getGenericParameterTypes();
		final Class<?>[] resolved = new Class<?>[parameters.length];

		for (int i = 0; i < parameters.length; i++)
			resolved[i] = owner.resolveType(parameters[i]).getRawType();

		return resolved;
	}
}
