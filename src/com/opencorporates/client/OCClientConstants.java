package com.opencorporates.client;

public interface OCClientConstants {
	
	public static String BASE_URL = "api.opencorporates.com";
	
	public static String URL_PREFIX = "&utf8=%E2%9C%93";
	
	public static String URL_BLOCK_SEARCH_ACTIVE_COMPANIES = "/v0.2/companies/search?current_status=Active&q=";
	
	public static String OUTPUT_HEADERS = "Input Company Name; company number; jurisdiction; branch status; company_type; name; matching score; created_at; registry_url; opencorporates_url\n".toUpperCase();

}
