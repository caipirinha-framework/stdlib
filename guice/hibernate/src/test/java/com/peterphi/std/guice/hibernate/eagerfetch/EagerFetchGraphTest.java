package com.peterphi.std.guice.hibernate.eagerfetch;

import com.google.inject.Inject;
import com.peterphi.std.guice.database.annotation.EagerFetch;
import com.peterphi.std.guice.hibernate.dao.HibernateDao;
import com.peterphi.std.guice.testing.GuiceUnit;
import com.peterphi.std.guice.testing.com.peterphi.std.guice.testing.annotations.GuiceConfig;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import org.hibernate.Hibernate;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(GuiceUnit.class)
@GuiceConfig(config = "hibernate-tests-in-memory-hsqldb.properties", classPackages = EagerFetchGraphTest.class)
public class EagerFetchGraphTest
{
	@Inject
	HibernateDao<Document, Long> dao;

	@Test
	public void leafElementCollectionIsAvailableAfterDaoTransactionCloses()
	{
		Document document = new Document();
		document.tags.add("jakarta");
		dao.persist(document);

		Document loaded = dao.getById(document.id);
		assertTrue(Hibernate.isInitialized(loaded.tags));
		assertEquals(Set.of("jakarta"), loaded.tags);

		Document loadedByIds = dao.getListById(List.of(document.id)).getFirst();
		assertTrue(Hibernate.isInitialized(loadedByIds.tags));
		assertEquals(Set.of("jakarta"), loadedByIds.tags);
	}

	@Entity(name = "eager_document")
	public static class Document
	{
		@Id
		@GeneratedValue
		public Long id;

		@EagerFetch
		@ElementCollection
		public Set<String> tags = new HashSet<>();
	}
}
