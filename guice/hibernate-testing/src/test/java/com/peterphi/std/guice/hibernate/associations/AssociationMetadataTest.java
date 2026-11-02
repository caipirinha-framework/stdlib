package com.peterphi.std.guice.hibernate.associations;

import com.peterphi.std.guice.database.annotation.WebQueryPrivate;
import com.peterphi.std.guice.hibernate.webquery.impl.QEntityFactory;
import com.peterphi.std.guice.hibernate.webquery.impl.exception.InvalidPropertyException;
import com.peterphi.std.guice.hibernate.webquery.impl.exception.PrivatePropertyUseRejected;
import com.peterphi.std.guice.hibernate.webquery.impl.jpa.JPAQueryBuilder;
import com.peterphi.std.guice.restclient.jaxb.webquery.WebQuery;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class AssociationMetadataTest
{
	@Parameterized.Parameters(name = "{0}")
	public static List<String> databases()
	{
		return List.of("HSQLDB", "PostgreSQL");
	}

	private final String database;
	private EmbeddedPostgres postgres;
	private final List<String> statements = new ArrayList<>();

	public AssociationMetadataTest(String database)
	{
		this.database = database;
	}

	@Before
	public void startDatabase() throws Exception
	{
		if (database.equals("PostgreSQL"))
			postgres = EmbeddedPostgres.builder().start();
	}

	@After
	public void stopDatabase() throws Exception
	{
		if (postgres != null)
			postgres.close();
	}

	private SessionFactory factory(Class<?>... entities)
	{
		Configuration config = new Configuration();
		if (postgres != null)
			config.getProperties().put("hibernate.connection.datasource", postgres.getPostgresDatabase());
		else
		{
			config.setProperty("hibernate.connection.driver_class", "org.hsqldb.jdbcDriver");
			config.setProperty("hibernate.connection.url", "jdbc:hsqldb:mem:" + UUID.randomUUID());
			config.setProperty("hibernate.connection.username", "sa");
		}
		config.setProperty("hibernate.hbm2ddl.auto", "create-drop");
		config.setStatementInspector(sql -> { statements.add(sql); return sql; });
		for (Class<?> entity : entities)
			config.addAnnotatedClass(entity);
		return config.buildSessionFactory();
	}

	@Test
	public void persistenceTargetsOverrideInterfaceDeclarationsAndColonPathsAvoidExtraJoins()
	{
		try (SessionFactory factory = factory(Target.class, Owner.class))
		{
			QEntityFactory schemas = new QEntityFactory(factory);
			var ownerSchema = schemas.get(Owner.class);
			assertEquals(Target.class, ownerSchema.getRelation("targets").getEntity().getEntityClass());
			assertEquals(Target.class, ownerSchema.getRelation("primary").getEntity().getEntityClass());
			assertEquals(Target.class, ownerSchema.getRelation("info:target").getEntity().getEntityClass());
			factory.inTransaction(session -> {
				Target target = new Target(); target.id = 42L; session.persist(target);
				Owner owner = new Owner(); owner.id = 1L; owner.primary = target;
				owner.info = new Info(); owner.info.target = target; owner.targets.add(target);
				session.persist(owner);
			});
			try (Session session = factory.openSession())
			{
				for (String path : List.of("primary:id", "info:target:id", "target:id"))
				{
					statements.clear();
					JPAQueryBuilder<Owner, Long> query = new JPAQueryBuilder<>(session, ownerSchema, false);
					query.forWebQuery(new WebQuery().eq(path, 42).orderAsc(path));
					assertEquals(path, List.of(1L), query.selectIDs());
					assertTrue("Foreign-key shortcut must not add joins: " + statements,
					           statements.stream().noneMatch(sql -> sql.toLowerCase().contains(" join ")));
				}
				JPAQueryBuilder<Owner, Long> invalid = new JPAQueryBuilder<>(session, ownerSchema, false);
				assertThrows(InvalidPropertyException.class, () -> invalid.forWebQuery(new WebQuery().eq("targets:id", 42)));
			}
		}
	}

	@Test
	public void privateAssociationInEmbeddedIdProtectsWholeIdentifiersAndShortcuts()
	{
		try (SessionFactory factory = factory(Target.class, RelationKeyOwner.class))
		{
			QEntityFactory schemas = new QEntityFactory(factory);
			var schema = schemas.get(RelationKeyOwner.class);
			assertThrows(PrivatePropertyUseRejected.class, () -> schema.assertIdentifierAccessible(false));
			assertThrows(PrivatePropertyUseRejected.class, () -> schema.getProperty("id", false));
			assertThrows(PrivatePropertyUseRejected.class, () -> schema.getProperty("id:target:id", false));
			assertTrue(schema.getProperty("id", true).isSchemaPrivate());
			factory.inTransaction(session -> {
				Target target = new Target(); target.id = 42L; session.persist(target);
				RelationKeyOwner owner = new RelationKeyOwner();
				owner.id = new RelationKey(); owner.id.target = target; owner.id.sequence = 3L;
				session.persist(owner);
			});
			try (Session session = factory.openSession())
			{
				JPAQueryBuilder<RelationKeyOwner, RelationKey> denied = new JPAQueryBuilder<>(session, schema, false);
				denied.forWebQuery(new WebQuery());
				assertThrows(PrivatePropertyUseRejected.class, denied::selectIDs);
				JPAQueryBuilder<RelationKeyOwner, RelationKey> permitted = new JPAQueryBuilder<>(session, schema, true);
				permitted.forWebQuery(new WebQuery().eq("id:target:id", 42));
				assertEquals(1, permitted.selectIDs().size());
			}
		}
	}

	@Test
	public void syntheticEntityOrderingChecksIdentifierPrivacyWithoutBlockingEntityProjections()
	{
		try (SessionFactory factory = factory(PrivateTarget.class, RelatedOwner.class))
		{
			QEntityFactory schemas = new QEntityFactory(factory);
			factory.inTransaction(session -> {
				PrivateTarget target = new PrivateTarget(); target.id = 42L; session.persist(target);
				RelatedOwner owner = new RelatedOwner(); owner.id = 1L; owner.target = target; session.persist(owner);
			});
			try (Session session = factory.openSession())
			{
				JPAQueryBuilder<PrivateTarget, Long> direct = new JPAQueryBuilder<>(session, schemas.get(PrivateTarget.class), false);
				assertThrows(PrivatePropertyUseRejected.class, () -> direct.forWebQuery(new WebQuery().orderAsc("entity")));
				JPAQueryBuilder<RelatedOwner, Long> related = new JPAQueryBuilder<>(session, schemas.get(RelatedOwner.class), false);
				assertThrows(PrivatePropertyUseRejected.class, () -> related.forWebQuery(new WebQuery().orderAsc("target.entity")));
				assertThrows(PrivatePropertyUseRejected.class, () -> related.forWebQuery(new WebQuery().orderAsc("target:id")));
				JPAQueryBuilder<PrivateTarget, Long> projection = new JPAQueryBuilder<>(session, schemas.get(PrivateTarget.class), false);
				projection.forWebQuery(new WebQuery());
				assertEquals(1, projection.selectCustomProjection(false, "entity").size());
				JPAQueryBuilder<RelatedOwner, Long> permitted = new JPAQueryBuilder<>(session, schemas.get(RelatedOwner.class), true);
				permitted.forWebQuery(new WebQuery().orderAsc("target.entity"));
				assertEquals(List.of(1L), permitted.selectIDs());
			}
		}
	}

	@Test
	public void targetClassKeepsConcreteSchemasAndGetterPrivacySeparate()
	{
		try (SessionFactory factory = factory(TargetClassOwner.class))
		{
			QEntityFactory schemas = new QEntityFactory(factory);
			var owner = schemas.get(TargetClassOwner.class);
			var first = owner.getRelation("first").getEntity();
			var second = owner.getRelation("second").getEntity();
			assertNotSame(first, second);
			assertTrue(first.hasProperty("first"));
			assertTrue(second.hasProperty("second"));
			var publicValues = owner.getRelation("publicValues").getEntity();
			var privateValues = owner.getRelation("privateValues").getEntity();
			assertNotSame(publicValues, privateValues);
			assertFalse(publicValues.getProperty("value", true).isSchemaPrivate());
			assertTrue(privateValues.getProperty("value", true).isSchemaPrivate());
			assertTrue(privateValues.encode().properties.isEmpty());
			factory.inTransaction(session -> {
				TargetClassOwner row = new TargetClassOwner(); row.id = 1L;
				PublicValue publicValue = new PublicValue(); publicValue.setValue("public"); row.publicValues.add(publicValue);
				PrivateValue privateValue = new PrivateValue(); privateValue.setValue("secret"); row.privateValues.add(privateValue);
				session.persist(row);
			});
			try (Session session = factory.openSession())
			{
				JPAQueryBuilder<TargetClassOwner, Long> allowed = new JPAQueryBuilder<>(session, owner, false);
				allowed.forWebQuery(new WebQuery().eq("publicValues.value", "public"));
				assertEquals(List.of(1L), allowed.selectIDs());
				JPAQueryBuilder<TargetClassOwner, Long> denied = new JPAQueryBuilder<>(session, owner, false);
				assertThrows(PrivatePropertyUseRejected.class, () -> denied.forWebQuery(new WebQuery().eq("privateValues.value", "secret")));
				denied.forWebQuery(new WebQuery());
				assertThrows(PrivatePropertyUseRejected.class, () -> denied.selectCustomProjection(false, "privateValues.value"));
			}
		}
	}

	@Test
	public void recursiveColonSizeMatchesDottedSizeOnNullableToOnePaths()
	{
		try (SessionFactory factory = factory(SizeTarget.class, SizeOwner.class))
		{
			QEntityFactory schemas = new QEntityFactory(factory);
			factory.inTransaction(session -> {
				SizeTarget full = new SizeTarget(); full.id = 42L; full.name = "full";
				SizeInfo child = new SizeInfo(); child.name = "child"; full.children.add(child); session.persist(full);
				SizeTarget empty = new SizeTarget(); empty.id = 43L; empty.name = "empty"; session.persist(empty);
				for (long id = 1; id <= 3; id++)
				{
					SizeOwner owner = new SizeOwner(); owner.id = id;
					owner.target = id == 1 ? full : id == 2 ? empty : null; session.persist(owner);
				}
			});
			try (Session session = factory.openSession())
			{
				var owner = schemas.get(SizeOwner.class);
				for (String path : List.of("target.children:size", "target:children:size"))
				{
					JPAQueryBuilder<SizeOwner, Long> query = new JPAQueryBuilder<>(session, owner, false);
					query.forWebQuery(new WebQuery().eq(path, 1).orderAsc("id"));
					assertEquals(path, List.of(1L), query.selectIDs());
					query.forWebQuery(new WebQuery().eq(path, 0).orderAsc("id"));
					assertEquals(path, List.of(2L, 3L), query.selectIDs());
				}
				for (String path : List.of("target.name", "target:name"))
				{
					JPAQueryBuilder<SizeOwner, Long> query = new JPAQueryBuilder<>(session, owner, false);
					query.forWebQuery(new WebQuery().isNull(path).orderAsc("id"));
					assertEquals(path, List.of(3L), query.selectIDs());
				}
				JPAQueryBuilder<SizeOwner, Long> denied = new JPAQueryBuilder<>(session, owner, false);
				assertThrows(PrivatePropertyUseRejected.class, () -> denied.forWebQuery(new WebQuery().eq("target:secret:size", 0)));
				assertThrows(PrivatePropertyUseRejected.class, () -> denied.forWebQuery(new WebQuery().eq("target.secret:size", 0)));
			}
		}
	}

	@Test
	public void nullableInverseIdentifiersUseLeftNavigationInsteadOfAssumingALocalForeignKey()
	{
		try (SessionFactory factory = factory(InverseParent.class, InverseChild.class))
		{
			QEntityFactory schemas = new QEntityFactory(factory);
			factory.inTransaction(session -> {
				InverseParent present = new InverseParent(); present.id = 1L; session.persist(present);
				InverseParent absent = new InverseParent(); absent.id = 2L; session.persist(absent);
				InverseChild child = new InverseChild(); child.id = 42L; child.parent = present; session.persist(child);
			});
			try (Session session = factory.openSession())
			{
				var parent = schemas.get(InverseParent.class);
				assertFalse(parent.getRelation("child").canUseIdentifierShortcut());
				for (String path : List.of("child.id", "child:id"))
				{
					JPAQueryBuilder<InverseParent, Long> query = new JPAQueryBuilder<>(session, parent, false);
					query.forWebQuery(new WebQuery().isNull(path));
					assertEquals(path, List.of(2L), query.selectIDs());
				}
			}
		}
	}

	@Test
	public void polymorphicQueriesRespectPrivateOverridesWithoutRestrictingPublicSiblings()
	{
		try (SessionFactory factory = factory(PrivacyBase.class, PrivacyChild.class, PrivacySibling.class, PrivacyHolder.class, Target.class))
		{
			QEntityFactory schemas = new QEntityFactory(factory);
			var base = schemas.get(PrivacyBase.class);
			var sibling = schemas.get(PrivacySibling.class);
			for (String path : List.of("secret", "info:value"))
			{
				assertThrows(path, PrivatePropertyUseRejected.class, () -> base.getProperty(path, false));
				assertFalse(sibling.getProperty(path, false).isSchemaPrivate());
			}
			for (String path : List.of("peer", "info:target"))
				assertThrows(path, PrivatePropertyUseRejected.class, () -> base.getRelation(path, false));
			for (String path : List.of("peer", "tags", "info.target", "info:value"))
				assertThrows(path, PrivatePropertyUseRejected.class, () -> base.resolveFetchPath(path, false));
			assertFalse(base.encode().properties.stream().anyMatch(property -> property.name.equals("secret") || property.name.equals("peer") || property.name.startsWith("info:")));
			assertTrue(sibling.encode().properties.stream().anyMatch(property -> property.name.equals("secret")));
			factory.inTransaction(session -> {
				Target target = new Target(); target.id = 42L; session.persist(target);
				PrivacyChild child = new PrivacyChild(); child.setId(1L); child.setSecret("classified");
				child.setPeer(target); child.setInfo(new PrivacyInfo()); child.getInfo().value = "hidden"; child.getInfo().target = target;
				child.getTags().add("hidden"); session.persist(child);
				PrivacySibling safe = new PrivacySibling(); safe.setId(2L); safe.setSecret("public"); session.persist(safe);
				PrivacyHolder holder = new PrivacyHolder(); holder.id = 3L; holder.target = child; session.persist(holder);
			});
			try (Session session = factory.openSession())
			{
				for (String path : List.of("secret", "info:value", "peer:id"))
				{
					JPAQueryBuilder<PrivacyBase, Long> query = new JPAQueryBuilder<>(session, base, false);
					assertThrows(path, PrivatePropertyUseRejected.class, () -> query.forWebQuery(new WebQuery().isNull(path)));
					assertThrows(path, PrivatePropertyUseRejected.class, () -> query.forWebQuery(new WebQuery().orderAsc(path)));
					query.forWebQuery(new WebQuery());
					assertThrows(path, PrivatePropertyUseRejected.class, () -> query.selectCustomProjection(false, path));
				}
				for (String path : List.of("target.secret", "target:secret", "target.peer:id", "target.info:value"))
				{
					JPAQueryBuilder<PrivacyHolder, Long> query = new JPAQueryBuilder<>(session, schemas.get(PrivacyHolder.class), false);
					assertThrows(path, PrivatePropertyUseRejected.class, () -> query.forWebQuery(new WebQuery().isNull(path)));
					query.forWebQuery(new WebQuery());
					assertThrows(path, PrivatePropertyUseRejected.class, () -> query.selectCustomProjection(false, path));
				}
				for (String path : List.of("peer", "tags", "info.target"))
				{
					JPAQueryBuilder<PrivacyBase, Long> query = new JPAQueryBuilder<>(session, base, false);
					assertThrows(path, PrivatePropertyUseRejected.class, () -> query.forWebQuery(new WebQuery().dbfetch(path)));
				}
				JPAQueryBuilder<PrivacyHolder, Long> holderFetch = new JPAQueryBuilder<>(session, schemas.get(PrivacyHolder.class), false);
				assertThrows(PrivatePropertyUseRejected.class, () -> holderFetch.forWebQuery(new WebQuery().dbfetch("target.peer")));
				JPAQueryBuilder<PrivacyBase, Long> privileged = new JPAQueryBuilder<>(session, base, true);
				privileged.forWebQuery(new WebQuery().eq("secret", "classified"));
				assertEquals(List.of(1L), privileged.selectIDs());
				assertEquals(1, privileged.selectCustomProjection(false, "secret").size());
				JPAQueryBuilder<PrivacySibling, Long> safe = new JPAQueryBuilder<>(session, sibling, false);
				safe.forWebQuery(new WebQuery().eq("secret", "public").orderAsc("secret"));
				assertEquals(List.of(2L), safe.selectIDs());
				assertEquals(1, safe.selectCustomProjection(false, "secret").size());
				JPAQueryBuilder<PrivacyBase, Long> narrowed = new JPAQueryBuilder<>(session, base, false);
				narrowed.forWebQuery(new WebQuery().subclass(PrivacySibling.class.getName()).eq("secret", "public"));
				assertEquals(List.of(2L), narrowed.selectIDs());
			}
		}
	}

	@Test
	public void polymorphicPrivateIdentifiersProtectWholeIdsAndSyntheticOrders()
	{
		try (SessionFactory factory = factory(PrivacyIdBase.class, PrivacyIdChild.class, PrivacyIdSibling.class))
		{
			QEntityFactory schemas = new QEntityFactory(factory);
			var base = schemas.get(PrivacyIdBase.class);
			assertThrows(PrivatePropertyUseRejected.class, () -> base.assertIdentifierAccessible(false));
			assertThrows(PrivatePropertyUseRejected.class, () -> base.getProperty("id", false));
			assertThrows(PrivatePropertyUseRejected.class, () -> base.getProperty("id:part", false));
			assertThrows(PrivatePropertyUseRejected.class, () -> base.resolveFetchPath("id:part", false));
			schemas.get(PrivacyIdSibling.class).assertIdentifierAccessible(false);
			factory.inTransaction(session -> {
				PrivacyIdChild child = new PrivacyIdChild(); child.setId(new PrivacyKey("hidden")); session.persist(child);
				PrivacyIdSibling sibling = new PrivacyIdSibling(); sibling.setId(new PrivacyKey("public")); session.persist(sibling);
			});
			try (Session session = factory.openSession())
			{
				JPAQueryBuilder<PrivacyIdBase, PrivacyKey> denied = new JPAQueryBuilder<>(session, base, false);
				denied.forWebQuery(new WebQuery());
				assertThrows(PrivatePropertyUseRejected.class, denied::selectIDs);
				assertThrows(PrivatePropertyUseRejected.class, () -> denied.selectCustomProjection(false, "id"));
				assertThrows(PrivatePropertyUseRejected.class, () -> denied.forWebQuery(new WebQuery().orderAsc("entity")));
				// Ordinary entity projection remains valid; named-path privacy is not serialisation policy.
				denied.forWebQuery(new WebQuery());
				assertEquals(2, denied.selectCustomProjection(false, "entity").size());
				JPAQueryBuilder<PrivacyIdBase, PrivacyKey> privileged = new JPAQueryBuilder<>(session, base, true);
				privileged.forWebQuery(new WebQuery());
				assertEquals(2, privileged.selectIDs().size());
				JPAQueryBuilder<PrivacyIdSibling, PrivacyKey> safe = new JPAQueryBuilder<>(session, schemas.get(PrivacyIdSibling.class), false);
				safe.forWebQuery(new WebQuery());
				assertEquals(List.of(new PrivacyKey("public")), safe.selectIDs());
			}
		}
	}

	@Test
	public void polymorphicEmbeddedIdComponentsProtectTheWholeIdentifier()
	{
		try (SessionFactory factory = factory(ComponentIdBase.class, ComponentIdChild.class, ComponentIdSibling.class))
		{
			QEntityFactory schemas = new QEntityFactory(factory);
			var base = schemas.get(ComponentIdBase.class);
			assertThrows(PrivatePropertyUseRejected.class, () -> base.getProperty("id:part", false));
			assertThrows(PrivatePropertyUseRejected.class, () -> base.getProperty("id", false));
			assertThrows(PrivatePropertyUseRejected.class, () -> base.assertIdentifierAccessible(false));
			assertFalse(schemas.get(ComponentIdSibling.class).getProperty("id:part", false).isSchemaPrivate());
			schemas.get(ComponentIdSibling.class).assertIdentifierAccessible(false);
		}
	}

	@Embeddable @Access(AccessType.PROPERTY)
	public static class ComponentKey implements Serializable
	{
		private String part;
		public String getPart() { return part; } public void setPart(String part) { this.part = part; }
		@Override public boolean equals(Object other) { return other instanceof ComponentKey key && Objects.equals(part, key.part); }
		@Override public int hashCode() { return Objects.hash(part); }
	}
	public static class PrivateComponentKey extends ComponentKey
	{
		@Override @WebQueryPrivate public String getPart() { return super.getPart(); }
	}
	@Entity(name = "component_id_base") @jakarta.persistence.Inheritance
	public static abstract class ComponentIdBase
	{
		private ComponentKey id;
		@EmbeddedId public ComponentKey getId() { return id; } public void setId(ComponentKey id) { this.id = id; }
	}
	@Entity(name = "component_id_child") public static class ComponentIdChild extends ComponentIdBase
	{
		@Override public PrivateComponentKey getId() { return (PrivateComponentKey) super.getId(); }
	}
	@Entity(name = "component_id_sibling") public static class ComponentIdSibling extends ComponentIdBase {}

	@Embeddable @Access(AccessType.FIELD) public static class PrivacyInfo { public String value; @ManyToOne public Target target; }
	@Entity(name = "privacy_domain_base") @jakarta.persistence.Inheritance
	public static abstract class PrivacyBase
	{
		private Long id; private String secret; private Target peer; private PrivacyInfo info;
		private Set<String> tags = new HashSet<>();
		@Id public Long getId() { return id; } public void setId(Long id) { this.id = id; }
		public String getSecret() { return secret; } public void setSecret(String value) { secret = value; }
		@ManyToOne(fetch = FetchType.LAZY) public Target getPeer() { return peer; } public void setPeer(Target value) { peer = value; }
		@Embedded public PrivacyInfo getInfo() { return info; } public void setInfo(PrivacyInfo value) { info = value; }
		@ElementCollection public Set<String> getTags() { return tags; } public void setTags(Set<String> value) { tags = value; }
	}
	@Entity(name = "privacy_domain_child")
	public static class PrivacyChild extends PrivacyBase
	{
		@Override @WebQueryPrivate public String getSecret() { return super.getSecret(); }
		@Override @WebQueryPrivate public Target getPeer() { return super.getPeer(); }
		@Override @WebQueryPrivate public PrivacyInfo getInfo() { return super.getInfo(); }
		@Override @WebQueryPrivate public Set<String> getTags() { return super.getTags(); }
	}
	@Entity(name = "privacy_domain_sibling") public static class PrivacySibling extends PrivacyBase {}
	@Entity(name = "privacy_domain_holder") public static class PrivacyHolder { @Id public Long id; @ManyToOne public PrivacyBase target; }
	@Embeddable @Access(AccessType.FIELD) public static class PrivacyKey implements Serializable
	{
		public String part;
		public PrivacyKey() {} public PrivacyKey(String part) { this.part = part; }
		@Override public boolean equals(Object other) { return other instanceof PrivacyKey key && Objects.equals(part, key.part); }
		@Override public int hashCode() { return Objects.hash(part); }
	}
	@Entity(name = "privacy_id_base") @jakarta.persistence.Inheritance
	public static abstract class PrivacyIdBase
	{
		private PrivacyKey id;
		@EmbeddedId public PrivacyKey getId() { return id; } public void setId(PrivacyKey id) { this.id = id; }
	}
	@Entity(name = "privacy_id_child") public static class PrivacyIdChild extends PrivacyIdBase
	{
		@Override @WebQueryPrivate public PrivacyKey getId() { return super.getId(); }
	}
	@Entity(name = "privacy_id_sibling") public static class PrivacyIdSibling extends PrivacyIdBase {}

	public interface Contract {}
	@Entity(name = "association_target")
	public static class Target implements Contract { @Id public Long id; }
	@Embeddable
	public static class Info
	{
		@ManyToOne(targetEntity = Target.class) @JoinColumn(name = "embedded_target_id") public Contract target;
	}
	@Entity(name = "association_owner")
	public static class Owner
	{
		@Id public Long id;
		@ManyToOne(targetEntity = Target.class) @JoinColumn(name = "primary_target_id") public Contract primary;
		@ManyToMany(targetEntity = Target.class) public Set<Contract> targets = new HashSet<>();
		@Embedded public Info info;
	}
	@Embeddable
	public static class RelationKey implements Serializable
	{
		@ManyToOne @WebQueryPrivate public Target target;
		public Long sequence;
		@Override public boolean equals(Object other) { return other instanceof RelationKey key && Objects.equals(target, key.target) && Objects.equals(sequence, key.sequence); }
		@Override public int hashCode() { return Objects.hash(target, sequence); }
	}
	@Entity(name = "relation_key_owner")
	public static class RelationKeyOwner { @EmbeddedId public RelationKey id; }
	@Entity(name = "private_association_target")
	public static class PrivateTarget { @Id @WebQueryPrivate public Long id; }
	@Entity(name = "related_owner")
	public static class RelatedOwner { @Id public Long id; @ManyToOne public PrivateTarget target; }

	public interface InfoContract {}
	@Embeddable public static class FirstInfo implements InfoContract { public String first; }
	@Embeddable public static class SecondInfo implements InfoContract { public String second; }
	public interface ValueContract { String getValue(); void setValue(String value); }
	@Embeddable @Access(AccessType.PROPERTY)
	public static class PublicValue implements ValueContract
	{
		private String value;
		public String getValue() { return value; }
		public void setValue(String value) { this.value = value; }
	}
	@Embeddable @Access(AccessType.PROPERTY)
	public static class PrivateValue implements ValueContract
	{
		private String value;
		@WebQueryPrivate public String getValue() { return value; }
		public void setValue(String value) { this.value = value; }
	}
	@Entity(name = "target_class_owner")
	public static class TargetClassOwner
	{
		@Id public Long id;
		@ElementCollection(targetClass = FirstInfo.class) public Set<InfoContract> first = new HashSet<>();
		@ElementCollection(targetClass = SecondInfo.class) public Set<InfoContract> second = new HashSet<>();
		@ElementCollection(targetClass = PublicValue.class) public Set<ValueContract> publicValues = new HashSet<>();
		@ElementCollection(targetClass = PrivateValue.class) public Set<ValueContract> privateValues = new HashSet<>();
	}
	@Embeddable public static class SizeInfo { public String name; }
	@Entity(name = "size_target")
	public static class SizeTarget
	{
		@Id public Long id;
		public String name;
		@ElementCollection public Set<SizeInfo> children = new HashSet<>();
		@WebQueryPrivate @ElementCollection public Set<SizeInfo> secret = new HashSet<>();
	}
	@Entity(name = "size_owner")
	public static class SizeOwner { @Id public Long id; @ManyToOne(fetch = FetchType.LAZY) public SizeTarget target; }
	@Entity(name = "inverse_parent")
	public static class InverseParent
	{
		@Id public Long id;
		@OneToOne(mappedBy = "parent", fetch = FetchType.LAZY) public InverseChild child;
	}
	@Entity(name = "inverse_child")
	public static class InverseChild
	{
		@Id public Long id;
		@OneToOne(fetch = FetchType.LAZY) public InverseParent parent;
	}
}
