package io.github.mainstringargs;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.github.underscore.lodash.U;

/**
 * The Class Converter.
 */
public class Converter {

	/**
	 * To json.
	 *
	 * @param inputXml the input xml
	 * @return the string
	 */
	public static String toJson(String inputXml) {
		String jsonConvertedString = U.xmlToJson(inputXml);
		return jsonConvertedString;

	}

	/**
	 * To xml.
	 *
	 * @param inputJson the input json
	 * @return the string
	 */
	public static String toXml(String inputJson) {
		String xmlConvertedString = U.jsonToXml(inputJson);
		return xmlConvertedString;
	}

	/**
	 * The main method.
	 *
	 * @param args the arguments
	 */
	public static void main(String[] args) {
		convert("build");
		convert("fetch");
	}

	/**
	 * Convert.
	 *
	 * @param file the file
	 */
	private static void convert(String file) {
		try {

			String content = new String(Files.readAllBytes(Paths.get(file + ".xml"))).replaceAll("\r\n", "\n");

			String jsonString = toJson(content);

			String unixText = jsonString.replaceAll("\r\n", "\n");

			FileWriter writer = new FileWriter(new File(file + ".json"));

			writer.append(unixText);
			writer.close();

			writer = new FileWriter(new File(file + "_converted.xml"));

			String xmlConvertedString = toXml(jsonString);

			writer.append(xmlConvertedString);
			writer.close();

		} catch (Exception je) {
			je.printStackTrace();
		}
	}
}
