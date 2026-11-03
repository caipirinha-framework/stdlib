# Guice Framework for building Light RESTful Java Apps
See [the project documentation](https://stdlib.readthedocs.io/en/latest/index.html), and [the project wiki](https://github.com/caipirinha-framework/stdlib/wiki).

The `jakarta` branch builds stdlib **15.0.0-SNAPSHOT** for Java 25 and later,
using Servlet 6.1 (Tomcat 11), Guice 7, RESTEasy 7, Hibernate ORM 7.4,
Jakarta JAXB 4 and MOXy 5.0.2. Rebuild consumers together: the public REST,
persistence and servlet APIs now use `jakarta.*`.

The root parent pins upstream ASM to 9.11.1 through the ASM BOM, aligning
MOXy and Guice on the same bytecode support libraries.

The reactor also packages OGNL 3.4.16 with a targeted generic method resolution
fix for Hibernate's persistent collections as `ognl:ognl:15.0.0-SNAPSHOT`.
Consumers must use this artifact instead of adding a separate upstream OGNL jar.

Use a Java 25 JDK for the shared artifacts:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -B clean install
```

Then build the `jakarta` branches of Job Manager (8.0.0-SNAPSHOT),
assembly-vcasod, assembly-signage and NENT (8.0.0-SNAPSHOT), in that order.
NENT's branch starts from `java-27`. The default bytecode target is 25;
the same artifacts run on Java 27. To test compilation and execution with
Java 27 bytecode, use a JDK 27 and `mvn -B clean verify -Djavalang.version=27`.
CI also tests JDK 27 with the default Java 25 target.

When porting another consumer:

- Convert the Jakarta EE APIs, including JAXB service descriptors and Jackson's
  Jakarta REST/JAXB providers. Java SE packages such as `javax.sql`,
  `javax.naming`, `javax.xml.parsers` and `javax.xml.transform` retain their names.
- Use `persist(entity)` for new entities. For detached entities, use
  `entity = dao.merge(entity)` and make further changes to the returned managed
  instance. Hibernate 7 removes `save`, `saveOrUpdate` and legacy Criteria.
- Preserve existing identity and sequence strategies. Hibernate's new default
  sequences differ from Hibernate 5's shared `hibernate_sequence`; map that
  sequence explicitly with allocation size 1 where existing databases use it.
- Replace removed versioned dialect names with `PostgreSQLDialect` or
  `SQLServerDialect`. Keep stdlib's `PostgresDialect` for existing `bytea` LOB
  mappings; it preserves both the column type and JDBC binding.

Tomcat 11 requires rebuilt Jakarta WARs. Use disposable databases to qualify
deployment and existing schema compatibility before upgrading a running stack.
Job Manager's `scripts/jakarta-runtime-image.sh` prepares the shared local
Tomcat 11 / Java 25–27 test image from the sibling `domains` bundle.
