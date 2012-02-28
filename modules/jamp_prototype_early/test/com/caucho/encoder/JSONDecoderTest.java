package com.caucho.encoder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

public class JSONDecoderTest {
	
	@Test
	public void simpleDecoderTest() throws Exception {
		Decoder decoder = new JSONDecoder();
		Map<String, Object> map = (Map<String, Object>) decoder.decodeObject("     { \"name\" \t\n : 5 }");
		System.out.println(map);
	}

	@Test
	public void simpleDecoderBasicTypes() throws Exception {
		Decoder decoder = new JSONDecoder();
		Map<String, Object> map = (Map<String, Object>) decoder.decodeObject("{" +
				                    "\"name\":\"rick\"," +
				                    "\"old\":false," +
				                    "\"age\":99," +
				                    "\"weight\":122.2," +
		"\"phoneNumber\":\t\n\"5205551212\"," +
		"\"mylist\":[1,2,3,4]} ");
		System.out.println(map);
		
		Double weight = (Double) map.get("weight");
		assertTrue("weight is 122.2", weight.doubleValue()==122.2);

		Integer age = (Integer) map.get("age");
		assertTrue("age is 99", age.intValue()==99);

		
		Boolean old = (Boolean) map.get("old");
		assertTrue("old is false", old.booleanValue()==false);
		
		String name = (String) map.get("name");
		
		assertEquals("name is Rick Hightower", name, "rick");
		
		List mylist = (List) map.get("mylist");
		
		
		assertEquals("list index 0 is 1", 1, mylist.get(0));
		

	}
	

	@Test
	public void simpleNestedObject() throws Exception {
		Decoder decoder = new JSONDecoder();
		Map<String, Object> map = (Map<String, Object>) decoder.decodeObject("{ " +
				                    "\"obj\": {\"age\":5, \"obj2\":{\"foo\":7}}," +
				                    "\"stop\":true" +
		"}");
		System.out.println(map);
	}
	
	@Test
	public void simpleJSONArray() throws Exception {
		Decoder decoder = new JSONDecoder();
		List<Object> list = (List<Object>) decoder.decodeObject("[0,1, 2, 3, 4, 5, 0]");
		System.out.println(list);
	}
	
	@Test
	public void jsonArrayWithObject() throws Exception {
		Decoder decoder = new JSONDecoder();
		List<Object> list = (List<Object>) decoder.decodeObject("[0,1, 2, 3, 4, 5, 0, {\"foo\":7}]");
		System.out.println(list);
	}
}
