package com.peterphi.std.guice.hibernate.webquery.impl.jpa;

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
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.metamodel.ManagedType;
import org.apache.commons.lang.StringUtils;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.hibernate.query.sqm.tree.SqmCopyContext;
import org.hibernate.query.sqm.tree.SqmNode;
import org.hibernate.query.sqm.tree.from.SqmFrom;
import org.hibernate.query.sqm.tree.from.SqmAttributeJoin;
import org.hibernate.query.sqm.tree.select.SqmSortSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Builds entity queries with one identifier per matched entity before pagination.
 * Collection sort keys use their minimum matching value for ascending order and
 * maximum for descending order; identifier ties make page boundaries stable.
 * Explicit projections retain their requested row and DISTINCT semantics.
 */
public class JPAQueryBuilder<T, ID> implements JPAQueryBuilderInternal
{
	private static final Logger log = LoggerFactory.getLogger(JPAQueryBuilder.class);
	private static final AtomicLong SNAPSHOT_ALIASES = new AtomicLong();

	private record OrderExpr(Order order, WQOrder src, boolean collection) {}

	private final Session session;
	private final CriteriaBuilder criteriaBuilder;

	private final QEntity initialEntity;
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
	private final Map<Expression<?>, Class<?>> propertyTypes = new IdentityHashMap<>();

	private boolean permitSchemaPrivate = false;
	private boolean terminalSnapshot;

	public JPAQueryBuilder(final Session session, final QEntity entity, final boolean defaultPermitSchemaPrivate)
	{
		this.session = session;
		this.criteriaBuilder = session.getCriteriaBuilder();
		this.initialEntity = entity;
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

		final String name = path.getHead().getPath();
		final Expression<?> expression = join.property(name, permitSchemaPrivate);
		propertyTypes.put(expression, join.getPropertyType(name, permitSchemaPrivate));
		return expression;
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

			final JPAJoin join = parent.join(path.getHead().getPath(), permitSchemaPrivate);

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
				return criteriaBuilder.not(property.in(parseValueList(property, line.valuelist)));
			case IN:
				return property.in(parseValueList(property, line.valuelist));
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
		return WQTypeHelper.parse(propertyTypes.getOrDefault(property, property.getJavaType()), value);
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

			final Predicate expr = property.in(ids);

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


	/** Initialise a new query, discarding all state from any previous query on this builder. */
	public void forWebQuery(final WebQuery query)
	{
		reset();

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
		this.fetches = new HashSet<>();
		if (StringUtils.equals(query.fetch, "id"))
			return;

		Set<String> explicit = query.getDBFetch();
		final boolean expand = explicit == null;
		if (expand)
			explicit = query.getExpand();

		if (explicit == null || (expand && explicit.size() == 1 && explicit.contains("all")))
		{
			addDefaultFetches();
			return;
		}

		for (String path : explicit)
		{
			if ("none".equals(path) || "_default".equals(path) || (expand && path.startsWith("-")))
				continue;
			fetches.add(entity.resolveFetchPath(path, permitSchemaPrivate));
		}
		if (explicit.contains("_default"))
			addDefaultFetches();
	}


	private void addDefaultFetches()
	{
		// Internal eager mappings apply even to schema-private relations. User-named
		// fetch paths above still require the caller's normal property privileges.
		for (String path : entity.getEagerFetch())
			fetches.add(entity.resolveFetchPath(path, true));
	}

	private void reset()
	{
		entity = initialEntity;
		conditions.clear();
		orders.clear();
		joins.clear();
		params.clear();
		propertyTypes.clear();
		fetches = null;
		offset = null;
		limit = null;
		generated = criteriaBuilder.createQuery();
		generated.distinct(false);
	}


	/**
	 * Terminal operations work on independent criteria trees. Native Hibernate does
	 * not copy criteria by default, so sharing a tree also mutates previously created
	 * Query objects, not just later builder results.
	 */
	private JPAQueryBuilder<T, ID> snapshot()
	{
		final SqmCopyContext context = SqmCopyContext.noParamCopyContext();
		final JPAQueryBuilder<T, ID> copy = new JPAQueryBuilder<>(session, initialEntity, permitSchemaPrivate);
		copy.entity = entity;
		copy.generated = copyNode(generated, context);
		copy.root = copyNode(root, context);
		for (Predicate condition : conditions)
			copy.conditions.add(copyNode(condition, context));
		for (OrderExpr order : orders)
			copy.orders.add(new OrderExpr(((SqmSortSpecification) order.order()).copy(context), order.src().clone(), order.collection()));
		joins.forEach((name, join) -> copy.joins.put(name, join.withRoot(copyNode((From<?, ?>) join.getJoinExpression(), context))));
		params.forEach((parameter, value) -> copy.params.put(copyNode(parameter, context), value));
		propertyTypes.forEach((expression, type) -> copy.propertyTypes.put(copyNode(expression, context), type));
		copy.fetches = fetches == null ? null : new HashSet<>(fetches);
		copy.offset = offset;
		copy.limit = limit;
		copy.terminalSnapshot = true;
		copy.isolateJoinAliases();
		return copy;
	}


	private void isolateJoinAliases()
	{
		final List<SqmFrom<?, ?>> froms = new ArrayList<>();
		final Set<SqmFrom<?, ?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Object queryRoot : generated.getRoots())
			collectFroms((SqmFrom<?, ?>) queryRoot, froms, seen);

		// Hibernate copies existing joins but not From's alias counter. New joins
		// would otherwise reuse copied aliases/NavigablePaths and can turn a full
		// collection fetch into its filtered join. A disjoint parent namespace also
		// covers direct joins/fetches added by custom Criteria callbacks.
		String prefix;
		do
		{
			prefix = "var_wq_copy_" + SNAPSHOT_ALIASES.incrementAndGet() + "_";
		}
		while (containsAliasPrefix(froms, prefix));
		for (int i = 0; i < froms.size(); i++)
			froms.get(i).setExplicitAlias(prefix + i);
	}


