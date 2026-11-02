package com.peterphi.std.guice.hibernate.webquery.impl.jpa;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

public interface JPAQueryCustomiser
{
	void apply(CriteriaBuilder cb, CriteriaQuery query, Root root, JPAQueryBuilderInternal builder);

}
