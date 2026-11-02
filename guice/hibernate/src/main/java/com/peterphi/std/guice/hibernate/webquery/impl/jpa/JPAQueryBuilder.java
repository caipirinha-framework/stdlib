package com.peterphi.std.guice.hibernate.webquery.impl.jpa;

import com.google.common.reflect.TypeToken;

import com.peterphi.std.NotImplementedException;
import com.peterphi.std.guice.hibernate.webquery.impl.QEntity;
import com.peterphi.std.guice.hibernate.webquery.impl.QRelation;
import com.peterphi.std.guice.hibernate.webquery.impl.WQTypeHelper;
import com.peterphi.std.guice.hibernate.webquery.impl.jpa.jpafunctions.JPAJoin;
import com.peterphi.std.guice.hibernate.webquery.impl.jpa.jpafunctions.WQPath;
import com.peterphi.std.guice.restclient.jaxb.webquery.WQConstraint;
import com.peterphi.std.guice.restclient.jaxb.webquery.WQConstraintLine;
import com.peterphi.std.guice.restclient.jaxb.webquery.WQFunctionType;
import com.peterphi.std.guice.restclient.jaxb.webquery.WQGroup;
import com.peterphi.std.guice.restclient.jaxb.webquery.WQGroupType;
import com.peterphi.std.guice.restclient.jaxb.webquery.WQOrder;
import com.peterphi.std.guice.restclient.jaxb.webquery.WebQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.metamodel.Attribute;
import org.apache.commons.lang.StringUtils;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.hibernate.query.sqm.tree.domain.SqmPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JPAQueryBuilder<T, ID> implements JPAQueryBuilderInternal
{
	private static final Logger log = LoggerFactory.getLogger(JPAQueryBuilder.class);

	private record OrderExpr( Order order,  WQOrder src){}

	private final Session session;
	private final CriteriaBuilder criteriaBuilder;

	private QEntity entity;
	private Root root;

	private CriteriaQuery generated;

	private final List<Predicate> conditions = new ArrayList<>();
	private final List<OrderExpr> orders = new ArrayList<>();
	private final Map<String, JPAJoin> joins = new HashMap<>();

	private Integer limit;
	private Integer offset;

	// If specified, overrides the default fetches
	private Set<String> fetches;

	private final Map<ParameterExpression, Object> params = new HashMap<>();

	private boolean permitSchemaPrivate = false;

	public JPAQueryBuilder(final Session session, final QEntity entity, final boolean defaultPermitSchemaPrivate)
	{
		this.session = session;
		this.criteriaBuilder = session.getCriteriaBuilder();
		this.entity = entity;
		this.permitSchemaPrivate = defaultPermitSchemaPrivate;
	}


	@Override
	public JPAQueryBuilder<T, ID> withPrivateSchemaAccess()
	{
		this.permitSchemaPrivate = true;
		return this;
	}


	void addFrom(final String subclasses)
	{
		if (StringUtils.isEmpty(subclasses))
		{
			// No subclass constraints, just a simple FROM call
			this.root = generated.from(entity.getMetamodelEntity());
		}
		else
		{
			List<String> discriminators = Arrays.asList(subclasses.split(","));

			final List<Class<?>> classes = getClassesByDiscriminators(discriminators);
			QEntity commonParentClass = entity.getCommonSubclass(discriminators);

			// All the discriminators share a common subclass, so allow the query to reference columns of that subclass
			if (commonParentClass != entity)
				this.entity = commonParentClass;

			this.root = generated.from(entity.getMetamodelEntity());

			// Apply the subclass constraints (if there are multiple constraints)
			if (classes.size() > 1)
				this.conditions.add(root.type().in(classes));
		}
	}


	/**
	 * Translates the set of string discriminators into entity classes
	 *
	 * @return
	 */
	private List<Class<?>> getClassesByDiscriminators(Collection<String> discriminators)
	{
		Map<String, Class<?>> entitiesByName = new HashMap<>();

		// Prepare a Map of discriminator name -> entity class
		for (QEntity child : entity.getSubEntities())
		{
			entitiesByName.put(child.getDiscriminatorValue(), child.getEntityClass());
		}

		// If the root class isn't abstract then add it to the list of possible discriminators too
		if (!entity.isEntityClassAbstract())
			entitiesByName.put(entity.getDiscriminatorValue(), entity.getEntityClass());

		// Translate the discriminator string values to classes
		List<Class<?>> classes = new ArrayList<>(discriminators.size());
		for (String discriminator : discriminators)
		{
			final Class<?> clazz = entitiesByName.get(discriminator);

			if (clazz != null)
				classes.add(clazz);
			else
				throw new IllegalArgumentException("Invalid class discriminator '" +
				                                   discriminator +
				                                   "', expected one of: " +
				                                   entitiesByName.keySet());
		}

		return classes;
	}


	@Override
	public void addConstraints(final Predicate... predicates)
	{
		for (Predicate predicate : predicates)
			this.conditions.add(predicate);

		// Add the constraints to the query
		generated.where(criteriaBuilder.and(conditions.toArray(new Predicate[0])));
	}


	@Override
	public void addConstraints(List<WQConstraintLine> constraints)
	{
		for (Predicate predicate : parseConstraint(constraints))
		{
			conditions.add(predicate);
		}

		// Add the constraints to the query
		generated.where(criteriaBuilder.and(conditions.toArray(new Predicate[0])));
	}


	public Expression<?> getProperty(final String path)
	{
		return getProperty(new WQPath(path));
	}


	/**
	 * Get a property, automatically creating any joins along the way as needed
	 *
	 * @param path
	 *
	 * @return
	 */
	@Override
	public Expression<?> getProperty(final WQPath path)
	{
		final JPAJoin join = getOrCreateJoin(path.getTail());

		return join.property(path.getHead().getPath(), permitSchemaPrivate);
	}


	/**
	 * Ensure a join has been set up for a path
	 *
	 * @param path
	 *
	 * @return
	 */
	@Override
	public JPAJoin getOrCreateJoin(final WQPath path)
	{
		if (path == null)
			return new JPAJoin(criteriaBuilder, entity, root, false);

		if (!joins.containsKey(path.getPath()))
		{
			final JPAJoin parent = getOrCreateJoin(path.getTail());

			final JPAJoin join = parent.join(path.getHead().getPath());

			joins.put(path.getPath(), join);

			return join;
		}
		else
		{
			return joins.get(path.getPath());
		}
	}


	private List<Predicate> parseConstraint(List<WQConstraintLine> constraints)
	{
		List<Predicate> list = new ArrayList<>(constraints.size());

		for (WQConstraintLine line : constraints)
		{
			if (line instanceof WQConstraint c)
				list.add(parseConstraint(c));
			else if (line instanceof WQGroup g)
				list.add(parseConstraint(g));
			else throw new IllegalArgumentException("Unknown constraint line type: " + line);
		}

		return list;
	}


	private <T> Predicate parseConstraint(final WQConstraint line)
	{
		final Expression property = getProperty(new WQPath(line.field));

		switch (line.function)
		{
			case IS_NULL:
				return criteriaBuilder.isNull(property);
			case NOT_NULL:
				return criteriaBuilder.isNotNull(property);
			case EQ:
				return criteriaBuilder.equal(property, parse(property, line.value));
			case NOT_IN:
				return criteriaBuilder.not(property.in(param(parseValueList(property, line.valuelist))));
			case IN:
				return property.in(param(parseValueList(property, line.valuelist)));
			case NEQ:
				return criteriaBuilder.notEqual(property, parse(property, line.value));
			case CONTAINS:
				return criteriaBuilder.like(property, "%" + line.value + "%");
			case NOT_CONTAINS:
				return criteriaBuilder.notLike(property, "%" + line.value + "%");
			case STARTS_WITH:
				return criteriaBuilder.like(property, line.value + "%");
			case NOT_STARTS_WITH:
				return criteriaBuilder.notLike(property, line.value + "%");
			case RANGE:
				return criteriaBuilder.between(property, parse(property, line.value), parse(property, line.value2));
			case GE:
				return criteriaBuilder.greaterThanOrEqualTo(property, parse(property, line.value));
			case GT:
				return criteriaBuilder.greaterThan(property, parse(property, line.value));
			case LE:
				return criteriaBuilder.lessThanOrEqualTo(property, parse(property, line.value));
			case LT:
				return criteriaBuilder.lessThan(property, parse(property, line.value));
			case EQ_REF:
				return criteriaBuilder.equal(property, getProperty(new WQPath(line.value)));
			case NEQ_REF:
				return criteriaBuilder.notEqual(property, getProperty(new WQPath(line.value)));
			case GE_REF:
				return criteriaBuilder.ge(property, (Expression) getProperty(new WQPath(line.value)));
			case GT_REF:
				return criteriaBuilder.gt(property, (Expression) getProperty(new WQPath(line.value)));
			case LE_REF:
				return criteriaBuilder.le(property, (Expression) getProperty(new WQPath(line.value)));
			case LT_REF:
				return criteriaBuilder.lt(property, (Expression) getProperty(new WQPath(line.value)));
			default:
				throw new IllegalArgumentException("Unknown or unsupported function:" + line.function);
		}
	}


	private ParameterExpression parse(final Expression property, final String value)
	{
		return param(parseValue(property, value));
	}


	private List<?> parseValueList(final Expression property, final List<String> values)
	{
		if (property.getJavaType() == String.class)
		{
			return values;
		}
		else
		{
			final List<Object> out = new ArrayList<>(values.size());
			for (String value : values)
				out.add(parseValue(property, value));

			return out;
		}
	}

	private Object parseValue(final Expression property, final String value)
	{
		final Class<?> clazz = property.getJavaType();

		if (clazz == Enum.class)
		{
			// Hibernate has unhelpfully given us the base Enum type. Need to translate this to an actual Java enum class
			// To do this we need to peek inside the Expression
			if (property instanceof SqmPath<?> p && p.getModel() instanceof Attribute a)
			{
				final Member member = a.getJavaMember();

				if (member instanceof Method m)
				{
					return WQTypeHelper.parse(TypeToken.of(p.getLhs().getJavaType()).resolveType(m.getGenericReturnType()).getRawType(), value);
				}
				else if (member instanceof Field f)
				{
					return WQTypeHelper.parse(TypeToken.of(p.getLhs().getJavaType()).resolveType(f.getGenericType()).getRawType(), value);
				}
				else
				{
					throw new IllegalArgumentException("Unsupported member type for enum: " + member.getClass());
				}
			}
			else
			{
				throw new IllegalArgumentException("Unknown enum property class: " + property.getClass());
			}
		}
		else
		{
			return WQTypeHelper.parse(clazz, value);
		}
	}


	private Predicate parseConstraint(final WQGroup group)
	{
		if (shouldBeInCriteria(group)) {
			// We can optimise this group into an IN criteria
			final String field = ((WQConstraint) group.constraints.get(0)).field;

			final Expression property = getProperty(field);

			final List<Object> ids = group.constraints
					                         .stream()
					                         .map(l -> parseValue(property, ((WQConstraint) l).value))
					                         .toList();

			final Predicate expr = property.in(param(ids));

			if (group.operator == WQGroupType.OR)
				return expr;
			else if (group.operator == WQGroupType.NONE)
				return expr.not();
			else
				throw new IllegalArgumentException("Unable to convert to IN criteria for group operator: " + group.operator);
		}
		else {
			List<Predicate> contents = parseConstraint(group.constraints);

			if (contents.size() == 1)
			{
				// Generate simpler criteria if the group has only one member
				if (group.operator == WQGroupType.NONE)
					return contents.get(0).not();
				else
					return contents.get(0);
			}
			else
			{
				switch (group.operator)
				{
					case NONE:
						contents.replaceAll(Predicate :: not);
						return criteriaBuilder.and(contents.toArray(new Predicate[0]));
					case AND:
						return criteriaBuilder.and(contents.toArray(new Predicate[0]));
					case OR:
						return criteriaBuilder.or(contents.toArray(new Predicate[0]));
					default:
						throw new IllegalArgumentException("Unknown group operator: " + group.operator);
				}
			}
		}
	}


	private <T> ParameterExpression<T> param(final T value)
	{
		final Class<T> clazz = (Class<T>) value.getClass();

		final ParameterExpression<T> param = criteriaBuilder.parameter(clazz);

		params.put(param, value);

		return param;
	}


	private boolean shouldBeInCriteria(final WQGroup group)
	{
		if ((group.operator == WQGroupType.OR || group.operator == WQGroupType.NONE) && group.constraints.size() > 1)
		{
			String property = null;

			for (WQConstraintLine line : group.constraints)
			{
				if (line instanceof WQConstraint)
				{
					WQConstraint constraint = (WQConstraint) line;

					if (constraint.function != WQFunctionType.EQ)
						return false; // function must be EQ
					else if (property == null)
						property = constraint.field; // record to check all other properties have the same name
					else if (!StringUtils.equals(property, constraint.field))
						return false; // property name varies
				}
				else
				{
					return false; // must only have simple constraints
				}
			}

			// No mismatches
			return true;
		}
		else
		{
			return false;
		}
	}


	public void forWebQuery(final WebQuery query)
	{
		this.params.clear();
		this.generated = criteriaBuilder.createQuery();
		this.generated.distinct(false);

		// Add FROM (N.B. adding support for subclass queries)
		addFrom(query.constraints.subclass);

		addConstraints(query.constraints.constraints);

		offset(query.getOffset());
		limit(query.getLimit());

		addOrders(query.orderings);

		addExpandAndFetches(query);
	}


	private void addExpandAndFetches(final WebQuery query)
	{
		// Clear all fetches and graphs first
		this.fetches = null;

		if (StringUtils.equals(query.fetch, "id"))
		{
			// Only wants the ID, so set up a fetch graph with nothing else in it
			this.fetches = new HashSet<>();
		}
		else
		{
			final Set<String> queryFetch = query.getDBFetch();
			final Set<String> queryExpand = query.getExpand();

			// If dbfetch is specified then use that first
			if (queryFetch != null)
			{
				this.fetches = queryFetch;

				// Allow a special value of "none" to be used
				this.fetches.remove("none");
			}
			// Fall back on expand
			else if (queryExpand != null)
			{
				this.fetches = queryExpand;

				// Allow a special values of "-idcollections" and "-collections" to be used (ignored here currently, but usable in serialisers)
				this.fetches.remove("none"); // Allow a special value of "none" to be used
				this.fetches.removeIf(v -> v.charAt(0) == '-');

				// Treat expand=all (coming from older API clients) as if expand was not specified
				if (this.fetches.size() == 1 && this.fetches.contains("all"))
					this.fetches = null;
			}


			// Fall back on entity default if a fetch/expand is unspecified, or if it is set to _default
			if (this.fetches == null)
			{
				this.fetches = entity.getEagerFetch();
			}
			else if (this.fetches.contains("_default"))
			{
				this.fetches.remove("_default");
				this.fetches.addAll(entity.getEagerFetch());
			}
		}
	}


	public void forIDs(final WebQuery original, final List<?> ids)
	{
		this.params.clear();
		this.generated = criteriaBuilder.createQuery();
		this.generated.distinct(false);

		if (original != null)
		{
			addFrom(original.constraints.subclass);

			// Re-state the order (so intra-page order is still correct, since otherwise it'll be whatever order the database decides to return)
			addOrders(original.orderings);

			// Make sure we eagerly fetch what's requested
			addExpandAndFetches(original);

			// Don't set an offset or limit when selecting specific IDs
			this.offset = null;
			this.limit = null;
		}
		else
		{
			addFrom(null);
		}

		if (root.getModel().hasSingleIdAttribute())
		{
			final Class idClass = root.getModel().getIdType().getJavaType();

			final Path id = root.get(root.getModel().getId(idClass));

			generated.where(id.in(param(ids)));
		}
		else
		{
			throw new NotImplementedException("Cannot handle query by IDs with IdClass!");
		}
	}


	public Long selectCount()
	{
		Query<Long> query = createSelectCount();

		return query.getSingleResult();
	}


	public List<ID> selectIDs()
	{
		Query<ID> query = createSelectIDs();

		List<?> results = query.getResultList();

		if (results.isEmpty())
			return Collections.emptyList();
		else if (results.get(0).getClass().isArray())
			// N.B. we explicitly do not remove duplicates because the only case of duplicates appearing should be if ordering by a collection
			return (List<ID>) results.stream().map(r -> Array.get(r, 0)).toList();
		else
			return (List<ID>) results;
	}


	public List<Object[]> selectCustomProjection(final boolean distinct, String... fields)
	{
		final Query<Object[]> query = createSelectCustomProjection(distinct, fields);
		final List<Object[]> results = query.getResultList();

		if (results.isEmpty())
			return Collections.emptyList();
		else
			return results;
	}


	public List<T> selectEntity()
	{
		final Query<T> query = createSelectEntity();

		return query.getResultList();
	}


	public Query<Long> createSelectCount()
	{
		final Query<Long> query;
		{
			final CriteriaQuery<Long> cq = generated;
			cq.orderBy(List.of()); // Order is not meaningful for a count query
			cq.select(criteriaBuilder.count(root));

			query = session.createQuery(cq);
		}

		// Set all the parameters
		for (Map.Entry<ParameterExpression, Object> entry : this.params.entrySet())
		{
			query.setParameter(entry.getKey(), entry.getValue());
		}

		return query;
	}


	public <C> List<C> selectCustom(JPAQueryCustomiser customiser)
	{
		final Query<C> query = createSelectCustom(customiser);

		return query.getResultList();
	}


	public <C> Query<C> createSelectCustom(JPAQueryCustomiser customiser)
	{
		final Query<C> query;
		{
			final CriteriaQuery<C> cq = generated;

			customiser.apply(criteriaBuilder, cq, root, this);

			query = session.createQuery(cq);
		}

		if (offset != null)
			query.setFirstResult(offset);
		if (limit != null)
			query.setMaxResults(limit);

		// Set all the parameters
		for (Map.Entry<ParameterExpression, Object> entry : this.params.entrySet())
		{
			query.setParameter(entry.getKey(), entry.getValue());
		}

		return query;
	}


	public Query<Object[]> createSelectCustomProjection(final boolean distinct, String[] fields)
	{
		final Query<Object[]> query;
		{
			final CriteriaQuery<Object[]> cq = generated;

			cq.orderBy(orders.stream().map(OrderExpr :: order).toList()); // Make sure we return the results in the correct order

			List<Selection<?>> selects = new ArrayList<>(fields.length + orders.size());

			for (String field : fields)
				selects.add(getProperty(field));

			if (distinct)
			{
				cq.distinct(true);

				if (!orders.isEmpty())
				{
					final Set<String> fieldSet = new HashSet<>(Arrays.asList(fields));

					// Make sure to select any ORDER BY field that isn't already included in the SELECT
					for (var order : orders)
					{
						if (!fieldSet.contains(order.src().field))
							selects.add(order.order().getExpression());
					}
				}
			}

			cq.multiselect(selects);

			query = session.createQuery(cq);
		}

		if (offset != null)
			query.setFirstResult(offset);
		if (limit != null)
			query.setMaxResults(limit);

		// Set all the parameters
		for (Map.Entry<ParameterExpression, Object> entry : this.params.entrySet())
		{
			query.setParameter(entry.getKey(), entry.getValue());
		}

		return query;
	}


	public Query<ID> createSelectIDs()
	{
		final Query<ID> query;
		{
			final CriteriaQuery<ID> cq = generated;
			cq.orderBy(orders.stream().map(OrderExpr :: order).toList()); // Make sure we return the results in the correct order

			if (root.getModel().hasSingleIdAttribute())
			{
				final var idAttr = root.getModel().getId(root.getModel().getIdType().getJavaType());
				cq.select(root.get(idAttr));
			}
			else
			{
				throw new NotImplementedException("Cannot handle ID selection with IdClass!");
			}

			query = session.createQuery(cq);
		}

		if (offset != null)
			query.setFirstResult(offset);
		if (limit != null)
			query.setMaxResults(limit);

		// Set all the parameters
		for (Map.Entry<ParameterExpression, Object> entry : this.params.entrySet())
		{
			query.setParameter(entry.getKey(), entry.getValue());
		}

		return query;
	}


	public Query<T> createSelectEntity()
	{
		final Query<T> query;
		{
			generated.select(root);

			applyFetches();

			generated.orderBy(orders.stream().map(OrderExpr :: order).toList());

			query = session.createQuery(generated);
		}

		if (offset != null)
			query.setFirstResult(offset);
		if (limit != null)
			query.setMaxResults(limit);

		// Hibernate 6+ removes duplicate entity results introduced by fetch joins.

		// Set all the parameters
		for (Map.Entry<ParameterExpression, Object> entry : this.params.entrySet())
		{
			query.setParameter(entry.getKey(), entry.getValue());
		}

		return query;
	}


	@Override
	public void applyFetches()
	{
		if (fetches != null)
			addFetches(fetches);
	}


	/**
	 * Returns true if one of the non-fetch joins specified will result in a collection being pulled back
	 *
	 * @return
	 */
	public boolean hasCollectionJoin()
	{
		for (JPAJoin join : joins.values())
		{
			if (join.isCollection())
				return true;
		}

		return false;
	}


	/**
	 * Returns true if one of the fetches specified will result in a collection being pulled back
	 **/
	public boolean hasCollectionFetch()
	{
		if (fetches != null)
			for (String fetch : fetches)
			{
				QEntity parent = entity;

				final String[] parts = StringUtils.split(fetch, '.');

				for (int i = 0; i < parts.length; i++)
				{
					// If this is a fully supported relation then continue checking
					if (parent.hasRelation(parts[i]))
					{
						final QRelation relation = parent.getRelation(parts[i]);

						parent = relation.getEntity();

						if (relation.isCollection())
						{
							if (log.isTraceEnabled())
								log.trace("Encountered fetch {}. This resolves to {} which is a collection", fetch, relation);

							return true;
						}
					}
					// This covers partially-supported things like Map and other basic collections that don't have a QRelation description
					else if (parent.hasNonEntityRelation(parts[i]))
					{
						if (parent.isNonEntityRelationCollection(parts[i]))
							return true;
					}
					else
					{
						log.warn(
								"Encountered relation {} on {} as part of path {}. Assuming QEntity simply does not know this relation. Assuming worst case scenario (collection join is involved)",
								parts[i],
								parent.getName(),
								fetch);
						return true;
					}
				}
			}

		return false;
	}


	private void addFetches(Collection<String> fetches)
	{
		Map<String, Fetch> created = new HashMap<>();

		for (String fetch : fetches)
		{
			Fetch parent = null;

			final String[] parts = StringUtils.split(fetch, '.');

			for (int i = 0; i < parts.length; i++)
			{
				final String path = StringUtils.join(parts, '.', 0, i + 1);

				Fetch existing = created.get(path);

				if (existing == null)
				{
					if (parent == null)
					{
						// attribute of root
						existing = root.fetch(parts[i], JoinType.LEFT);
					}
					else
					{
						// attribute of parent
						existing = parent.fetch(parts[i], JoinType.LEFT);
					}

					created.put(path, existing);
				}

				parent = existing;
			}
		}
	}


	public void offset(int offset)
	{
		if (offset <= 0)
			this.offset = null;
		else
			this.offset = offset;
	}


	public void limit(int limit)
	{
		if (limit <= 0)
			this.limit = null;
		else
			this.limit = limit;
	}


	private void addOrders(final List<WQOrder> orders)
	{
		for (WQOrder ordering : orders)
		{
			final Expression<?> property = getProperty(new WQPath(ordering.field));

			if (ordering.isAsc())
				this.orders.add(new OrderExpr(criteriaBuilder.asc(property), ordering));
			else
				this.orders.add(new OrderExpr(criteriaBuilder.desc(property), ordering));
		}

		generated.orderBy(this.orders.stream().map(OrderExpr :: order).toList());
	}
}
