package com.peterphi.std.util.jaxb;

import com.peterphi.std.util.DOMUtils;
import com.peterphi.std.util.jaxb.exception.SchemaValidationException;
import com.peterphi.std.util.jaxb.pkg1.SomeXml;
import com.peterphi.std.util.jaxb.type.MultiXSDSchemaFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test MultiXSDGenerator and MultiXSDValidator
 */
public class MultiXSDGeneratorAndValidatorTest
{
	/**
	 * N.B. xx has no namespace - xml:any is ##any rather than ##other if this test passes
	 */
	private static final String SAMPLE_XML = "<x:SomeXml xmlns:x=\"urn:pkg1\">\n" +
	                                         "\t<x:name>xxx</x:name>\n" +
	                                         "\t<x:other1><xxxxx xmlns=\"urn:some:made:up:xmlns\"/></x:other1>\n" +
	                                         "\t<x:other2><xx/></x:other2>\n" +
	                                         "</x:SomeXml>";


	/**
	 * Test that this outputs a Multi-XSD schema without errors, doesn't assert that the output should look a particular way
	 * (namespaces might be different with different jaxb versions)
	 *
	 * @throws Exception
	 */
	@Test
	public void testMultiXSDWithLoosenedConstraints() throws Exception
	{
		generateSchemaAndValidate(true);
	}


	/**
	 * Test that without loosenXmlAnyConstraints, schema validation fails (##any vs ##other in xml:any in the schema)
	 *
	 * @throws Exception
	 */
	@Test
	public void testMultiXSDWithStrictConstraints() throws Exception
	{
		assertThrows(SchemaValidationException.class, () -> generateSchemaAndValidate(false));
	}


	private void generateSchemaAndValidate(boolean loosenXmlAny) throws IOException
	{
		JAXBSerialiser serialiser = JAXBSerialiser.getInstance(MultiXSDSchemaFiles.class);

		final MultiXSDSchemaFiles schema = new MultiXSDGenerator().withLoosenXmlAnyConstraints(loosenXmlAny)
		                                                          .generate(JAXBSerialiser.getInstance(SomeXml.class));

		// Test that we can serialise and deserialise the schema
		{
			final String xml = serialiser.serialise(schema);

			MultiXSDSchemaFiles deserialised = (MultiXSDSchemaFiles) serialiser.deserialise(xml);
			assertNotNull(deserialised);
			assertEquals(2, deserialised.files.size());

			// Make sure the schemas have been deserialised
			assertNotNull(deserialised.files.get(0).schemaElement());
			assertNotNull(deserialised.files.get(1).schemaElement());
		}

		// Now test that we can validate a document against the generated schema
		XSDValidator validator = new XSDValidator(schema);


		// N.B. Should throw SchemaValidationException because <xx/> has no namespace (violating ##other)
		validator.validate(DOMUtils.parse(SAMPLE_XML));
	}
}