	private static boolean containsAliasPrefix(List<SqmFrom<?, ?>> froms, String prefix)
	{
		return froms.stream().anyMatch(from -> (from.getExplicitAlias() != null && from.getExplicitAlias().startsWith(prefix)) ||
		                                      from.getNavigablePath().getFullPath().contains(prefix));
	}


	private static void collectFroms(SqmFrom<?, ?> from, List<SqmFrom<?, ?>> result, Set<SqmFrom<?, ?>> seen)
	{
		if (!seen.add(from))
			return;
		result.add(from);
		for (var join : from.getSqmJoins())
			collectFroms(join, result, seen);
		for (var treated : from.getSqmTreats())
			collectFroms(treated, result, seen);
	}


	@SuppressWarnings("unchecked")
	private static <N> N copyNode(N node, SqmCopyContext context)
	{
		return (N) ((SqmNode) node).copy(context);
	}


	/**
	 * Build an unpaginated fetch for an already selected ID page. The executor restores
	 * the first query's ID order after fetching, so collection joins cannot reorder it.
	 */
	public void forIDs(final WebQuery original, final List<?> ids)
	{
		reset();
		addFrom(original == null ? null : original.constraints.subclass);
		if (original != null)
			addExpandAndFetches(original);

		if (root.getModel().hasSingleIdAttribute())
		{
			final Class idClass = root.getModel().getIdType().getJavaType();

			final Path id = root.get(root.getModel().getId(idClass));

			addConstraints(id.in(ids));
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
		return createSelectIDs().getResultList();
	}


	/** Internal entity pagination does not expose identifiers as query results. */
	List<ID> selectIDsForEntityPage()
	{
		return snapshot().buildSelectIDs(false).getResultList();
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
		if (hasCollectionOrder() || ((offset != null || limit != null) && (hasCollectionJoin() || hasCollectionFetch())))
		{
			final List<ID> ids = selectIDsForEntityPage();
			if (ids.isEmpty())
				return Collections.emptyList();
			final JPAQueryBuilder<T, ID> entityBuilder = new JPAQueryBuilder<>(session, entity, permitSchemaPrivate);
			entityBuilder.forIDs(null, ids);
			entityBuilder.fetches = fetches == null ? null : new HashSet<>(fetches);
			// Native TREAT paths retain their typed attributes. Initialise them only
			// after configured parent collections have finished hydrating.
			final JPAFetchPlan nativeFetches = new JPAFetchPlan(root.getModel(), null, directFetchPaths());
			final Map<Object, T> byId = new HashMap<>();
			final var identifiers = session.getSessionFactory().getPersistenceUnitUtil();
			for (T value : entityBuilder.selectEntity())
			{
				nativeFetches.initialise(value);
				byId.put(identifiers.getIdentifier(value), value);
			}
			return ids.stream().filter(byId::containsKey).map(byId::get).toList();
		}
		final Query<T> query = createSelectEntity();

		return query.getResultList();
	}


	public Query<Long> createSelectCount()
	{
		return snapshot().buildSelectCount();
	}


	private Query<Long> buildSelectCount()
	{
		clearEntityFetches();
		final Query<Long> query;
		{
			final CriteriaQuery<Long> cq = generated;
			cq.orderBy(List.of()); // Order is not meaningful for a count query
			cq.groupBy(List.of());
			cq.having();
			cq.distinct(false);
			cq.select(criteriaBuilder.countDistinct(root));

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
		return snapshot().buildSelectCustom(customiser);
	}


	private <C> Query<C> buildSelectCustom(JPAQueryCustomiser customiser)
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
		return snapshot().buildSelectCustomProjection(distinct, fields);
	}


	private Query<Object[]> buildSelectCustomProjection(final boolean distinct, String[] fields)
	{
		clearEntityFetches();
		final Query<Object[]> query;
		{
			final CriteriaQuery<Object[]> cq = generated;

			cq.orderBy(orders.stream().map(OrderExpr :: order).toList()); // Make sure we return the results in the correct order

			List<Selection<?>> selects = new ArrayList<>(fields.length + orders.size());

			for (String field : fields)
				selects.add(getProperty(field));

			cq.distinct(distinct);
			if (distinct)
			{

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
		return snapshot().buildSelectIDs(true);
	}


	private Query<ID> buildSelectIDs(boolean exposeIdentifier)
	{
		clearEntityFetches();
		if (exposeIdentifier)
			entity.assertIdentifierAccessible(permitSchemaPrivate);
		if (!root.getModel().hasSingleIdAttribute())
			throw new NotImplementedException("Cannot handle ID selection with IdClass!");

		final CriteriaQuery<ID> cq = generated;
		final var idAttribute = root.getModel().getId(root.getModel().getIdType().getJavaType());
		final Expression<ID> id = root.get(idAttribute);
		cq.select(id);

		final List<Order> idOrders = new ArrayList<>(orders.size() + 1);
		final List<Expression<?>> groups = new ArrayList<>();
		groups.add(id);
		for (OrderExpr order : orders)
		{
			Expression<?> expression = order.order().getExpression();
			if (order.collection())
			{
				// One entity may have many matching child values. Ascending sorts use
				// the minimum and descending sorts the maximum, before applying limits.
				expression = aggregateOrder(expression, order.src());
			}
			else if (!order.src().field.endsWith(":size") && !groups.contains(expression))
			{
				// Collection sizes are correlated subqueries, not legal GROUP BY expressions.
				groups.add(expression);
			}
			idOrders.add(order.src().isAsc() ? criteriaBuilder.asc(expression) : criteriaBuilder.desc(expression));
		}
		cq.distinct(false);
		cq.groupBy(List.of());
		cq.having();
		final boolean projectOrderingColumns = hasCollectionJoin() && !hasCollectionOrder();
		if (hasCollectionOrder())
			cq.groupBy(groups);
		else if (projectOrderingColumns)
		{
			// Scalar sort values are fixed for each entity. Selecting them alongside
			// its ID keeps DISTINCT portable to databases requiring ORDER BY in SELECT.
			final List<Selection<?>> selections = new ArrayList<>();
			selections.add(id);
			for (OrderExpr order : orders)
				if (!selections.contains(order.order().getExpression()))
					selections.add(order.order().getExpression());
			if (selections.size() == 1)
			{
				// HSQLDB 2.7.4 can apply OFFSET before single-column DISTINCT.
				// A non-empty aggregate group preserves entity page boundaries.
				cq.groupBy(id);
				cq.having(criteriaBuilder.gt(criteriaBuilder.count(root), 0L));
			}
			else
			{
				cq.multiselect(selections);
				cq.distinct(true);
			}
		}

		// Stable page boundaries also need a tie-breaker when requested sort values match.
		if (orders.stream().noneMatch(order -> "id".equals(order.src().field)))
			idOrders.add(criteriaBuilder.asc(id));
		cq.orderBy(idOrders);

		final Query<ID> query = projectOrderingColumns
				? session.createQuery(cq).setTupleTransformer((tuple, aliases) -> (ID) tuple[0])
				: session.createQuery(cq);
		if (offset != null)
			query.setFirstResult(offset);
		if (limit != null)
			query.setMaxResults(limit);
		for (Map.Entry<ParameterExpression, Object> entry : params.entrySet())
			query.setParameter(entry.getKey(), entry.getValue());
		return query;
	}


	@SuppressWarnings({"rawtypes", "unchecked"})
	private Expression<?> aggregateOrder(Expression<?> expression, WQOrder order)
	{
		final Class<?> type = expression.getJavaType();
		if (type == boolean.class || type == Boolean.class)
		{
			final Expression<Integer> encoded = criteriaBuilder.<Integer>selectCase()
					.when(criteriaBuilder.isTrue((Expression<Boolean>) expression), 1)
					.when(criteriaBuilder.isFalse((Expression<Boolean>) expression), 0)
					.otherwise((Integer) null);
			return order.isAsc() ? criteriaBuilder.min(encoded) : criteriaBuilder.max(encoded);
		}
		return order.isAsc() ? criteriaBuilder.least((Expression) expression) : criteriaBuilder.greatest((Expression) expression);
	}

	/**
	 * Create a native Hibernate query. Its pagination uses native query semantics;
	 * use selectEntity() for entity pages when collection joins multiply SQL rows.
	 */
	public Query<T> createSelectEntity()
	{
		return snapshot().buildSelectEntity();
	}


	private Query<T> buildSelectEntity()
	{
		final JPAFetchPlan fetchPlan = new JPAFetchPlan(root.getModel(), fetches, directFetchPaths());
		final boolean separateFetches = fetchPlan.requiresSeparateLoading() || (hasCollectionJoin(false) && hasListOrCollectionFetch());
		final Query<T> query;
		{
			generated.select(root);

			if (separateFetches)
				clearEntityFetches();
			else
				applyFetches();

			final List<Order> entityOrders = new ArrayList<>(orders.stream().map(OrderExpr::order).toList());
			if ((offset != null || limit != null) && orders.stream().noneMatch(order -> "id".equals(order.src().field)))
			{
				final var idAttribute = root.getModel().getId(root.getModel().getIdType().getJavaType());
				entityOrders.add(criteriaBuilder.asc(root.get(idAttribute)));
			}
			generated.orderBy(entityOrders);

			query = session.createQuery(generated);
		}

		if (offset != null)
			query.setFirstResult(offset);
		if (limit != null)
			query.setMaxResults(limit);

		// Hibernate 6+ removes duplicate entity results introduced by fetch joins.
		if (separateFetches)
			query.setTupleTransformer((values, aliases) -> {
				final T value = (T) values[0];
				fetchPlan.initialise(value);
				return value;
			});

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
		// Public calls on the reusable builder defer materialisation to a terminal
		// snapshot. Custom callbacks already receive that snapshot and apply now.
		if (terminalSnapshot && fetches != null)
			addFetches(fetches);
	}


	/** Whether an ordering crosses a collection and therefore needs one value per root entity. */
	public boolean hasCollectionOrder()
	{
		return orders.stream().anyMatch(OrderExpr::collection);
	}


	/**
	 * Returns true if one of the non-fetch joins specified will result in a collection being pulled back
	 *
	 * @return
	 */
	public boolean hasCollectionJoin()
	{
		return hasCollectionJoin(true);
	}


	private boolean hasCollectionJoin(boolean includeFetches)
	{
		final List<SqmFrom<?, ?>> froms = new ArrayList<>();
		collectFroms((SqmFrom<?, ?>) root, froms, Collections.newSetFromMap(new IdentityHashMap<>()));
		return froms.stream().anyMatch(from -> from instanceof SqmAttributeJoin<?, ?> join &&
		                                    join.getAttribute().isCollection() && (includeFetches || !join.isFetched()));
	}


	/** Keep explicit join predicates usable when the terminal result is not an entity. */
	private void clearEntityFetches()
	{
		final List<SqmFrom<?, ?>> froms = new ArrayList<>();
		collectFroms((SqmFrom<?, ?>) root, froms, Collections.newSetFromMap(new IdentityHashMap<>()));
		for (SqmFrom<?, ?> from : froms)
			if (from instanceof SqmAttributeJoin<?, ?> join)
				join.clearFetched();
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


	/**
	 * Lists and plain Collections may be bags, whose fetched rows are not deduplicated
	 * by Hibernate. Isolate their fetch from matching collection joins even without a
	 * limit. Indexed lists are conservatively handled the same way.
	 */
	public boolean hasListOrCollectionFetch()
	{
		return new JPAFetchPlan(root.getModel(), fetches, directFetchPaths()).hasListOrCollectionFetch();
	}


	private void addFetches(Collection<String> fetches)
	{
		Map<String, Fetch> created = directFetches();

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


	private List<JPAFetchPlan.Path> directFetchPaths()
	{
		final List<JPAFetchPlan.Path> result = new ArrayList<>();
		collectDirectFetchPaths((SqmFrom<?, ?>) root, List.of(), result,
		                       Collections.newSetFromMap(new IdentityHashMap<>()));
		return result;
	}


	private void collectDirectFetchPaths(SqmFrom<?, ?> from, List<JPAFetchPlan.Step> prefix,
	                                    List<JPAFetchPlan.Path> result, Set<SqmFrom<?, ?>> seen)
	{
		if (!seen.add(from))
			return;
		for (var join : from.getSqmJoins())
			if (join instanceof SqmAttributeJoin<?, ?> attributeJoin)
			{
				final ManagedType<?> ownerType = session.getSessionFactory().getMetamodel().managedType(from.getJavaType());
				final List<JPAFetchPlan.Step> path = new ArrayList<>(prefix);
				path.add(new JPAFetchPlan.Step(ownerType, attributeJoin.getAttribute()));
				if (attributeJoin.isFetched())
					result.add(new JPAFetchPlan.Path(List.copyOf(path)));
				collectDirectFetchPaths(attributeJoin, path, result, seen);
			}
		for (var treated : from.getSqmTreats())
			collectDirectFetchPaths(treated, prefix, result, seen);
	}


	private Map<String, Fetch> directFetches()
	{
		final Map<String, Fetch> result = new HashMap<>();
		collectDirectFetches((SqmFrom<?, ?>) root, "", result, Collections.newSetFromMap(new IdentityHashMap<>()));
		return result;
	}


	private static void collectDirectFetches(SqmFrom<?, ?> from, String prefix, Map<String, Fetch> result, Set<SqmFrom<?, ?>> seen)
	{
		if (!seen.add(from))
			return;
		for (var join : from.getSqmJoins())
			if (join instanceof SqmAttributeJoin<?, ?> attributeJoin)
			{
				final String path = prefix.isEmpty() ? attributeJoin.getAttribute().getName() :
				                    prefix + "." + attributeJoin.getAttribute().getName();
				if (attributeJoin.isFetched())
					result.put(path, attributeJoin);
				collectDirectFetches(attributeJoin, path, result, seen);
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
			final WQPath path = new WQPath(ordering.field);
			final JPAJoin join = getOrCreateJoin(path.getTail());
			final Expression<?> property = join.orderingProperty(path.getHead().getPath(), permitSchemaPrivate);
			boolean collection = false;
			for (WQPath parent = path.getTail(); parent != null; parent = parent.getTail())
				collection |= getOrCreateJoin(parent).isCollection();

			if (ordering.isAsc())
				this.orders.add(new OrderExpr(criteriaBuilder.asc(property), ordering, collection));
			else
				this.orders.add(new OrderExpr(criteriaBuilder.desc(property), ordering, collection));
		}

		generated.orderBy(this.orders.stream().map(OrderExpr :: order).toList());
	}
}
