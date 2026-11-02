WebQuery
========

The guice-hibernate plugin provides a number of convenience functions over Hibernate. One of these is a very simple, unified way to run arbitrary queries in a way that queries can be taken from the end-user and augmented (e.g. with limits, or with access control constraints) and executed.

This system generates a JPA Criteria Query, which is then translated into SQL by Hibernate.


Fields, Operators and Values
----------------------------

Field References
****************

Field references are usually as simple as "relation.otherRelation.field". This will automatically create a join via "relation" and then "otherRelation" before specifying the "field" field (N.B. hibernate field naming, not database column names are used).
See :ref:`Special Field References<SpecialFieldReferences>` for more advanced use.

Embedded values use colon-separated names, for example ``address:city`` or
``details:state``. Nested embedded values can use several segments. An old
flattened name remains accepted when it identifies exactly one property; use
the qualified name when two embedded values contain the same property name.

``@WebQueryPrivate`` excludes a named property from constraints, ordering,
projections and explicit fetch requests. The restriction follows overridden
getters and private embedded containers, including shortcut paths such as
``relation:id``. A base-type query can return subclass instances, so its schema
also protects paths declared private by a concrete subclass. Querying a safe
sibling or a narrower subclass uses that type's own result domain. The same
rule applies when navigating an association targeting the base type.
Internal default fetching and ordinary entity serialisation
retain their application-defined behaviour; this annotation is not a JSON/XML
serialisation policy.


Operators
*********

Null And Not Null
'''''''''''''''''

There are 2 special values for a field:

 * **_null**: if used, translates to an IS NULL database constraint
 * **_notnull**: if used, translates to an IS NOT NULL database constraint

Operators on values
'''''''''''''''''''

Many operators are supported to simplify field constraints by providing a prefix to the value:

 * **equals**: prefix `_f_eq_`, also by specifying no prefix, since it is the default function (e.g. `visibility=_f_eq_PUBLIC`)
 * **not equals**: prefix `_f_neq_` (e.g. `visibility=_f_neq_PRIVATE`)
 * **starts with**: prefix `_f_starts_` (only possible on string fields) (e.g. `text=_f_starts_four score and seven years ago`)
 * **not starts with**: prefix `_f_nstarts_` (only possible on string fields) (e.g. `text=_f_nstarts_four score and seven years ago`)
 * **contains**: prefix `_f_contains_` (only possible on string fields, uses SQL LIKE so this is a very slow operation) (e.g. `text=_f_contains_java`)
 * **not contains**: prefix `_f_ncontains_` (only possible on string fields, uses SQL LIKE so this is a very slow operation) (e.g. `text=_f_ncontains_python`)
 * **range**: prefix `_f_range_`, with the value expressed as MIN..MAX (e.g. `quantity=_f_range_1..100`, `id=_f_range_alpha..omega`)
 * **greater than**: prefix `_f_gt_`
 * **greater than or equal to**: prefix `_f_ge_`
 * **less than**: prefix `_f_lt_`
 * **less than or equal to**: prefix `_f_le_`

Operators on field references
'''''''''''''''''''''''''''''

Field reference operators allow comparing the values of two fields

 * **fields equal**: form `_f_eqref_(other field name)` - matches only cases where 2 named fields have the same value
 * **fields not equal**: form `_f_neqref_(other field name)` - matches only cases where 2 named fields do not have the same value
 * **field less than other field**: form `_f_ltref_(other field name)` - where field is less than `(other field name)`
 * **field less than or eq other field**: form `_f_leref_(other field name)` - where field is less than or equal to `(other field name)`
 * **field greater than other field**: form `_f_gtref_(other field name)` - where field is greater than `(other field name)`
 * **field greater than or eq other field**: form `_f_geref_(other field name)` - where field is greater than or equal to `(other field name)`
 

Values
******

Data Types
''''''''''
The data type is picked up and parsed based on the Hibernate field type. The supported types are:
 * **string**
 * **number**: encoded in decimal
 * **boolean**: encoded as case insensitive true/false, yes/no, on/off (canonical representation being true/false)
 * **date**: encoded as an ISO date-with-optional-time, parsed by Joda Time (e.g. `2001-01-01` or `2001-01-01T09:00:00.25+0100`). Special values are provided, `now` (for the current instant in time), `today` (for the first instant of today), `tomorrow` (for the first instant of tomorrow) and `yesterday` (for the first instant of yesterday)
 * **uuid**: encoded as text (e.g. `3F2504E0-4F89-41D3-9A0C-0305E82C3301`)
 * **enum**: encoded as case-sensitive enum value
 * **timecode**: encoded as SMPTE timecode with @timebase at the end `hh:mm:ss:ff@nom[:denom]` (e.g. `01:00:00:04@25` for 1 hour and 24 frames at 25 FPS)
 * **sample count**: encoded as `samples@timebase` (e.g. `1920@48000` for 1920 samples at 48kHz)

DateTime Expression (Date Maths)
''''''''''''''''''''''''''''''''

Queries are often needed to express rich behaviour with calendar dates and times. For this reason, a query may express either an absolute timestamp or a datetime expression.

Absolute datetimes can be expressed as either a solo date or as an ISO8601 datetime with a timezone:
 - 2019-01-01 which is the 1st of January 2019 at 00:00:00 (the first instant of the day) in the server's timezone
 - 2019-01-01T00:00:00Z which is a precise time in UTC

Anchors
"""""""

Relative times can use a number of anchor datetimes:
 - **now**: the time when the query was processed
 - **today**: the first moment of the current day (in the server's configured timezone)
 - **yesterday**: the first moment of the previous day (in the server's configured timezone)
 - **tomorrow**: the first moment of the following day (in the server's configured timezone)
 - **sow**: the first moment of the first Monday of this week (in the server's configured timezone)
 - **som**: the first moment of the 1st day of this month (in the server's configured timezone)
 - **soy**: the first moment of the 1st day of this year

Add/Subtract Period
"""""""""""""""""""

In addition, an anchor can be modified using the form "anchor-(ISO period)". For example, "today-P2D" is today minus 2 days. The ISO8601 period has the form "PnYnMnDTnHnMnS", with empty field omitted.


Special Field References
************************
.. _SpecialFieldReferences:


Collection Size
''''''''''''''''

