package com.opencorporates.client;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.Scanner;
import java.util.logging.Logger;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;
import com.opencorporates.client.pojo.response.*;

/**
 * 
 * Simple Opencorporates.com client that read as input a list of company names,
 * querry the RESTfull search service ( and not the reconciliation api ! ) and output the
 * result. A very simple matching score based on the Levensthein distance of the
 * company names is used to rank the matching.
 * 
 * @Author: Antoine Logean
 * 
 */

public class OCClient implements OCClientConstants {

	private Properties properties;
	private String token;
	private String list_of_companies_input_filename;
	private String list_of_companies_output_filename;
	private String proxy;
	private boolean has_proxy;
	private int proxy_port;
	private String url_prefix;

	private final static Logger log = Logger
			.getLogger(OCClient.class.getName());

	/**
	 * Base class constructor that loads the needed parameters from the
	 * OCClient.properties file
	 */
	public OCClient() {
		loadClientProperties();
		log.info("Properties of the client loaded...");
	}

	/**
	 * 
	 * Simple company name normalisation pre-processing that - removes some
	 * "blank" characters - removes some trivial meaningless company name parts
	 * (like "LIMITED" or "LMD")
	 * 
	 * @param name
	 * @return
	 */
	public String normalizeName(String name) {

		// removing some "blank" characters
		name = name.replaceAll(",", " ");
		name = name.replaceAll("/", " ");
		name = name.replaceAll("\\.", " ");
		name = name.replaceAll("'", " ");
		name = name.replaceAll("-", " ");
		
		// remove any thing that is included in brakets (like (NO LONGER VALID))
		name = name.replaceAll("\\(.*\\)", "");
		
		// remove single braket (strange but can happen ...)
		name = name.replaceAll("\\(", "");
		name = name.replaceAll("\\)", "");

		// removing some company name specific meaningless words
		name = name.replaceAll("LIMITED", "");
		name = name.replaceAll("LTD", "");

		// remove tail and head white spaces
		name = name.trim();

		// put everything to lower case
		name = name.toLowerCase();

		return name;
	}

	/**
	 * Compute the Levnshtein distance from two strings (here company names).
	 * 
	 * Implementation taken from
	 * http://rosettacode.org/wiki/levenshtein_distance#Java
	 * 
	 * @param source
	 *            company name
	 * @param target
	 *            company name
	 * 
	 * @return integer value, 0 corresponding to a perfect match.
	 */
	public int computeLevenshteinDistance(String source, String target) {
		source = source.toLowerCase();
		target = target.toLowerCase();

		int[] costs = new int[target.length() + 1];
		for (int i = 0; i <= source.length(); i++) {
			int lastValue = i;
			for (int j = 0; j <= target.length(); j++) {
				if (i == 0)
					costs[j] = j;
				else {
					if (j > 0) {
						int newValue = costs[j - 1];
						if (source.charAt(i - 1) != target.charAt(j - 1))
							newValue = Math.min(Math.min(newValue, lastValue),
									costs[j]) + 1;
						costs[j - 1] = lastValue;
						lastValue = newValue;
					}
				}
			}
			if (i > 0)
				costs[target.length()] = lastValue;
		}
		return costs[target.length()];
	}

