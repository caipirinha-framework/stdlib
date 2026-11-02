package com.peterphi.std.guice.hibernate.webquery.embeddedpktest;

import com.peterphi.std.guice.database.annotation.Transactional;
import com.peterphi.std.guice.hibernate.dao.HibernateDao;
import com.peterphi.std.guice.restclient.jaxb.webquery.WebQuery;

import java.util.List;

public class EmbeddedPkDaoImpl extends HibernateDao<EmbeddedPkEntity, SomePrimaryKey>
{
	@SuppressWarnings("deprecation")
	public List<EmbeddedPkEntity> findByTimestamp(final long timestamp)
	{
		return find(new WebQuery().eq("id:timestamp", timestamp)).getList();
	}


	@Transactional
	public List<EmbeddedPkEntity> runQuery(String query)
	{
		return createReadQuery(query).list();
	}
}
