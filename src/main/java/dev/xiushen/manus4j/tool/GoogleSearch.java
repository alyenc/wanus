/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.xiushen.manus4j.tool;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.xiushen.manus4j.tool.support.ToolExecuteResult;
import dev.xiushen.manus4j.tool.support.serpapi.SerpApiProperties;
import dev.xiushen.manus4j.tool.support.serpapi.SerpApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class GoogleSearch implements BiFunction<String, ToolContext, ToolExecuteResult> {

	private static final Logger log = LoggerFactory.getLogger(GoogleSearch.class);

	private final SerpApiService service;

	private static final String PARAMETERS = """
			{
			    "type": "object",
			    "properties": {
			        "query": {
			            "type": "string",
			            "description": "(required) The search query to submit to Google."
			        },
			        "num_results": {
			            "type": "integer",
			            "description": "(optional) The number of search results to return. Default is 10.",
			            "default": 10
			        }
			    },
			    "required": ["query"]
			}
			""";

	private static final String name = "google_search";

	private static final String description = """
			Perform a Google search and return a list of relevant links.
			Use this tool when you need to find information on the web, get up-to-date data, or research specific topics.
			The tool returns a list of URLs that match the search query.
			""";

	public static OpenAiApi.FunctionTool getToolDefinition() {
		OpenAiApi.FunctionTool.Function function = new OpenAiApi.FunctionTool.Function(description, name, PARAMETERS);
        return new OpenAiApi.FunctionTool(function);
	}

	public static FunctionToolCallback<String, ToolExecuteResult> getFunctionToolCallback() {
		return FunctionToolCallback.builder(name, new GoogleSearch())
			.description(description)
			.inputSchema(PARAMETERS)
			.inputType(String.class)
			.build();
	}

	private static final String SERP_API_KEY = System.getenv("SERP_API_KEY");

	public GoogleSearch() {
		service = new SerpApiService(new SerpApiProperties(SERP_API_KEY, "google"));
	}

	public ToolExecuteResult run(String toolInput) {
		log.info("GoogleSearch toolInput:" + toolInput);
		Type type = new TypeToken<Map<String, Object>>() {}.getType();
		Map<String, Object> toolInputMap = new Gson().fromJson(toolInput, type);
		String query = (String) toolInputMap.get("query");

		Integer numResults = 2;
		if (toolInputMap.get("num_results") != null) {
			numResults = (Integer) toolInputMap.get("num_results");
		}
		SerpApiService.Request request = new SerpApiService.Request(query);
		Map<String, Object> response = service.apply(request);

		if (response.containsKey("answer_box") && response.get("answer_box") instanceof List) {
			response.put("answer_box", ((List) response.get("answer_box")).getFirst());
		}

		String toret = "";
		if (response.containsKey("answer_box")
				&& ((Map<String, Object>) response.get("answer_box")).containsKey("answer")) {
			toret = ((Map<String, Object>) response.get("answer_box")).get("answer").toString();
		}
		else if (response.containsKey("answer_box")
				&& ((Map<String, Object>) response.get("answer_box")).containsKey("snippet")) {
			toret = ((Map<String, Object>) response.get("answer_box")).get("snippet").toString();
		}
		else if (response.containsKey("answer_box")
				&& ((Map<String, Object>) response.get("answer_box")).containsKey("snippet_highlighted_words")) {
			toret = ((List<String>) ((Map<String, Object>) response.get("answer_box")).get("snippet_highlighted_words"))
				.get(0);
		}
		else if (response.containsKey("sports_results")
				&& ((Map<String, Object>) response.get("sports_results")).containsKey("game_spotlight")) {
			toret = ((Map<String, Object>) response.get("sports_results")).get("game_spotlight").toString();
		}
		else if (response.containsKey("shopping_results")
				&& ((List<Map<String, Object>>) response.get("shopping_results")).get(0).containsKey("title")) {
			List<Map<String, Object>> shoppingResults = (List<Map<String, Object>>) response.get("shopping_results");
			List<Map<String, Object>> subList = shoppingResults.subList(0, 3);
			toret = subList.toString();
		}
		else if (response.containsKey("knowledge_graph")
				&& ((Map<String, Object>) response.get("knowledge_graph")).containsKey("description")) {
			toret = ((Map<String, Object>) response.get("knowledge_graph")).get("description").toString();
		}
		else if ((((List<Map<String, Object>>) response.get("organic_results")).get(0)).containsKey("snippet")) {
			toret = (((List<Map<String, Object>>) response.get("organic_results")).get(0)).get("snippet").toString();
		}
		else if ((((List<Map<String, Object>>) response.get("organic_results")).get(0)).containsKey("link")) {
			toret = (((List<Map<String, Object>>) response.get("organic_results")).get(0)).get("link").toString();
		}
		else if (response.containsKey("images_results")
				&& ((Map<String, Object>) ((List<Map<String, Object>>) response.get("images_results")).get(0))
					.containsKey("thumbnail")) {
			List<String> thumbnails = new ArrayList<>();
			List<Map<String, Object>> imageResults = (List<Map<String, Object>>) response.get("images_results");
			for (Map<String, Object> item : imageResults.subList(0, 10)) {
				thumbnails.add(item.get("thumbnail").toString());
			}
			toret = thumbnails.toString();
		}
		else {
			toret = "No good search result found";
		}
		log.warn("SerpapiTool result:" + toret);
		return new ToolExecuteResult(toret);
	}

	@Override
	public ToolExecuteResult apply(String s, ToolContext toolContext) {
		return run(s);
	}

}