	/**
	 * main method that do all the job
	 */
	public void start_reconciliation() {

		File file = new File(this.list_of_companies_input_filename);
		log.info("Company names readed from "
				+ this.list_of_companies_input_filename);

		CloseableHttpClient httpclient = HttpClients.createDefault();

		HttpGet request = null;

		try {
			PrintWriter writer = new PrintWriter(
					this.list_of_companies_output_filename, "UTF-8");
			log.info("Results written in "
					+ this.list_of_companies_output_filename);

			// write the column headers in the output file
			writer.write(OUTPUT_HEADERS);

			HttpHost target = new HttpHost(BASE_URL, 80, "http");

			RequestConfig config = RequestConfig.DEFAULT;

			config = setup_proxy(config);

			Scanner inputStream = new Scanner(file);

			inputStream.useDelimiter(";");

			// hashNext() loops line-by-line
			while (inputStream.hasNext()) {

				// read single line, put in string
				String original_input_company_name = inputStream.next();

				String normalized_company_name = normalizeName(original_input_company_name);
				String data = normalized_company_name.replaceAll("\\s+", "+")
						+ URL_PREFIX;
				log.info("querry for " + original_input_company_name);
				if (data != "") {
					log.info("GET " + URL_BLOCK_SEARCH_ACTIVE_COMPANIES + data
							+ "...");
					request = new HttpGet(URL_BLOCK_SEARCH_ACTIVE_COMPANIES
							+ data);
					if (this.has_proxy)
						request.setConfig(config);
					CloseableHttpResponse response = httpclient.execute(target,
							request);
					try {
						HttpEntity entity = response.getEntity();
						if (entity != null) {
							String result = EntityUtils.toString(entity);
							Gson gson = new Gson();
							OCResponse resp = gson.fromJson(result,
									OCResponse.class);
							int match = resp.results.companies.length;
							writer.write(original_input_company_name
									+ ";;;;;;;\n");
							if (match > 0) {
								log.info(match + " found...");
								for (OCompany company : resp.results.companies) {
									toCsv(writer, company,
											normalized_company_name, null);
								}
							}
						}
					} finally {
						response.close();
					}
				}
			}
			// after loop, close scanner
			inputStream.close();
			writer.close();

		} catch (FileNotFoundException e) {
			log.severe("input file not found.");
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			log.severe("Encoding problem.");
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			log.severe("Http protocol errror");
			e.printStackTrace();
		} catch (IOException e) {
			log.severe("IO error");
			e.printStackTrace();
		} finally {
			try {
				httpclient.close();
			} catch (IOException e) {
				log.severe("IO error by closing the http client");
				e.printStackTrace();
			}

		}
	}

	/**
	 * Setup the proxy
	 * 
	 * @param config
	 * @return
	 */
	private RequestConfig setup_proxy(RequestConfig config) {
		if (this.has_proxy) {
			HttpHost proxy = new HttpHost(this.proxy, this.proxy_port, "http");
			config = RequestConfig.custom().setProxy(proxy).build();
		}
		return config;
	}

	/**
	 * write a matched result in the output csv file. The result can be filtered
	 * by country.
	 * 
	 * @param writer
	 * @param company
	 * @param company_name_ref
	 * @param country_filter
	 */
	public void toCsv(PrintWriter writer, OCompany company,
			String company_name_ref, String country_filter) {
		boolean flag = true;
		if (country_filter == null) {
			flag = true;
		} else {
			if (country_filter == company.company.jurisdiction_code) {
				flag = true;
			} else
				flag = false;
		}
		if (flag) {
			String company_output = ";"
					+ company.company.company_number
					+ ";"
					+ company.company.jurisdiction_code
					+ ";"
					+ company.company.branch_status
					+ ";"
					+ company.company.company_type
					+ ";"
					+ company.company.name
					+ ";"
					+ computeLevenshteinDistance(
							normalizeName(company.company.name),
							normalizeName(company_name_ref)) + ";"
					+ company.company.created_at + ";"
					+ company.company.registry_url + ";"
					+ company.company.opencorporates_url;
			System.out.println(company_output);
			writer.write(company_output + "\n");
		}
	}

	/**
	 * loads the needed parameters from the OCClient.properties file and set up
	 * the corresponding instance variables.
	 */
	private void loadClientProperties() {

		this.properties = new Properties();

		InputStream in = getClass().getResourceAsStream("OCClient.properties");

		try {
			this.properties.load(in);
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		// setting the input/output files
		this.list_of_companies_input_filename = this.properties
				.getProperty("input_filename");
		System.out.println(this.list_of_companies_input_filename);
		this.list_of_companies_output_filename = this.properties
				.getProperty("output_filename");

		// setting the proxy
		if (this.properties.containsKey("proxy")) {
			this.proxy = this.properties.getProperty("proxy");
			this.has_proxy = true;
			if (this.properties.containsKey("proxy_port")) {
				this.proxy_port = Integer.parseInt(this.properties
						.getProperty("proxy_port"));
			} else {
				this.proxy_port = 8080;
			}
		} else {
			this.proxy = null;
			this.has_proxy = false;
		}

		// setting the url prefix with optional api token
		if (this.properties.containsKey("token")) {
			this.token = this.properties.getProperty("token");
			this.url_prefix = "&api_token=" + this.token + "&utf8=%E2%9C%93";
		} else {
			this.token = null;
			this.url_prefix = "&utf8=%E2%9C%93";
		}
	}

	public static void main(String[] args) {

		OCClient oc = new OCClient();
		oc.start_reconciliation();

	}

}