For collection relations, the special field "relation:size" can be used to build constraints on the number of elements in the collection.


Minimising joins for relation ID constraint
'''''''''''''''''''''''''''''''''''''''''''

To minimise the number of joins created for certain criteria it may be advantageous to use a special way of referring to the ID of a relation. The typical way to reference a relation's ID is "relation.id", however this creates a join and then constrains the ID column on the table we have joined to; by expressing this instead as "relation:id", the constraint will be to the local ID column in the source table, with no need to join (can be useful when needing to write a query that will use a constrained index if the planner is not able to determine this itself)


Named Joins
'''''''''''

Occasionally it is necessary to apply multiple constraints to the same member of a collection. By suffixing a relation name with a square-bracketed name, you can reference a particular join. For instance:

::

	children[a].name=_f_starts_A
	children[a].age=15


Will only match a parent having a 15-year-old child whose name starts with the letter "A".

By contrast, the following two queries are identical, and will find a parent with a 15-year-old child and with a child whose name starts with "A".

::

	children.name=_f_starts_A
	children.age=15
	
	OR
	
	children[a].name=_f_starts_A
	children[b].age=5

This named join functionality can be used multiple times, e.g. "children[a].teachers[a]" will allow you to add particular constraints to match a teacher of a given child. This could look like:

::

	children[a].name=_f_starts_A
	children[a].age=15
	children[a].teacher[a].subject=English
	children[a].teacher[a].degree=PhD

The above query would match parents having a 15-year old child whose name starts with A, who has an English teacher with a PhD.


Advanced Fetching - Named Columns
*********************************

If only particular database columns are required, and not whole entities, you can set the "fetch" control parameter to a comma-separated list of fields/relations to return (for whole relations, using "relation.entity").

Entity Pagination and Collection Ordering
****************************************

Entity and identifier searches count distinct root entities. Matching several
children does not duplicate a parent's identifier or consume extra page slots.
When a collection join or fetch affects pagination, the automatic strategy
selects the ordered identifier page before fetching the entities and their
requested relationships.

An ascending order on a collection property uses the minimum matching value
per entity; descending order uses the maximum. Named joins determine which
child rows participate. Null ordering follows the database's rules. Identifier
tie-breakers make page boundaries deterministic when requested sort values
are equal. Projection queries retain their requested row and DISTINCT semantics.

Requested relationships are loaded completely, including children that did not
match a collection constraint. Fetching a bag together with another collection
uses separate collection loads to avoid multiplying bag elements. This preserves
duplicates actually stored in the bag, and may issue a secondary select per
owner when Hibernate batching is not configured. Safe fetch graphs retain join
fetching.

Query-builder terminal operations use independent criteria trees. Selecting
entities, identifiers, counts or projections does not change a later operation
or a previously created Hibernate query.

JSON Transport
**************

JSON constraints retain their existing shape: a constraint has ``field`` and
``function``, while a group has ``operator`` and ``constraints``. Nested groups
and negation do not require an added type discriminator. Paging values inside
the root ``constraints`` object are authoritative; duplicate convenience
``limit``, ``offset`` and ``computeSize`` fields are ignored on input.

``expand`` and ``dbfetch`` accept comma-separated strings or arrays of strings.
An empty array explicitly requests no relationships, equivalent to ``"none"``;
null retains the default behaviour. Blank path elements are rejected.


Query String Format
-------------------
One of the best uses of this system is to allow queries via a simple GET request. In this mode, queries are composed of a list of key=value pairs passed in via Query String, with special key values for control parameters (such as offset and limit). Very simple AND and OR rules are in place here - keys=value pairs with identical keys are ORred together, after which point all key=value pairs are ANDed together.

In this mode, control parameters (such as offset, limit and order) are mapped to special query string names, prefixed with an underscore character.


Control Parameters
******************

Control parameters are as follows:
 * **_offset**: the start result in the result set
 * **_limit**: the maximum result set size. If set to -1 then no result rows will be returned (useful with _compute_size=true if only a count is desired)
 * **_order**: used to order the result set. It can be repeated to order by a number of fields. Has the form "col asc" or "col desc"
 * **_compute_size**: set to true to compute a total resultset size in addition to fetching back some results
 * **_log_sql**: set to true to log the SQL executed as a result of this query. Useful for figuring out performance issues
 * **_log_performance**: set to true to log performance metrics on this query execution
 * **_expand**: a comma-separated list of relations to expand when serialising the resultset
 * **_fetch**: either "id", "entity" or a comma-separated list of arbitrary fields to retrieve
 * **_dbfetch**: a comma-separated list of relations to eagerly join to when querying
 * **_class**: for entities with type hierarchies, the subclass to return, and to base this query on (value uses discriminator, not Java class name)
 * **_name**: an optional query name designed to allow server-side acceleration or tuning of fixed-format queries

Example
*******

Consider an entity model describing blog posts for the following example Query String variant (for easier reading, this has been expressed unencoded and with a separate line per key value pair):

::
	
	_offset=0
	_limit=250
	_order=datePosted DESC
	_order=id ASC
	author.first_name=_f_neq_Alice
	datePosted=_f_range_2014-01-01..2014-02-01
	deleted=false
	postType=PUBLIC
	text=_f_contains_java
	text=_f_contains_python

The above query will search for the first 250 posts (in descending date posted order, then id order for posts with identical post dates) that match the following query (described in pseudo-SQL/HQL):
	
::
	
	postType = PostType.PUBLIC
	AND deleted = false
	AND datePosted BETWEEN 2014-01-01T00:00:00Z AND 2014-02-01T00:00:00Z
	AND author.first_name != 'Alice'
	AND (text CONTAINS java OR text CONTAINS python)


The generated Hibernate Criteria will automatically join to the `author` entity.


XML Format
----------

The XML format is very close to the native Java representation of a query; proper documentation on this is forthcoming. Here is what the example query from the previous section looks like in XML form.

.. code-block:: xml

	<?xml version="1.0" encoding="UTF-8"?>
	<WebQuery xmlns:q="http://ns.peterphi.com/stdlib/webquery/1.0" fetch="id" logSQL="false">
		<q:constraints offset="0" limit="250" computeSize="false">
			<q:Constraint field="postType" function="EQ" value="PUBLIC"/>
			<q:Constraint field="deleted" function="EQ" value="false"/>
			<q:Constraint field="author.first_name" function="NEQ" value="Alice"/>
			<q:Constraint field="datePosted" function="RANGE" value="2014-01-01T00:00:00Z" value2="2014-02-01T00:00:00Z"/>
			<q:ConstraintsGroup operator="OR">
				<q:Constraint field="someRelation:id" function="CONTAINS" value="java"/>
				<q:Constraint field="someRelation:id" function="CONTAINS" value="python"/>
			</q:ConstraintsGroup>
		</q:constraints>
		<q:ordering>
			<q:order field="id" direction="asc" />
		</q:ordering>
	</WebQuery>


Java Use
--------

The WebQuery type may be instantiated in Java and helper functions used to generate criteria and groups. Here is what the example query from the previous section looks like in Java form.

.. code-block:: java
	
	dao.find(new WebQuery()
	                 .eq("postType", PostType.PUBLIC)
	                 .eq("deleted", false)
	                 .neq("author.first_name", "Alice")
	                 .range("datePosted", "2014-01-01T00:00:00Z", "2014-02-01T00:00:00Z")
	                 .contains("java", "python") // Automatically creates an OR group
	                 .orderAsc("id")
	                 .limit(250));

Text Format
-----------

The text format provides a simple but expressive syntax similar to a basic SQL query. In particular it allows combinations of ANDs and ORs, as well as NOTs to form complex queries, which can then be supplied as a Query String.
This query format is intended for human use, not for programmatic access - in the latter case, the XML form should be preferred.

and strings can optionally be quoted - the following strings are treated identically:

.. code-block:: sql

	Alice
	"Alice"
	'Alice'
	`Alice`

Quoted strings must start and end with the same character, and may not contain that character (so a string that must contain double-quotes should be quoted with a ' or ` character. Operators and keywords should be quoted for clarity (unquoted strings otherwise could lead to confusing error messages in the case of a syntax error) but this is not mandatory.


Here's the format listed above in the XML Format, expressed as a text query:


.. code-block:: sql

	postType = PUBLIC
	AND deleted = false
	AND author.first_name != Alice
	AND datePosted BETWEEN 2014-01-01T00:00:00Z and 2014-02-01T00:00:00Z
	AND (someRelation:id CONTAINS java OR someRelation:id CONTAINS python)
	ORDER BY id


In addition to allowing ANDs, ORs and NOTs to be arbitrarily combined, the text mode also allows the use of the IN function, just like the XML mode. The syntax for this is:

.. code-block:: sql

	id IN (1,2,3,4,5) AND parent:id NOT IN (7,8,9)

