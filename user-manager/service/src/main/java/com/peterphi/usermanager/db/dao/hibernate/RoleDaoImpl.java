package com.peterphi.usermanager.db.dao.hibernate;

import com.peterphi.std.guice.database.annotation.Transactional;
import com.peterphi.usermanager.db.entity.RoleEntity;
import com.google.inject.Singleton;
import com.peterphi.std.guice.hibernate.dao.HibernateDao;

@Singleton
public class RoleDaoImpl extends HibernateDao<RoleEntity, String>
{
	@Transactional
	public RoleEntity getOrCreate(final String id, final String caption)
	{
		RoleEntity existing = getById(id);

		if (existing == null)
		{
			existing = new RoleEntity();

			existing.setId(id);
			existing.setCaption(caption);

			persist(existing);
		}

		return existing;
	}
}
