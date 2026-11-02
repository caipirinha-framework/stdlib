package com.peterphi.std.guice.hibernate.largetable;

import com.peterphi.std.guice.database.annotation.Transactional;
import com.peterphi.std.guice.hibernate.dao.HibernateDao;
import com.peterphi.std.guice.restclient.jaxb.webquery.WebQuery;

public class MyTestDaoImpl extends HibernateDao<LargeTableSimplePKEntity, Long>
{
	@Transactional(readOnly = true)
	public long countWithAInName()
	{
		return count(new WebQuery().contains("name", "a"));
	}
}
